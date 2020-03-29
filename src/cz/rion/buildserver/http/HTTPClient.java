package cz.rion.buildserver.http;

import java.nio.channels.SocketChannel;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public class HTTPClient extends HTTPAuthClient {

	public HTTPClient(SocketChannel client, int BuilderID, RuntimeDB db, StaticDB sdb, TestManager tests, RemoteUIProviderServer remoteAdmin) {
		super(new CompatibleSocketClient(client), BuilderID, db, sdb, tests);
	}

}
