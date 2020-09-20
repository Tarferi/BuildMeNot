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
import cz.rion.buildserver.wrappers.MyFS;

public class LayeredPermissionDB extends LayeredTestDB {

	public static class PermissionManager {

		private final LayeredPermissionDB db;
		private final String defaultUsername;

		public PermissionManager(LayeredPermissionDB db, String defaultUsername) {
			this.db = db;
			this.defaultUsername = defaultUsername;
		}

		public UsersPermission getPermissionForLogin(int session_id, String login, int user_id) {
			return new UsersPermission(session_id, login, user_id, db);
		}

		public UsersPermission getDefaultPermission() {
			return new UsersPermission(0, defaultUsername, 0, db);
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

		private UsersPermission(int session_id, String login, int user_id, LayeredPermissionDB db) {
			this.db = db;
			this.Login = login;
			this.UserID = user_id;
			this.SessionID = session_id;
		}

		public final boolean allowDetails(String toolchain, String test_id) {
			return can(WebPermission.SeeDetails(toolchain, test_id));
		}

		public final boolean allowSee(String toolchain, String test_id) {
			return can(WebPermission.SeeTest(toolchain, test_id));
		}

		public boolean allowExecute(String toolchain, String test_id) {
			return can(WebPermission.ExecuteTest(toolchain, test_id));
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
				db.getPermissionsFor(Login, permissions, primaries);
			}
			if (fullName == null) {
				Object[] parts = db.getFullNameAndGroup(Login);
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

	protected LayeredPermissionDB(String dbName) throws DatabaseException {
		super(dbName);
		if (Settings.GetInitGroupsAndUsers()) {
			if (Settings.GetInitGroupsAndUsers()) {
				throw new DatabaseException("Absolutely fucking not");
			}
			this.dropTable("groups");
			this.dropTable("users_group");
		}
		this.makeTable("groups", KEY("ID"), NUMBER("parent_group_id"), TEXT("name"), BIGTEXT("permissions"));
		this.makeTable("users_group", KEY("ID"), NUMBER("user_id"), NUMBER("group_id"), NUMBER("primary_group"));
		if (Settings.GetInitGroupsAndUsers()) {
			if (Settings.GetInitGroupsAndUsers()) {
				throw new DatabaseException("Absolutely fucking not");
			}
			handleInit();
		}
	}

	private void handleInit() {
		handleInit("Admins.", "admins.json");
		handleInit("ISU2020.Teachers.", "isu_teachers.json");
		handleInit("ISU2020.Students.", "isu_students.json");
		linkGroups();
	}

	private void linkGroups() {
		final String tableName = "groups";
		try {
			String file = MyFS.readFile("groups.json");
			if (file != null) {
				JsonValue val = JsonValue.parse(file);
				if (val != null) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						for (int i = 0; i < 2; i++) { // Do it twice so linkage can be direct
							file = "";
							for (Entry<String, JsonValue> entry : obj.getEntries()) {
								String name = entry.getKey();
								JsonObject grp = entry.getValue().asObject();

								int parentID = 0;
								int id = 0;
								String permissions = "[]";

								if (grp.containsString("Parent")) {
									String parent = grp.getString("Parent").Value;
									JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), parent));
									// JsonArray res = this.select("SELECT * FROM groups WHERE name = '?'",
									// parent).getJSON();
									if (res != null) {
										if (!res.Value.isEmpty()) {
											parentID = res.Value.get(0).asObject().getNumber("ID").Value;
										}
									}
								}

								if (grp.containsArray("Permissions")) {
									permissions = grp.getArray("Permissions").getJsonString();
								}

								// Select or create new
								JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), name));
								if (res.Value.isEmpty()) {
									this.insert(tableName, new ValuedField(this.getField(tableName, "parent_group_id"), 0), new ValuedField(this.getField(tableName, "name"), name), new ValuedField(this.getField(tableName, "permissions"), "[]"));
									res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), name));
								}
								if (res.Value.isEmpty()) {
									throw new Exception("Database error?");
								}
								id = res.Value.get(0).asObject().getNumber("ID").Value;
								// Update
								this.update(tableName, id, new ValuedField(this.getField(tableName, "parent_group_id"), parentID), new ValuedField(this.getField(tableName, "permissions"), permissions));
							}
						}
					}
				}
			}
		} catch (

		Exception e) {
			e.printStackTrace();
		}
	}

	private void handleInit(String prefix, String fileName) {
		try {
			String file = MyFS.readFile(fileName);
			if (file != null) {
				JsonValue val = JsonValue.parse(file);
				if (val != null) {
					if (val.isObject()) {
						handleInit(prefix, val.asObject());
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void initIUSGroup(String name, List<String> members) throws Exception {
		try {
			final String tableName = "groups";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), name));
			if (res.asArray().Value.isEmpty()) {
				this.insert(tableName, new ValuedField(this.getField(tableName, "parent_group_id"), 0), new ValuedField(this.getField(tableName, "name"), name), new ValuedField(this.getField(tableName, "permissions"), "[]"));
				res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), name));
				if (res.asArray().Value.isEmpty()) {
					throw new Exception("Failed to create new group");
				}
			}
			int groupID = res.asArray().Value.get(0).asObject().getNumber("ID").Value;
			// Validate members
			if (members != null) {
				final String usersTableName = "users";
				if (!members.isEmpty()) {
					Map<String, Boolean> presenceInGroup = new HashMap<>();
					Map<String, Integer> usersByID = new HashMap<>();
					res = this.select(usersTableName, new TableField[] { getField(usersTableName, "ID") }, false);
					for (JsonValue val : res.Value) {
						int id = val.asObject().getNumber("ID").Value;
						String login = val.asObject().getString("login").Value;
						usersByID.put(login, id);
					}
					final String tableName2 = "user_group";

					res = this.select(usersTableName, new TableField[] { getField(usersTableName, "login") }, new ComparisionField[] { new ComparisionField(getField(tableName2, "group_id"), groupID) }, new TableJoin[] { new TableJoin(getField(tableName2, "user_id"), getField(usersTableName, "ID")) }, false);
					if (res != null) {
						if (res.isArray()) {
							for (JsonValue val : res.asArray().Value) {
								String login = val.asObject().getString("login").Value;
								presenceInGroup.put(login, true);
							}
						}
					}
					for (String member : members) {
						if (!presenceInGroup.containsKey(member) && usersByID.containsKey(member)) {
							int userID = usersByID.get(member);
							this.insert(tableName2, new ValuedField(this.getField(tableName2, "user_id"), userID), new ValuedField(this.getField(tableName2, "group_id"), groupID), new ValuedField(this.getField(tableName2, "primary_group"), 1));
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	private void handleInit(String prefix, JsonObject groups) throws Exception {
		for (Entry<String, JsonValue> group : groups.getEntries()) {
			String name = group.getKey();
			JsonValue val = group.getValue();
			List<String> members = new ArrayList<>();
			if (val.isArray()) {
				JsonArray ar = val.asArray();
				for (JsonValue v : ar.Value) {
					if (v.isString()) {
						String str = v.asString().Value;
						members.add(str);
					}
				}
				initIUSGroup(prefix + name, members);
			}
		}
	}

	private void parsePermResult(JsonValue res, Permission perms, List<Integer> inspectGroups, List<String> primary) {
		if (res != null) {
			if (!res.asArray().Value.isEmpty()) {
				for (JsonValue val : res.asArray().Value) {
					int parent = val.asObject().getNumber("parent_group_id").Value;
					if (parent != 0) {
						inspectGroups.add(parent);
					}
					if (val.asObject().containsNumber("primary_group") && val.asObject().containsString("name")) {
						int sprimary = val.asObject().getNumber("primary_group").Value;
						if (sprimary == 1) {
							primary.add(val.asObject().getString("name").Value);
						}
					}
					JsonValue permVal = JsonValue.parse(val.asObject().getString("permissions").Value);
					if (permVal != null) {
						for (JsonValue permValVal : permVal.asArray().Value) {
							perms.add(Permission.getBranch(permValVal.asString().Value));
						}
					}
				}
			}
		}
	}

	private int getDefaultGroupID() throws DatabaseException {
		final String defaultGroupName = Settings.GetDefaultGroup();
		final String tableName = "groups";

		JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), defaultGroupName));

		if (res.Value.isEmpty()) {
			this.insert(tableName, new ValuedField(this.getField(tableName, "name"), defaultGroupName), new ValuedField(this.getField(tableName, "permissions"), "[]"));
			res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), defaultGroupName));
		}
		if (!res.Value.isEmpty()) {
			return res.Value.get(0).asObject().getNumber("ID").Value;
		}
		return -1;
	}

	private int getUserIDFromLogin(String login) throws DatabaseException {
		final String tableName = "users";
		JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "login"), login));
		if (!res.Value.isEmpty()) {
			return res.Value.get(0).asObject().getNumber("ID").Value;
		}
		return -1;
	}

	private void addUser(String name, String group, String login) {
		try {
			final String tableName = "users";
			this.insert(tableName, new ValuedField(this.getField(tableName, "name"), name), new ValuedField(this.getField(tableName, "usergroup"), group), new ValuedField(this.getField(tableName, "login"), login), new ValuedField(this.getField(tableName, "permissions"), "[]"));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	private void getPermissionsFor(String login, Permission perms, List<String> primary) {
		try {
			List<Integer> inspectGroups = new ArrayList<>();

			final String usersTableName = "users";
			final String groupsTableName = "groups";
			final String userGroupsTableName = "users_group";

			final TableField[] fields = new TableField[] {
					getField(usersTableName, "permissions").getRenamedInstance("up"),

					getField(userGroupsTableName, "primary_group"),

					getField(groupsTableName, "name"),
					getField(groupsTableName, "parent_group_id"),
					getField(groupsTableName, "permissions")
			};
			final ComparisionField[] comparators = new ComparisionField[] {
					new ComparisionField(getField(usersTableName, "login"), login)
			};

			final TableJoin[] joins = new TableJoin[] {
					new TableJoin(getField(usersTableName, "ID"), getField(userGroupsTableName, "user_id")),
					new TableJoin(getField(groupsTableName, "ID"), getField(userGroupsTableName, "group_id"))
			};

			JsonArray res = this.select(usersTableName, fields, comparators, joins, true);

			// JsonArray res = this.select("SELECT users.permissions as up,
			// users_group.primary_group, groups.name, groups.parent_group_id,
			// groups.permissions FROM users, groups, users_group WHERE groups.ID =
			// users_group.group_id AND users_group.user_id = users.ID AND users.login =
			// '?'", login).getJSON();
			if (res.Value.isEmpty()) {
				int userID = getUserIDFromLogin(login);
				if (userID == -1) {
					addUser(login, "FIT", login);
					userID = getUserIDFromLogin(login);
				}

				int defaultGroupID = getDefaultGroupID();
				if (userID >= 0 && defaultGroupID >= 0) {
					final String tableName = "users_group";
					this.insert(tableName, new ValuedField(this.getField(tableName, "user_id"), userID), new ValuedField(this.getField(tableName, "group_id"), defaultGroupID), new ValuedField(this.getField(tableName, "primary_group"), 1));
					// this.execute("INSERT INTO users_group (user_id, group_id, primary_group)
					// VALUES (?, ?, ?)", userID, defaultGroupID, 1);
				}
			} else {
				try {
					for (JsonValue perm : JsonValue.parse(res.Value.get(0).asObject().getString("up").Value).asArray().Value) {
						perms.add(Permission.getBranch(perm.asString().Value));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			parsePermResult(res, perms, inspectGroups, primary);
			while (!inspectGroups.isEmpty()) {
				int id = inspectGroups.remove(0);

				final String tableName = "groups";
				res = this.select(tableName, new TableField[] { getField(tableName, "parent_group_id"), getField(tableName, "permissions") }, true, new ComparisionField(getField(tableName, "ID"), id));
				parsePermResult(res, perms, inspectGroups, primary);
			}

		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public Object[] getFullNameAndGroup(String login) {
		try {
			final String tableName = "users";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "name"), getField(tableName, "usergroup"), getField(tableName, "ID") }, true, new ComparisionField(getField(tableName, "login"), login));
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

	public UsersPermission loadPermissions(int session_id, String login, int user_id) {
		return new UsersPermission(session_id, login, user_id, this);
	}

}
