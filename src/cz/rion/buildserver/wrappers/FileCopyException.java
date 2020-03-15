package cz.rion.buildserver.wrappers;

public class FileCopyException extends Exception {

	public final String description;
	private final Exception exception;

	public FileCopyException(String description) {
		this(description, null);
	}

	public FileCopyException(String description, Exception exception) {
		this.description = description;
		this.exception = exception;
	}

}
