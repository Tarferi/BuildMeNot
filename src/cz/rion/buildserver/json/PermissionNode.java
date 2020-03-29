package cz.rion.buildserver.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import cz.rion.buildserver.permissions.Permission;
import cz.rion.buildserver.permissions.PermissionBranch;

public class PermissionNode {
	private final int perm;
	private PermissionNode parent;
	private final List<PermissionNode> children = new ArrayList<>();
	private boolean isRoot = false;
	private boolean hasStar = false;

	public PermissionBranch toBranch() {
		List<Integer> path = new ArrayList<>();
		PermissionNode n = this;
		if (this.hasStar) {
			path.add(PermissionBranch.getKwstar());
		}
		while (!n.isRoot) {
			path.add(0, n.perm);
			n = n.parent;
		}
		int[] perms = new int[path.size()];
		int index = 0;
		for (Integer p : path) {
			perms[index] = p;
			index++;
		}
		return new PermissionBranch(perms);
	}

	private void propagate() {
		for (PermissionNode child : children) {
			child.parent = this;
			child.propagate();
		}
	}

	public PermissionNode(Permission perm) {
		this(0, perm);
		this.isRoot = true;
	}

	private PermissionNode(int value, Permission perm) {
		this.perm = value;
		if (perm.hasStar()) {
			hasStar = true;
		}
		for (Entry<Integer, Permission> entry : perm.getChildren().entrySet()) {
			this.children.add(new PermissionNode(entry.getKey(), entry.getValue()));
		}
		propagate();
	}

	public List<PermissionNode> getChildren() {
		return children;
	}
}
