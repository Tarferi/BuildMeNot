package cz.rion.buildserver.exceptions;

public class HTTPServerException extends Exception {

	public final String description;
	private final Exception exception;

	public HTTPServerException(String description, Exception exception) {
		this.description = description;
		this.exception = exception;
	}

}
