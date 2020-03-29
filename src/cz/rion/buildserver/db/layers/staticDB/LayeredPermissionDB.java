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

		public UsersPermission getPermissionForLogin(int session_id, String login) {
			return new UsersPermission(session_id, login, db);
		}

		public UsersPermission getDefaultPermission() {
			return new UsersPermission(0, defaultUsername, db);
		}
	}

	public static final class UsersPermission {

		private Permission permissions;
		private List<String> primaries = null;
		private final LayeredPermissionDB db;
		public final String Login;
		private String fullName = null;
		private String userGroup = null;
		private int UserID;
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

		private UsersPermission(int session_id, String login, LayeredPermissionDB db) {
			this.db = db;
			this.Login = login;
			this.SessionID = session_id;
		}

		public final boolean allowDetails(String test_id) {
			return can(WebPermission.SeeDetails(test_id));
		}

		public final boolean allowSee(String test_id) {
			return can(WebPermission.SeeTest(test_id));
		}

		public final boolean allowFireFox() {
			return can(WebPermission.SeeFireFox);
		}

		public int getUserID() {
			handleInit();
			return UserID;
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
				UserID = (int) parts[2];
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

	}

	private final PermissionManager manager = new PermissionManager(this, Settings.GetDefaultUsername());

	public PermissionManager getPermissionManager() {
		return manager;
	}

	protected LayeredPermissionDB(String dbName) throws DatabaseException {
		super(dbName);
		// this.execute("DROP TABLE IF EXISTS groups");
		// this.execute("DROP TABLE IF EXISTS users_group");
		this.makeTable("groups", KEY("ID"), NUMBER("parent_group_id"), TEXT("name"), BIGTEXT("permissions"));
		this.makeTable("users_group", KEY("ID"), NUMBER("user_id"), NUMBER("group_id"), NUMBER("primary_group"));
		// handleInit();
	}

	private void handleInit() {
		handleInit("Admins.", "admins.json");
		handleInit("ISU2020.Teachers.", "isu_teachers.json");
		handleInit("ISU2020.Students.", "isu_students.json");
		linkGroups();
	}

	private void linkGroups() {
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
									JsonArray res = this.select("SELECT * FROM groups WHERE name = '?'", parent).getJSON();
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
								JsonArray res = this.select("SELECT * FROM groups WHERE name = '?'", name).getJSON();
								if (res == null) {
									throw new Exception("Database error?");
								}
								if (res.Value.isEmpty()) {
									this.execute("INSERT INTO groups (parent_group_id, name, permissions) VALUES (?, '?', '?')", 0, name, "[]");
									res = this.select("SELECT * FROM groups WHERE name = '?'", name).getJSON();
								}
								if (res == null) {
									throw new Exception("Database error?");
								}
								if (res.Value.isEmpty()) {
									throw new Exception("Database error?");
								}
								id = res.Value.get(0).asObject().getNumber("ID").Value;
								// Update
								this.execute("UPDATE groups SET permissions = '?', parent_group_id = ? WHERE ID = ?", permissions, parentID, id);
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
			JsonArray res = this.select("SELECT * FROM groups WHERE name = '?'", name).getJSON();
			if (res == null) { // New group
				throw new Exception("Failed to create new group");
			}
			if (res.asArray().Value.isEmpty()) {
				this.execute("INSERT INTO groups (parent_group_id, name, permissions) VALUES (?, '?', '?')", 0, name, "[]");
				res = this.select("SELECT * FROM groups WHERE name = '?'", name).getJSON();
				if (res == null) {
					throw new Exception("Failed to create new group");
				}
				if (res.asArray().Value.isEmpty()) {
					throw new Exception("Failed to create new group");
				}
			}
			int groupID = res.asArray().Value.get(0).asObject().getNumber("ID").Value;
			// Validate members
			if (members != null) {
				if (!members.isEmpty()) {
					Map<String, Boolean> presenceInGroup = new HashMap<>();
					Map<String, Integer> usersByID = new HashMap<>();
					res = select("SELECT * FROM users").getJSON();
					if (res != null) {
						if (res.isArray()) {
							for (JsonValue val : res.asArray().Value) {
								int id = val.asObject().getNumber("ID").Value;
								String login = val.asObject().getString("login").Value;
								usersByID.put(login, id);
							}
						}
					}

					res = select("SELECT users.login FROM users, users_group WHERE users_group.group_id = ? and users_group.user_id = users.ID", groupID).getJSON();
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
							this.execute("INSERT INTO users_group (user_id, group_id, primary_group) VALUES (?, ?, ?)", userID, groupID, 1);
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
		JsonArray res = this.select("SELECT ID FROM groups WHERE name = '?'", defaultGroupName).getJSON();
		if (res != null) {
			if (res.Value.isEmpty()) {
				execute("INSERT INTO groups (name, permissions) VALUES ('?', '?')", defaultGroupName, "[]");
				res = this.select("SELECT ID FROM groups WHERE name = '?'", defaultGroupName).getJSON();
			}
			if (res != null) {
				if (!res.Value.isEmpty()) {
					return res.Value.get(0).asObject().getNumber("ID").Value;
				}
			}
		}
		return -1;
	}

	private int getUserIDFromLogin(String login) throws DatabaseException {
		JsonArray res = this.select("SELECT ID FROM users WHERE login = '?'", login).getJSON();
		if (res != null) {
			if (!res.Value.isEmpty()) {
				return res.Value.get(0).asObject().getNumber("ID").Value;
			}
		}
		return -1;
	}

	private void addUser(String name, String group, String login) {
		try {
			this.execute("INSERT INTO users (name, usergroup, login, permissions) VALUES ('?', '?', '?', '?')", name, group, login, "[]");
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	private void getPermissionsFor(String login, Permission perms, List<String> primary) {
		try {
			List<Integer> inspectGroups = new ArrayList<>();
			JsonArray res = this.select("SELECT users.permissions as up, users_group.primary_group, groups.name, groups.parent_group_id, groups.permissions FROM users, groups, users_group WHERE groups.ID = users_group.group_id AND users_group.user_id = users.ID AND users.login = '?'", login).getJSON();
			if (res != null) {
				if (res.Value.isEmpty()) {
					int userID = getUserIDFromLogin(login);
					if (userID == -1) {
						addUser(login, "FIT", login);
						userID = getUserIDFromLogin(login);
					}

					int defaultGroupID = getDefaultGroupID();
					if (userID >= 0 && defaultGroupID >= 0) {
						this.execute("INSERT INTO users_group (user_id, group_id, primary_group) VALUES (?, ?, ?)", userID, defaultGroupID, 1);
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
			}
			parsePermResult(res, perms, inspectGroups, primary);
			while (!inspectGroups.isEmpty()) {
				int id = inspectGroups.remove(0);
				res = this.select("SELECT groups.parent_group_id, groups.permissions FROM groups WHERE groups.ID = ?", id).getJSON();
				parsePermResult(res, perms, inspectGroups, primary);
			}

		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public Object[] getFullNameAndGroup(String login) {
		try {
			JsonArray res = this.select("SELECT * FROM users WHERE login = '?'", login).getJSON();
			if (res != null) {
				if (!res.Value.isEmpty()) {
					String name = res.Value.get(0).asObject().getString("name").Value;
					String grp = res.Value.get(0).asObject().getString("usergroup").Value;
					int id = res.Value.get(0).asObject().getNumber("ID").Value;
					return new Object[] { name, grp, id };
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new Object[] { login, Settings.GetDefaultGroup(), 0 };
	}

	public UsersPermission loadPermissions(int session_id, String login) {
		return new UsersPermission(session_id, login, this);
	}

}