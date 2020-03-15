package cz.rion.buildserver.wrappers;

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

		private RunResult(MyExecResult nasm, MyExecResult golink, MyExecResult runtime) {
			this.nasm = nasm;
			this.golink = golink;
			this.runtime = runtime;
		}
	}

	public static RunResult runRaw(String workingDir, String asm, String stdin, int timeout) throws NasmExecutionException, GoLinkExecutionException, RuntimeExecutionException {
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
			nasmResult = MyExec.execute(workingDir, "", NASM_PATH + "/nasm.exe", new String[] { "-f", "win32", "run.asm", "-o", "run.obj" }, 5000);
		} catch (CommandLineExecutionException e) {
			throw new NasmExecutionException("Failed to run NASM on source file", e);
		}
		if (nasmResult.returnCode != 0) {
			throw new NasmExecutionException("Nasm returned " + nasmResult.returnCode, null, nasmResult);
		}

		MyExecResult linkResult;
		try {
			linkResult = MyExec.execute(workingDir, "", NASM_PATH + "/GoLink.exe", new String[] { "run.obj", "/console", "/mix", "msvcrt.dll", "kernel32.dll" }, 5000);
		} catch (CommandLineExecutionException e) {
			throw new GoLinkExecutionException("Failed to run GoLink on object file", e);
		}
		if (linkResult.returnCode != 0) {
			throw new GoLinkExecutionException("GoLink returned " + linkResult.returnCode, null, linkResult);
		}

		MyExecResult runtime;
		try {
			runtime = MyExec.execute(workingDir, stdin, workingDir + "/run.exe", new String[0], timeout);
		} catch (CommandLineExecutionException e) {
			throw new RuntimeExecutionException("Failed to run result file", e);
		}
		return new RunResult(nasmResult, linkResult, runtime);

	}

	public static void clean(String workingDir) {
		MyFS.deleteFileSilent(workingDir+"/run.asm");
		MyFS.deleteFileSilent(workingDir+"/run.obj");
		MyFS.deleteFileSilent(workingDir+"/run.exe");
		MyFS.deleteFileSilent(workingDir+"/rw32-2018.inc");
	}
	
	public static RunResult run(String workingDir, String asm, String stdin, int timeout) throws NasmExecutionException, GoLinkExecutionException, RuntimeExecutionException {
		try {
			RunResult result = runRaw(workingDir, asm, stdin, timeout);
			clean(workingDir);
			return result;
		} catch (NasmExecutionException | GoLinkExecutionException | RuntimeExecutionException e) {
			clean(workingDir);
			throw e;
		}
	}

}
