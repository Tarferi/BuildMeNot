package cz.rion.buildserver.http;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.PermissionManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.permissions.PermissionBranch;

public abstract class HTTPPermissionClient extends AbstractHTTPClient {

	private static final class PermissionContext {
		private UsersPermission perms;
		private final PermissionManager permanager;

		private PermissionContext(String toolchain, RuntimeDB rdb, StaticDB sdb) {
			this.permanager = new PermissionManager(sdb, Settings.GetDefaultUsername());
			this.perms = permanager.getDefaultPermission(toolchain);
		}
	}

	private final Map<String, PermissionContext> contexts = new HashMap<>();
	private final StaticDB sdb;
	private final RuntimeDB rdb;
	private String toolchain = "";

	@Override
	protected void ToolChainKnown(Toolchain toolchain) {
		super.ToolChainKnown(toolchain);
		this.toolchain = toolchain.getName();
	}

	private PermissionContext getContext(String toolchain) {
		synchronized (contexts) {
			if (contexts.containsKey(toolchain)) {
				return contexts.get(toolchain);
			} else {
				PermissionContext context = new PermissionContext(toolchain, rdb, sdb);
				contexts.put(toolchain, context);
				return context;
			}
		}
	}

	protected HTTPPermissionClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		this.sdb = sdb;
		this.rdb = rdb;
	}

	protected boolean allow(PermissionBranch action) {
		return getContext(toolchain).perms.can(action);
	}

	protected UsersPermission getPermissions() {
		return getContext(toolchain).perms;
	}

	protected void loadPermissions(int session_id, String login, int user_id) {
		getContext(toolchain).perms = getContext(toolchain).permanager.getPermissionForLogin(toolchain, session_id, login, user_id);
	}

	public int getUserID() {
		return getContext(toolchain).perms.UserID;
	}

	public String getLogin() {
		return getContext(toolchain).perms.Login;
	}
}
