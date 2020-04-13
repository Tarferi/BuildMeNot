package cz.rion.buildserver.exceptions;

public class HTTPServerException extends Exception {

	public final String description;

	public HTTPServerException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}

}
