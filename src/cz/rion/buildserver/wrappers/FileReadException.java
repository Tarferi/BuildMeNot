package cz.rion.buildserver.wrappers;

public class FileReadException extends Exception {

	public final String description;
	private final Exception exception;

	public FileReadException(String description) {
		this(description, null);
	}

	public FileReadException(String description, Exception exception) {
		this.description = description;
		this.exception = exception;
	}

}
