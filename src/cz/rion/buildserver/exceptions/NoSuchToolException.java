package cz.rion.buildserver.exceptions;

public class NoSuchToolException extends RuntimeExecutionException {

	private static final long serialVersionUID = 1L;
	
	public NoSuchToolException(int toolMissing) {
		super("Missing tool: " + toolMissing);
	}
}
