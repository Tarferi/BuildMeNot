package cz.rion.buildserver.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.BuildThread.ClientAccepter;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.cia.Dexter;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPServer {

	private final int port;
	public final List<BuildThread> builders = new ArrayList<>();
	public final RemoteUIProviderServer remoteUI;

	public final TestManager tests;

	public final RuntimeDB db;
	public final StaticDB sdb;

	private LinkedBlockingQueue<SocketChannel> clients = new LinkedBlockingQueue<>();

	private final ClientAccepter acc = new ClientAccepter() {

		@Override
		public SocketChannel accept() {
			try {
				return clients.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public int getQueueSize() {
			return clients.size();
		}

	};

	public HTTPServer(int port) throws DatabaseException, IOException {
		this.sdb = new StaticDB(Settings.getStaticDB());
		this.db = new RuntimeDB(Settings.getMainDB(), sdb);
		this.tests = new TestManager(sdb, "./web/tests");
		this.remoteUI = new RemoteUIProviderServer(this);
		this.port = port;
		for (int i = 0; i < Settings.getBuildersCount(); i++) {
			builders.add(new BuildThread(this, i, acc));
		}
		new Dexter(this);
	}

	public void addRemoteUIClient(CompatibleSocketClient socket) {
		remoteUI.addClient(socket);
	}

	public void run() throws HTTPServerException {
		ServerSocketChannel server;
		try {
			server = ServerSocketChannel.open();
			server.configureBlocking(true);
			server.socket().bind(new InetSocketAddress(port));
		} catch (IOException e) {
			throw new HTTPServerException("Failed to start server on port " + port, e);
		}
		while (true) {
			SocketChannel client;
			try {
				client = server.accept();
			} catch (IOException e) {
				throw new HTTPServerException("Failed to accept client on port " + port, e);
			}
			// HTTPClient myClient;
			// myClient = new HTTPClient(db, sdb, tests, client, remoteUI);
			try {
				clients.put(client);
			} catch (InterruptedException e) {
				e.printStackTrace();
				try {
					client.close();
				} catch (IOException e1) {
				}
			}
		}
	}
}
