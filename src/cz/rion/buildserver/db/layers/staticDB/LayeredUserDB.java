package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.permissions.Permission;
import cz.rion.buildserver.permissions.PermissionBranch;

public abstract class LayeredUserDB extends LayeredSSLDB {

	private static class PermissionContext {
		public final List<LocalUser> LoadedUsers = new ArrayList<>();
		public final Map<String, LocalUser> LoadedUsersByLogin = new HashMap<>();
		public final Map<Integer, LocalUser> LoadedUsersByID = new HashMap<>();

		public final String toolchain;
		private final LayeredUserDB sdb;

		private PermissionContext(String toolchain, LayeredUserDB sdb) {
			this.toolchain = toolchain;
			this.sdb = sdb;
			reload();
		}

		public void reload() {
			this.sdb.loadLocalUsers(toolchain, LoadedUsers, LoadedUsersByLogin, LoadedUsersByID);
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

	public LocalUser getUser(String toolchain, int userID) {
		if (!mappings.containsKey(toolchain)) {
			mappings.put(toolchain, new PermissionContext(toolchain, this));
		}
		PermissionContext context = mappings.get(toolchain);
		if (context.LoadedUsersByID.containsKey(userID)) {
			return context.LoadedUsersByID.get(userID);
		} else {
			return null;
		}
	}

	private final Map<String, PermissionContext> mappings = new HashMap<>();

	public LayeredUserDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("users", KEY("ID"), TEXT("name"), TEXT("usergroup"), TEXT("login"), BIGTEXT("permissions"), TEXT("toolchain"));
	}

	public static class RemoteUser {
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

	private final boolean loadLocalUsers(String toolchain, List<LocalUser> loadedUsers, Map<String, LocalUser> loadedUsersByLogin, Map<Integer, LocalUser> loadedUsersByID) {
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
				loadedUsersByID.put(user.ID, user);
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public Object[] getFullNameAndGroupAndEmailAndOwnPerms(String login, Toolchain toolchain, Permission permissions) {
		try {
			final String tableName = "users";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "name"), getField(tableName, "permissions"), getField(tableName, "usergroup"), getField(tableName, "ID") }, true, new ComparisionField(getField(tableName, "login"), login), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
			if (!res.Value.isEmpty()) {
				String name = res.Value.get(0).asObject().getString("name").Value;
				String grp = res.Value.get(0).asObject().getString("usergroup").Value;
				String email = login.startsWith("x") ? login + "@stud.fit.vutbr.cz" : login + "@vutbr.cz"; // TODO: add email to database perhaps
				int id = res.Value.get(0).asObject().getNumber("ID").Value;
				String perms = res.Value.get(0).asObject().getString("permissions").Value;
				JsonValue pev = JsonValue.parse(perms);
				if (pev != null) {
					if (pev.isArray()) {
						for (JsonValue v : pev.asArray().Value) {
							if (v.isString()) {
								permissions.add(new PermissionBranch(v.asString().Value));
							}
						}
					}
				}
				return new Object[] { name, grp, id, email };
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new Object[] { login, Settings.GetDefaultGroup(), 0, "" };
	}

	@Override
	public boolean clearUsers(Toolchain toolchain) {
		try {
			this.execute_raw("DELETE FROM users WHERE toolchain = ?", toolchain.getName());
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		if (mappings.containsKey(toolchain)) {
			mappings.get(toolchain).reload();
		}
		return true;
	}

	public List<RemoteUser> getUserIDsWhoCanByGroup(String toolchain, PermissionBranch branch) throws DatabaseException {
		List<RemoteUser> lst = new ArrayList<>();
		final String usersTableName = "users";
		final String groupsTableName = "groups";
		final String userGroupsTableName = "users_group";

		final Set<String> foundBadPermissions = new HashSet<>();
		final Set<String> foundGoodPermissions = new HashSet<>();

		TableField[] fields = new TableField[] { getField(groupsTableName, "toolchain"), getField(usersTableName, "name"), getField(usersTableName, "login"), getField(groupsTableName, "permissions"), getField(groupsTableName, "name").getRenamedInstance("group_name") };
		ComparisionField[] comparators = new ComparisionField[] {};

		TableJoin[] joins = new TableJoin[] { new TableJoin(getField(usersTableName, "ID"), getField(userGroupsTableName, "user_id")), new TableJoin(getField(groupsTableName, "ID"), getField(userGroupsTableName, "group_id")) };
		JsonArray data = select(usersTableName, fields, comparators, joins, true);
		for (JsonValue item : data.Value) {
			String perm = item.asObject().getString("permissions").Value;
			String ctoolchain = item.asObject().getString("toolchain").Value;
			if (ctoolchain.equals(toolchain)) {
				if (!foundBadPermissions.contains(perm)) {
					String login = item.asObject().getString("login").Value;
					String name = item.asObject().getString("name").Value;
					String group_name = item.asObject().getString("group_name").Value;
					boolean add = false;
					if (!foundGoodPermissions.contains(perm)) {
						JsonValue val = JsonValue.parse(perm);
						if (val.isArray()) {
							for (JsonValue v : val.asArray().Value) {
								if (v.isString()) {
									add |= new Permission(v.asString().Value).covers(branch);
								}
							}
						}

						if (add) {
							foundGoodPermissions.add(perm);
						}
					} else { // it's in foundGoodPermissions
						add = true;
					}
					if (add) {
						lst.add(new RemoteUser(login, group_name, name));
					} else {
						foundBadPermissions.add(perm);
					}
				}
			}
		}
		return lst;
	}

	@Override
	public boolean createUser(Toolchain toolchain, String login, String origin, String fullName, List<String> permissionGroups, int rootPermissionGroupID) {
		final String tableName = "users";
		JsonArray res;
		try {
			res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "login"), login));
			if (res.Value.size() == 0) {
				return this.insert(tableName, new ValuedField(this.getField(tableName, "name"), fullName), new ValuedField(this.getField(tableName, "permissions"), "[]"), new ValuedField(this.getField(tableName, "usergroup"), origin), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "toolchain"), toolchain.getName()));
			} else if (res.Value.size() == 1) {
				if (res.Value.get(0).isObject()) {
					if (res.Value.get(0).asObject().containsNumber("ID")) {
						int id = res.Value.get(0).asObject().getNumber("ID").Value;
						return this.update(tableName, id, new ValuedField(this.getField(tableName, "name"), fullName), new ValuedField(this.getField(tableName, "permissions"), "[]"), new ValuedField(this.getField(tableName, "usergroup"), origin), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "toolchain"), toolchain.getName()));
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
