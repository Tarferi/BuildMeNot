package cz.rion.buildserver;

import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.server.HTTPClientFactory;
import cz.rion.buildserver.http.server.HTTPServer;
import cz.rion.buildserver.http.stateless.StatelessClient;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;
import cz.rion.buildserver.wrappers.MyThread;
import cz.rion.buildserver.http.*;

public class BuildThread {

	public interface ClientAccepter {
		public HTTPClientFactory accept();

		public int getQueueSize();
	}

	private final int ID;
	private final HTTPServer server;

	public static final class BuilderStats {
		private int totalJobsFinished = 0;
		private int totalHackJobs = 0;
		private int totalResourceJobs = 0;
		private int totalHTMLJobs = 0;
		private int totalAdminJobs = 0;
		private int totalJobsPassed = 0;

		public int getTotalJobsFinished() {
			return totalJobsFinished;
		}

		public int getTotalJobsPassed() {
			return totalJobsPassed;
		}

		public int getTotlaHackJobs() {
			return totalHackJobs;
		}

		public int getTotalResourceJobs() {
			return totalResourceJobs;
		}

		public int getHTMLJobs() {
			return totalHTMLJobs;
		}

		public int getTotalAdminJobs() {
			return totalAdminJobs;
		}

		private BuilderStats() {
			this(0, 0, 0, 0, 0, 0);
		}

		public BuilderStats(int totalJobsFinished, int totalHackJobs, int totalResourceJobs, int totalHTMLJobs, int totalAdminJobs, int totalJobsPassed) {
			this.totalAdminJobs = totalAdminJobs;
			this.totalHackJobs = totalHackJobs;
			this.totalHTMLJobs = totalHTMLJobs;
			this.totalJobsFinished = totalJobsFinished;
			this.totalResourceJobs = totalResourceJobs;
			this.totalJobsPassed = totalJobsPassed;

		}
	}

	private final BuilderStats stats = new BuilderStats();

	private boolean waiting = false;

	private final MyThread thread = new MyThread() {
		@Override
		public void runAsync() {
			async();
		}
	};

	private final ClientAccepter accepter;
	private final StatelessClient processor;

	public BuildThread(HTTPServer server, int myID, ClientAccepter accepter, StatelessClient processor) {
		this.ID = myID;
		this.server = server;
		this.accepter = accepter;
		this.processor = processor;
	}

	private HTTPClientFactory currentClient;

	private void async() {
		thread.setName("Worker " + ID);
		while (true) {
			try {
				server.data.remoteUI.writeBuilderDataUpdate(ID, this);
				waiting = true;
				currentClient = null;
				while (currentClient == null) {
					currentClient = accepter.accept();
					if (currentClient == null) {
						synchronized (thread) { // Overhead
							thread.wait(1000);
						}
					}
				}
				waiting = false;
				server.data.remoteUI.writeBuilderDataUpdate(ID, this);

				try {
					HTTPRequest req = currentClient.getRequest();
					HTTPResponse resp = processor.getResponse(req);
					currentClient.writeResponse(resp);
				} catch (SwitchClientException e) {
					server.addRemoteUIClient(e.socket);
				} catch (Exception e) {
					e.printStackTrace();
					currentClient.close();
				}

			} catch (Throwable t) { // Ultimate catcher
				t.printStackTrace();
			} finally {
				waiting = true;
				server.data.remoteUI.writeBuilderDataUpdate(ID, this);
			}
		}
	}

	public int getQueueSize() {
		return accepter.getQueueSize();
	}

	public BuilderStats getBuilderStats() {
		synchronized (stats) {
			return stats;
		}
	}

	public BuilderStatus getBuilderStatus() {
		synchronized (stats) {
			if (waiting) {
				return BuilderStatus.IDLE;
			} else {
				return BuilderStatus.WORKING;
			}
		}
	}

	public HTTPClientFactory getClient() {
		return currentClient;
	}

	private boolean started = false;

	public void start() {
		if (!started) {
			started = true;
			thread.start();
		}
	}
}
