package cz.rion.buildserver.ui;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.db.layers.staticDB.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;
import cz.rion.buildserver.ui.events.DatabaseTableRowEditEvent;
import cz.rion.buildserver.ui.events.Event;
import cz.rion.buildserver.ui.events.BuilderUpdateEvent;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent;
import cz.rion.buildserver.ui.events.EventManager;
import cz.rion.buildserver.ui.events.FileListLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.events.FileSavedEvent;
import cz.rion.buildserver.ui.events.FileSavedEvent.FileSaveResult;
import cz.rion.buildserver.ui.events.PingEvent;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.FileCreatedEvent;
import cz.rion.buildserver.ui.events.FileCreatedEvent.FileCreationInfo;
import cz.rion.buildserver.ui.events.StatusChangeEvent;
import cz.rion.buildserver.ui.events.StatusMessageEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserInfo;
import cz.rion.buildserver.ui.provider.InputPacketRequest;
import cz.rion.buildserver.ui.provider.MemoryBuffer;
import cz.rion.buildserver.ui.provider.RemoteUIClient;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class UIDriver {

	private static class UIDriverThread extends Thread {
		private boolean run = true;

	}

	public final EventManager EventManager = new EventManager();
	private RemoteUIClient client;
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
				e.printStackTrace();
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

	private JsonObject readJSON(InputPacketRequest inBuffer) throws IOException {
		JsonValue val = JsonValue.parse(inBuffer.readString());
		if (val.isObject()) {
			return val.asObject();
		}
		throw new IOException("Not a JSON?");
	}

	private List<BuildThreadInfo> readBuilders(InputPacketRequest inBuffer) throws IOException {
		final List<BuildThreadInfo> builders = new ArrayList<>();
		int totalBuilders = inBuffer.readInt();
		for (int i = 0; i < totalBuilders; i++) {
			builders.add(readBuilder(inBuffer, i));
		}
		return builders;
	}

	private BuildThreadInfo readBuilder(InputPacketRequest inBuffer, int index) throws IOException {
		int queueSize = inBuffer.readInt();
		int totalJobsFinished = inBuffer.readInt();
		int totalAdminJobs = inBuffer.readInt();
		int totalHTMLJobs = inBuffer.readInt();
		int totalResourceJobs = inBuffer.readInt();
		int totalHackJobs = inBuffer.readInt();
		int totalJobsPassed = inBuffer.readInt();
		BuilderStats stats = new BuilderStats(totalJobsFinished, totalHackJobs, totalResourceJobs, totalHTMLJobs, totalAdminJobs, totalJobsPassed);
		int builderID = inBuffer.readInt();
		BuilderStatus bs = BuilderStatus.values()[builderID];
		return new BuildThreadInfo(index, queueSize, stats, bs);
	}

	private Event readNextEvent() {
		InputPacketRequest inBuffer = client.getNext(true);
		if (inBuffer != null) {
			try {
				int code = inBuffer.readInt();
				if (code == BuildersLoadedEvent.ID) {
					return new BuildersLoadedEvent(readBuilders(inBuffer));
				} else if (code == BuilderUpdateEvent.ID) {
					int index = inBuffer.readInt();
					return new BuilderUpdateEvent(readBuilder(inBuffer, index));
				} else if (code == StatusMessageEvent.ID) {
					return new StatusMessageEvent(readJSON(inBuffer));
				} else if (code == UsersLoadedEvent.ID) {
					return new UsersLoadedEvent(readUsers(inBuffer));
				} else if (code == FileListLoadedEvent.ID) {
					return new FileListLoadedEvent(readFiles(inBuffer));
				} else if (code == FileLoadedEvent.ID) {
					return new FileLoadedEvent(readFile(inBuffer));
				} else if (code == FileSavedEvent.ID) {
					return new FileSavedEvent(readFileSave(inBuffer));
				} else if (code == FileCreatedEvent.ID) {
					return new FileCreatedEvent(readFileCreate(inBuffer));
				} else if (code == DatabaseTableRowEditEvent.ID) {
					return new DatabaseTableRowEditEvent(readDatabaseEditResult(inBuffer));
				} else if (code == PingEvent.ID) {
					return new PingEvent(inBuffer.readString());
				} else {
					throw new IOException("Invalid OP code: " + code);
				}
			} catch (IOException e) {
				if (client != null) { // Client closed from another thread
					e.printStackTrace();
				}
			}
		}
		System.out.print("Empty input buffer");
		return new StatusChangeEvent(Status.DISCONNECTED);
	}

	private boolean readDatabaseEditResult(InputPacketRequest inBuffer) throws IOException {
		int val = inBuffer.readInt();
		return val == 42;
	}

	private FileCreationInfo readFileCreate(InputPacketRequest client) throws IOException {
		FileInfo fo = readFile(client);
		if (fo == null) {
			return new FileCreationInfo(null, false);
		}
		return new FileCreationInfo(fo, true);
	}

	private FileSaveResult readFileSave(InputPacketRequest client) throws IOException {
		FileInfo fo = readFile(client);
		if (fo == null) {
			return new FileSaveResult(null, false);
		}
		return new FileSaveResult(fo, true);
	}

	private FileInfo readFile(InputPacketRequest inBuffer) throws IOException {
		boolean exists = inBuffer.readInt() == 1;
		if (exists) {
			int id = inBuffer.readInt();
			String name = inBuffer.readString();
			String contents = inBuffer.readString();
			byte[] cnt = contents.getBytes(Settings.getDefaultCharset());
			contents = new String(cnt, Charset.forName("UTF-8"));
			return new FileInfo(id, name, contents);
		} else {
			return null;
		}
	}

	private List<DatabaseFile> readFiles(InputPacketRequest inBuffer) throws IOException {
		List<DatabaseFile> lst = new ArrayList<>();
		int totalSize = inBuffer.readInt();
		for (int i = 0; i < totalSize; i++) {
			int id = inBuffer.readInt();
			String name = inBuffer.readString();
			lst.add(new DatabaseFile(id, name));
		}
		return lst;
	}

	private List<UserInfo> readUsers(InputPacketRequest inBuffer) throws IOException {
		List<UserInfo> lst = new ArrayList<>();
		int totalSize = inBuffer.readInt();
		for (int i = 0; i < totalSize; i++) {
			int UserID = inBuffer.readInt();
			String Login = inBuffer.readString();
			Date RegistrationDate = inBuffer.readDate();
			Date LastActiveDate = inBuffer.readDate();
			Date lastLoginDate = inBuffer.readDate();
			int TotalTestsSubmitted = inBuffer.readInt();
			String LastTestID = inBuffer.readString();
			Date LastTestDate = inBuffer.readDate();
			String fullName = inBuffer.readString();
			String group = inBuffer.readString();
			String PermissionGroup = inBuffer.readString();
			UserInfo ui = new UserInfo(UserID, Login, fullName, group, RegistrationDate, LastActiveDate, lastLoginDate, TotalTestsSubmitted, LastTestID, LastTestDate, PermissionGroup);
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
						CompatibleSocketClient raw = null;
						try {
							if (client != null) {
								client.close();
								client = null;
							}
							raw = new CompatibleSocketClient(new Socket(server, port));
							raw.writeSync(("AUTH " + auth + " HTTP/1.1\r\n\r\n").getBytes(Settings.getDefaultCharset()));
							int code = raw.readSync();
							if (code != 42) {
								throw new IOException("Invalid welcome message");
							}

							client = new RemoteUIClient(raw);
							startThreads(false, false, true); // Load event reader
							stopAll = false;
							setStatus(Status.CONNECTED);
						} catch (IOException e) {
							setStatus(Status.DISCONNECTED);
						} finally {
							if (stopAll) {
								stopThreads();
							}
							if (client == null && raw != null) {
								raw.close();
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
					if (EventManager.getStatus() == Status.CONNECTED) {
						try {
							MemoryBuffer buf = new MemoryBuffer.SignleClientMemoryBuffer(32, client);
							buf.write(new byte[] { (byte) code });
							for (Object o : params) {
								if (o instanceof String) {
									buf.writeString((String) o);
								} else if (o instanceof Integer) {
									buf.writeInt((int) o);
								} else {
									throw new Exception("Invalid object type to write");
								}
							}
							synchronized (writeSyncer) {
								if (!client.write(buf, true)) {
									setStatus(Status.DISCONNECTED);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
							setStatus(Status.DISCONNECTED);
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

	public void saveFile(int ID, String fileName, String newContents) {
		byte[] cnt = newContents.getBytes(Charset.forName("UTF-8"));
		addJob(FileSavedEvent.ID, ID, fileName, new String(cnt, Settings.getDefaultCharset()));
	}

	public void createFile(String name) {
		addJob(FileCreatedEvent.ID, name);
	}

	public void editTableRow(int fileID, JsonObject values) {
		addJob(DatabaseTableRowEditEvent.ID, fileID, values.getJsonString());
	}

	public void sengPing(String pinger) {
		addJob(PingEvent.ID, pinger);
	}

}
