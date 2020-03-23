package cz.rion.buildserver.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.wrappers.MyExec;
import cz.rion.buildserver.wrappers.NasmWrapper;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;
import cz.rion.buildserver.wrappers.NasmWrapper.RunResult;

public class TestManager {

	public static final class TestResult {
		public final boolean passed;
		public final String data;
		private final TestResultsExpectations[] full;
		private String finalASM;

		public TestResult(String finalASM, boolean passed, String data, TestResultsExpectations[] full) {
			this.finalASM = finalASM;
			this.passed = passed;
			this.data = data;
			this.full = full;
		}

		public JsonObject getFailedDescriptionData() {
			JsonObject res = new JsonObject();
			JsonArray result = new JsonArray(new ArrayList<JsonValue>());
			res.add("final_code", new JsonString(finalASM));
			res.add("failed_tests", result);
			for (int i = 0; i < full.length; i++) {
				if (full[i] != null) {
					if (!full[i].passed) {
						JsonObject obj = new JsonObject();
						if (full[i].expectedCode != full[i].returnedCode) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonNumber(full[i].expectedCode));
							codeObj.add("got", new JsonNumber(full[i].returnedCode));
							obj.add("code", codeObj);
						}
						if (!full[i].expectedSTDOUT.equals(full[i].returnedSTDOUT)) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonString(full[i].expectedSTDOUT));
							codeObj.add("got", new JsonString(full[i].returnedSTDOUT));
							obj.add("stdout", codeObj);
						}
						if (!full[i].expectedSTDERR.equals(full[i].returnedSTDERR)) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonString(full[i].expectedSTDERR));
							codeObj.add("got", new JsonString(full[i].returnedSTDERR));
							obj.add("stderr", codeObj);
						}
						result.add(obj);
					}
				}
			}
			return res;
		}
	}

	public static final class TestInput {
		private final String exeName;
		private String workingDir;

		private TestInput(String workingDir, String exeName) {
			this.workingDir = workingDir;
			this.exeName = exeName;
		}

		public MyExecResult execute(String stdin, String[] arguments, int timeout) throws CommandLineExecutionException {
			String[] runArgs = Settings.getGExecutableParams();
			String[] nargs = new String[arguments.length + runArgs.length];
			System.arraycopy(runArgs, 0, nargs, 0, runArgs.length);
			System.arraycopy(arguments, 0, nargs, runArgs.length, arguments.length);
			for (int i = 0; i < nargs.length; i++) {
				nargs[i] = nargs[i].replace("$CWD$", workingDir);
			}
			String exeCmd = Settings.hasNoExecPath() ? exeName : workingDir + "/" + exeName;
			return MyExec.execute(workingDir, stdin, exeCmd, nargs, timeout);
		}
	}

	private final List<AsmTest> tests = new ArrayList<>();
	private Map<String, AsmTest> mtest = new HashMap<>();

	private final String testDirectory;
	private final StaticDB sdb;

	public TestManager(StaticDB sdb, String testDirectory) {
		this.testDirectory = testDirectory;
		this.sdb = sdb;
		reloadTests();
	}

	public void reloadTests() {
		synchronized (tests) {
			tests.clear();
			mtest.clear();
			List<AsmTest> jsonTests = JsonTestManager.load(sdb, testDirectory);
			tests.addAll(jsonTests);

			for (AsmTest t : tests) {
				mtest.put(t.getID().toLowerCase(), t);
			}
			sortTests();
		}
	}

	public List<AsmTest> getAllTests() {
		reloadTests();
		return tests;
	}

	private void sortTests() {
		tests.sort(new Comparator<AsmTest>() {

			@Override
			public int compare(AsmTest o1, AsmTest o2) {
				return o1.getID().compareTo(o2.getID());
			}
		});
	}

	public JsonObject run(int builderID, String test_id, String asm) {
		AsmTest test = null;
		synchronized (tests) {
			if (mtest.containsKey(test_id.toLowerCase())) {
				test = mtest.get(test_id.toLowerCase());
			}
		}
		int code = 1;
		String message = "<span class='log_err'>Neznámá chyba</span>";
		TestResult testResult = null;
		if (test == null) {
			code = 1;
			message = "<span class='log_err'>Uvedený test nebyl nalezen</span>";
		} else {
			asm = test.CodeValid(asm);
			if (asm == null) {
				message = "<span class='log_err'>Neplatný kód. Pravdìpodobnì nesmí být definované návìští _main ani CMAIN</span>";
			} else {
				RunResult result = null;
				try {
					result = NasmWrapper.run("./test" + builderID, asm, "", 2000, false, false);
				} catch (NasmExecutionException e) {
					code = 1;
					message = "<span class='log_err'>Nepodaøilo se pøeložit kód<br />" + e.getDescription().replaceAll("\n", "<br />") + "</span>";
				} catch (GoLinkExecutionException e) {
					message = "<span class='log_err'>Nepodaøilo se pøeložit kód</span>";
				} catch (RuntimeExecutionException e) { // Should never happen
					message = "<span class='log_err'>Nepodaøilo se pøeložit kód</span>";
				}
				if (result != null) {
					TestInput input = new TestInput(result.exePath, result.exeName);
					testResult = test.perform(input);
					NasmWrapper.clean(result.exePath);
				}
				if (testResult != null) {
					code = testResult.passed ? 0 : 1;
					message = testResult.data;
				}
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("code", new JsonNumber(code));
		obj.add("result", new JsonString(message));
		if (testResult != null) {
			obj.add("details", testResult.getFailedDescriptionData());
		}
		return obj;
	}

}
