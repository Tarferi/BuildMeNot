package cz.rion.buildserver.exceptions;

public class CommandLineExecutionException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public final String description;

	public CommandLineExecutionException(String description) {
		super(description);
		this.description = description;
	}

	public CommandLineExecutionException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}
}
