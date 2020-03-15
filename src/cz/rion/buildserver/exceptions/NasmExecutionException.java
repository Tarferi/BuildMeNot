package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class NasmExecutionException extends Exception {

	public final String description;
	private final Exception exception;
	private final MyExecResult execResult;

	public NasmExecutionException(String description) {
		this(description, null);
	}

	public NasmExecutionException(String description, Exception exception) {
		this(description, null, null);
	}

	public NasmExecutionException(String description, Exception exception, MyExecResult execResult) {
		this.description = description;
		this.exception = exception;
		this.execResult = execResult;
	}

}
