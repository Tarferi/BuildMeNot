package cz.rion.buildserver.permissions;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public class PermissionBranch {

	private static int kwSz = 10;

	private static final int kwStar = 6;
	private static final Map<String, Integer> kws = new HashMap<>();
	private static final Map<Integer, String> kwIs = new HashMap<>();

	private final int[] branch;

	public final Toolchain toolchain;

	public final int[] getBranch() {
		return branch;
	}

	public PermissionBranch(Toolchain toolchain, String permission) {
		this(toolchain, parse(permission));
	}

	public PermissionBranch(Toolchain toolchain, int[] perms) {
		this.branch = perms;
		this.toolchain = toolchain;
	}

	private static String getKw(int value) {
		return kwIs.get(value);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < branch.length; i++) {
			int br = branch[i];
			if (i > 0) {
				sb.append(".");
			}
			if (br == getKwstar()) {
				sb.append("*");
			} else {
				sb.append(getKw(br));
			}
		}
		return sb.toString();
	}

	static int[] parse(String perm) {
		if (perm.isEmpty()) {
			return new int[0];
		}
		String[] keys = perm.split("\\.");
		int[] ret = new int[keys.length];
		for (int i = 0; i < keys.length; i++) {
			ret[i] = kwToInt(keys[i]);
		}
		return ret;
	}

	private static int kwToInt(String kw) {
		kw = kw.toUpperCase();
		if (kw.equals("*")) {
			return getKwstar();
		}
		synchronized (kws) {
			if (kws.containsKey(kw)) {
				return kws.get(kw);
			} else {
				kws.put(kw, kwSz);
				kwIs.put(kwSz, kw);
				kwSz++;
				return kwSz - 1;
			}
		}
	}

	public static int getKwstar() {
		return kwStar;
	}

}