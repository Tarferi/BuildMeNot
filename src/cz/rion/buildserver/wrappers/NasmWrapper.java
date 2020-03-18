package cz.rion.buildserver.wrappers;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class NasmWrapper {

	private static final String NASM_PATH = "./nasm";

	public static class RunResult {
		public final MyExecResult nasm;
		public final MyExecResult golink;
		public final MyExecResult runtime;
		public final String exePath;
		public final String exeName;

		private RunResult(MyExecResult nasm, MyExecResult golink, MyExecResult runtime, String exePath, String exeName) {
			this.nasm = nasm;
			this.golink = golink;
			this.runtime = runtime;
			this.exePath = exePath;
			this.exeName = exeName;
		}
	}

	public static RunResult runRaw(String workingDir, String asm, String stdin, int timeout, boolean runExe) throws NasmExecutionException, GoLinkExecutionException, RuntimeExecutionException {
		try {
			MyFS.writeFile(workingDir + "/run.asm", asm);
		} catch (FileWriteException e) {
			throw new NasmExecutionException("Failed to write source file", e);
		}
		try {
			MyFS.copyFile(NASM_PATH + "/rw32-2018.inc", workingDir + "/rw32-2018.inc");
		} catch (FileCopyException e) {
			throw new NasmExecutionException("Failed to copy inc file", e);
		}
		MyExecResult nasmResult;
		try {
			String[] nparams = Settings.getNasmExecutableParams();
			String[] params = new String[3 + nparams.length];
			params[0] = "run.asm";
			params[1] = "-o";
			params[2] = Settings.getObjectFileName();
			System.arraycopy(nparams, 0, params, 3, nparams.length);

			nasmResult = MyExec.execute(workingDir, "", Settings.getNasmPath() + "/" + Settings.getNasmExecutableName(), params, 5000);
		} catch (CommandLineExecutionException e) {
			throw new NasmExecutionException("Failed to run NASM on source file", e);
		}
		if (nasmResult.returnCode != 0) {
			throw new NasmExecutionException("Nasm returned " + nasmResult.returnCode, null, nasmResult);
		}

		MyExecResult linkResult;
		try {
			String[] nparams = Settings.getGoLinkExecutableParams();
			String[] params = new String[1 + nparams.length];
			params[0] = Settings.getObjectFileName();
			System.arraycopy(nparams, 0, params, 1, nparams.length);

			linkResult = MyExec.execute(workingDir, "", Settings.getGoLinkPath() + "/" + Settings.getGoLinkExecutableName(), params, 5000);
		} catch (CommandLineExecutionException e) {
			throw new GoLinkExecutionException("Failed to run GoLink on object file", e);
		}
		if (linkResult.returnCode != 0) {
			throw new GoLinkExecutionException("GoLink returned " + linkResult.returnCode, null, linkResult);
		}
		if (runExe) {
			MyExecResult runtime;
			try {
				runtime = MyExec.execute(workingDir, stdin, workingDir + "/" + Settings.getExecutableFileName(), new String[0], timeout);
			} catch (CommandLineExecutionException e) {
				throw new RuntimeExecutionException("Failed to run result file", e);
			}
			return new RunResult(nasmResult, linkResult, runtime, workingDir, "/" + Settings.getExecutableFileName());
		}
		return new RunResult(nasmResult, linkResult, null, workingDir, "/" + Settings.getExecutableFileName());
	}

	public static void clean(String workingDir) {
		MyFS.deleteFileSilent(workingDir + "/run.asm");
		MyFS.deleteFileSilent(workingDir + "/" + Settings.getObjectFileName());
		MyFS.deleteFileSilent(workingDir + "/" + Settings.getExecutableFileName());
		MyFS.deleteFileSilent(workingDir + "/rw32-2018.inc");
	}

	public static RunResult run(String workingDir, String asm, String stdin, int timeout, boolean clean, boolean runExe) throws NasmExecutionException, GoLinkExecutionException, RuntimeExecutionException {
		try {
			RunResult result = runRaw(workingDir, asm, stdin, timeout, runExe);
			if (clean) {
				clean(workingDir);
			}
			return result;
		} catch (NasmExecutionException | GoLinkExecutionException | RuntimeExecutionException e) {
			clean(workingDir);
			throw e;
		}
	}

}
