package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;
import cz.rion.buildserver.wrappers.MyFS;

public class JsonTestManager {

	private static final class JsonTest implements AsmTest {

		private final String id;
		private final String initialASM;
		private final List<TestVerificationData> tests;
		private final String description;
		private final String title;
		private final String prepend;
		private final String append;
		private String finalASM;
		private final boolean hidden;

		@Override
		public String getID() {
			return id;
		}

		@Override
		public TestResult perform(TestInput input) {
			int total = tests.size();
			int passed = 0;
			TestResultsExpectations[] results = new TestResultsExpectations[tests.size()];
			int index = 0;
			for (TestVerificationData test : tests) {
				try {
					MyExecResult result = input.execute(test.stdin, test.arguments, test.timeout);
					TestResultsExpectations data = new TestResultsExpectations(test.code, result.returnCode, test.stdout, result.stdout, test.stderr, result.stderr);
					results[index] = data;
					if (!data.passed) {
						passed++;
						passed--;
					} else {
						passed++;
					}
				} catch (CommandLineExecutionException e) {
					e.printStackTrace();
					return new TestResult(finalASM, false, "<span class='log_err'>Nepoda�ilo se spustit test</span>", results);
				}
				index++;
			}
			if (passed == total) {
				return new TestResult(finalASM, true, "<span class='log_ok'>Test pro�el :)</span>", results);
			} else {
				int perc = (passed * 100) / total;
				return new TestResult(finalASM, false, "<span class='log_err'>Chyba: Pro�lo " + perc + "% test�!</span>", results);
			}
		}

		private JsonTest(String id, String title, String description, List<TestVerificationData> tests, String initialASM, String append, String prepend, boolean isHidden) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.tests = tests;
			this.initialASM = initialASM;
			this.prepend = prepend;
			this.append = append;
			this.finalASM = prepend + "\r\n" + initialASM + "\r\n" + append;
			this.hidden = isHidden;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public String getInitialCode() {
			return initialASM;
		}

		@Override
		public String getTitle() {
			return id + ": " + title;
		}

		@Override
		public String CodeValid(String asm) {
			if (prepend.contains("_main:") || prepend.contains("CMAIN") || append.contains("_main:") || append.contains("CMAIN")) {
				if (!asm.contains("_main:") && !asm.contains("CMAIN")) {
					return prepend + "\r\n" + asm + "\r\n" + append;
				} else {
					return null;
				}
			}
			this.finalASM = asm;
			return asm;
		}

		@Override
		public boolean isHidden() {
			return this.hidden;
		}
	}

	private static final class TestVerificationData {

		public final String stdin;
		public final String stdout;
		public final int timeout;
		public final String[] arguments;
		private String stderr;
		private int code;

		private TestVerificationData(String stdin, String stdout, String stderr, int code, int timeout, String[] arguments) {
			this.stdin = stdin;
			this.stdout = stdout;
			this.timeout = timeout;
			this.arguments = arguments;
			this.stderr = stderr;
			this.code = code;
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

	private static void fillFSTests(List<JsonObject> data, String directory) {
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
						data.add(val.asObject());
					}
				}
			}
		}
	}

	private static void fillDBTests(StaticDB sdb, List<JsonObject> data) {
		if (sdb == null) {
			return;
		}
		List<DatabaseFile> files = sdb.getFiles();
		for (DatabaseFile file : files) {
			String fname = file.FileName;
			if (fname.startsWith("tests/") && fname.endsWith(".json")) {
				try {
					FileInfo fileData = sdb.getFile(file.ID);
					JsonValue val = JsonValue.parse(fileData.Contents);
					if (val != null) {
						if (val.isObject()) {
							data.add(val.asObject());
						}
					}
				} catch (DatabaseException e) {
					continue;
				}
			}
		}
	}

	private static AsmTest ConvertJsonToTest(JsonObject obj) {
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
					tvd.add(new TestVerificationData(stdin, stdout, stderr, code, timeout, arguments));
				} else {
					testOk = false;
					break;
				}
			}
			if (!testOk) {
				return null;
			}

			String id = obj.getString("id").Value;
			String description = obj.getString("description").Value;
			String title = obj.getString("title").Value;
			String prepend = obj.containsString("prepend") ? obj.getString("prepend").Value : "";
			String append = obj.containsString("append") ? obj.getString("append").Value : "";

			String initialASM = obj.containsString("init") ? obj.getString("init").Value : "";
			boolean hidden = obj.containsNumber("hidden") ? obj.getNumber("hidden").Value == 1 : false;
			return new JsonTest(id, title, description, tvd, initialASM, append, prepend, hidden);
		}
		return null;
	}

	public static List<AsmTest> load(StaticDB sdb, String testDirectory) {
		List<AsmTest> lst = new ArrayList<>();
		List<JsonObject> tests = new ArrayList<>();
		fillFSTests(tests, testDirectory);
		fillDBTests(sdb, tests);
		for (JsonObject obj : tests) {
			AsmTest test = ConvertJsonToTest(obj);
			if (test != null) {
				lst.add(test);
			}
		}
		return lst;
	}

}
