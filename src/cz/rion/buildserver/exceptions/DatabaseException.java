package cz.rion.buildserver.exceptions;

public class DatabaseException extends Exception {

	public final String description;
	private final Exception exception;

	public DatabaseException(String description, Exception exception) {
		this.description = description;
		this.exception = exception;
	}

}
