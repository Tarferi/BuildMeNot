package cz.rion.buildserver.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.wrappers.MyExec;
import cz.rion.buildserver.wrappers.NasmWrapper;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.NasmWrapper.RunResult;

public class TestManager {

	public static final class TestResult {
		public final boolean passed;
		public final String data;

		public TestResult(boolean passed, String data) {
			this.passed = passed;
			this.data = data;
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
			return MyExec.execute(workingDir, stdin, workingDir + "/" + exeName, arguments, timeout);
		}
	}

	private final List<AsmTest> tests = new ArrayList<>();
	private Map<String, AsmTest> mtest = new HashMap<>();

	private final String testDirectory;

	public TestManager(String testDirectory) {
		this.testDirectory = testDirectory;
		reloadTests();
	}

	public void reloadTests() {
		synchronized (tests) {
			tests.clear();
			mtest.clear();
			List<AsmTest> jsonTests = JsonTestManager.load(testDirectory);
			tests.addAll(jsonTests);

			for (AsmTest t : tests) {
				mtest.put(t.getID().toLowerCase(), t);
			}
		}
	}

	public List<AsmTest> getAllTests() {
		//reloadTests();
		return tests;
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
				TestResult testResult = null;
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
		return obj;
	}

}
