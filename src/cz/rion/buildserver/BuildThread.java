package cz.rion.buildserver;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.http.HTTPClient;
import cz.rion.buildserver.http.HTTPServer;

public class BuildThread {

	private final int ID;
	private final HTTPServer server;

	private final List<HTTPClient> jobs = new ArrayList<>();
	private boolean waiting = false;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			async();
		}
	};

	public BuildThread(HTTPServer server, int myID) {
		this.ID = myID;
		this.server = server;
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
			}
			try {
				client.run(ID);
			} catch (Exception | Error e) {
			}
		}
	}

	public int getQueueSize() {
		synchronized (jobs) {
			return jobs.size();
		}
	}

	public void addJob(HTTPClient client) {
		synchronized (jobs) {
			jobs.add(client);
			if (waiting) {
				jobs.notify();
			}
		}
	}
}
