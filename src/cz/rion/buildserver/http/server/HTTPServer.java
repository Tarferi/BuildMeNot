package cz.rion.buildserver.http.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.BuildThread.ClientAccepter;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.cia.Dexter;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.http.stateless.StatelessClient;
import cz.rion.buildserver.http.sync.HTTPSyncClientFactory;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPServer {

	private final int port;
	public final ServerData data;

	private final ClientAccepter acc = new ClientAccepter() {

		@Override
		public HTTPClientFactory accept() {
			try {
				return data.clients.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public int getQueueSize() {
			return data.clients.size();
		}

	};

	protected ServerData createData() {
		StaticDB sdb;
		StatelessClient processor;
		try {
			sdb = new StaticDB(Settings.getStaticDB());
			RuntimeDB db = new RuntimeDB(Settings.getMainDB(), sdb);
			TestManager tests = new TestManager(sdb, "./web/tests");
			List<BuildThread> builders = new ArrayList<>();
			RemoteUIProviderServer remoteUI = new RemoteUIProviderServer(db, sdb, builders);
			Dexter dexter = new Dexter(this);

			processor = new StatelessClient(db, sdb, tests);

			for (int i = 0; i < Settings.getBuildersCount(); i++) {
				builders.add(new BuildThread(this, i, acc, processor));
			}
			return new ServerData(db, sdb, tests, remoteUI, builders, dexter);
		} catch (DatabaseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public HTTPServer(int port) throws DatabaseException, IOException {
		this.port = port;
		data = createData();
	}

	public void addRemoteUIClient(CompatibleSocketClient socket) {
		synchronized (data.remoteUI) {
			data.remoteUI.addClient(socket);
		}
	}

	protected ServerSocketChannel createServerSocket() throws HTTPServerException {
		ServerSocketChannel server = null;
		try {
			server = ServerSocketChannel.open();
			server.configureBlocking(true);
			server.socket().bind(new InetSocketAddress(port));
			return server;
		} catch (IOException e) {
			throw new HTTPServerException("Failed to start server on port " + port, e);
		}
	}

	public void run() throws HTTPServerException {
		if (data == null) {
			return;
		}
		for (BuildThread builder : data.builders) {
			builder.start();
		}
		data.remoteUI.start();
		data.dexter.start();
		ServerSocketChannel server = createServerSocket();
		while (true) {
			SocketChannel client;
			try {
				client = server.accept();
			} catch (IOException e) {
				throw new HTTPServerException("Failed to accept client on port " + port, e);
			}
			try {
				data.clients.put(new HTTPSyncClientFactory(new CompatibleSocketClient(client)));
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
