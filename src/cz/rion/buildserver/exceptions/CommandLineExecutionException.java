package cz.rion.buildserver.exceptions;

public class CommandLineExecutionException extends Exception {

	public final String description;
	private final Exception exception;

	public CommandLineExecutionException(String description) {
		this(description, null);
	}

	public CommandLineExecutionException(String description, Exception exception) {
		super(exception);
		this.description = description;
		this.exception = exception;
	}
}
