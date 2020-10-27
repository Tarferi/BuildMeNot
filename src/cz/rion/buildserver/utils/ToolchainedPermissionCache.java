package cz.rion.buildserver.utils;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.permissions.PermissionBranch;

public class ToolchainedPermissionCache {

	private final Map<String, PermissionBranch> cache = new HashMap<>();
	private String permission;

	public ToolchainedPermissionCache(String permission) {
		this.permission = permission;
	}

	public PermissionBranch toBranch(Toolchain tc) {
		synchronized (cache) {
			PermissionBranch v = cache.get(tc.getName());
			if (v == null) {
				v = new PermissionBranch(tc, permission);
				cache.put(tc.getName(), v);
			}
			return v;
		}
	}
}
