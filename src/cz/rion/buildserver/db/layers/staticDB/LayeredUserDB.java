package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredUserDB extends LayeredDBFileWrapperDB {

	public final List<LocalUser> LoadedUsers = new ArrayList<>();
	public final Map<String, LocalUser> LoadedUsersByLogin = new HashMap<>();

	public LayeredUserDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("users", KEY("ID"), TEXT("name"), TEXT("usergroup"), TEXT("login"), BIGTEXT("permissions"));
		//loadLocalUsers();
		//loadRemoteUsers();
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

	private final boolean loadLocalUsers() {
		LoadedUsers.clear();
		LoadedUsersByLogin.clear();
		try {

			final String tableName = "users";
			JsonArray data = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "login"), getField(tableName, "usergroup") }, true);
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
				final String tableName = "users";
				this.update(tableName, user.ID, new ValuedField(this.getField(tableName, "name"), user.FullName), new ValuedField(this.getField(tableName, "usergroup"), user.Group), new ValuedField(this.getField(tableName, "login"), user.Login));
				// execute("UPDATE users SET name = '?', usergroup = '?', login = '?' WHERE ID =
				// ?", user.FullName, user.Group, user.Login, user.ID);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}

		for (RemoteUser user : toCreate) {
			try {
				final String tableName = "users";
				this.insert(tableName, new ValuedField(this.getField(tableName, "name"), user.FullName), new ValuedField(this.getField(tableName, "usergroup"), user.Group), new ValuedField(this.getField(tableName, "login"), user.Login));
				// execute("INSERT INTO users (name, usergroup, login) VALUES ('?', '?', '?')",
				// user.FullName, user.Group, user.Login);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		loadLocalUsers();
		return true;
	}

}
