package cz.rion.buildserver.wrappers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import cz.rion.buildserver.exceptions.CommandLineExecutionException;

public class MyExec {

	public static class MyExecResult {
		public final int returnCode;
		public final String stdout;
		public final String stderr;

		private MyExecResult(int returnCode, String stdout, String stderr) {
			this.returnCode = returnCode;
			this.stdout = stdout;
			this.stderr = stderr;
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
				sbstdout.append(new String(buffer));
			}
		} catch (IOException e) {
			throw new CommandLineExecutionException("Failed while reading STDOUT", e);
		}
		return sbstdout.toString();
	}

	public static MyExecResult execute(String workingDir, String stdin, String command, String[] arguments, int timeout) throws CommandLineExecutionException {
		String[] cmdArr = new String[1 + arguments.length];
		cmdArr[0] = command;
		System.arraycopy(arguments, 0, cmdArr, 1, arguments.length);
		Process p;
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
			p.getOutputStream().write(stdin.getBytes());
			p.getOutputStream().close();
		} catch (IOException e) {
			throw new CommandLineExecutionException("Failed to write to STDIN", e);
		}

		int returnCode = 0;
		try {
			if (p.waitFor(timeout, TimeUnit.MILLISECONDS)) {
				returnCode = p.exitValue();
			} else {
				p.destroyForcibly();
				new CommandLineExecutionException("Process took longer than " + timeout + " ms to finish");
			}
		} catch (InterruptedException e) {
			p.destroyForcibly();
			throw new CommandLineExecutionException("Failed to wait for process", e);
		}
		String stdout = readStream(p.getInputStream());
		String stderr = readStream(p.getErrorStream());
		return new MyExecResult(returnCode, stdout, stderr);
	}
}
