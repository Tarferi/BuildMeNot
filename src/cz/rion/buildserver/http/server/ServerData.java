package cz.rion.buildserver.http.server;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.cia.Dexter;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;

public final class ServerData {
	public final List<BuildThread> builders;
	public final RemoteUIProviderServer remoteUI;

	public final TestManager tests;

	public final RuntimeDB db;
	public final StaticDB sdb;

	public final Dexter dexter;

	public final LinkedBlockingQueue<HTTPClientFactory> clients = new LinkedBlockingQueue<>();

	protected ServerData(RuntimeDB db, StaticDB sdb, TestManager tests, RemoteUIProviderServer remoteUI, List<BuildThread> builders, Dexter dexter) {
		this.builders = builders;
		this.remoteUI = remoteUI;
		this.tests = tests;
		this.db = db;
		this.sdb = sdb;
		this.dexter = dexter;
	}

	public ServerData(ServerData clone) {
		this(clone.db, clone.sdb, clone.tests, clone.remoteUI, clone.builders, clone.dexter);
	}
}