package cz.rion.buildserver.wrappers;

public class FileReadException extends Exception {

	private static final long serialVersionUID = 1L;
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
