package cz.rion.buildserver.exceptions;

public class NoSuchToolchainException extends RuntimeExecutionException {
	
	private static final long serialVersionUID = 1L;
	
	public NoSuchToolchainException(String toolchainMissing) {
		super("Missing toolchain: " + toolchainMissing);
	}

	public NoSuchToolchainException(String string, Exception e) {
		super(string, e);
	}

}
