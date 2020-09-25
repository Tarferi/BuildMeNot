package cz.rion.buildserver.exceptions;

public class HTTPServerException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	public final String description;

	public HTTPServerException(String description, Exception exception) {
		super(description, exception);
		this.description = description;
	}

}
