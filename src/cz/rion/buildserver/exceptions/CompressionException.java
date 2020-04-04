package cz.rion.buildserver.exceptions;

public class CompressionException extends Exception {

	public CompressionException(String string, Exception e) {
		super(string, e);
	}

	public CompressionException(String string) {
		super(string);
	}

}
