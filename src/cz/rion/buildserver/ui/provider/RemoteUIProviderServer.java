package cz.rion.buildserver.ui.provider;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.RuntimeDB.RuntimeUserStats;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.db.layers.LayeredUserDB.LocalUser;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.http.MySocketClient;
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

	public enum BuilderStatus {
		IDLE(0, "Idle"), WORKING(1, "Working");

		public final int code;
		public final String title;

		BuilderStatus(int code, String title) {
			this.code = code;
			this.title = title;
		}
	}

	private final List<RemoteUIClient> clients = new ArrayList<>();
	private final List<RemoteUIClient> unregisteredClients = new ArrayList<>();
	private final Selector selector;
	private final HTTPServer server;
	private final StaticDB sdb;
	private final RuntimeDB db;

	private final Thread thread = new Thread() {
		@Override
		public void run() {
			Thread.currentThread().setName("Remote UI provider");
			async();
		}
	};

	public RemoteUIProviderServer(HTTPServer server) throws IOException {
		selector = Selector.open();
		this.server = server;
		this.db = server.db;
		this.sdb = server.sdb;
		thread.start();
	}

	public void addClient(MySocketClient socket) {
		try {
			socket.configureBlocking(false);
		} catch (IOException e1) {
			e1.printStackTrace();
			socket.close();
			return;
		}
		RemoteUIClient client = new RemoteUIClient(socket);
		synchronized (unregisteredClients) {
			unregisteredClients.add(client);
			selector.wakeup();
		}
	}

	private void writeBuilder(BuildThread builder, MemoryBuffer outBuffer) {
		BuilderStats stats = builder.getBuilderStats();
		outBuffer.writeInt(builder.getQueueSize());
		outBuffer.writeInt(stats.getTotalJobsFinished());
		outBuffer.writeInt(stats.getTotalAdminJobs());
		outBuffer.writeInt(stats.getHTMLJobs());
		outBuffer.writeInt(stats.getTotalResourceJobs());
		outBuffer.writeInt(stats.getTotlaHackJobs());
		outBuffer.writeInt(stats.getTotalJobsPassed());
		outBuffer.writeInt(builder.getBuilderStatus().code);
	}

	private boolean handle(int code, InputPacketRequest inBuffer, MemoryBuffer outBuffer) throws IOException {
		if (code == BuildersLoadedEvent.ID) {
			int totalBuilders = server.builders.size();
			outBuffer.writeInt(BuildersLoadedEvent.ID);
			outBuffer.writeInt(totalBuilders);
			for (BuildThread builder : server.builders) {
				writeBuilder(builder, outBuffer);
			}
			return true;
		} else if (code == UsersLoadedEvent.ID) {
			writeUserList(inBuffer, outBuffer);
			return true;
		} else if (code == FileListLoadedEvent.ID) {
			writeFileList(inBuffer, outBuffer);
			return true;
		} else if (code == FileLoadedEvent.ID) {
			writeFile(inBuffer, outBuffer);
			return true;
		} else if (code == FileSavedEvent.ID) {
			storeFile(inBuffer, outBuffer);
			return true;
		} else if (code == FileCreatedEvent.ID) {
			createFile(inBuffer, outBuffer);
			return true;
		}
		return false;
	}

	private void createFile(InputPacketRequest inBuffer, MemoryBuffer outBuffer) throws IOException {
		String newName = inBuffer.readString();
		outBuffer.writeInt(FileCreatedEvent.ID);
		try {
			FileInfo fo = sdb.createFile(newName, "");
			if (fo == null) {
				outBuffer.writeInt(0);
				return;
			} else {
				outBuffer.writeInt(1);
				outBuffer.writeInt(fo.ID);
				outBuffer.writeString(fo.FileName);
				outBuffer.writeString(fo.Contents);
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}

	private void storeFile(InputPacketRequest inBuffer, MemoryBuffer outBuffer) throws IOException {
		int fileID = inBuffer.readInt();
		String newFileName = inBuffer.readString();
		String newContents = inBuffer.readString();
		outBuffer.writeInt(FileSavedEvent.ID);
		try {
			FileInfo fo = sdb.getFile(fileID);
			if (fo == null) {
				outBuffer.writeInt(0);
				return;
			} else {
				sdb.storeFile(fo, newFileName, newContents);
				fo = sdb.getFile(fileID);
				if (fo == null) { // Check the write operation
					outBuffer.writeInt(0);
				} else {
					outBuffer.writeInt(1);
					outBuffer.writeInt(fo.ID);
					outBuffer.writeString(fo.FileName);
					outBuffer.writeString(fo.Contents);
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			throw new IOException();
		}
	}

	private FileInfo getFile(int fileID) {
		FileInfo fo = null;
		try {
			fo = sdb.getFile(fileID);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		if (fo == null) {
			try {
				fo = LayeredDBFileWrapperDB.getFile(db, fileID);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		return fo;
	}

	private void writeFile(InputPacketRequest inBuffer, MemoryBuffer outBuffer) throws IOException {
		int fileID = inBuffer.readInt();
		outBuffer.writeInt(FileLoadedEvent.ID);
		FileInfo fo = LayeredDBFileWrapperDB.processPostLoadedFile(db, LayeredDBFileWrapperDB.processPostLoadedFile(sdb, getFile(fileID)));
		if (fo == null) {
			outBuffer.writeInt(0);
			return;
		} else {
			outBuffer.writeInt(1);
			outBuffer.writeInt(fo.ID);
			outBuffer.writeString(fo.FileName);
			outBuffer.writeString(fo.Contents);
		}
	}

	private void writeFileList(InputPacketRequest inBuffer, MemoryBuffer outBuffer) {
		List<DatabaseFile> lst = sdb.getFiles();
		LayeredDBFileWrapperDB.loadDatabaseFiles(db, lst);
		outBuffer.writeInt(FileListLoadedEvent.ID);
		outBuffer.writeInt(lst.size());
		for (DatabaseFile file : lst) {
			outBuffer.writeInt(file.ID);
			outBuffer.writeString(file.FileName);
		}
	}

	private void writeUserList(InputPacketRequest inBuffer, MemoryBuffer outBuffer) {
		List<RuntimeUserStats> stats = this.db.getUserStats();
		Map<String, LocalUser> statics = this.sdb.LoadedUsersByLogin;
		outBuffer.writeInt(UsersLoadedEvent.ID);
		outBuffer.writeInt(stats.size());
		for (RuntimeUserStats stat : stats) {
			LocalUser usr = statics.get(stat.Login);
			String fullName = "???";
			String group = "???";
			if (usr != null) {
				fullName = usr.FullName;
				group = usr.Group;
			}
			outBuffer.writeInt(stat.UserID);
			outBuffer.writeString(stat.Login);
			outBuffer.writeDate(stat.RegistrationDate);
			outBuffer.writeDate(stat.LastActiveDate);
			outBuffer.writeDate(stat.lastLoginDate);
			outBuffer.writeInt(stat.TotalTestsSubmitted);
			outBuffer.writeString(stat.LastTestID);
			outBuffer.writeDate(stat.LastTestDate);
			outBuffer.writeString(fullName);
			outBuffer.writeString(group);
		}
	}

	private void writeStatusMessage(MemoryBuffer outBuffer, String raw) {
		outBuffer.writeInt(StatusMessageEvent.ID);
		outBuffer.writeString(raw);
	}

	private void dispatch(MemoryBuffer outBuffer) {
		System.out.println("[RemoteUI] sending " + RemoteUIClient.RemoteOperation.fromCode(outBuffer.get()[4]));
		if (outBuffer.isForSingleClient()) {
			RemoteUIClient client = outBuffer.getClient();
			if (!client.write(outBuffer, false)) {
				closeClient(client);
			}
		} else {
			List<RemoteUIClient> toClose = new ArrayList<>();
			synchronized (clients) {
				for (RemoteUIClient client : clients) {
					if (!client.write(outBuffer, false)) {
						toClose.add(client);
					}
				}
				for (RemoteUIClient client : toClose) {
					client.close();
					clients.remove(client);
				}
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
		String str = obj.getJsonString();
		MemoryBuffer outBuffer = new MemoryBuffer.BroadcastMemoryBuffer(str.length());
		writeStatusMessage(outBuffer, str);
		dispatch(outBuffer);
	}

	public void writeTestCollect(String address, String login) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("login", new JsonString(login));
		obj.add("type", new JsonNumber(StatusMessageType.GET_TESTS.code));
		String str = obj.getJsonString();
		MemoryBuffer outBuffer = new MemoryBuffer.BroadcastMemoryBuffer(str.length());
		writeStatusMessage(outBuffer, str);
		dispatch(outBuffer);
	}

	public void writeGetResource(String address, String login, int code, String codeDescription, String path) {
		JsonObject obj = new JsonObject();
		obj.add("address", new JsonString(address));
		obj.add("code", new JsonNumber(code));
		obj.add("login", new JsonString(login));
		obj.add("result", new JsonString(codeDescription));
		obj.add("type", new JsonNumber(StatusMessageType.GET_RESOURCE.code));
		obj.add("path", new JsonString(path));
		String str = obj.getJsonString();
		MemoryBuffer outBuffer = new MemoryBuffer.BroadcastMemoryBuffer(str.length());
		writeStatusMessage(outBuffer, str);
		dispatch(outBuffer);
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
		String str = obj.getJsonString();
		MemoryBuffer outBuffer = new MemoryBuffer.BroadcastMemoryBuffer(str.length());
		writeStatusMessage(outBuffer, str);
		dispatch(outBuffer);
	}

	public void writeBuilderDataUpdate(int builderIndex, BuildThread buildThread) {
		MemoryBuffer outBuffer = new MemoryBuffer.BroadcastMemoryBuffer(64);
		outBuffer.writeInt(BuilderUpdateEvent.ID);
		outBuffer.writeInt(builderIndex);
		writeBuilder(buildThread, outBuffer);
		dispatch(outBuffer);
	}

	private void closeClient(RemoteUIClient client) {
		synchronized (clients) {
			client.close();
			clients.remove(client);
		}
	}

	private void handle(RemoteUIClient client, InputPacketRequest request) {
		try {
			byte[] b = new byte[1];
			request.read(b);
			int code = b[0];
			System.out.println("[RemoteUIProvider]: received " + RemoteUIClient.RemoteOperation.fromCode(code));
			MemoryBuffer toSend = new MemoryBuffer.SignleClientMemoryBuffer(128, client);
			if (!handle(code, request, toSend)) {
				synchronized (clients) {
					client.close();
				}
			} else { // Handled, send response
				dispatch(toSend);
			}
		} catch (Exception e) {
			e.printStackTrace();
			closeClient(client);
		}
	}

	private void async() {
		while (true) {
			try {
				selector.select(500);
				synchronized (unregisteredClients) {
					if (!unregisteredClients.isEmpty()) {
						for (RemoteUIClient client : unregisteredClients) {
							client.register(selector, SelectionKey.OP_READ, client);
							clients.add(client);
						}
						unregisteredClients.clear();
					}
				}
				Set<SelectionKey> keys = selector.selectedKeys();
				for (SelectionKey sk : keys) {
					Object attach = sk.attachment();
					RemoteUIClient client = null;
					if (attach instanceof RemoteUIClient) {
						client = (RemoteUIClient) attach;
					}
					if (sk.isReadable()) {
						if (client != null) {
							try {
								client.processAvailableBytes();
								while (client.hasNextAsyncEvent()) {
									InputPacketRequest packet = client.getNextAsync();
									handle(client, packet);
								}
							} catch (Throwable t) { // Close client and continue
								closeClient(client);
							}
						}
					}
					if (client != null) {
						if(!client.isConnected()) {
							closeClient(client);
						}
					}
				}
				keys.clear();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
