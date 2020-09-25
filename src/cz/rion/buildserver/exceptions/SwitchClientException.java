package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.http.CompatibleSocketClient;

public class SwitchClientException extends Exception {
	
	private static final long serialVersionUID = 1L;
	
	public final CompatibleSocketClient socket;

	public SwitchClientException(CompatibleSocketClient client) {
		this.socket = client;
	}

}
