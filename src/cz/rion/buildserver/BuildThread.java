package cz.rion.buildserver;

import java.nio.channels.SocketChannel;

import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.AbstractHTTPClient.HTTPClientIntentType;
import cz.rion.buildserver.http.HTTPClient;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;
import cz.rion.buildserver.wrappers.MyThread;

public class BuildThread {

	public interface ClientAccepter {
		public SocketChannel accept();

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
	private ClientAccepter accepter;

	public BuildThread(HTTPServer server, int myID, ClientAccepter accepter) {
		this.ID = myID;
		this.server = server;
		this.accepter = accepter;
		thread.start();
	}

	private void account(boolean ok, RemoteUIProviderServer remoteAdmin, HTTPClientIntentType type, String address, String login, int code, String codeDescription, String path, String asm, String testResult, String test_id) {
		switch (type) {
		case ADMIN:
			stats.totalAdminJobs++;
			break;
		case GET_HTML:
			stats.totalHTMLJobs++;
			remoteAdmin.writeGetHTML(address, login, code, codeDescription, path);
			break;
		case GET_RESOURCE:
			stats.totalResourceJobs++;
			remoteAdmin.writeGetResource(address, login, code, codeDescription, path);
			break;
		case HACK:
			stats.totalHackJobs++;
			break;
		case COLLECT_TESTS:
			remoteAdmin.writeTestCollect(address, login);
			break;

		case UNKNOWN:
			break;

		case PERFORM_TEST:
			if (ok) {
				stats.totalJobsPassed++;
			}
			stats.totalJobsFinished++;
			remoteAdmin.writeTestResult(address, login, code, codeDescription, asm, test_id);
			remoteAdmin.writeBuilderDataUpdate(this.ID, this);
			break;
		}
	}

	private SocketChannel currentClient;

	private void async() {
		thread.setName("Worker " + ID);
		while (true) {
			try {
				server.remoteUI.writeBuilderDataUpdate(ID, this);
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
				server.remoteUI.writeBuilderDataUpdate(ID, this);
				HTTPClient client = new HTTPClient(currentClient, ID, server.db, server.sdb, server.tests, server.remoteUI);
				try {
					client.run();
				} catch (SwitchClientException e) {
					server.addRemoteUIClient(e.socket);
				} catch (Exception | Error e) {
					e.printStackTrace();
				} finally {
					JsonObject result = client.getReturnValue();
					int code = result == null ? 1 : (result.containsNumber("code") ? result.getNumber("code").Value : 1);
					String codeDescription = result == null ? null : result.containsString("result") ? result.getString("result").Value : null;
					String path = client.getDownloadedDocumentPath();
					String runtimeResult = result == null ? null : result.getJsonString();
					String test_id = client.getTestID();
					String asm = client.getASM();
					account(client.haveTestsPassed(), this.server.remoteUI, client.getIntention(), client.getAddress(), client.getLogin(), code, codeDescription, path, asm, runtimeResult, test_id);
				}
			} catch (Throwable t) { // Ultimate catcher
				t.printStackTrace();
			} finally {
				waiting = true;
				server.remoteUI.writeBuilderDataUpdate(ID, this);
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

	public SocketChannel getClient() {
		return currentClient;
	}
}
