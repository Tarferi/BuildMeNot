package cz.rion.buildserver.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import cz.rion.buildserver.exceptions.HTTPServerException;

public class HTTPServer {

	private final int port;

	public HTTPServer(int port) {
		this.port = port;
	}

	public void run() throws HTTPServerException {
		ServerSocket server;
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			throw new HTTPServerException("Failed to start server on port " + port, e);
		}
		while (true) {
			Socket client;
			try {
				client = server.accept();
			} catch (IOException e) {
				throw new HTTPServerException("Failed to accept client on port " + port, e);
			}
			HTTPClient myClient = new HTTPClient(client);
			
		}
	}
}
