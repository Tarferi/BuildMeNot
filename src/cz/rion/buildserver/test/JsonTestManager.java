package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.utils.Pair;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;
import cz.rion.buildserver.wrappers.MyFS;

public class JsonTestManager {

	public static final class JsonTest implements GenericTest {

		private final String id;
		private final String initialASM;
		private final List<TestVerificationData> tests;
		private final String description;
		private final String title;
		private final String prepend;
		private final String append;
		private String finalASM;
		private final boolean hidden;
		private final boolean secret;
		private final String[] allowedInstructions;
		public final boolean replace;
		private final ReplacementEntry[] replacement;
		private final Toolchain toolchain;

		@Override
		public String getID() {
			return id;
		}

		public TestResult perform(BadResults badResults, TestInput input) {
			int total = tests.size();
			int passed = 0;
			TestResultsExpectations[] results = new TestResultsExpectations[tests.size()];
			int index = 0;
			SystemFailureMessage finalOsError = new SystemFailureMessage();
			for (TestVerificationData test : tests) {
				try {
					MyExecResult result = input.execute(test.stdin, test.arguments, test.timeout, toolchain);
					SystemFailureMessage osError = new SystemFailureMessage(result);
					if (osError.Type == SystemFailureMessageType.Segfault) {
						badResults.setNext(BadResultType.SegFault);
					} else if (osError.Type == SystemFailureMessageType.Timeout) {
						badResults.setNext(BadResultType.Timeout);
					}
					if (osError.Severity > finalOsError.Severity) {
						finalOsError = osError;
					}
					TestResultsExpectations data = new TestResultsExpectations(test.code, result.returnCode, test.stdout, result.stdout, test.stderr, result.stderr, test.stdin);
					results[index] = data;
					if (!data.passed) {
						passed++;
						passed--;
						if (test.isBase) {
							badResults.setNext(BadResultType.BadBase);
						}
					} else {
						passed++;
					}
				} catch (CommandLineExecutionException e) {
					e.printStackTrace();
					return new TestResult(finalASM, false, "<span class='log_err'>Nepodaøilo se spustit test kvùli interní chybì serveru</span>", results);
				}
				index++;
			}
			if (!finalOsError.IsNone()) {
				return new TestResult(finalASM, false, "<span class='log_err'>" + finalOsError.GetMessage() + "</span>", results);
			}
			if (passed == total) {
				return new TestResult(finalASM, true, "<span class='log_ok'>Test prošel :)</span>", results);
			} else {
				int perc = (passed * 100) / total;
				badResults.setNext(BadResultType.BadTests);
				return new TestResult(finalASM, false, "<span class='log_err'>Chyba: Prošlo " + perc + " % testù!</span>", results);
			}
		}

		private JsonTest(String id, Toolchain toolchain, String title, String description, List<TestVerificationData> tests, String initialASM, String append, String prepend, boolean isHidden, boolean isSecret, String[] allowedInstructions, boolean replace, ReplacementEntry[] replacement) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.tests = tests;
			this.initialASM = initialASM;
			this.prepend = prepend;
			this.append = append;
			this.finalASM = prepend + "\r\n" + initialASM + "\r\n" + append;
			this.hidden = isHidden;
			this.secret = isSecret;
			this.allowedInstructions = allowedInstructions;
			this.replace = replace;
			this.replacement = replacement;
			this.toolchain = toolchain;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public String getSubmittedCode() {
			return initialASM;
		}

		@Override
		public String getTitle() {
			return id + ": " + title;
		}

		private static final String getInvalidInstruction(String lt, String[] allowedInstructions) {
			boolean validStart = false;
			for (String instruction : allowedInstructions) {
				if (lt.startsWith(instruction.toLowerCase())) { // Could allow "MOVX" if "MOV" is allowed
					String next = lt.substring(instruction.length());
					if (next.length() == 0) {
						validStart = true;
						break;
					} else {
						char c = next.charAt(0);
						if (c == ' ' || c == '\r' || c == '\n' || c == '\t') {
							if (instruction.toLowerCase().startsWith("rep")) {
								String inv = getInvalidInstruction(next.trim(), allowedInstructions);
								if (inv != null) {
									return inv;
								}
							}
							validStart = true;
							break;
						}
					}
				}
			}
			if (!validStart) {
				return "Nepovolená instrukce: " + lt.trim().split(" ")[0].toUpperCase();
			}
			return null;
		}

		public String VerifyCode(BadResults badResults, String asm) {
			if (allowedInstructions != null) {
				boolean codeSection = true;
				String[] asmLine = asm.split("\n");
				for (String line : asmLine) {
					String lt = line.trim().toLowerCase();
					if (lt.contains(";")) {
						if (lt.trim().equals(";")) {
							continue;
						}
						lt = lt.split(";")[0].trim();
					}
					if (lt.contains(":")) {
						if (lt.endsWith(":")) {
							continue;
						}
						String[] d = lt.split(":");
						lt = d[d.length - 1].trim(); // Last
					}
					if (!lt.isEmpty()) {
						if (lt.startsWith("section")) {
							codeSection = lt.contains(".text");
						} else if (codeSection) {
							if (lt.trim().startsWith("%")) {
								if (!lt.trim().startsWith("%include")) {
									return null;
								}
							} else {
								String fail = getInvalidInstruction(lt, allowedInstructions);
								if (fail != null) {
									badResults.setNext(BadResultType.BadInstructions);
									return fail;
								}
							}
						}
					}
				}
			}

			if (prepend.contains("_main:") || prepend.contains("CMAIN") || append.contains("_main:") || append.contains("CMAIN")) {
				if (asm.contains("_main:") || asm.contains("CMAIN")) {
					return "Nesmí být definován vstupní bod programu";
				}
			}
			return null;
		}

		@Override
		public boolean isHidden() {
			return this.hidden;
		}

		@Override
		public boolean isSecret() {
			return secret;
		}

		public String[] getAllowedInstructions() {
			return allowedInstructions;
		}

		private static final Pattern pattern = Pattern.compile("\\%include +\"([^\\\"]+)\"", Pattern.MULTILINE);

		public String GetFinalCode(String login, String asm) {
			if (replace) {
				asm = asm.replaceAll("\\$LOGIN\\$", login);
			}
			asm = asm.replaceAll("(?i)cextern", "cextern");
			asm = asm.replaceAll("(?i)extern", "extern");
			if (replacement != null) {
				for (ReplacementEntry rep : replacement) {
					asm = asm.replaceAll(rep.source, rep.replacement);
				}
			}
			if (asm.toLowerCase().contains("extern")) {
				return null;
			}
			asm = prepend + "\r\n" + asm + "\r\n" + append;

			final Matcher matcher = pattern.matcher(asm);

			// The substituted value will be contained in the result variable
			String path = new File("./nasm/").getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\\\\";
			asm = matcher.replaceAll("%include \"" + path + "$1\"");

			this.finalASM = asm;
			return asm;
		}

		@Override
		public String getToolchain() {
			return toolchain.getName();
		}
	}

	private enum SystemFailureMessageType {
		None(0, ""), Timeout(5, "Tvému kódu vypršel pøidìlený èas na test. Optimalizuj ho a zkus to znovu..."), Segfault(10, "Segmentation fault"), PermissionDenied(20, "Interní selhání. Prosím nahlaš to @Tarferi"), StderrSomething(30, "");

		public final int Severity;
		private final String Message;

		private SystemFailureMessageType(int severity, String message) {
			this.Severity = severity;
			this.Message = message;
		}
	}

	private static final class SystemFailureMessage {
		private final SystemFailureMessageType Type;
		private final MyExecResult result;

		public final int Severity;

		private SystemFailureMessage(MyExecResult result) {
			this.result = result;
			this.Type = getOSError(result);
			this.Severity = Type.Severity;
		}

		public String GetMessage() {
			if (Type == SystemFailureMessageType.StderrSomething) {
				return result.stderr;
			}
			return Type.Message;
		}

		public boolean IsNone() {
			return Type == SystemFailureMessageType.None;
		}

		public SystemFailureMessage() {
			this.result = null;
			this.Type = SystemFailureMessageType.None;
			this.Severity = Type.Severity;
		}

		private SystemFailureMessageType getOSError(MyExecResult result) {
			String stderr = result.stderr.toLowerCase();
			if (stderr.contains("denied") || stderr.contains("permission")) {
				return SystemFailureMessageType.PermissionDenied;
			} else if (stderr.contains("segmentation")) {
				return SystemFailureMessageType.Segfault;
			} else if (result.returnCode == 50 || result.Timeout) {
				return SystemFailureMessageType.Timeout;
			} else if (result.returnCode < 0 || result.returnCode > 100) {
				return SystemFailureMessageType.Segfault;
			}

			return SystemFailureMessageType.None;
		}
	}

	private static final class TestVerificationData {

		public final String stdin;
		public final String stdout;
		public final int timeout;
		public final boolean isBase;
		public final String[] arguments;
		private String stderr;
		private int code;

		private TestVerificationData(String stdin, String stdout, String stderr, int code, int timeout, boolean base, String[] arguments) {
			this.stdin = stdin;
			this.stdout = stdout;
			this.timeout = timeout;
			this.arguments = arguments;
			this.stderr = stderr;
			this.code = code;
			this.isBase = base;
		}
	}

	private static final class ReplacementEntry {
		public final String source;
		public final String replacement;

		private ReplacementEntry(String source, String replacement) {
			this.source = source;
			this.replacement = replacement;
		}
	}

	private static void collectDir(File file, Collection<File> all) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				all.add(child);
				collectDir(child, all);
			}
		}
	}

	private static void fillFSTests(List<Pair<String, JsonObject>> data, String directory) {
		Collection<File> all = new ArrayList<File>();
		try {
			collectDir(new File(directory), all);
		} catch (Exception | Error e) {
		}
		for (File f : all) {
			if (f.getAbsolutePath().toLowerCase().endsWith(".json")) {
				String test;
				try {
					test = MyFS.readFile(f.getAbsolutePath());
				} catch (FileReadException e) {
					continue;
				}
				JsonValue val = JsonValue.parse(test);
				if (val != null) {
					if (val.isObject()) {
						data.add(new Pair<>(f.getAbsolutePath(), val.asObject()));
					}
				}
			}
		}
	}

	private static void fillDBTests(StaticDB sdb, List<Pair<String, JsonObject>> data, Toolchain toolchain) {
		if (sdb == null) {
			return;
		}
		List<DatabaseFile> files = sdb.getFiles();
		for (DatabaseFile file : files) {
			String fname = file.FileName;
			if (fname.startsWith("tests/") && fname.endsWith(".json")) {
				try {
					FileInfo fileData = sdb.getFile(file.ID, true, toolchain);
					JsonValue val = JsonValue.parse(fileData.Contents);
					if (val != null) {
						if (val.isObject()) {
							data.add(new Pair<>(fileData.FileName, val.asObject()));
						}
					}
				} catch (DatabaseException e) {
					continue;
				}
			}
		}
	}

	private static GenericTest ConvertJsonToTest(String path, JsonObject obj, StaticDB sdb) {
		// Get category name. Find last "tests/" from the end
		String[] tmp = path.split("tests\\/");
		String category = tmp[tmp.length - 1].split("\\/")[0];

		if (obj.containsArray("tests") && obj.containsString("id") && obj.containsString("description") && obj.containsString("title")) {
			List<JsonValue> tests = obj.getArray("tests").Value;

			List<TestVerificationData> tvd = new ArrayList<>();
			boolean testOk = true;
			for (JsonValue tst : tests) {
				if (!tst.isObject()) {
					testOk = false;
					break;
				}
				JsonObject tsto = tst.asObject();
				if (tsto.containsString("stdin") && tsto.containsString("stdout") && tsto.containsNumber("code") && tsto.containsString("stderr") && tsto.containsNumber("timeout")) {
					String stdin = tsto.getString("stdin").Value;
					String stdout = tsto.getString("stdout").Value;
					String stderr = tsto.getString("stderr").Value;
					int timeout = tsto.getNumber("timeout").Value;
					int code = tsto.getNumber("code").Value;
					String[] arguments = new String[0];
					if (obj.containsArray("arguments")) {
						JsonArray args = obj.getArray("arguments").asArray();
						int argsNum = 0;
						for (JsonValue arg : args.Value) {
							if (arg.isString() || arg.isBoolean() || arg.isNumber()) {
								argsNum++;
							}
						}
						arguments = new String[argsNum];
						int argsI = 0;
						for (JsonValue arg : args.Value) {
							if (arg.isString() || arg.isBoolean() || arg.isNumber()) {
								arguments[argsI] = arg.getJsonString();
								argsI++;
							}
						}
					}
					boolean base = false;
					if (tsto.containsNumber("base")) {
						base = tsto.getNumber("base").Value == 1;
					}
					tvd.add(new TestVerificationData(stdin, stdout, stderr, code, timeout, base, arguments));
				} else {
					testOk = false;
					break;
				}
			}
			if (!testOk) {
				return null;
			}

			String[] allowedInstructions = null;

			if (obj.containsArray("instructions")) {
				JsonArray allowedInstr = obj.getArray("instructions");
				allowedInstructions = new String[allowedInstr.Value.size()];
				int index = 0;
				for (JsonValue val : allowedInstr.Value) {
					allowedInstructions[index] = val.asString().Value;
					index++;
				}
			}

			ReplacementEntry[] replacement = null;
			if (obj.containsArray("replacement")) {
				JsonArray replacements = obj.getArray("replacement");
				replacement = new ReplacementEntry[replacements.Value.size()];
				int index = 0;
				for (JsonValue val : replacements.Value) {
					String source = val.asObject().getString("from").Value;
					String to = val.asObject().getString("to").Value;
					replacement[index] = new ReplacementEntry(source, to);
					index++;
				}
			}

			String id = obj.getString("id").Value;
			String description = obj.getString("description").Value;
			String title = obj.getString("title").Value;
			String prepend = obj.containsString("prepend") ? obj.getString("prepend").Value : "";
			String append = obj.containsString("append") ? obj.getString("append").Value : "";
			String toolchain = obj.containsString("toolchain") ? obj.getString("toolchain").Value : category;

			String initialASM = obj.containsString("init") ? obj.getString("init").Value : "";
			boolean hidden = obj.containsNumber("hidden") ? obj.getNumber("hidden").Value == 1 : false;
			boolean secret = obj.containsNumber("secret") ? obj.getNumber("secret").Value == 1 : false;
			boolean replace = obj.containsNumber("replace") ? obj.getNumber("replace").Value == 1 : false;

			Toolchain tc = null;
			try {
				tc = sdb.getToolchain(toolchain);
			} catch (NoSuchToolchainException e) {
			}
			if (tc != null) {
				return new JsonTest(id, tc, title, description, tvd, initialASM, append, prepend, hidden, secret, allowedInstructions, replace, replacement);
			}
		}
		return null;

	}

	public static List<GenericTest> load(StaticDB sdb, String testDirectory, Toolchain toolchain) {
		List<GenericTest> lst = new ArrayList<>();
		List<Pair<String, JsonObject>> tests = new ArrayList<>();
		fillFSTests(tests, testDirectory);
		fillDBTests(sdb, tests, toolchain);
		for (Pair<String, JsonObject> obj : tests) {
			GenericTest test = ConvertJsonToTest(obj.Key, obj.Value, sdb);
			if (test != null) {
				lst.add(test);
			}
		}
		return lst;
	}

}
