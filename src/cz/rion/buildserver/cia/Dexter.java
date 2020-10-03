package cz.rion.buildserver.cia;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.http.server.HTTPClientFactory;
import cz.rion.buildserver.http.server.HTTPServer;
import cz.rion.buildserver.ui.provider.RemoteUIClient;
import cz.rion.buildserver.wrappers.MyThread;

public class Dexter {

	private final HTTPServer server;

	private static final long interval = 1000 * 60; // Every minute

	private final MyThread _thread = new MyThread() {

		@Override
		public void runAsync() {
			setName("Dexter");
			async();
		}
	};

	public Dexter(HTTPServer server) {
		this.server = server;
	}

	private Map<BuildThread, HTTPClientFactory> builderClients = new HashMap<>();

	private void killViolently(HTTPClientFactory client) {
		try {
			System.out.println("Killing HTTP client: " + client.getRemoteAddress());
			client.close();
		} catch (Throwable t) { // No emotion shown
		}
	}

	private void killViolently(RemoteUIClient client) {
		try {
			System.out.println("Killing Admin client: " + client.getRemoteAddress());
			client.close();
			sendPing(client); // For faster removal
		} catch (Throwable t) {
		}
	}

	private void sweepBuilders() {
		List<BuildThread> builders = server.data.builders;
		for (BuildThread builder : builders) {
			HTTPClientFactory client = builder.getClient();
			if (!builderClients.containsKey(builder)) {
				builderClients.put(builder, client);
			} else {
				HTTPClientFactory previousClient = builderClients.get(builder);
				if (previousClient == client && client != null) { // Stuck? Violence
					killViolently(client);
				}
			}
		}
	}

	private void sendPing(RemoteUIClient client) {
		try {
			server.data.remoteUI.writePing(client);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void sweepAdmins() {
		List<RemoteUIClient> clients = server.data.remoteUI.getClients();
		Date now = new Date();
		for (RemoteUIClient client : clients) {
			Date last = client.getLastOperation();
			if (now.getTime() - last.getTime() > interval * 2) { // No ping response? Death, then
				killViolently(client);
			} else {
				sendPing(client);
			}
		}
	}

	private void sweep() {
		sweepBuilders();
		sweepAdmins();
	}

	private void async() {
		final Object syncer = new Object();
		while (true) {
			try {
				synchronized (syncer) {
					syncer.wait(interval);
				}
				sweep();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private boolean started = false;

	public void start() {
		if (!started) {
			started = true;
			this._thread.start();
		}
	}

}
