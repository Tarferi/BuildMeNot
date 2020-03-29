package cz.rion.buildserver.permissions;

public class WebPermission {

	private static final String base = "WEB.TESTS";
	public static final PermissionBranch SeeFireFox = Permission.getBranch(base + ".FIREFOX");

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