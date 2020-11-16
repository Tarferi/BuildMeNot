package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.permissions.Permission;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.Pair;

public abstract class LayeredUserDB extends LayeredSSLDB {

	private static class PermissionContext {
		public final List<LocalUser> LoadedUsers = new ArrayList<>();
		public final Map<String, LocalUser> LoadedUsersByLogin = new HashMap<>();
		public final Map<Integer, LocalUser> LoadedUsersByID = new HashMap<>();

		public final Toolchain toolchain;
		private final LayeredUserDB sdb;

		private PermissionContext(Toolchain toolchain, LayeredUserDB sdb) {
			this.toolchain = toolchain;
			this.sdb = sdb;
			reload();
		}

		public void reload() {
			this.sdb.loadLocalUsers(toolchain, LoadedUsers, LoadedUsersByLogin, LoadedUsersByID);
		}
	}

	public static class PermissionedUser {
		public final String Name;
		public final String Login;
		public final String GroupName;
		public final int GroupID;

		private PermissionedUser(String name, String login, String group, int groupID) {
			this.Name = name;
			this.Login = login;
			this.GroupName = group;
			this.GroupID = groupID;
		}
	}

	@SuppressWarnings("deprecation")
	public List<PermissionedUser> getPermissionedUsers(Toolchain tc) {
		ArrayList<PermissionedUser> lst = new ArrayList<>();
		try {
			TableField[] fields = new TableField[] { getField("users", "name"), getField("users", "login"), getField("groups", "name").getRenamedInstance("gname"), getField("groups", "ID") };
			JsonArray res = this.select_raw("SELECT\r\n" + "	users.name as name,\r\n" + "	users.login as login,\r\n" + "	groups.name as gname,\r\n" + "	groups.ID as id\r\n" + "FROM\r\n" + "	users,\r\n" + "	groups,\r\n" + "	users_group\r\n" + "\r\n" + "WHERE\r\n" + "	users.id = users_group.user_id\r\n" + "AND\r\n" + "	groups.id = users_group.group_id\r\n" + "AND\r\n" + "	users_group.primary_group = 1\r\n" + "\r\n" + "AND users.toolchain='?'", tc.getName()).getJSON(false, fields, tc);
			for (JsonValue row : res.Value) {
				if (row.isObject()) {
					JsonObject obj = row.asObject();
					if (obj.containsString("name") && obj.containsString("login") && obj.containsString("gname") && obj.containsNumber("id")) {
						String name = obj.getString("name").Value;
						String gname = obj.getString("gname").Value;
						String login = obj.getString("login").Value;
						int id = obj.getNumber("id").Value;
						lst.add(new PermissionedUser(name, login, gname, id));
					}
				}
			}
		} catch (CompressionException | DatabaseException e) {
			e.printStackTrace();
		}
		return lst;
	}

	public Map<String, LocalUser> getLoadedUsersByLogin(Toolchain toolchain) {
		if (!mappings.containsKey(toolchain.getName())) {
			mappings.put(toolchain.getName(), new PermissionContext(toolchain, this));
		}
		PermissionContext context = mappings.get(toolchain.getName());
		return context.LoadedUsersByLogin;
	}

	public LocalUser getUser(Toolchain toolchain, String login) {
		if (!mappings.containsKey(toolchain.getName())) {
			mappings.put(toolchain.getName(), new PermissionContext(toolchain, this));
		}
		PermissionContext context = mappings.get(toolchain.getName());
		if (context.LoadedUsersByLogin.containsKey(login)) {
			return context.LoadedUsersByLogin.get(login);
		} else {
			return null;
		}
	}

	public LocalUser getUser(Toolchain toolchain, int userID) {
		if (!mappings.containsKey(toolchain.getName())) {
			mappings.put(toolchain.getName(), new PermissionContext(toolchain, this));
		}
		PermissionContext context = mappings.get(toolchain.getName());
		if (context.LoadedUsersByID.containsKey(userID)) {
			return context.LoadedUsersByID.get(userID);
		} else {
			return null;
		}
	}

	private final Map<String, PermissionContext> mappings = new HashMap<>();
	private final DatabaseInitData dbData;

	public LayeredUserDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.makeTable("users", false, KEY("ID"), TEXT("name"), TEXT("usergroup"), TEXT("login"), BIGTEXT("permissions"), TEXT("toolchain"));
		this.dbData = dbData;
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

	public class LocalUserPermissionGroup {
		public final String GroupName;
		public final int GroupID;
		public final boolean IsPrimary;

		private LocalUserPermissionGroup(int id, String name, boolean primary) {
			this.GroupID = id;
			this.GroupName = name;
			this.IsPrimary = primary;
		}
	}

	public class LocalUser extends RemoteUser {
		public final int ID;
		public final List<LocalUserPermissionGroup> PermissionGroups;

		private LocalUser(int id, String login, String group, String fullName, List<LocalUserPermissionGroup> groups) {
			super(login, group, fullName);
			this.ID = id;
			this.PermissionGroups = groups;
			if (groups.isEmpty()) {
				groups.add(new LocalUserPermissionGroup(-1, Settings.GetDefaultGroup(), true));
			}
		}

		public LocalUser getCloned() {
			List<LocalUserPermissionGroup> p = new ArrayList<>();
			p.addAll(PermissionGroups);
			return new LocalUser(ID, Login, Group, FullName, p);
		}
	}

	private final Map<String, List<LocalUserPermissionGroup>> getPermissionGroups(Toolchain toolchain) throws DatabaseException {
		Map<String, List<LocalUserPermissionGroup>> mp = new HashMap<>();

		final String usersTableName = "users";
		final String groupsTableName = "groups";
		final String userGroupsTableName = "users_group";

		TableField[] fields = new TableField[] { getField(groupsTableName, "name"), getField(groupsTableName, "ID").getRenamedInstance("GroupID"), getField(usersTableName, "login"), getField(userGroupsTableName, "primary_group") };
		ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(getField(userGroupsTableName, "toolchain"), toolchain.getName()) };

		TableJoin[] joins = new TableJoin[] { new TableJoin(getField(usersTableName, "ID"), getField(userGroupsTableName, "user_id")), new TableJoin(getField(groupsTableName, "ID"), getField(userGroupsTableName, "group_id")),

		};
		JsonArray data = select(usersTableName, fields, comparators, joins, true);
		for (JsonValue item : data.Value) {
			String name = item.asObject().getString("name").Value;
			String login = item.asObject().getString("login").Value;
			boolean primary = item.asObject().getNumber("primary_group").Value == 1;
			int gid = item.asObject().getNumber("GroupID").Value;
			if (!mp.containsKey(login.toLowerCase())) {
				ArrayList<LocalUserPermissionGroup> itm = new ArrayList<LocalUserPermissionGroup>();
				itm.add(new LocalUserPermissionGroup(gid, name, primary));
				mp.put(login.toLowerCase(), itm);
			} else {
				mp.get(login.toLowerCase()).add(new LocalUserPermissionGroup(gid, name, primary));
			}
		}
		return mp;
	}

	private final boolean loadLocalUsers(Toolchain toolchain, List<LocalUser> loadedUsers, Map<String, LocalUser> loadedUsersByLogin, Map<Integer, LocalUser> loadedUsersByID) {
		loadedUsers.clear();
		loadedUsersByLogin.clear();

		try {
			Map<String, List<LocalUserPermissionGroup>> groups = getPermissionGroups(toolchain);
			final String tableName = "users";
			JsonArray data = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "login"), getField(tableName, "usergroup") }, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
			for (JsonValue val : data.Value) {
				JsonObject obj = val.asObject();
				int id = obj.getNumber("ID").Value;
				String name = obj.getString("name").Value;
				String usergroup = obj.getString("usergroup").Value;
				String login = obj.getString("login").Value;
				List<LocalUserPermissionGroup> permGroups = groups.containsKey(login.toLowerCase()) ? groups.get(login.toLowerCase()) : new ArrayList<>();
				LocalUser user = new LocalUser(id, login, usergroup, name, permGroups);
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
								permissions.add(new PermissionBranch(toolchain, v.asString().Value));
							}
						}
					}
				}
				return new Object[] { name, grp, id, email };
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new Object[] { login, Settings.GetDefaultGroup(), -1, "" };
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean clearUsers(Toolchain toolchain) {
		try {
			this.execute_raw("DELETE FROM users WHERE toolchain = ?", toolchain.getName());
		} catch (DatabaseException e) {
			e.printStackTrace();
			return false;
		}
		if (mappings.containsKey(toolchain.getName())) {
			mappings.get(toolchain.getName()).reload();
		}
		return true;
	}

	public List<RemoteUser> getUserIDsWhoCanByGroup(Toolchain toolchain, PermissionBranch branch) throws DatabaseException {
		List<RemoteUser> lst = new ArrayList<>();
		final String usersTableName = "users";
		final String groupsTableName = "groups";
		final String userGroupsTableName = "users_group";

		final Set<String> foundBadPermissions = new HashSet<>();
		final Set<String> foundGoodPermissions = new HashSet<>();

		TableField[] fields = new TableField[] { getField(groupsTableName, "toolchain"), getField(usersTableName, "name"), getField(usersTableName, "login"), getField(groupsTableName, "permissions"), getField(groupsTableName, "name").getRenamedInstance("group_name") };
		ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(getField(groupsTableName, "toolchain"), toolchain.getName()) };

		TableJoin[] joins = new TableJoin[] { new TableJoin(getField(usersTableName, "ID"), getField(userGroupsTableName, "user_id")), new TableJoin(getField(groupsTableName, "ID"), getField(userGroupsTableName, "group_id")) };
		JsonArray data = select(usersTableName, fields, comparators, joins, true);
		for (JsonValue item : data.Value) {
			String perm = item.asObject().getString("permissions").Value;
			String ctoolchain = item.asObject().getString("toolchain").Value;
			if (ctoolchain.equals(toolchain.getName())) {
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
									add |= new Permission(toolchain, v.asString().Value).covers(branch);
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
	public boolean createUser(Toolchain toolchain, String login, String origin, String fullName, List<String> permissionGroups, int rootPermissionGroupID, String implicitPermission) {
		final String tableName = "users";
		JsonArray res;
		try {
			res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "login"), login));
			if (res.Value.size() == 0) {
				return this.insert(tableName, new ValuedField(this.getField(tableName, "name"), fullName), new ValuedField(this.getField(tableName, "permissions"), implicitPermission), new ValuedField(this.getField(tableName, "usergroup"), origin), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "toolchain"), toolchain.getName()));
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

	@Override
	public void clearCache() {
		super.clearCache();
		mappings.clear();
	}

	@Override
	public void afterInit() {
		final Map<String, UsersConfigFile> registeredFiles = new HashMap<>();
		super.afterInit();
		this.registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainAdded(Toolchain t) {
				synchronized (registeredFiles) {
					if (!registeredFiles.containsKey(t.getName())) {
						UsersConfigFile f = new UsersConfigFile(t);
						registeredFiles.put(t.getName(), f);
						dbData.Files.registerVirtualFile(f);
					}
				}
			}

			@Override
			public void toolchainRemoved(Toolchain t) {
				synchronized (registeredFiles) {
					if (registeredFiles.containsKey(t.getName())) {
						UsersConfigFile f = registeredFiles.remove(t.getName());
						dbData.Files.unregisterVirtualFile(f);
					}
				}
			}

		});
	}

	public abstract boolean assignGroup(Toolchain toolchain, String login, Collection<Pair<String, Boolean>> set, int rootPermissionGroupID);

	public abstract boolean changePrimaryForGroup(Toolchain toolchain, String login, String group, boolean primary);

	private final class UsersConfigFile extends VirtualFile {

		private static final String prefix_users = "\t - ";
		private static final String suffix_users = ":";
		private static final String prefix_groups_primary = "\t\t + ";
		private static final String suffix_groups_primary = "";
		private static final String prefix_groups = "\t\t - ";
		private static final String suffix_groups = "";

		public UsersConfigFile(Toolchain toolchain) {
			super("users_" + toolchain.getName() + ".ini", toolchain);
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			Map<String, LocalUser> data = getLoadedUsersByLogin(Toolchain);
			StringBuilder sb = new StringBuilder();
			sb.append("# Formát: \n");
			sb.append("#" + prefix_users + "xlogin00" + suffix_users + "\n");
			sb.append("#" + prefix_groups + "Skupina 1" + suffix_groups + "\n");
			sb.append("#" + prefix_groups + "Skupina 2" + suffix_groups + "\n");
			sb.append("#" + prefix_groups_primary + "Skupina 3 (primární)" + suffix_groups_primary + "\n");

			for (Entry<String, LocalUser> entry : data.entrySet()) {
				boolean appended = false;
				for (LocalUserPermissionGroup g : entry.getValue().PermissionGroups) {
					if (!g.GroupName.equals(Settings.GetDefaultGroup())) {
						if (!appended) {
							sb.append(prefix_users + entry.getKey() + suffix_users + "\n");
							appended = true;
						}
						if (g.IsPrimary) {
							sb.append(prefix_groups_primary + g.GroupName + suffix_groups_primary + "\n");
						} else {
							sb.append(prefix_groups + g.GroupName + suffix_groups + "\n");
						}
					}
				}
			}
			return sb.toString();
		}

		private Map<String, LocalUser> getLoadedUsersByLogin(Toolchain toolchain) {
			Map<String, LocalUser> mp = new HashMap<>();
			Map<String, LocalUser> original = LayeredUserDB.this.getLoadedUsersByLogin(toolchain);
			for (Entry<String, LocalUser> entry : original.entrySet()) {
				mp.put(entry.getKey(), entry.getValue().getCloned());
			}
			return mp;
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			try {
				String lastLogin = null;
				Map<String, Set<Pair<String, Boolean>>> data = new HashMap<>();
				for (String line : value.split("\n")) {
					if (line.startsWith("#") || line.trim().isEmpty()) {
						continue;
					}
					if (line.startsWith(prefix_users) && line.endsWith(suffix_users)) {
						lastLogin = line.substring(prefix_users.length()).trim();
						lastLogin = lastLogin.substring(0, lastLogin.length() - suffix_users.length());
					} else if (((line.startsWith(prefix_groups) && line.endsWith(suffix_groups)) || (line.startsWith(prefix_groups_primary) && line.endsWith(suffix_groups_primary))) && lastLogin != null) {
						boolean primary = line.startsWith(prefix_groups_primary);
						String group = line.substring(prefix_groups.length()).trim();
						group = group.substring(0, group.length() - suffix_groups.length());
						if (!data.containsKey(lastLogin)) {
							data.put(lastLogin, new HashSet<>());
						}
						data.get(lastLogin).add(new Pair<>(group, primary));
					} else {
						throw new VirtualFileException("Invalid format");
					}
				}
				Map<String, LocalUser> existing = getLoadedUsersByLogin(Toolchain);

				Map<String, Set<Pair<String, Boolean>>> newUsersOrGroups = new HashMap<>();
				Map<String, Set<String>> groupsToRemove = new HashMap<>();
				Map<String, Set<Pair<String, Boolean>>> groupsToUpdate = new HashMap<>();

				// Compare to existing data
				for (Entry<String, Set<Pair<String, Boolean>>> entry : data.entrySet()) {
					String login = entry.getKey().toLowerCase();
					if (!existing.containsKey(login)) { // Brand new user, not supported
						// newUsersOrGroups.put(login, entry.getValue());
					} else { // Existing user -> check groups
						Map<String, LocalUserPermissionGroup> existingGroups = new HashMap<>();
						for (LocalUserPermissionGroup g : existing.get(login).PermissionGroups) {
							if (!g.GroupName.equals(Settings.GetDefaultGroup())) {
								existingGroups.put(g.GroupName, g);
							}
						}
						for (Pair<String, Boolean> groupC : entry.getValue()) {
							String group = groupC.Key;
							boolean primary = groupC.Value;
							if (existingGroups.containsKey(group)) { // Group still exists
								if (existingGroups.get(group).IsPrimary != primary) { // Primary changed
									if (!groupsToUpdate.containsKey(login)) {
										groupsToUpdate.put(login, new HashSet<>());
									}
									groupsToUpdate.get(login).add(new Pair<>(group, primary));
								} else { // No change, do nothing

								}
								existingGroups.remove(group);
							} else { // Group to add
								if (!newUsersOrGroups.containsKey(login)) {
									newUsersOrGroups.put(login, new HashSet<>());
								}
								newUsersOrGroups.get(login).add(new Pair<>(group, primary));
							}
						}
						for (Entry<String, LocalUserPermissionGroup> groupC : existingGroups.entrySet()) {
							String group = groupC.getKey();
							if (!groupsToRemove.containsKey(login)) {
								groupsToRemove.put(login, new HashSet<>());
							}
							groupsToRemove.get(login).add(group);
						}
					}
				}

				boolean b = true;
				// Project changes
				JsonObject idata = new JsonObject();
				idata.add("action", "change_users");
				idata.add("toolchain", Toolchain.getName());
				JsonObject added = new JsonObject();
				JsonObject removed = new JsonObject();
				JsonObject updated = new JsonObject();

				for (Entry<String, Set<Pair<String, Boolean>>> entry : newUsersOrGroups.entrySet()) {
					String login = entry.getKey();
					b &= assignGroup(Toolchain, login, entry.getValue(), -1);
					JsonObject ar = new JsonObject();
					for (Pair<String, Boolean> s : entry.getValue()) {
						ar.add(s.Key, s.Value);
					}
					added.add(login, ar);
				}
				for (Entry<String, Set<String>> entry : groupsToRemove.entrySet()) {
					String login = entry.getKey();
					for (String group : entry.getValue()) {
						b &= unassignGroup(Toolchain, login, group);
					}
					JsonArray ar = new JsonArray();
					for (String s : entry.getValue()) {
						ar.add(s);
					}
					removed.add(login, ar);
				}
				for (Entry<String, Set<Pair<String, Boolean>>> entry : groupsToUpdate.entrySet()) {
					String login = entry.getKey();
					JsonArray arr = new JsonArray();
					for (Pair<String, Boolean> g : entry.getValue()) {
						String group = g.Key;
						boolean primary = g.Value;
						b &= changePrimaryForGroup(Toolchain, login, group, primary);
						JsonObject o = new JsonObject();
						o.add(group, primary);
						arr.add(o);
					}
					if (!arr.Value.isEmpty()) {
						updated.add("login", arr);
					}
				}

				if (!added.getEntries().isEmpty()) {
					idata.add("added", added);
				}
				if (!removed.getEntries().isEmpty()) {
					idata.add("removed", removed);
				}
				if (!removed.getEntries().isEmpty()) {
					idata.add("update", updated);
				}
				StaticDB sdb = (StaticDB) LayeredUserDB.this;
				sdb.adminLog(context.getToolchain(), context.getAddress(), context.getLogin(), "CHANGE_USERS", idata.getJsonString());
				return b;
			} finally {
				clearCache();
			}
		}
	}

	public abstract boolean unassignGroup(Toolchain toolchain, String login, String group);
}
