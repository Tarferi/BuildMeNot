package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class GoLinkExecutionException extends Exception {

	public final String description;
	private final Exception exception;
	private final MyExecResult execResult;

	public GoLinkExecutionException(String description) {
		this(description, null);
	}

	public GoLinkExecutionException(String description, Exception exception) {
		this(description, null, null);
	}

	public GoLinkExecutionException(String description, Exception exception, MyExecResult execResult) {
		this.description = description;
		this.exception = exception;
		this.execResult = execResult;
	}

}
