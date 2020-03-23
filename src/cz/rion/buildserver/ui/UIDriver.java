package cz.rion.buildserver.ui;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.layers.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;
import cz.rion.buildserver.ui.events.Event;
import cz.rion.buildserver.ui.events.BuilderUpdateEvent;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent;
import cz.rion.buildserver.ui.events.EventManager;
import cz.rion.buildserver.ui.events.FileListLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.events.FileSavedEvent;
import cz.rion.buildserver.ui.events.FileSavedEvent.FileSaveResult;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.FileCreatedEvent;
import cz.rion.buildserver.ui.events.FileCreatedEvent.FileCreationInfo;
import cz.rion.buildserver.ui.events.StatusChangeEvent;
import cz.rion.buildserver.ui.events.StatusMessageEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserInfo;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class UIDriver {

	private static class UIDriverThread extends Thread {
		private boolean run = true;

	}

	public final EventManager EventManager = new EventManager();
	private Socket client;
	private Object writeSyncer = new Object();

	private UIDriverThread reader = null;
	private UIDriverThread writer = null;
	private Object threadSyncer = new Object();

	public final MainWindow wnd;

	public UIDriver(MainWindow wnd) {
		this.wnd = wnd;
	}

	private List<Runnable> jobs = new ArrayList<>();

	private boolean waiting = false;

	private void asyncWriter(UIDriverThread me) {
		Thread.currentThread().setName("UIDriver writer thread");
		while (me.run) {
			Runnable job;
			synchronized (jobs) {
				if (jobs.isEmpty()) {
					waiting = true;
					while (true) {
						try {
							jobs.wait(1000);
						} catch (InterruptedException e) {
						}
						if (!me.run) {
							return;
						}
						if (!jobs.isEmpty()) {
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

	private void asyncReader(UIDriverThread me) {
		Thread.currentThread().setName("UIDriver reader thread");
		while (me.run) {
			Event e = readNextEvent();
			if (e instanceof StatusChangeEvent) {
				StatusChangeEvent se = (StatusChangeEvent) e;
				if (se.getStatus() == Status.DISCONNECTED) {
					stopThreads();
				}
			}
			e.dispatch(EventManager);
		}
	}

	private void stopThreads() {
		synchronized (threadSyncer) {
			if (client != null) {
				try {
					client.close();
				} catch (Exception e) {
				}
				client = null;
			}
			if (reader != null) {
				reader.run = false;
				reader = null;
			}
			if (writer != null) {

				writer.run = false;
				writer = null;
				synchronized (jobs) {
					if (waiting) {
						waiting = false;
						jobs.notify();
					}
				}
			}
		}
	}

	private void startThreads(boolean stopCurrent, boolean startWriter, boolean startReader) {
		synchronized (threadSyncer) {
			if (stopCurrent) {
				stopThreads();
			}
			if (startReader) {
				reader = new UIDriverThread() {

					@Override
					public void run() {
						asyncReader(this);
					}
				};
				reader.start();
			}
			if (startWriter) {
				writer = new UIDriverThread() {

					@Override
					public void run() {
						asyncWriter(this);
					}
				};
				writer.start();
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

	private JsonObject readJSON() throws IOException {
		JsonValue val = JsonValue.parse(RemoteUIProviderServer.readString(client));
		if (val.isObject()) {
			return val.asObject();
		}
		throw new IOException("Not a JSON?");
	}

	private List<BuildThreadInfo> readBuilders() throws IOException {
		final List<BuildThreadInfo> builders = new ArrayList<>();
		int totalBuilders = RemoteUIProviderServer.readInt(client);
		for (int i = 0; i < totalBuilders; i++) {
			builders.add(readBuilder(i));
		}
		return builders;
	}

	private BuildThreadInfo readBuilder(int index) throws IOException {
		int queueSize = RemoteUIProviderServer.readInt(client);
		int totalJobsFinished = RemoteUIProviderServer.readInt(client);
		int totalAdminJobs = RemoteUIProviderServer.readInt(client);
		int totalHTMLJobs = RemoteUIProviderServer.readInt(client);
		int totalResourceJobs = RemoteUIProviderServer.readInt(client);
		int totalHackJobs = RemoteUIProviderServer.readInt(client);
		int totalJobsPassed = RemoteUIProviderServer.readInt(client);
		BuilderStats stats = new BuilderStats(totalJobsFinished, totalHackJobs, totalResourceJobs, totalHTMLJobs, totalAdminJobs, totalJobsPassed);
		BuilderStatus bs = BuilderStatus.values()[RemoteUIProviderServer.readInt(client)];
		return new BuildThreadInfo(index, queueSize, stats, bs);
	}

	private Event readNextEvent() {
		try {
			int code = RemoteUIProviderServer.readInt(client);
			if (code == BuildersLoadedEvent.ID) {
				return new BuildersLoadedEvent(readBuilders());
			} else if (code == BuilderUpdateEvent.ID) {
				int index = RemoteUIProviderServer.readInt(client);
				return new BuilderUpdateEvent(readBuilder(index));
			} else if (code == StatusMessageEvent.ID) {
				return new StatusMessageEvent(readJSON());
			} else if (code == UsersLoadedEvent.ID) {
				return new UsersLoadedEvent(readUsers(client));
			} else if (code == FileListLoadedEvent.ID) {
				return new FileListLoadedEvent(readFiles(client));
			} else if (code == FileLoadedEvent.ID) {
				return new FileLoadedEvent(readFile(client));
			} else if (code == FileSavedEvent.ID) {
				return new FileSavedEvent(readFileSave(client));
			} else if (code == FileCreatedEvent.ID) {
				return new FileCreatedEvent(readFileCreate(client));
			} else {
				throw new IOException("Invalid OP code: " + code);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new StatusChangeEvent(Status.DISCONNECTED);
	}

	private FileCreationInfo readFileCreate(Socket client) throws IOException {
		FileInfo fo = readFile(client);
		if (fo == null) {
			return new FileCreationInfo(null, false);
		}
		return new FileCreationInfo(fo, true);
	}

	private FileSaveResult readFileSave(Socket client) throws IOException {
		FileInfo fo = readFile(client);
		if (fo == null) {
			return new FileSaveResult(null, false);
		}
		return new FileSaveResult(fo, true);
	}

	private FileInfo readFile(Socket client) throws IOException {
		boolean exists = RemoteUIProviderServer.readInt(client) == 1;
		if (exists) {
			int id = RemoteUIProviderServer.readInt(client);
			String name = RemoteUIProviderServer.readString(client);
			String contents = RemoteUIProviderServer.readString(client);
			byte[] cnt = contents.getBytes(Settings.getDefaultCharset());
			contents = new String(cnt, Charset.forName("UTF-8"));
			return new FileInfo(id, name, contents);
		} else {
			return null;
		}
	}

	private List<DatabaseFile> readFiles(Socket client) throws IOException {
		List<DatabaseFile> lst = new ArrayList<>();
		int totalSize = RemoteUIProviderServer.readInt(client);
		for (int i = 0; i < totalSize; i++) {
			int id = RemoteUIProviderServer.readInt(client);
			String name = RemoteUIProviderServer.readString(client);
			lst.add(new DatabaseFile(id, name));
		}
		return lst;
	}

	private List<UserInfo> readUsers(Socket client) throws IOException {
		List<UserInfo> lst = new ArrayList<>();
		int totalSize = RemoteUIProviderServer.readInt(client);
		for (int i = 0; i < totalSize; i++) {
			int UserID = RemoteUIProviderServer.readInt(client);
			String Login = RemoteUIProviderServer.readString(client);
			Date RegistrationDate = RemoteUIProviderServer.readDate(client);
			Date LastActiveDate = RemoteUIProviderServer.readDate(client);
			Date lastLoginDate = RemoteUIProviderServer.readDate(client);
			int TotalTestsSubmitted = RemoteUIProviderServer.readInt(client);
			String LastTestID = RemoteUIProviderServer.readString(client);
			Date LastTestDate = RemoteUIProviderServer.readDate(client);
			String fullName = RemoteUIProviderServer.readString(client);
			String group = RemoteUIProviderServer.readString(client);
			UserInfo ui = new UserInfo(UserID, Login, fullName, group, RegistrationDate, LastActiveDate, lastLoginDate, TotalTestsSubmitted, LastTestID, LastTestDate);
			lst.add(ui);
		}
		return lst;
	}

	public void login(final String server, final int port, final String auth) {
		startThreads(true, true, false); // Start new writer (and stop current)
		addJob(new Runnable() {

			@Override
			public void run() {
				boolean stopAll = true;
				synchronized (EventManager.getStatusSyncer()) {
					if (EventManager.getStatus() == Status.DISCONNECTED) {
						setStatus(Status.CONNECTING);
						try {
							if (client != null) {
								client.close();
								client = null;
							}
							client = new Socket(server, port);
							OutputStream out = client.getOutputStream();
							synchronized (writeSyncer) {
								out.write(("AUTH " + auth + " HTTP/1.1\r\n\r\n").getBytes(Settings.getDefaultCharset()));
							}
							byte[] b = new byte[1];
							RemoteUIProviderServer.read(client, b);
							int code = b[0];
							if (code != 42) {
								throw new IOException("Invalid welcome message");
							}
							startThreads(false, false, true); // Load event reader
							stopAll = false;
							setStatus(Status.CONNECTED);
						} catch (IOException e) {
							setStatus(Status.DISCONNECTED);
						} finally {
							if (stopAll) {
								stopThreads();
							}
						}
					}
				}
			}
		});
	}

	private void setStatus(Status status) {
		StatusChangeEvent.setStatus(EventManager, status);
	}

	private void addJob(final int code, final Object... params) {
		addJob(new Runnable() {

			@Override
			public void run() {
				synchronized (EventManager.getStatusSyncer()) {
					synchronized (writeSyncer) {
						if (EventManager.getStatus() == Status.CONNECTED) {
							try {
								client.getOutputStream().write(code);
								for (Object o : params) {
									if (o instanceof String) {
										RemoteUIProviderServer.writeString(client, (String) o);
									} else if (o instanceof Integer) {
										RemoteUIProviderServer.writeInt(client, (int) o);
									} else {
										throw new Exception("Invalid object type to write");
									}
								}
							} catch (Exception e) {
								e.printStackTrace();
								setStatus(Status.DISCONNECTED);
							}
						}
					}
				}
			}

		});
	}

	public void getBuilders() {
		addJob(BuildersLoadedEvent.ID);
	}

	public void disconnect() {
		addJob(new Runnable() {

			@Override
			public void run() {
				synchronized (EventManager.getStatusSyncer()) {
					if (EventManager.getStatus() != Status.DISCONNECTED) {
						stopThreads();
						setStatus(Status.DISCONNECTED);
					}
				}
			}
		});
	}

	public void reloadUsers() {
		addJob(UsersLoadedEvent.ID);
	}

	public void reloadFiles() {
		addJob(FileListLoadedEvent.ID);
	}

	public void loadFile(int ID) {
		addJob(FileLoadedEvent.ID, ID);
	}

	public void saveFile(int ID, String newContents) {
		byte[] cnt = newContents.getBytes(Charset.forName("UTF-8"));
		addJob(FileSavedEvent.ID, ID, new String(cnt, Settings.getDefaultCharset()));
	}

	public void createFile(String name) {
		addJob(FileCreatedEvent.ID, name);
	}

}
