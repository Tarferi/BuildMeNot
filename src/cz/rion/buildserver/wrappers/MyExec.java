package cz.rion.buildserver.wrappers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.test.TestManager.RunnerLogger;

public class MyExec {

	public static class TestResultsExpectations {
		public final int expectedCode;
		public final int returnedCode;
		public final String expectedSTDOUT;
		public final String returnedSTDOUT;
		public final String expectedSTDERR;
		public final String returnedSTDERR;
		public final String STDIN;

		public final boolean passed;

		public TestResultsExpectations(int expectedCode, int returnedCode, String expectedSTDOUT, String returnedSTDOUT, String expectedSTDERR, String returnedSTDERR, String STDIN) {
			this.expectedCode = expectedCode;
			this.returnedCode = returnedCode;
			this.expectedSTDOUT = expectedSTDOUT;
			this.returnedSTDOUT = returnedSTDOUT;
			this.expectedSTDERR = expectedSTDERR;
			this.returnedSTDERR = returnedSTDERR;
			this.STDIN = STDIN;

			this.passed = expectedCode == returnedCode && expectedSTDOUT.equals(returnedSTDOUT) && expectedSTDERR.equals(returnedSTDERR);
		}

		public void logDetails(RunnerLogger logger) {
			if (!passed) {
				logger.log("Test failed");
				if (expectedCode != returnedCode) {
					logger.logError("Expected code [0], got [1]", expectedCode, returnedCode);
				}
				if (!expectedSTDOUT.equals(returnedSTDOUT)) {
					logger.logError("Expected stdout [0] (" + expectedSTDOUT.length() + " bytes), got [1] (" + returnedSTDOUT.length() + " bytes)", expectedSTDOUT, returnedSTDOUT);
				}
				if (!expectedSTDERR.equals(returnedSTDERR)) {
					logger.logError("Expected stderr [0] (" + expectedSTDERR.length() + " bytes), got [1] (" + returnedSTDERR.length() + " bytes)", expectedSTDERR, returnedSTDERR);
				}
			}
		}
	}

	public static class MyExecResult {
		public final int returnCode;
		public final String stdout;
		public final String stderr;
		public final boolean Timeout;

		private MyExecResult(int returnCode, String stdout, String stderr, boolean Timeout) {
			this.returnCode = returnCode;
			this.stdout = stdout;
			this.stderr = stderr;
			this.Timeout = Timeout;
		}
	}

	private static String readStream(InputStream stream) throws CommandLineExecutionException {
		StringBuilder sbstdout = new StringBuilder();
		final int limit = 1024 * 100; // 100 kb max najednou
		try {
			while (stream.available() > 0) {
				int available = stream.available();
				if (available > limit) {
					available = limit;
				}
				byte[] buffer = new byte[available];
				if (stream.read(buffer) != available) {
					throw new CommandLineExecutionException("Buffer underflow");
				}
				sbstdout.append(new String(buffer, Settings.getDefaultCharset()));
			}
		} catch (IOException e) {
			if (!e.getMessage().equals("Stream closed")) {
				throw new CommandLineExecutionException("Failed while reading STDOUT", e);
			}
		}
		return sbstdout.toString();
	}

	public static MyExecResult execute(String workingDir, String stdin, String command, String[] arguments, int timeout) throws CommandLineExecutionException {
		String[] cmdArr = new String[1 + arguments.length];
		cmdArr[0] = command;
		System.arraycopy(arguments, 0, cmdArr, 1, arguments.length);
		Process p = null;
		try {
			try {
				if (cmdArr.length == 1) {
					p = Runtime.getRuntime().exec(command, null, new File(workingDir));
				} else {
					p = Runtime.getRuntime().exec(cmdArr, null, new File(workingDir));
				}
			} catch (IOException e) {
				throw new CommandLineExecutionException("Failed to execute " + command, e);
			}
			try {
				p.getOutputStream().write(stdin.getBytes(Settings.getDefaultCharset()));
				p.getOutputStream().close();
			} catch (IOException e) {
				throw new CommandLineExecutionException("Failed to write to STDIN", e);
			}
			boolean timedOut = false;
			int returnCode = 0;
			try {
				if (p.waitFor(timeout, TimeUnit.MILLISECONDS)) {
					returnCode = p.exitValue();
				} else {
					timedOut = true;
					returnCode = -50;
					p.destroyForcibly();
					new CommandLineExecutionException("Process took longer than " + timeout + " ms to finish");
				}
			} catch (InterruptedException e) {
				p.destroyForcibly();
				throw new CommandLineExecutionException("Failed to wait for process", e);
			}
			String stdout = readStream(p.getInputStream());
			String stderr = readStream(p.getErrorStream());
			return new MyExecResult(returnCode, stdout, stderr, timedOut);
		} finally {
			try {
				p.destroyForcibly();
			} catch (Exception e) {
			}
		}
	}
}
