package cz.rion.buildserver.exceptions;

public class HTTPClientException extends Exception {

	private static final long serialVersionUID = 1L;
	public final String description;

	public HTTPClientException(String description) {
		super(description);
		this.description = description;
	}

	public HTTPClientException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}

}
