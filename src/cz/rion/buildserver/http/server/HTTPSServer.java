package cz.rion.buildserver.http.server;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;

public class HTTPSServer extends HTTPServer {

	private final HTTPServer http;

	public HTTPSServer(int port, HTTPServer existingServer) throws DatabaseException, IOException {
		super(port);
		this.http = existingServer;
	}

	@Override
	protected ServerData createData() {
		return http.data;
	}

	@Override
	protected ServerSocketChannel createServerSocket() throws HTTPServerException {
		return null;
	}
}
