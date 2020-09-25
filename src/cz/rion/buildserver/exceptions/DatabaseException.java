package cz.rion.buildserver.exceptions;

public class DatabaseException extends Exception {

	private static final long serialVersionUID = 1L;
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
