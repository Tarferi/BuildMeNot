package cz.rion.buildserver.ui.provider;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.http.HTTPServer;

public class RemoteUIProviderServer {

	public static int readInt(Socket sock) throws IOException {
		byte[] data = new byte[4];
		sock.getInputStream().read(data);
		return ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff) & 0xffffffff;
	}

	public static void writeInt(Socket sock, int x) throws IOException {
		byte[] data = new byte[4];
		data[0] = (byte) ((x >> 24) & 0xff);
		data[1] = (byte) ((x >> 16) & 0xff);
		data[2] = (byte) ((x >> 8) & 0xff);
		data[3] = (byte) (x & 0xff);
		sock.getOutputStream().write(data);
	}

	public enum BuilderStatus {
		IDLE(0, "Idle"), WORKING(1, "Working");

		public final int code;
		public final String title;

		BuilderStatus(int code, String title) {
			this.code = code;
			this.title = title;
		}
	}

	public enum Operation {
		GET_BUILDERS(0);

		public final int code;

		Operation(int code) {
			this.code = code;
		}
	}

	private final List<Socket> clients = new ArrayList<>();
	private final HTTPServer server;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			async();
		}
	};

	public RemoteUIProviderServer(HTTPServer server) {
		this.server = server;
		thread.start();
	}

	public void addClient(Socket socket) {
		synchronized (clients) {
			clients.add(socket);
		}
	}

	private void handle(Operation code, Socket client) throws IOException {
		if (code == Operation.GET_BUILDERS) {
			int totalBuilders = server.builders.size();
			writeInt(client, totalBuilders);
			for (BuildThread builder : server.builders) {
				BuilderStats stats = builder.getBuilderStats();
				writeInt(client, builder.getQueueSize());
				writeInt(client, stats.getTotalJobsFinished());
				writeInt(client, stats.getTotalAdminJobs());
				writeInt(client, stats.getHTMLJobs());
				writeInt(client, stats.getTotalResourceJobs());
				writeInt(client, stats.getTotlaHackJobs());
				writeInt(client, stats.getTotalJobsPassed());
				writeInt(client, builder.getBuilderStatus().code);
			}
		}
	}

	private void async() {
		Object syncer = new Object();
		while (true) {
			List<Socket> toRemove = new ArrayList<>();
			synchronized (clients) {
				for (Socket client : clients) {
					int available;
					int code = 0;
					try {
						available = client.getInputStream().available();
						if (available > 0) {
							code = client.getInputStream().read();
							if (code >= 0 && code < Operation.values().length) {
								handle(Operation.values()[code], client);
							} else {
								toRemove.add(client);
								continue;
							}
						}
					} catch (IOException e) {
						toRemove.add(client);
						continue;
					}
				}
				for (Socket sock : toRemove) {
					clients.remove(sock);
					try {
						sock.close();
					} catch (IOException e) {
					}
				}
			}
			synchronized (syncer) {
				try {
					syncer.wait(200);
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
