package cz.rion.buildserver.wrappers;

public class FileReadException extends Exception {

	public final String description;

	public FileReadException(String description) {
		super(description);
		this.description = description;
	}

	public FileReadException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}
}
