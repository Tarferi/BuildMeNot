package cz.rion.buildserver.ui.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public abstract class ListPathItem {
	public final boolean isDirectory;
	public final String name;
	public final ListPathItemDirectory parent;

	public ListPathItem(ListPathItemDirectory parent, final String name, boolean isDirectory) {
		this.isDirectory = isDirectory;
		this.name = name;
		this.parent = parent;
		if (parent != null) {
			parent.items.add(this);
			if (this.isDirectory) {
				parent.itemsMap.put(this.name, this);
			}
		}
	}

	protected String getPath() {
		return parent == null ? name : parent.getPath() + "/" + name;
	}

	@Override
	public String toString() {
		return name;
	}

	public static class ListPathItemDirectory extends ListPathItem {
		public final List<ListPathItem> items = new ArrayList<ListPathItem>() {
			private static final long serialVersionUID = 1L;

			@Override
			public void clear() {
				super.clear();
				itemsMap.clear();
			}
		};
		private final Map<String, ListPathItem> itemsMap = new HashMap<>();
		public final ListPathItemDirectory parent;

		public ListPathItemDirectory(final String name, ListPathItemDirectory parent) {
			super(parent, name, true);
			this.parent = parent;
			if (parent != null) {
				items.add(new ListPathItemDirectory("..", null));
			}
		}

		public ListPathItemDirectory find(String path) {
			ListPathItemDirectory root = this;
			String[] parts = path.replaceAll("\\\\", "/").split("/");
			for (int i = 0; i < parts.length - 1; i++) {
				if (root.itemsMap.containsKey(parts[i])) {
					ListPathItem item = root.itemsMap.get(parts[i]);
					root = (ListPathItemDirectory) item;
				} else {
					return null;
				}
			}
			return root;
		}
	}

	private static ListPathItemDirectory getParent(ListPathItemDirectory root, String path) {
		String[] parts = path.replaceAll("\\\\", "/").split("/");
		for (int i = 0; i < parts.length - 1; i++) {
			if (root.itemsMap.containsKey(parts[i])) {
				ListPathItem item = root.itemsMap.get(parts[i]);
				root = (ListPathItemDirectory) item;
			} else {
				root = new ListPathItemDirectory(parts[i], root);
			}
		}
		return root;
	}

	private static String getName(String path) {
		String[] parts = path.replaceAll("\\\\", "/").split("/");
		return parts.length == 1 ? parts[0] : parts[parts.length - 1];
	}

	public static class ListPathItemFile extends ListPathItem {
		public final FileInfo File;

		public ListPathItemFile(ListPathItemDirectory root, FileInfo file) {
			super(getParent(root, file.Name), getName(file.Name), false);
			this.File = file;
		}
	}

}