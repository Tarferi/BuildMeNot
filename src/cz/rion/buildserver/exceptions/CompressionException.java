package cz.rion.buildserver.exceptions;

public class CompressionException extends Exception {

	private static final long serialVersionUID = 1L;

	public CompressionException(String string, Exception e) {
		super(string, e);
	}

	public CompressionException(String string) {
		super(string);
	}

}
