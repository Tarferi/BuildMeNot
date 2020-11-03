package cz.rion.buildserver.test;

import java.util.List;
import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.test.TestManager.RunnerLogger;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;

public abstract class JsonTest implements GenericTest {

	private final String id;
	private final String description;
	private final String title;
	private final boolean hidden;
	private final boolean secret;
	private final Toolchain toolchain;
	private final String initialCode;
	private final List<TestVerificationData> tests;
	private final StaticDB sdb;
	private VirtualFileManager files;

	@Override
	public String getID() {
		return id;
	}

	public String getErrorDescription(TestResultsExpectations data) {
		return null;
	}

	@Override
	public TestResult perform(RunnerLogger logger, BadResults badResults, TestInput input) {
		int total = tests.size();
		int passed = 0;
		TestResultsExpectations[] results = new TestResultsExpectations[tests.size()];
		int index = 0;
		SystemFailureMessage finalOsError = new SystemFailureMessage();
		String lastErrorMessage = null;
		for (TestVerificationData test : tests) {
			try {
				logger.log("Begin test. Stdin [0], timeout [1], arguments [2]", test.stdin, test.timeout, test.arguments);
				MyExecResult result = input.execute(test.stdin, test.arguments, test.timeout, toolchain);
				SystemFailureMessage osError = new SystemFailureMessage(result);
				if (osError.Type == SystemFailureMessageType.Segfault) {
					logger.logError("Segfault");
					badResults.setNext(BadResultType.SegFault);
				} else if (osError.Type == SystemFailureMessageType.Timeout) {
					logger.logError("Timeout");
					badResults.setNext(BadResultType.Timeout);
				}
				if (osError.Severity > finalOsError.Severity) {
					finalOsError = osError;
				}
				TestResultsExpectations data = new TestResultsExpectations(test.code, result.returnCode, test.stdout, result.stdout, test.stderr, result.stderr, test.stdin);
				results[index] = data;
				if (!data.passed) {
					if (lastErrorMessage == null) {
						lastErrorMessage = getErrorDescription(data);
					}
					data.logDetails(logger);
					passed++;
					passed--;
					if (test.isBase) {
						badResults.setNext(BadResultType.BadBase);
					}
				} else {
					logger.log("Test passed");
					passed++;
				}
			} catch (CommandLineExecutionException e) {
				e.printStackTrace();
				return new TestResult(false, "<span class='log_err'>Nepoda�ilo se spustit test kv�li intern� chyb� serveru</span>", results);
			}
			index++;
		}
		if (!finalOsError.IsNone()) {
			return new TestResult(false, "<span class='log_err'>" + finalOsError.GetMessage() + "</span>", results);
		}
		if (passed == total) {
			return new TestResult(true, "<span class='log_ok'>Test pro�el :)</span>", results);
		} else {
			int perc = (passed * 100) / total;
			badResults.setNext(BadResultType.BadTests);
			String resultText = "<span class='log_err'>Chyba: Pro�lo " + perc + " % test�!</span>";
			if (lastErrorMessage != null) {
				resultText += "<br /><span class='log_err'>P��klad chyb: " + lastErrorMessage + "</span>";
			}
			return new TestResult(false, resultText, results);
		}
	}

	protected JsonTest(String id, StaticDB sdb, VirtualFileManager files, Toolchain toolchain, String title, String description, String initialCode, List<TestVerificationData> tests, boolean hidden, boolean secret) {
		this.sdb = sdb;
		this.files = files;
		this.id = id;
		this.title = title;
		this.tests = tests;
		this.initialCode = initialCode;
		this.description = description;
		this.hidden = hidden;
		this.secret = secret;
		this.toolchain = toolchain;
	}

	@Override
	public StaticDB getStaticDB() {
		return sdb;
	}

	@Override
	public VirtualFileManager getFiles() {
		return files;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getInitialCode() {
		return initialCode;
	}

	@Override
	public String getTitle() {
		return id + ": " + title;
	}

	/**
	 * 
	 * @param badResults
	 * @param code
	 * @return null if code is verified, String to explain why it's not
	 */
	public String VerifyCode(BadResults badResults, String code) {
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

	@Override
	public Toolchain getToolchain() {
		return toolchain;
	}

	public enum SystemFailureMessageType {
		None(0, ""), Timeout(5, "Tv�mu k�du vypr�el p�id�len� �as na test. Optimalizuj ho a zkus to znovu..."), Segfault(10, "Segmentation fault"), PermissionDenied(20, "Intern� selh�n�. Pros�m nahla� to @Tarferi"), StderrSomething(30, "");

		public final int Severity;
		private final String Message;

		private SystemFailureMessageType(int severity, String message) {
			this.Severity = severity;
			this.Message = message;
		}
	}

	protected static final class SystemFailureMessage {
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

	protected static final class TestVerificationData {

		public final String stdin;
		public final String stdout;
		public final int timeout;
		public final boolean isBase;
		public final String[] arguments;
		private String stderr;
		private int code;

		public TestVerificationData(String stdin, String stdout, String stderr, int code, int timeout, boolean base, String[] arguments) {
			this.stdin = stdin;
			this.stdout = stdout;
			this.timeout = timeout;
			this.arguments = arguments;
			this.stderr = stderr;
			this.code = code;
			this.isBase = base;
		}
	}

	protected static final class ReplacementEntry {
		public final String source;
		public final String replacement;

		public ReplacementEntry(String source, String replacement) {
			this.source = source;
			this.replacement = replacement;
		}
	}

}