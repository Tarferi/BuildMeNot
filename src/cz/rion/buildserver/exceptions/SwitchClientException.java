package cz.rion.buildserver.exceptions;

import cz.rion.buildserver.http.MySocketClient;

public class SwitchClientException extends Exception {

	public final MySocketClient socket;

	public SwitchClientException(MySocketClient client) {
		this.socket = client;
	}

}
