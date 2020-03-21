package cz.rion.buildserver;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.db.MyDB;
import cz.rion.buildserver.db.SQLiteDB;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.HTTPClient;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class BuildThread {

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

	private final List<HTTPClient> jobs = new ArrayList<>();
	private boolean waiting = false;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			async();
		}
	};
	private final SQLiteDB db;

	public BuildThread(HTTPServer server, int myID) {
		this.ID = myID;
		this.server = server;
		this.db = server.db;
		thread.start();
	}

	private void account(boolean ok, HTTPClient.HTTPClientIntentType type) {
		switch (type) {
		case ADMIN:
			stats.totalAdminJobs++;
			break;
		case GET_HTML:
			stats.totalHTMLJobs++;
			break;
		case GET_RESOURCE:
			stats.totalResourceJobs++;
			break;
		case HACK:
			stats.totalHackJobs++;
			break;
		case PERFORM_TEST:
			if (ok) {
				stats.totalJobsPassed++;
			}
			stats.totalJobsFinished++;
			break;
		}
	}

	private void async() {
		thread.setName("Worker " + ID);
		while (true) {
			HTTPClient client;
			synchronized (jobs) {
				if (jobs.isEmpty()) {
					waiting = true;
					try {
						jobs.wait();
					} catch (InterruptedException e) {
					}
				}
				client = jobs.remove(0);
			}
			try {
				client.run(ID);
			} catch (SwitchClientException e) {
				server.addRemoteUIClient(e.socket);
			} catch (Exception | Error e) {
				e.printStackTrace();
			} finally {
				account(client.haveTestsPassed(), client.getIntent());
			}
		}
	}

	public int getQueueSize() {
		synchronized (jobs) {
			return jobs.size();
		}
	}

	public BuilderStats getBuilderStats() {
		synchronized (jobs) {
			return stats;
		}
	}

	public void addJob(HTTPClient client) {
		synchronized (jobs) {
			jobs.add(client);
			if (waiting) {
				waiting = false;
				jobs.notify();
			}
		}
	}

	public BuilderStatus getBuilderStatus() {
		synchronized (jobs) {
			if (waiting) {
				return BuilderStatus.IDLE;
			} else {
				return BuilderStatus.WORKING;
			}
		}
	}
}
