package cz.rion.buildserver.exceptions;

import java.net.Socket;

public class SwitchClientException extends Exception {

	public final Socket socket;

	public SwitchClientException(Socket socket) {
		this.socket = socket;
	}

}
