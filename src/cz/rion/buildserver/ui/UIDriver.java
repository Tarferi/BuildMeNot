package cz.rion.buildserver.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class UIDriver {

	private Socket client;
	private Thread thread = new Thread() {

		@Override
		public void run() {
			async();
		}
	};

	public static class BuildThreadInfo {
		public final int ID;
		public final int QueueSize;
		public final int TotalJobsFinished;
		public final BuilderStatus Status;

		public BuildThreadInfo(int id, int size, int total, BuilderStatus bs) {
			this.ID = id;
			this.QueueSize = size;
			this.TotalJobsFinished = total;
			this.Status = bs;
		}
	}

	public static interface LoginCallback {
		public void loggedIn();

		public void loginFailed();
	}

	public static interface GetBuilderCallback {
		public void haveBuilders(List<BuildThreadInfo> builders);

		public void noBuildersBecauseOfError();
	}

	enum Status {
		DISCONNECTED, CONNECTING, CONNECTED
	}

	private Status status = Status.DISCONNECTED;
	public final MainWindow wnd;

	public UIDriver(MainWindow wnd) {
		this.wnd = wnd;
		thread.start();
	}

	public Status getStatus() {
		return status;
	}

	private List<Runnable> jobs = new ArrayList<>();

	private boolean waiting = false;

	private Object statusSyncer = new Object();

	private void async() {
		thread.setName("UIDriver thread");
		while (true) {
			Runnable job;
			synchronized (jobs) {
				if (jobs.isEmpty()) {
					waiting = true;
					while (true) {
						try {
							jobs.wait(1000);
						} catch (InterruptedException e) {
						}
						if (jobs.isEmpty()) {
							doSomethingUpdatey();
						} else {
							break;
						}
					}
				}
				job = jobs.remove(0);
			}
			try {
				job.run();
			} catch (Exception | Error e) {
			}
		}
	}

	private void addJob(Runnable job) {
		synchronized (jobs) {
			jobs.add(job);
			if (waiting) {
				waiting = false;
				jobs.notify();
			}
		}
	}

	private GetBuilderCallback GetBuilderCallback = null;

	private void doSomethingUpdatey() {
		synchronized (statusSyncer) {
			if (status == Status.CONNECTED && GetBuilderCallback != null) {
				getBuilders(GetBuilderCallback);
			}
		}
	}

	public void login(final String server, final int port, final String auth, final LoginCallback callback) {
		synchronized (statusSyncer) {
			if (status == Status.DISCONNECTED) {
				status = Status.CONNECTING;
				final Runnable failer = new Runnable() {

					@Override
					public void run() {
						callback.loginFailed();
					}

				};

				final Runnable nonfailer = new Runnable() {

					@Override
					public void run() {
						callback.loggedIn();
					}

				};

				Runnable job = new Runnable() {

					@Override
					public void run() {
						synchronized (statusSyncer) {
							try {
								if (client != null) {
									client.close();
									client = null;
								}
								client = new Socket(server, port);
								OutputStream out = client.getOutputStream();
								out.write(("AUTH " + auth + " HTTP/1.1\r\n\r\n").getBytes());
								InputStream in = client.getInputStream();
								int code = in.read();
								if (code != 42) {
									status = Status.DISCONNECTED;
									SwingUtilities.invokeLater(failer);
								} else {
									status = Status.CONNECTED;
									SwingUtilities.invokeLater(nonfailer);
								}
							} catch (IOException e) {
								status = Status.DISCONNECTED;
								SwingUtilities.invokeLater(failer);
							}
						}
					}

				};
				addJob(job);
			}
		}
	}

	public void getBuilders(final GetBuilderCallback callback) {
		synchronized (statusSyncer) {
			this.GetBuilderCallback = callback;
			if (status == Status.CONNECTED) {
				final List<BuildThreadInfo> builders = new ArrayList<>();
				final Runnable nonfailer = new Runnable() {

					@Override
					public void run() {
						callback.haveBuilders(builders);
					}

				};

				final Runnable failer = new Runnable() {

					@Override
					public void run() {
						callback.noBuildersBecauseOfError();
					}

				};

				Runnable job = new Runnable() {

					@Override
					public void run() {
						synchronized (statusSyncer) {
							try {
								client.getOutputStream().write(RemoteUIProviderServer.Operation.GET_BUILDERS.code);
								int totalBuilders = RemoteUIProviderServer.readInt(client);
								for (int i = 0; i < totalBuilders; i++) {
									int queueSize = RemoteUIProviderServer.readInt(client);
									int totalJobs = RemoteUIProviderServer.readInt(client);
									BuilderStatus bs = BuilderStatus.values()[RemoteUIProviderServer.readInt(client)];
									builders.add(new BuildThreadInfo(i, queueSize, totalJobs, bs));
								}
								SwingUtilities.invokeLater(nonfailer);
							} catch (IOException e) {
								status = Status.DISCONNECTED;
								SwingUtilities.invokeLater(failer);
							}
						}
					}

				};
				addJob(job);
			}
		}
	}
}
