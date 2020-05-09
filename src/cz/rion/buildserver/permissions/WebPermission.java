package cz.rion.buildserver.permissions;

public class WebPermission {

	private static final String base = "WEB.TESTS";
	public static final PermissionBranch BypassTimeout = Permission.getBranch(base + ".NO_TIMEOUT");
	public static final PermissionBranch SeeFireFox = Permission.getBranch(base + ".FIREFOX");
	public static final PermissionBranch SeeSecretTests = Permission.getBranch(base + ".SECRET");
	public static final PermissionBranch SeeAdminAdmin = Permission.getBranch("WEB.SEE_ADMIN");

	public static PermissionBranch ExecuteTest(String test_id) {
		return Permission.getBranch(base + ".EXECUTE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeTest(String test_id) {
		return Permission.getBranch(base + ".SEE." + test_id.replace("_", "."));
	}

	public static PermissionBranch SeeDetails(String test_id) {
		return Permission.getBranch(base + ".DETAILS." + test_id.replace("_", "."));
	}

}
