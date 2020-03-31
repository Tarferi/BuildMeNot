package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class RuntimeExecutionException extends Exception {

	public final String description;
	private final Exception exception;
	public final MyExecResult execResult;

	public RuntimeExecutionException(String description) {
		this(description, null);
	}

	public RuntimeExecutionException(String description, Exception exception) {
		this(description, null, null);
	}

	public RuntimeExecutionException(String description, Exception exception, MyExecResult execResult) {
		this.description = description;
		this.exception = exception;
		this.execResult = execResult;
	}

}
