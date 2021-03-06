package cz.rion.buildserver.wrappers;

public class FileCopyException extends Exception {

	private static final long serialVersionUID = 1L;
	public final String description;

	public FileCopyException(String description) {
		super(description);
		this.description = description;
	}

	public FileCopyException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}

}
