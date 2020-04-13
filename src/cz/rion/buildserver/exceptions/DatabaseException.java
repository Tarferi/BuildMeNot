package cz.rion.buildserver.exceptions;

public class DatabaseException extends Exception {

	public final String description;

	public DatabaseException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}

	public DatabaseException(String description) {
		super(description);
		this.description = description;
	}

}
