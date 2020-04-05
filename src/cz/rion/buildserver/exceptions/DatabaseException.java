package cz.rion.buildserver.exceptions;

public class DatabaseException extends Exception {

	public final String description;
	private final Exception exception;

	public DatabaseException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
		this.exception = exception;
	}

	public DatabaseException(String string) {
		this(string, null);
	}

}
