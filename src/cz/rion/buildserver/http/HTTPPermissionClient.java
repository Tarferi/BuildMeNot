package cz.rion.buildserver.http;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.PermissionManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.permissions.PermissionBranch;

public class HTTPPermissionClient extends AbstractHTTPClient {

	private UsersPermission perms;
	private final PermissionManager permanager;

	protected HTTPPermissionClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		this.permanager = new PermissionManager(sdb, Settings.GetDefaultUsername());
		this.perms = permanager.getDefaultPermission();
	}

	protected boolean allow(PermissionBranch action) {
		return perms.can(action);
	}

	protected UsersPermission getPermissions() {
		return perms;
	}

	protected void loadPermissions(int session_id, String login, int user_id) {
		this.perms = this.permanager.getPermissionForLogin(session_id, login, user_id);
	}

	public int getUserID() {
		return perms.UserID;
	}

	public String getLogin() {
		return perms.Login;
	}
}
