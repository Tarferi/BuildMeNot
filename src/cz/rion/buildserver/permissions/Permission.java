package cz.rion.buildserver.permissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.json.PermissionNode;

public final class Permission {

	private boolean hasStar = false;
	private Map<Integer, Permission> children = new HashMap<>();

	private Permission() {
	}

	public Permission(String raw) {
		this.add(new PermissionBranch(raw));
	}

	public static PermissionBranch getBranch(String raw) {
		return new PermissionBranch(raw);
	}

	private static boolean covers(Permission root, int[] branch, int levelWithinBranch) {
		if (root.hasStar) { // * allows all
			return true;
		} else if (levelWithinBranch >= branch.length) { // Got beyond branch -> all levels passed if we got this far
			return true;
		} else { // Check level
			int level = branch[levelWithinBranch];
			if (root.children.containsKey(level)) {
				return covers(root.children.get(level), branch, levelWithinBranch + 1);
			}
		}
		return false;
	}

	private List<PermissionNode> getLeafs() {
		List<PermissionNode> lst = new ArrayList<>();
		List<PermissionNode> s = new ArrayList<>();
		PermissionNode root = new PermissionNode(this);
		s.add(root);

		while (!s.isEmpty()) {
			PermissionNode p = s.remove(0);
			if (p.getChildren().isEmpty()) { // No children -> leaf
				lst.add(p);
			} else {
				for (PermissionNode child : p.getChildren()) {
					s.add(child);
				}
			}
		}

		return lst;
	}

	private List<PermissionBranch> getBranches() {
		List<PermissionBranch> branches = new ArrayList<>();
		List<PermissionNode> leafs = getLeafs();
		for (PermissionNode leaf : leafs) {
			branches.add(leaf.toBranch());
		}
		return branches;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		List<PermissionBranch> branches = getBranches();
		int index = 0;
		for (PermissionBranch branch : branches) {
			if (index > 0) {
				sb.append("\r\n");
			}
			sb.append(branch.toString());
			index++;
		}
		return sb.toString();
	}

	public boolean covers(PermissionBranch requiredPermission) {
		return covers(this, requiredPermission.getBranch(), 0);
	}

	private void add(Permission root, int[] branch, int levelWithinBranch) {
		if (root.hasStar) {
			return;
		} else if (levelWithinBranch >= branch.length) { // Reached end of branch
			return;
		} else { // Add single level to root
			int level = branch[levelWithinBranch];
			if (level == PermissionBranch.getKwstar()) { // Remove all children, set star and stop recursion
				root.hasStar = true;
				root.children.clear();
			} else {
				if (root.children.containsKey(level)) { // Already exist -> move inside
					add(root.children.get(level), branch, levelWithinBranch + 1);
				} else { // Doesn't exist, create new
					Permission np = new Permission();
					root.children.put(level, np);
					add(np, branch, levelWithinBranch + 1);
				}
			}
		}
	}

	public void add(PermissionBranch branch) {
		add(this, branch.getBranch(), 0);
	}

	public boolean hasStar() {
		return hasStar;
	}

	public Map<Integer, Permission> getChildren() {
		return children;
	}

}
