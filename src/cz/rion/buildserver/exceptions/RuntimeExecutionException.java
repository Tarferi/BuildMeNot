package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.wrappers.MyExec.MyExecResult;

public class RuntimeExecutionException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	public final String description;
	public final MyExecResult execResult;

	public RuntimeExecutionException(String description) {
		super(description);
		this.description = description;
		this.execResult = null;
	}

	public RuntimeExecutionException(String description, Exception exception) {
		this(description, exception, null);
	}

	public RuntimeExecutionException(String description, Exception exception, MyExecResult execResult) {
		super(description, exception);
		this.description = description;
		this.execResult = execResult;
	}

}
