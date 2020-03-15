package cz.rion.buildserver.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.db.MyDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPServer {

	private static final int TOTAL_BUILDERS = 8;

	private final int port;
	public final List<BuildThread> builders = new ArrayList<>();
	private final RemoteUIProviderServer remoteUI = new RemoteUIProviderServer(this);

	public final MyDB db;

	private BuildThread getBuilder() {
		int min = -1;
		BuildThread selectedBuilder = null;
		for (BuildThread builder : builders) {
			int bs = builder.getQueueSize();
			if (min == -1) {
				min = builder.getQueueSize();
				selectedBuilder = builder;

			} else if (bs < min) {
				min = bs;
				selectedBuilder = builder;
			}
		}
		return selectedBuilder;
	}

	public HTTPServer(int port) throws DatabaseException {
		this.db = new MyDB("data.sqlite");
		this.port = port;
		for (int i = 0; i < TOTAL_BUILDERS; i++) {
			builders.add(new BuildThread(this, i));
		}
	}

	public void addRemoteUIClient(Socket socket) {
		remoteUI.addClient(socket);
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
			getBuilder().addJob(myClient);
		}
	}
}
