package cz.rion.buildserver.exceptions;

public class NoSuchToolException extends RuntimeExecutionException {

	public NoSuchToolException(int toolMissing) {
		super("Missing tool: " + toolMissing);
	}
}
