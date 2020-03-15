package cz.rion.buildserver.exceptions;

public class HTTPClientException extends Exception {

	public final String description;
	private final Exception exception;

	public HTTPClientException(String description) {
		this(description, null);
	}

	public HTTPClientException(String description, Exception exception) {
		this.description = description;
		this.exception = exception;
	}

}
