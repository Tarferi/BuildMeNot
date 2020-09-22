package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.permissions.Permission;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.permissions.WebPermission;
import cz.rion.buildserver.utils.CachedData;

public class LayeredPermissionDB extends LayeredTestDB {

	private static final int PERMISSION_REFRESH_SEC = 0;

	public static class PermissionManager {

		private final LayeredPermissionDB db;
		private final String defaultUsername;

		public PermissionManager(LayeredPermissionDB db, String defaultUsername) {
			this.db = db;
			this.defaultUsername = defaultUsername;
		}

		public UsersPermission getPermissionForLogin(String toolchain, int session_id, String login, int user_id) {
			return new UsersPermission(toolchain, session_id, login, user_id, db);
		}

		public UsersPermission getDefaultPermission(String toolchain) {
			return new UsersPermission(toolchain, 0, defaultUsername, 0, db);
		}
	}

	public static final class UsersPermission {

		private Permission permissions;
		private List<String> primaries = null;
		private final LayeredPermissionDB db;
		public final String Login;
		private String fullName = null;
		private String userGroup = null;
		private int StaticUserID;
		public final int UserID;
		private int SessionID;
		private final String Toolchain;

		public int getSessionID() {
			return SessionID;
		}

		public JsonObject getIdentity() {
			handleInit();
			JsonObject obj = new JsonObject();
			obj.add("login", new JsonString(Login));
			obj.add("name", new JsonString(fullName));
			obj.add("group", new JsonString(userGroup));
			obj.add("primary", new JsonString(primaries.isEmpty() ? "" : primaries.get(0)));
			return obj;
		}

		private UsersPermission(String toolchain, int session_id, String login, int user_id, LayeredPermissionDB db) {
			this.db = db;
			this.Login = login;
			this.UserID = user_id;
			this.SessionID = session_id;
			this.Toolchain = toolchain;
		}

		public final boolean allowDetails(String test_id) {
			return can(WebPermission.SeeDetails(Toolchain, test_id));
		}

		public final boolean allowSee(String test_id) {
			return can(WebPermission.SeeTest(Toolchain, test_id));
		}

		public boolean allowExecute(String test_id) {
			return can(WebPermission.ExecuteTest(Toolchain, test_id));
		}

		public final boolean allowFireFox() {
			return can(WebPermission.SeeFireFox);
		}

		public int getStaticUserID() {
			handleInit();
			return StaticUserID;
		}

		private void handleInit() {
			if (permissions == null) {
				permissions = new Permission("");
				primaries = new ArrayList<>();
				db.getPermissionsFor(Login, permissions, primaries, Toolchain);
			}
			if (fullName == null) {
				Object[] parts = db.getFullNameAndGroup(Login, Toolchain);
				fullName = (String) parts[0];
				userGroup = (String) parts[1];
				StaticUserID = (int) parts[2];
			}
		}

		public boolean hasKnownPrimaryGroup() {
			handleInit();
			return !primaries.isEmpty();
		}

		public String getPrimaryGroup() {
			return primaries.get(0);
		}

		public boolean can(PermissionBranch action) {
			if (Login == null) {
				return false;
			}
			handleInit();
			return permissions.covers(action);
		}

		public boolean allowSeeSecretTests() {
			return can(WebPermission.SeeSecretTests);
		}

		public boolean allowBypassTimeout() {
			return can(WebPermission.BypassTimeout);
		}

		public boolean allowSeeWebAdmin() {
			return can(WebPermission.SeeAdminAdmin);
		}

	}

	private final PermissionManager manager = new PermissionManager(this, Settings.GetDefaultUsername());

	public PermissionManager getPermissionManager() {
		return manager;
	}

	public void getPermissionsFor(String login, Permission permissions, List<String> primaries, String toolchain) {
		List<InternalUserGroupMemberShip> groups = getUserGroups(login, toolchain);
		if (groups != null) {
			for (InternalUserGroupMemberShip group : groups) {
				for (String perm : group.Group.getPermissions(true)) {
					permissions.add(new PermissionBranch(perm));
				}
				if (group.isPrimary) {
					primaries.add(group.Group.Name);
				}
			}
		}
	}

	private class InternalUserGroup {
		private final String Name;
		private final int ID;
		private final List<String> Permissions = new ArrayList<>();
		private InternalUserGroup Parent;

		private InternalUserGroup(int id, String name, String permissions, InternalUserGroup Parent) {
			this.Name = name;
			this.ID = id;
			this.Parent = Parent;
			JsonValue val = JsonValue.parse(permissions);
			if (val != null) {
				if (val.isArray()) {
					for (JsonValue v : val.asArray().Value) {
						if (v.isString()) {
							Permissions.add(v.asString().Value);
						}
					}
				}
			}
		}

		public List<String> getPermissions(boolean includeParent) {
			List<String> perms = new ArrayList<>();
			perms.addAll(Permissions);
			if (includeParent && Parent != null && Parent != this) {
				perms.addAll(Parent.getPermissions(includeParent));
			}
			return perms;
		}
	}

	private class InternalUserGroupMemberShip {
		public final boolean isPrimary;
		public final InternalUserGroup Group;

		private InternalUserGroupMemberShip(boolean primary, InternalUserGroup group) {
			this.isPrimary = primary;
			this.Group = group;
		}
	}

	private class GroupsCacheCLS extends CachedData<Map<Integer, InternalUserGroup>> {

		private String toolchain;

		public GroupsCacheCLS(String toolchain) {
			super(PERMISSION_REFRESH_SEC);
			this.toolchain = toolchain;
		}

		@Override
		protected Map<Integer, InternalUserGroup> update() {
			Map<Integer, InternalUserGroup> ret = new HashMap<>();
			final String tableName = "groups";
			try {
				final TableField groups_id = getField(tableName, "ID");
				final TableField groups_name = getField(tableName, "name");
				final TableField groups_permission = getField(tableName, "permissions");
				final TableField groups_parent = getField(tableName, "parent_group_id");
				final TableField groups_toolchain = getField(tableName, "toolchain");

				final TableField[] fields = new TableField[] { groups_id, groups_name, groups_permission, groups_parent, groups_toolchain };
				final ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(groups_toolchain, toolchain) };
				JsonArray res = select(tableName, fields, true, comparators);
				Map<Integer, Integer> parents = new HashMap<>();
				for (JsonValue val : res.Value) {
					boolean ok = false;
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("permissions") && obj.containsNumber("parent_group_id")) {
							int id = obj.getNumber("ID").Value;
							String name = obj.getString("name").Value;
							String perms = obj.getString("permissions").Value;
							int parent_id = obj.getNumber("parent_group_id").Value;
							ret.put(id, new InternalUserGroup(id, name, perms, (InternalUserGroup) null));
							parents.put(id, parent_id);
							ok = true;
						}
					}
					if (!ok) {
						return null;
					}
				}
				for (Entry<Integer, Integer> parent : parents.entrySet()) {
					int group_id = parent.getKey();
					int parent_id = parent.getValue();
					if (parent_id == -1) {
						continue;
					} else if (ret.containsKey(parent_id) && ret.containsKey(group_id)) {
						ret.get(group_id).Parent = ret.get(parent_id);
					} else {
						return null;
					}
				}

				return ret;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

	};

	private final Map<String, GroupsCacheCLS> GroupCaches = new HashMap<>();

	private Map<Integer, InternalUserGroup> loadGroups(String toolchain) {
		synchronized (GroupCaches) {
			if (GroupCaches.containsKey(toolchain)) {
				return GroupCaches.get(toolchain).get();
			} else {
				GroupsCacheCLS cache = new GroupsCacheCLS(toolchain);
				GroupCaches.put(toolchain, cache);
				return cache.get();
			}
		}
	}

	private List<InternalUserGroupMemberShip> getUserMemberships(String toolchain, int userID) {
		Map<Integer, InternalUserGroup> groups = loadGroups(toolchain);
		if (groups != null) {
			List<InternalUserGroupMemberShip> lst = new ArrayList<>();
			final String tableName = "users_group";
			try {
				final TableField users_group_primary = getField(tableName, "primary_group");
				final TableField users_group_user_id = getField(tableName, "user_id");
				final TableField users_group_group_id = getField(tableName, "group_id");
				final TableField groups_toolchain = getField(tableName, "toolchain");

				final TableField[] fields = new TableField[] { users_group_primary, users_group_group_id };
				final ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(users_group_user_id, userID), new ComparisionField(groups_toolchain, toolchain) };
				JsonArray res = select(tableName, fields, true, comparators);
				for (JsonValue val : res.Value) {
					boolean ok = false;
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("primary_group") && obj.containsNumber("group_id")) {
							int primary_group = obj.getNumber("primary_group").Value;
							int group_id = obj.getNumber("group_id").Value;
							if (!groups.containsKey(group_id)) {
								return null;
							}
							lst.add(new InternalUserGroupMemberShip(primary_group == 1, groups.get(group_id)));
							ok = true;
						}
					}
					if (!ok) {
						return null;
					}
				}
				return lst;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private List<InternalUserGroupMemberShip> getUserGroups(String login, String toolchain) {
		Integer userID = this.getUserIDByLogin(login, toolchain);
		if (userID != null) {
			return getUserMemberships(toolchain, userID);
		}
		return null;
	}

	protected LayeredPermissionDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("groups", KEY("ID"), NUMBER("parent_group_id"), TEXT("name"), BIGTEXT("permissions"), TEXT("toolchain"));
		this.makeTable("users_group", KEY("ID"), NUMBER("user_id"), NUMBER("group_id"), NUMBER("primary_group"), TEXT("toolchain"));
	}

	@Override
	public boolean clearUsers(String toolchain) {
		if (super.clearUsers(toolchain)) {
			try {
				this.execute_raw("DELETE FROM groups WHERE toolchain = ?", toolchain);
				this.execute_raw("DELETE FROM users_group WHERE toolchain = ?", toolchain);
				return true;
			} catch (DatabaseException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	private Integer getUserIDByLogin(String login, String toolchain) {
		final String tableName = "users";
		try {
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "login"), login), new ComparisionField(getField(tableName, "toolchain"), toolchain));
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					if (val.asObject().containsNumber("ID")) {
						return val.asObject().getNumber("ID").Value;
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Integer createGroup(String toolchain, int parentID, String child, String newPermissions, boolean create) {
		final String tableName = "groups";
		try {
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), child), new ComparisionField(getField(tableName, "toolchain"), toolchain));
			if (res.Value.size() == 1) { // Exists
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					if (val.asObject().containsNumber("ID")) {
						int id = val.asObject().getNumber("ID").Value;
						if (newPermissions != null) { // Replace permissions
							if (this.update(tableName, id, new ValuedField(getField(tableName, "permissions"), newPermissions), new ValuedField(getField(tableName, "parent_group_id"), parentID))) {
								return id;
							}
						} else { // No need to update permissions, just update parent
							if (this.update(tableName, id, new ValuedField(getField(tableName, "parent_group_id"), parentID))) {
								return id;
							}
						}
					}
				}
			} else if (res.Value.size() == 0 && create) { // It doesn't exist, create
				newPermissions = newPermissions == null ? "[]" : newPermissions;
				if (this.insert(tableName, new ValuedField(getField(tableName, "permissions"), newPermissions), new ValuedField(getField(tableName, "name"), child), new ValuedField(getField(tableName, "parent_group_id"), parentID), new ValuedField(getField(tableName, "toolchain"), toolchain))) {
					res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), child), new ComparisionField(getField(tableName, "toolchain"), toolchain));
					if (res.Value.size() == 1) { // Exists
						JsonValue val = res.Value.get(0);
						if (val.isObject()) {
							if (val.asObject().containsNumber("ID")) {
								int id = val.asObject().getNumber("ID").Value;
								return id;
							}
						}

					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

	private boolean assignGroup(String toolchain, int groupID, int userID, boolean primary) {
		final String tableName = "users_group";
		int newPrimary = primary ? 1 : 0;
		try {
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "primary_group") }, false, new ComparisionField(getField(tableName, "user_id"), userID), new ComparisionField(getField(tableName, "group_id"), groupID), new ComparisionField(getField(tableName, "toolchain"), toolchain));
			if (res.Value.size() == 1) { // Exists
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					if (val.asObject().containsNumber("ID") && val.asObject().containsNumber("primary_group")) {
						int id = val.asObject().getNumber("ID").Value;
						int primary_group = val.asObject().getNumber("primary_group").Value;
						if (primary_group != newPrimary) { // Update primary
							return this.update(tableName, id, new ValuedField(getField(tableName, "primary_group"), newPrimary));
						} else { // Current data is valid
							return true;
						}
					}
				}
			} else if (res.Value.size() == 0) { // It doesn't exist, create
				return this.insert(tableName, new ValuedField(getField(tableName, "user_id"), userID), new ValuedField(getField(tableName, "group_id"), groupID), new ValuedField(getField(tableName, "primary_group"), newPrimary), new ValuedField(getField(tableName, "toolchain"), toolchain));
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	private Integer getGroupIDByName(String toolchain, String group) {
		return getGroupIDByName(toolchain, group, null);
	}

	private Integer getGroupIDByName(String toolchain, String group, Integer parentID) {
		if (group.trim().isEmpty()) {
			return null;
		}

		String[] parts = group.trim().split("\\.");
		StringBuilder total = new StringBuilder();

		Integer lastID = parentID;
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			String currentName = i == 0 ? part : total.toString() + "." + part;
			lastID = createGroup(toolchain, lastID, currentName, null, parentID != null);
			if (lastID == null) {
				return null;
			}
			total.append(i == 0 ? part : "." + part);
		}
		return lastID;
	}

	@Override
	public Integer getRootPermissionGroup(String toolchain, String name) {
		return createGroup(toolchain, -1, name, null, true);
	}

	@Override
	public boolean addPermission(String toolchain, String group, String permission) {
		Map<Integer, InternalUserGroup> groups = loadGroups(toolchain);
		if (groups != null) {
			for (Entry<Integer, InternalUserGroup> entry : groups.entrySet()) {
				if (entry.getValue().Name.equals(group)) {
					int parentID = entry.getValue().Parent == null ? -1 : entry.getValue().Parent.ID;
					JsonArray ja = new JsonArray(new ArrayList<JsonValue>());
					boolean add = true;
					for (String perm : entry.getValue().getPermissions(false)) {
						ja.add(new JsonString(perm));
						if (permission.trim().equals(perm)) {
							add = false;
						}
					}
					if (add) {
						ja.add(new JsonString(permission.trim()));
					}
					if (this.createGroup(toolchain, parentID, group, ja.getJsonString(), true) == null) {
						return false;
					}

				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean createUser(String toolchain, String login, String origin, String fullName, List<String> permissionGroups, int rootPermissionGroupID) {
		if (super.createUser(toolchain, login, origin, fullName, permissionGroups, rootPermissionGroupID)) { // Create row in "users" table

			// Get the user ID and verify that it has all the groups
			Integer userID = getUserIDByLogin(login, toolchain);

			for (String group : permissionGroups) {
				Integer groupID = getGroupIDByName(toolchain, group, rootPermissionGroupID);
				if (groupID == null) {
					return false;
				}
				if (!assignGroup(toolchain, groupID, userID, true)) {
					return false;
				}
			}

			if (userID == null) {
				return false;
			}
			return true;
		}
		return false;
	}
}
