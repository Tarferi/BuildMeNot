package cz.rion.buildserver;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.db.MyDB;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.HTTPClient;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class BuildThread {

	private final int ID;
	private final HTTPServer server;
	int totalJobsFinished = 0;

	private final List<HTTPClient> jobs = new ArrayList<>();
	private boolean waiting = false;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			async();
		}
	};
	private MyDB db;

	public BuildThread(HTTPServer server, int myID) {
		this.ID = myID;
		this.server = server;
		this.db = server.db;
		thread.start();
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
				totalJobsFinished++;
			}
			try {
				client.run(db, ID);
			} catch (SwitchClientException e) {
				server.addRemoteUIClient(e.socket);
			} catch (Exception | Error e) {
			}
		}
	}

	public int getQueueSize() {
		synchronized (jobs) {
			return jobs.size();
		}
	}

	public int getTotalFinishedJobs() {
		synchronized (jobs) {
			return totalJobsFinished;
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
