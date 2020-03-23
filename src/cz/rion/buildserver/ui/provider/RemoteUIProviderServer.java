package cz.rion.buildserver.ui.provider;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.RuntimeDB.RuntimeUserStats;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.db.layers.LayeredUserDB.LocalUser;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.ui.events.BuilderUpdateEvent;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent;
import cz.rion.buildserver.ui.events.FileCreatedEvent;
import cz.rion.buildserver.ui.events.FileListLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.events.FileSavedEvent;
import cz.rion.buildserver.ui.events.StatusMessageEvent;
import cz.rion.buildserver.ui.events.UsersLoadedEvent;
import cz.rion.buildserver.ui.events.StatusMessageEvent.StatusMessageType;

public class RemoteUIProviderServer {

	public static void read(Socket sock, byte[] target) throws IOException {
		int needed = target.length;
		while (needed > 0) {
			int read = sock.getInputStream().read(target, target.length - needed, needed);
			if (read < 0) {
				throw new IOException("Read error");
			}
			needed -= read;
		}
	}

	public static int readInt(Socket sock) throws IOException {
		byte[] data = new byte[4];
		read(sock, data);
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

	public static void writeDate(Socket sock, Date d) throws IOException {
		long x = d.getTime();
		byte[] data = new byte[8];
		data[0] = (byte) ((x >> 56) & 0xff);
		data[1] = (byte) ((x >> 48) & 0xff);
		data[2] = (byte) ((x >> 40) & 0xff);
		data[3] = (byte) ((x >> 32) & 0xff);
		data[4] = (byte) ((x >> 24) & 0xff);
		data[5] = (byte) ((x >> 16) & 0xff);
		data[6] = (byte) ((x >> 8) & 0xff);
		data[7] = (byte) (x & 0xff);
		sock.getOutputStream().write(data);
	}

	public static Date readDate(Socket sock) throws IOException {
		byte[] data = new byte[8];
		read(sock, data);
		long l = 0;
		l |= data[0] & 0xff;
		l <<= 8;
		l |= data[1] & 0xff;
		l <<= 8;
		l |= data[2] & 0xff;
		l <<= 8;
		l |= data[3] & 0xff;
		l <<= 8;
		l |= data[4] & 0xff;
		l <<= 8;
		l |= data[5] & 0xff;
		l <<= 8;
		l |= data[6] & 0xff;
		l <<= 8;
		l |= data[7] & 0xff;
		return new Date(l);
	}

	public static void writeString(Socket sock, String str) throws IOException {
		byte[] raw = str.getBytes(Settings.getDefaultCharset());
		writeInt(sock, raw.length);
		sock.getOutputStream().write(raw);
	}

	public static String readString(Socket sock) throws IOException {
		int length = readInt(sock);
		byte[] raw = new byte[length];
		read(sock, raw);
		return new String(raw, Settings.getDefaultCharset());
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

	private Socket client = null;
	private final HTTPServer server;
	private final Object syncer = new Object();
	private boolean waitingForClient = false;
	private final StaticDB sdb;
	private final RuntimeDB db;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			Thread.currentThread().setName("Remote UI provider");
			async();
		}
	};

	public RemoteUIProviderServer(HTTPServer server) {
		this.server = server;
		this.db = server.db;
		this.sdb = server.sdb;
		thread.start();
	}

	private void closeClient(Socket client) {
		if (client != null) {
			try {
				client.close();
			} catch (Exception e) {
			}
		}
	}

	public void addClient(Socket socket) {
		synchronized (syncer) {
			closeClient(client);
			client = socket;
			if (waitingForClient) {
				waitingForClient = false;
				syncer.notify();
			}
		}
	}

	private void writeBuilder(BuildThread builder, Socket client) throws IOException {
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

	private boolean handle(int code, Socket client) throws IOException {
		if (code == BuildersLoadedEvent.ID) {
			int totalBuilders = server.builders.size();
			writeInt(client, BuildersLoadedEvent.ID);
			writeInt(client, totalBuilders);
			for (BuildThread builder : server.builders) {
				writeBuilder(builder, client);
			}
			return true;
		} else if (code == UsersLoadedEvent.ID) {
			writeUserList(client);
			return true;
		} else if (code == FileListLoadedEvent.ID) {
			writeFileList(client);
			return true;
		} else if (code == FileLoadedEvent.ID) {
			writeFile(client);
			return true;
		} else if (code == FileSavedEvent.ID) {
			storeFile(client);
			return true;
		} else if (code == FileCreatedEvent.ID) {
			createFile(client);
			return true;
		}
		return false;
	}

	private void createFile(Socket client) throws IOException {
		String newName = readString(client);
		writeInt(client, FileCreatedEvent.ID);
		try {
			FileInfo fo = sdb.createFile(newName, "");
			if (fo == null) {
				writeInt(client, 0);
				return;
			} else {
				writeInt(client, 1);
				writeInt(client, fo.ID);
				writeString(client, fo.FileName);
				writeString(client, fo.Contents);
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}

	private void storeFile(Socket client) throws IOException {
		int fileID = readInt(client);
		String newContents = readString(client);
		writeInt(client, FileSavedEvent.ID);
		try {
			FileInfo fo = sdb.getFile(fileID);
			if (fo == null) {
				writeInt(client, 0);
				return;
			} else {
				sdb.storeFile(fo, newContents);
				fo = sdb.getFile(fileID);
				if (fo == null) { // Check the write operation
					writeInt(client, 0);
				} else {
					writeInt(client, 1);
					writeInt(client, fo.ID);
					writeString(client, fo.FileName);
					writeString(client, fo.Contents);
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}

	private void writeFile(Socket client) throws IOException {
		int fileID = readInt(client);
		writeInt(client, FileLoadedEvent.ID);
		try {
			FileInfo fo = sdb.getFile(fileID);
			if (fo == null) {
				writeInt(client, 0);
				return;
			} else {
				writeInt(client, 1);
				writeInt(client, fo.ID);
				writeString(client, fo.FileName);
				writeString(client, fo.Contents);
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}

	private void writeFileList(Socket client) throws IOException {
		List<DatabaseFile> lst = sdb.getFiles();
		synchronized (syncer) {
			writeInt(client, FileListLoadedEvent.ID);
			writeInt(client, lst.size());
			for (DatabaseFile file : lst) {
				writeInt(client, file.ID);
				writeString(client, file.FileName);
			}
		}
	}

	private void writeUserList(Socket client) {
		List<RuntimeUserStats> stats = this.db.getUserStats();
		Map<String, LocalUser> statics = this.sdb.LoadedUsersByLogin;
		try {
			synchronized (syncer) {
				writeInt(client, UsersLoadedEvent.ID);
				writeInt(client, stats.size());
				for (RuntimeUserStats stat : stats) {
					LocalUser usr = statics.get(stat.Login);
					String fullName = "???";
					String group = "???";
					if (usr != null) {
						fullName = usr.FullName;
						group = usr.Group;
					}
					writeInt(client, stat.UserID);
					writeString(client, stat.Login);
					writeDate(client, stat.RegistrationDate);
					writeDate(client, stat.LastActiveDate);
					writeDate(client, stat.lastLoginDate);
					writeInt(client, stat.TotalTestsSubmitted);
					writeString(client, stat.LastTestID);
					writeDate(client, stat.LastTestDate);
					writeString(client, fullName);
					writeString(client, group);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			closeClient(client);
		}
	}

	private void writeRaw(String raw) {
		synchronized (syncer) {
			try {
				if (client != null) {
					writeInt(client, StatusMessageEvent.ID);
					writeString(client, raw);
				}
			} catch (Exception e) {
				e.printStackTrace();
				closeClient(client);
				client = null;
			}
		}
	}

	public void writeGetHTML(String address, String login, int code, String codeDescription, String path) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("code", new JsonNumber(code));
		obj.add("login", new JsonString(login));
		obj.add("result", new JsonString(codeDescription));
		obj.add("type", new JsonNumber(StatusMessageType.LOAD_HTML.code));
		obj.add("path", new JsonString(path));
		writeRaw(obj.getJsonString());
	}

	public void writeTestCollect(String address, String login) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("login", new JsonString(login));
		obj.add("type", new JsonNumber(StatusMessageType.GET_TESTS.code));
		writeRaw(obj.getJsonString());
	}

	public void writeGetResource(String address, String login, int code, String codeDescription, String path) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("code", new JsonNumber(code));
		obj.add("login", new JsonString(login));
		obj.add("result", new JsonString(codeDescription));
		obj.add("type", new JsonNumber(StatusMessageType.GET_RESOURCE.code));
		obj.add("path", new JsonString(path));
		writeRaw(obj.getJsonString());
	}

	public void writeTestResult(String address, String login, int code, String codeDescription, String asm, String test_id) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("code", new JsonNumber(code));
		obj.add("login", new JsonString(login));
		obj.add("result", new JsonString(codeDescription));
		obj.add("type", new JsonNumber(StatusMessageType.PERFORM_TEST.code));
		obj.add("asm", new JsonString(asm));
		obj.add("test_id", new JsonString(test_id));
		writeRaw(obj.getJsonString());
	}

	public void writeBuliderDataUpdate(int builderIndex, BuildThread buildThread) {
		synchronized (syncer) {
			if (client != null) {
				try {
					writeInt(client, BuilderUpdateEvent.ID);
					writeInt(client, builderIndex);
					writeBuilder(buildThread, client);
				} catch (IOException e) {
				}
			}
		}
	}

	private void async() {
		while (true) {
			Socket client = null;
			synchronized (syncer) {
				if (this.client == null) {
					waitingForClient = true;
					try {
						syncer.wait();
					} catch (InterruptedException e) {
					}
				}
				if (this.client != null) {
					client = this.client;
				}
			}
			try {
				byte[] b = new byte[1];
				read(client, b);
				int code = b[0];
				if (!handle(code, client)) {
					synchronized (syncer) {
						if (this.client == client) {
							this.client = null;
						}
						closeClient(client);
					}
				}
			} catch (Exception e) {
				if (this.client == client) {
					this.client = null;
				}
				closeClient(client);
			}
		}
	}
}
