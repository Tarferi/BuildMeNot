package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public abstract class LayeredUserDB extends LayeredDBFileWrapperDB {

	private static class PermissionContext {
		public final List<LocalUser> LoadedUsers = new ArrayList<>();
		public final Map<String, LocalUser> LoadedUsersByLogin = new HashMap<>();

		public final String toolchain;
		private final LayeredUserDB sdb;

		private PermissionContext(String toolchain, LayeredUserDB sdb) {
			this.toolchain = toolchain;
			this.sdb = sdb;
			reload();
		}

		public void reload() {
			this.sdb.loadLocalUsers(toolchain, LoadedUsers, LoadedUsersByLogin);
		}
	}

	public Map<String, LocalUser> getLoadedUsersByLogin(Toolchain toolchain) {
		if (!mappings.containsKey(toolchain.getName())) {
			mappings.put(toolchain.getName(), new PermissionContext(toolchain.getName(), this));
		}
		PermissionContext context = mappings.get(toolchain.getName());
		return context.LoadedUsersByLogin;
	}

	public LocalUser getUser(String toolchain, String login) {
		if (!mappings.containsKey(toolchain)) {
			mappings.put(toolchain, new PermissionContext(toolchain, this));
		}
		PermissionContext context = mappings.get(toolchain);
		if (context.LoadedUsersByLogin.containsKey(login)) {
			return context.LoadedUsersByLogin.get(login);
		} else {
			return null;
		}
	}

	private final Map<String, PermissionContext> mappings = new HashMap<>();

	public LayeredUserDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("users", KEY("ID"), TEXT("name"), TEXT("usergroup"), TEXT("login"), BIGTEXT("permissions"), TEXT("toolchain"));
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
		public final String PrimaryPermGroup;

		private LocalUser(int id, String login, String group, String fullName, String PrimaryPermGroup) {
			super(login, group, fullName);
			this.ID = id;
			this.PrimaryPermGroup = PrimaryPermGroup;
		}
	}

	private final Map<String, String> getPrimaryGroups(String toolchain) throws DatabaseException {
		Map<String, String> mp = new HashMap<>();

		final String usersTableName = "users";
		final String groupsTableName = "groups";
		final String userGroupsTableName = "users_group";

		TableField[] fields = new TableField[] { getField(groupsTableName, "name"), getField(usersTableName, "login"), };
		ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(getField(userGroupsTableName, "primary_group"), 1) };

		TableJoin[] joins = new TableJoin[] { new TableJoin(getField(usersTableName, "ID"), getField(userGroupsTableName, "user_id")), new TableJoin(getField(groupsTableName, "ID"), getField(userGroupsTableName, "group_id")),

		};
		JsonArray data = select(usersTableName, fields, comparators, joins, true);
		for (JsonValue item : data.Value) {
			String name = item.asObject().getString("name").Value;
			String login = item.asObject().getString("login").Value;
			mp.put(login.toLowerCase(), name);
		}
		return mp;
	}

	private final boolean loadLocalUsers(String toolchain, List<LocalUser> loadedUsers, Map<String, LocalUser> loadedUsersByLogin) {
		loadedUsers.clear();
		loadedUsersByLogin.clear();

		try {
			Map<String, String> primaryGroups = getPrimaryGroups(toolchain);
			final String tableName = "users";
			JsonArray data = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "login"), getField(tableName, "usergroup") }, true, new ComparisionField(getField(tableName, "toolchain"), toolchain));
			for (JsonValue val : data.Value) {
				JsonObject obj = val.asObject();
				int id = obj.getNumber("ID").Value;
				String name = obj.getString("name").Value;
				String usergroup = obj.getString("usergroup").Value;
				String login = obj.getString("login").Value;
				String permGroup = primaryGroups.containsKey(login.toLowerCase()) ? primaryGroups.get(login.toLowerCase()) : "";
				LocalUser user = new LocalUser(id, login, usergroup, name, permGroup);
				loadedUsers.add(user);
				loadedUsersByLogin.put(login, user);
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public Object[] getFullNameAndGroup(String login, String toolchain) {
		try {
			final String tableName = "users";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "name"), getField(tableName, "usergroup"), getField(tableName, "ID") }, true, new ComparisionField(getField(tableName, "login"), login), new ComparisionField(getField(tableName, "toolchain"), toolchain));
			if (!res.Value.isEmpty()) {
				String name = res.Value.get(0).asObject().getString("name").Value;
				String grp = res.Value.get(0).asObject().getString("usergroup").Value;
				int id = res.Value.get(0).asObject().getNumber("ID").Value;
				return new Object[] { name, grp, id };
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new Object[] { login, Settings.GetDefaultGroup(), 0 };
	}

	@Override
	public boolean clearUsers(String toolchain) {
		try {
			this.execute_raw("DELETE FROM users WHERE toolchain = ?", toolchain);
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		if (mappings.containsKey(toolchain)) {
			mappings.get(toolchain).reload();
		}
		return true;
	}

	@Override
	public boolean createUser(String toolchain, String login, String origin, String fullName, List<String> permissionGroups, int rootPermissionGroupID) {
		final String tableName = "users";
		JsonArray res;
		try {
			res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "toolchain"), toolchain), new ComparisionField(getField(tableName, "login"), login));
			if (res.Value.size() == 0) {
				return this.insert(tableName, new ValuedField(this.getField(tableName, "name"), fullName), new ValuedField(this.getField(tableName, "permissions"), "[]"), new ValuedField(this.getField(tableName, "usergroup"), origin), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "toolchain"), toolchain));
			} else if (res.Value.size() == 1) {
				if (res.Value.get(0).isObject()) {
					if (res.Value.get(0).asObject().containsNumber("ID")) {
						int id = res.Value.get(0).asObject().getNumber("ID").Value;
						return this.update(tableName, id, new ValuedField(this.getField(tableName, "name"), fullName), new ValuedField(this.getField(tableName, "permissions"), "[]"), new ValuedField(this.getField(tableName, "usergroup"), origin), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "toolchain"), toolchain));
					}
				}
				return false;
			} else { // Multiple accounts in the same toolchain?
				return false;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
	}
}
