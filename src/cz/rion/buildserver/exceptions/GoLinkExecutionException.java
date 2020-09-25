package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class GoLinkExecutionException extends Exception {

	private static final long serialVersionUID = 1L;
	public final String description;
	public final MyExecResult execResult;

	public GoLinkExecutionException(String description) {
		super(description);
		this.description = description;
		this.execResult = null;
	}

	public GoLinkExecutionException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
		this.execResult = null;
	}

	public GoLinkExecutionException(String description, Exception exception, MyExecResult execResult) {
		super(description, exception);
		this.description = description;
		this.execResult = execResult;
	}

}
