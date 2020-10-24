package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.ExecutionResult;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.ToolchainLogger;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedToolchainData;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.wrappers.MyExec;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;
import cz.rion.buildserver.wrappers.MyFS;

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

		public int getGoodTests() {
			int ret = 0;
			for (int i = 0; i < full.length; i++) {
				if (full[i] != null) {
					if (full[i].passed) {
						ret++;
					}
				}
			}
			return ret;
		}

		public int getBadTests() {
			int ret = 0;
			for (int i = 0; i < full.length; i++) {
				if (full[i] != null) {
					if (full[i].passed) {
						continue;
					}
				}
				ret++;
			}
			return ret;
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
						boolean showStdin = false;
						if (full[i].expectedCode != full[i].returnedCode) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonNumber(full[i].expectedCode));
							codeObj.add("got", new JsonNumber(full[i].returnedCode));
							obj.add("code", codeObj);
							showStdin = true;
						}
						if (!full[i].expectedSTDOUT.equals(full[i].returnedSTDOUT)) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonString(full[i].expectedSTDOUT));
							codeObj.add("got", new JsonString(full[i].returnedSTDOUT));
							obj.add("stdout", codeObj);
							showStdin = true;
						}
						if (!full[i].expectedSTDERR.equals(full[i].returnedSTDERR)) {
							JsonObject codeObj = new JsonObject();
							codeObj.add("expected", new JsonString(full[i].expectedSTDERR));
							codeObj.add("got", new JsonString(full[i].returnedSTDERR));
							obj.add("stderr", codeObj);
							showStdin = true;
						}
						if (showStdin) {
							obj.add("stdin", new JsonString(full[i].STDIN));
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

		public MyExecResult execute(String stdin, String[] arguments, int timeout, Toolchain toolchain) throws CommandLineExecutionException {
			String[] runArgs = toolchain.runnerParams;
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

	private final class TestCollection {
		private final Map<String, List<GenericTest>> tests = new HashMap<String, List<GenericTest>>();
		private Map<String, GenericTest> mtest = new HashMap<String, GenericTest>();
	}

	private final CachedToolchainData2<TestCollection> Tests = new CachedToolchainDataWrapper2<TestCollection>(300, new CachedToolchainDataGetter2<TestCollection>() {

		@Override
		public CachedData<TestCollection> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {

			return new CachedDataWrapper2<>(refreshIntervalInSeconds, new CachedDataGetter<TestCollection>() {

				@Override
				public TestCollection update() {
					TestCollection collection = new TestCollection();
					collection.tests.clear();
					collection.mtest.clear();
					List<GenericTest> jsonTests = JsonTestManager.load(sdb, testDirectory, toolchain);
					for (GenericTest test : jsonTests) {
						String toolchain = test.getToolchain().toLowerCase();
						if (!collection.tests.containsKey(toolchain)) {
							collection.tests.put(toolchain, new ArrayList<GenericTest>());
						}
						collection.tests.get(toolchain).add(test);
						collection.mtest.put(test.getToolchain().toUpperCase() + "/" + test.getID().toLowerCase(), test);
					}
					for (List<GenericTest> entry : collection.tests.values()) {
						entry.sort(new Comparator<GenericTest>() {

							@Override
							public int compare(GenericTest o1, GenericTest o2) {
								return o1.getID().compareTo(o2.getID());
							}
						});
					}
					return collection;
				}

			});
		}
	});

	private final String testDirectory;
	private final StaticDB sdb;

	public TestManager(StaticDB sdb, String testDirectory) {
		this.testDirectory = testDirectory;
		this.sdb = sdb;
	}

	public void reloadTests() {

	}

	private static final List<GenericTest> emptyListOfTests = new ArrayList<>();

	public List<GenericTest> getAllTests(Toolchain toolchain) {
		TestCollection cache = Tests.get(toolchain);
		if (cache.tests.containsKey(toolchain.getName().toLowerCase())) {
			return cache.tests.get(toolchain.getName().toLowerCase());
		}
		return emptyListOfTests;
	}

	private static final Object globalSyncer = new Object();

	private static class StringWrapper {
		private String data;

		public StringWrapper(String data) {
			this.data = data;
		}

		public void set(String data) {
			this.data = data;
		}

		public String get() {
			return data;
		}
	}

	public static final class TestResults {
		public final int ResultCode;
		public final int GoodTests;
		public final int BadTests;
		public final String ResultDescription;
		public final String Details;

		private TestResults(int result, int good, int bad, String descr, String details) {
			this.ResultCode = result;
			this.GoodTests = good;
			this.BadTests = bad;
			this.ResultDescription = descr;
			this.Details = details;
		}
	}

	public TestResults run(final BadResults badResults, int builderID, Toolchain toolchain, String test_id, String asm, String login) {
		GenericTest test = null;
		synchronized (Tests.get(toolchain).tests) {
			String testKey = toolchain.getName().toUpperCase() + "/" + test_id.toLowerCase();
			if (Tests.get(toolchain).mtest.containsKey(testKey)) {
				test = Tests.get(toolchain).mtest.get(testKey);
			}
		}
		int code = 1;
		int good = 0;
		int bad = 0;
		final JsonArray rawMessage = new JsonArray(new ArrayList<JsonValue>());
		final StringWrapper message = new StringWrapper("<span class='log_err'>Neznámá chyba</span>");
		TestResult testResult = null;
		if (test == null) {
			code = 1;
			rawMessage.add(new JsonString("Uvedený test nebyl nalezen"));
			message.set("<span class='log_err'>Uvedený test nebyl nalezen</span>");
		} else {
			Toolchain runner = null;
			try {
				runner = sdb.getToolchain(test.getToolchain());
			} catch (NoSuchToolchainException err) {
				rawMessage.add(new JsonString("Neznámý toolchain: " + toolchain));
				message.set("<span class='log_err'>Neznámý toolchain: " + toolchain + "</span>");
			}
			if (runner != null) {
				ToolchainLogger errorLogger = new ToolchainLogger() {

					@Override
					public void logError(String error) {
						rawMessage.add(new JsonString(error));
						message.set("<span class='log_err'>" + error + "</span>");
					}

					@Override
					public BadResults getBadResults() {
						return badResults;
					}

				};

				String workingDirectory = new File("./tests/" + toolchain.getName() + "/" + builderID).getAbsolutePath();
				try {
					ExecutionResult result = runner.run(errorLogger, test, workingDirectory, asm, "", login);

					if (result.wasOK()) {
						TestInput input = new TestInput(workingDirectory, runner.getLastOutputFileName());
						testResult = test.perform(badResults, input);
						MyFS.deleteFileSilent(workingDirectory);
					}
					if (testResult != null) {
						code = testResult.passed ? 0 : 1;
						message.set(testResult.data);
						good = testResult.getGoodTests();
						bad = testResult.getBadTests();
					}
				} finally {
					MyFS.deleteFileSilent(workingDirectory);
				}
			}
		}
		String details = null;
		if (testResult != null) {
			details = testResult.getFailedDescriptionData().getJsonString();
		} else if (!rawMessage.Value.isEmpty()) {
			details = rawMessage.getJsonString();
		}
		return new TestResults(code, good, bad, message.get(), details);

	}

}
