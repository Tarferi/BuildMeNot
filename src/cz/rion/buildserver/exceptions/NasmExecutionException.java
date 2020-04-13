package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class NasmExecutionException extends Exception {

	public final String description;
	public final MyExecResult execResult;

	public String getDescription() {
		return execResult == null ? description : description + ": " + execResult.stdout + "\n" + execResult.stderr;
	}

	public NasmExecutionException(String description) {
		super(description);
		this.description = description;
		this.execResult = null;
	}

	public NasmExecutionException(String description, Exception exception) {
		this(description, null, null);
	}

	public NasmExecutionException(String description, Exception exception, MyExecResult execResult) {
		super(description, exception);
		this.description = description;
		this.execResult = execResult;
	}

}
