package cz.rion.buildserver.exceptions;

public class FileWriteException extends Exception {

	private static final long serialVersionUID = 1L;
	public final String description;

	public FileWriteException(String description) {
		this.description = description;
	}

}
