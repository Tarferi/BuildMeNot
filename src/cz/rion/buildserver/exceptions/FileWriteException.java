package cz.rion.buildserver.exceptions;

public class FileWriteException extends Exception {

	public final String description;

	public FileWriteException(String description) {
		this.description = description;
	}

}
