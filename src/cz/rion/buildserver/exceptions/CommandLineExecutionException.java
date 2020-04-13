package cz.rion.buildserver.exceptions;

public class CommandLineExecutionException extends Exception {

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
