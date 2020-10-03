package cz.rion.buildserver.permissions;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public class WebPermission {

	private static final String base = "WEB.TESTS";
	public static final PermissionBranch BypassTimeout = Permission.getBranch(base + ".NO_TIMEOUT");
	public static final PermissionBranch SeeSecretTests = Permission.getBranch(base + ".SECRET");
	public static final PermissionBranch SeeAdminAdmin = Permission.getBranch("WEB.SEE_ADMIN");

	public static PermissionBranch ExecuteTest(Toolchain toolchain, String test_id) {
		return Permission.getBranch(base + "." + toolchain.getName() + ".EXECUTE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeTest(Toolchain toolchain, String test_id) {
		return Permission.getBranch(base + "." + toolchain.getName() + ".SEE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeDetails(Toolchain toolchain, String test_id) {
		return Permission.getBranch(base + "." + toolchain.getName() + ".DETAILS." + test_id.replace("_", "."));
	}

}
