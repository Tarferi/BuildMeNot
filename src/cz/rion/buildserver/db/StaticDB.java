package cz.rion.buildserver.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class StaticDB extends SQLiteDB {

	public final List<LocalUser> LoadedUsers = new ArrayList<>();
	public final Map<String, LocalUser> LoadedUsersByLogin = new HashMap<>();

	private final Object fileTable = new Object();

	public static class RuntimeClientSession {
		public final String login;
		public final String session;
		public final String fullName;
		public final String group;

		private RuntimeClientSession(String login, String session, String fullName, String group) {
			this.login = login;
			this.session = session;
			this.fullName = fullName;
			this.group = group;
		}
	}

	private static class RemoteUser {
		public final String Login;
		public final String Group;
		public final String FullName;

		private RemoteUser(String login, String group, String fullName) {
			this.Login = login;
			this.Group = group;
			this.FullName = fullName;
		}
	}

	public class LocalUser extends RemoteUser {
		public final int ID;

		private LocalUser(int id, String login, String group, String fullName) {
			super(login, group, fullName);
			this.ID = id;
		}
	}

	public StaticDB(String fileName) throws DatabaseException {
		super(fileName);
		this.makeTable("users", KEY("ID"), TEXT("name"), TEXT("usergroup"), TEXT("login"));
		this.makeTable("files", KEY("ID"), TEXT("name"), TEXT("contents"));
		System.out.println("Loading static database...");
		loadLocalUsers();
		loadRemoteUsers();
		System.out.println("Loading static database finished");
	}

	public FileInfo createFile(String name, String contents) throws DatabaseException {
		synchronized (fileTable) {
			try {
				this.execute("INSERT INTO files (name, contents) VALUES ('?', '?')", name, contents);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
			JsonArray res = select("SELECT ID, name, contents FROM files ORDER BY ID Desc LIMIT 1").getJSON();
			if (res != null) {
				if (res.Value.size() == 1) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
							int id = obj.getNumber("ID").Value;
							String fname = obj.getString("name").Value;
							String c = obj.getString("contents").Value;
							return new FileInfo(id, fname, c);
						}
					}
				}
			}
		}
		return null;
	}

	public static class DatabaseFile {
		public final int ID;
		public final String FileName;

		public DatabaseFile(int id, String fileName) {
			this.ID = id;
			this.FileName = fileName;
		}

		@Override
		public String toString() {
			return "[" + ID + "] " + FileName;
		}
	}

	public FileInfo getFile(int fileID) throws DatabaseException {
		JsonArray res = select("SELECT ID, name, contents FROM files WHERE ID = ?", fileID).getJSON();
		if (res != null) {
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String contents = obj.getString("contents").Value;
						return new FileInfo(id, name, contents);
					}
				}
			}
		}
		return null;
	}

	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> result = new ArrayList<>();
		try {
			JsonArray res = select("SELECT ID, name FROM files").getJSON();
			if (res != null) {
				for (JsonValue val : res.Value) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name")) {
							int id = obj.getNumber("ID").Value;
							String name = obj.getString("name").Value;
							result.add(new DatabaseFile(id, name));
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return result;
	}

	public final void storeFile(DatabaseFile file, String newContents) {
		try {
			this.execute("UPDATE files SET name = '?', contents = '?' WHERE ID = ?", file.FileName, newContents, file.ID);
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public final FileInfo loadFile(String name) {
		try {
			JsonArray res = this.select("SELECT * FROM files WHERE name = '?'", name).getJSON();
			if (res != null) {
				if (!res.Value.isEmpty()) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
							int id = obj.getNumber("ID").Value;
							String fname = obj.getString("name").Value;
							String contents = obj.getString("contents").Value;
							return new FileInfo(id, fname, contents);
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

	private final boolean loadLocalUsers() {
		LoadedUsers.clear();
		LoadedUsersByLogin.clear();
		try {
			DatabaseResult res = this.select("SELECT * FROM users");
			JsonArray data = res.getJSON();
			if (data != null) {
				for (JsonValue val : data.Value) {
					if (!val.isObject()) {
						return false;
					}
					JsonObject obj = val.asObject();
					if (!obj.containsNumber("ID") || !obj.containsString("name") || !obj.containsString("login") || !obj.containsString("usergroup")) {
						return false;
					}
				}
				for (JsonValue val : data.Value) {
					JsonObject obj = val.asObject();
					int id = obj.getNumber("ID").Value;
					String name = obj.getString("name").Value;
					String usergroup = obj.getString("usergroup").Value;
					String login = obj.getString("login").Value;
					LocalUser user = new LocalUser(id, login, usergroup, name);
					LoadedUsers.add(user);
					LoadedUsersByLogin.put(login, user);
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	private final boolean loadRemoteUsers() throws DatabaseException {
		if (!Settings.UsersRemoteUserDB()) {
			return true;
		}
		JsonArray arr = null;
		try {
			String jsn = MyFS.readFile(Settings.GetRemoteUserDB());
			JsonValue val = JsonValue.parse(jsn);
			if (val.isArray()) {
				arr = val.asArray();
			} else {
				return false;
			}
		} catch (FileReadException e) {
			return false;
		}
		if (arr == null) {
			return false;
		}

		List<LocalUser> toUpdate = new ArrayList<>();
		List<RemoteUser> toCreate = new ArrayList<>();

		for (JsonValue val : arr.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("Name") && obj.containsString("Group") && obj.containsString("Login")) {
					String login = obj.getString("Login").Value;
					String name = obj.getString("Name").Value;
					String group = obj.getString("Group").Value;
					if (LoadedUsersByLogin.containsKey(login.toLowerCase())) {
						LocalUser known = LoadedUsersByLogin.get(login.toLowerCase());
						if (!name.equals(known.FullName) || !group.equals(known.Group)) {
							toUpdate.add(new LocalUser(known.ID, login, group, name));
						}
						continue;
					} else {
						toCreate.add(new RemoteUser(login, group, name));
						continue;
					}
				}
			}
			throw new DatabaseException("Incorrect user JSON format");
		}

		for (LocalUser user : toUpdate) {
			try {
				execute("UPDATE users SET name = '?', usergroup = '?', login = '?' WHERE ID = ?", user.FullName, user.Group, user.Login, user.ID);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}

		for (RemoteUser user : toCreate) {
			try {
				execute("INSERT INTO users (name, usergroup, login) VALUES ('?', '?', '?')", user.FullName, user.Group, user.Login);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		loadLocalUsers();
		return true;
	}
}
