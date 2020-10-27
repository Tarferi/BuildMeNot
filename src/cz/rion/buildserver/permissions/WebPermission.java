package cz.rion.buildserver.permissions;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.utils.ToolchainedPermissionCache;

public class WebPermission {

	private static final String base = "WEB.TESTS";
	public static final ToolchainedPermissionCache BypassTimeout = new ToolchainedPermissionCache(base + ".NO_TIMEOUT");
	public static final ToolchainedPermissionCache SeeSecretTests = new ToolchainedPermissionCache(base + ".SECRET");
	public static final ToolchainedPermissionCache SeeAdminAdmin = new ToolchainedPermissionCache("WEB.SEE_ADMIN");

	public static PermissionBranch ExecuteTest(Toolchain toolchain, String test_id) {
		return Permission.getBranch(toolchain, base + "." + toolchain.getName() + ".EXECUTE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeTest(Toolchain toolchain, String test_id) {
		return Permission.getBranch(toolchain, base + "." + toolchain.getName() + ".SEE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeDetails(Toolchain toolchain, String test_id) {
		return Permission.getBranch(toolchain, base + "." + toolchain.getName() + ".DETAILS." + test_id.replace("_", "."));
	}

}
