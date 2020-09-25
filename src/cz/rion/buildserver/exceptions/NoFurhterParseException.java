package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.http.HTTPResponse;

public class NoFurhterParseException extends Exception {

	public final HTTPResponse response;

	public NoFurhterParseException(HTTPResponse response) {
		this.response = response;
	}

	private static final long serialVersionUID = 1L;

}
