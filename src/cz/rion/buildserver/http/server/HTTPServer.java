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
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.http.stateless.StatelessClient;
import cz.rion.buildserver.http.sync.HTTPSyncClientFactory;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPServer implements DatabaseInitData.CacheClearer {

	private final int port;
	public final ServerData data;
	private final HTTPSServer https;

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

	private StatelessClient processor = null;

	protected ServerData createData() {
		StaticDB sdb;

		try {
			sdb = new StaticDB(new DatabaseInitData(Settings.getStaticDB(), this));
			RuntimeDB db = new RuntimeDB(new DatabaseInitData(Settings.getMainDB(), this), sdb);
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
		this(port, 0);
	}

	public HTTPServer(int port, int portHTTPS) throws DatabaseException, IOException {
		this.port = port;
		data = createData();
		if (portHTTPS > 0) {
			https = new HTTPSServer(Settings.GetHTTPSServerPort(), data);
		} else {
			https = null;
		}
	}

	public void addRemoteUIClient(CompatibleSocketClient socket) {
		synchronized (data.remoteUI) {
			data.remoteUI.addClient(socket);
		}
	}

	protected ServerSocketChannel createServerSocket(int port) throws HTTPServerException {
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
		if (data == null || port == 0) {
			return;
		}
		for (BuildThread builder : data.builders) {
			builder.start();
		}
		data.remoteUI.start();
		data.dexter.start();
		if (https != null) {
			https.run();
		}
		ServerSocketChannel server = createServerSocket(port);
		while (true) {
			SocketChannel client;
			try {
				client = server.accept();
			} catch (IOException e) {
				throw new HTTPServerException("Failed to accept client on port " + port, e);
			}
			try {
				data.clients.put(new HTTPSyncClientFactory(new CompatibleSocketClient(client), false));
			} catch (InterruptedException e) {
				e.printStackTrace();
				try {
					client.close();
				} catch (IOException e1) {
				}
			}
		}
	}

	@Override
	public void clearCache() {
		if (processor != null) {
			processor.clearCache();
		}
	}
}
