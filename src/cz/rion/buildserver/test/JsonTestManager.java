package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyFS;

public class JsonTestManager {

	private static final class JsonTest implements AsmTest {

		private final String id;
		private final String initialASM;
		private final List<TestVerificationData> tests;
		private final String description;
		private final String title;

		@Override
		public String getID() {
			return id;
		}

		@Override
		public TestResult perform(TestInput input) {
			for (TestVerificationData test : tests) {
				try {
					MyExecResult result = input.execute(test.stdin, test.arguments, test.timeout);
					if (!result.stdout.equals(test.stdout) || !result.stderr.equals(test.stderr) || result.returnCode != test.code) {
						return new TestResult(false, "<span class='log_err'>Chyba: pro testovací vstupy nesouhlasí výstupy!</span>");
					}
				} catch (CommandLineExecutionException e) {
					return new TestResult(false, "<span class='log_err'>Nepodaøilo se spustit test</span>");
				}
			}
			return new TestResult(true, "<span class='log_ok'>Test prošel :)</span>");
		}

		private JsonTest(String id, String title, String description, List<TestVerificationData> tests, String initialASM) {
			this.id = id;
			this.title = title;
			this.description = description;
			this.tests = tests;
			this.initialASM = initialASM;
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

	public static List<AsmTest> load(String testDirectory) {
		List<AsmTest> lst = new ArrayList<>();
		Collection<File> all = new ArrayList<File>();
		try {
			collectDir(new File(testDirectory), all);
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
						JsonObject obj = val.asObject();
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
								continue;
							}

							String id = obj.getString("id").Value;
							String description = obj.getString("description").Value;
							String title = obj.getString("title").Value;

							String initialASM = obj.containsString("init") ? obj.getString("init").Value : "";
							lst.add(new JsonTest(id, title, description, tvd, initialASM));
						}
					}
				}
			}
		}
		return lst;
	}

}
