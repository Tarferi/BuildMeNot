package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.ExecutionResult;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.ToolchainLogger;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
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

		public TestResult(boolean passed, String data, TestResultsExpectations[] full) {
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

		public JsonObject getFailedDescriptionData(String userCode) {
			JsonObject res = new JsonObject();
			JsonArray result = new JsonArray(new ArrayList<JsonValue>());
			res.add("final_code", new JsonString(userCode));
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
		private final String workingDir;

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

				private final UserContext toolchainContext = new UserContext() {

					@Override
					public Toolchain getToolchain() {
						return toolchain;
					}

					@Override
					public String getLogin() {
						return "root";
					}

					@Override
					public String getAddress() {
						return "0.0.0.0";
					}

					@Override
					public boolean wantCompressedData() {
						return false;
					}

				};

				@Override
				public TestCollection update() {
					TestCollection collection = new TestCollection();
					collection.tests.clear();
					collection.mtest.clear();
					List<GenericTest> jsonTests = JsonTestManager.load(files, sdb, testDirectory, toolchainContext);
					for (GenericTest test : jsonTests) {
						String toolchain = test.getToolchain().getName().toLowerCase();
						if (!collection.tests.containsKey(toolchain)) {
							collection.tests.put(toolchain, new ArrayList<GenericTest>());
						}
						collection.tests.get(toolchain).add(test);
						collection.mtest.put(test.getToolchain().getName().toUpperCase() + "/" + test.getID().toLowerCase(), test);
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

	private final VirtualFileManager files;

	public void reloadTests() {
		synchronized (Tests) {
			Tests.clear();
		}
	}

	public TestManager(VirtualFileManager files, StaticDB sdb, String testDirectory) {
		this.testDirectory = testDirectory;
		this.sdb = sdb;
		this.files = files;
		this.sdb.setTestManager(this);
	}

	private static final List<GenericTest> emptyListOfTests = new ArrayList<>();

	public List<GenericTest> getAllTests(Toolchain toolchain) {
		synchronized (Tests) {
			reloadTests();
			TestCollection cache = Tests.get(toolchain);
			if (cache.tests.containsKey(toolchain.getName().toLowerCase())) {
				return cache.tests.get(toolchain.getName().toLowerCase());
			}
		}
		return emptyListOfTests;
	}

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

	public static final class RunnerLogger {
		private JsonArray logs = new JsonArray();

		public JsonArray getLogs() {
			return logs;
		}

		private JsonArray get(Object... objects) {
			JsonArray ar = new JsonArray();
			for (Object obj : objects) {
				if (obj instanceof String[]) {
					String[] s = (String[]) obj;
					for (String ss : s) {
						ar.add(ss);
					}
				} else if (obj instanceof String) {
					String s = (String) obj;
					ar.add(s);
				} else if (obj instanceof Integer) {
					int i = (int) obj;
					ar.add(i);
				} else {
					ar.add(obj.toString());
				}
			}
			return ar;
		}

		private void add(String type, String message, Object... objects) {
			JsonObject obj = new JsonObject();
			obj.add("type", type);
			if (objects.length > 0) {
				obj.add("params", get(objects));
			}
			obj.add("text", message);
			logs.add(obj);
		}

		public void log(String message, Object... objects) {
			add("Info", message, objects);
		}

		public void logError(String message, Object... objects) {
			add("error", message, objects);
		}

	}

	public TestResults run(final BadResults badResults, int builderID, Toolchain toolchain, String test_id, String userCode, String login, RunnerLogger loggerP) {
		final RunnerLogger logger = loggerP == null ? new RunnerLogger() : loggerP;
		logger.log("Begin running test " + test_id + " for " + login + " on " + toolchain.getName() + " for given code", userCode);
		GenericTest test = null;
		synchronized (Tests) {
			synchronized (Tests.get(toolchain).tests) {
				String testKey = toolchain.getName().toUpperCase() + "/" + test_id.toLowerCase();
				if (Tests.get(toolchain).mtest.containsKey(testKey)) {
					test = Tests.get(toolchain).mtest.get(testKey);
				}
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
			logger.logError("Uvedený test nebyl nalezen");
		} else {
			Toolchain runner = test.getToolchain();
			ToolchainLogger errorLogger = new ToolchainLogger() {

				@Override
				public void logInfo(String error, Object... objects) {
					logger.log(error, objects);
				}

				@Override
				public void logError(String error, Object... objects) {
					rawMessage.add(new JsonString(error));
					message.set("<span class='log_err'>" + error + "</span>");
					logger.logError(error, objects);
				}

				@Override
				public BadResults getBadResults() {
					return badResults;
				}

			};

			String workingDirectory = new File("./tests/" + toolchain.getName() + "/" + builderID).getAbsolutePath();
			logger.log("Working directory set to " + workingDirectory);
			logger.log("Toolchain for compiling set to " + runner.getName());
			try {
				ExecutionResult result = runner.run(errorLogger, test, workingDirectory, userCode, "", login);
				workingDirectory = result.newWorkingDirectory;
				if (result.wasOK()) {
					logger.log("Executing result is OK");
					TestInput input = new TestInput(workingDirectory, runner.getLastOutputFileName());
					testResult = test.perform(logger, badResults, input);
					MyFS.deleteFileSilent(workingDirectory);
				} else {
					logger.logError("Compilation failulre");
				}
				if (testResult != null) {
					logger.log("Executing result details available");
					code = testResult.passed ? 0 : 1;
					message.set(testResult.data);
					good = testResult.getGoodTests();
					bad = testResult.getBadTests();
				}
			} finally {
				logger.log("Execution ended");
				MyFS.deleteFileSilent(workingDirectory);
			}
		}
		String details = null;
		if (testResult != null) {
			details = testResult.getFailedDescriptionData(userCode).getJsonString();
		} else if (!rawMessage.Value.isEmpty()) {
			details = rawMessage.getJsonString();
		}
		return new TestResults(code, good, bad, message.get(), details);

	}

	public Set<String> getPriorTestIDs(Toolchain toolchain, String test_id) {
		synchronized (Tests) {
			synchronized (Tests.get(toolchain).tests) {
				String testKey = toolchain.getName().toUpperCase() + "/" + test_id.toLowerCase();
				if (Tests.get(toolchain).mtest.containsKey(testKey)) {
					GenericTest test = Tests.get(toolchain).mtest.get(testKey);
					return test.getPriorTestsIDs();
				}
			}
		}
		return null;
	}

}
