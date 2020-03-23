package cz.rion.buildserver.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPServer {

	private final int port;
	public final List<BuildThread> builders = new ArrayList<>();
	private final RemoteUIProviderServer remoteUI;

	private final TestManager tests;

	public final RuntimeDB db;
	public final StaticDB sdb;

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
		this.db = new RuntimeDB(Settings.getMainDB());
		this.sdb = new StaticDB(Settings.getStaticDB());
		this.tests = new TestManager(sdb, "./web/tests");
		this.remoteUI = new RemoteUIProviderServer(this);
		this.port = port;
		for (int i = 0; i < Settings.getBuildersCount(); i++) {
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
			HTTPClient myClient = new HTTPClient(db, sdb, tests, client, remoteUI);
			getBuilder().addJob(myClient);
		}
	}
}
