package cz.rion.buildserver.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;

public class VirtualFileManager {

	public VirtualFileManager() {

	}

	public static interface UserContext {
		public Toolchain getToolchain();

		public String getLogin();

		public String getAddress();

		public boolean wantCompressedData();
	}

	public List<VirtualFile> getFile(String name, UserContext context) {
		List<VirtualFile> result = new ArrayList<>();
		Toolchain toolchain = context.getToolchain();
		synchronized (allFiles) {
			if (toolchain.IsRoot) {
				for (Entry<String, List<VirtualFile>> entry : allFiles.entrySet()) {
					for (VirtualFile vf : entry.getValue()) {
						if (vf.Name.equals(name)) {
							result.add(vf);
						}
					}
				}
			} else {
				List<VirtualFile> shared = allFiles.get("shared");
				if (shared != null) {
					for (VirtualFile vf : shared) {
						if (vf.Name.equals(name)) {
							result.add(vf);
						}
					}
				}
				List<VirtualFile> toolchainFiles = allFiles.get(toolchain.getName());
				if (toolchainFiles != null) {
					for (VirtualFile vf : toolchainFiles) {
						if (vf.Name.equals(name)) {
							result.add(vf);
						}
					}
				}
			}
		}
		return result;
	}

	public VirtualFile getFile(int ID, UserContext context) {
		Toolchain toolchain = context.getToolchain();
		synchronized (allFiles) {
			if (toolchain.IsRoot) {
				for (Entry<String, List<VirtualFile>> entry : allFiles.entrySet()) {
					for (VirtualFile vf : entry.getValue()) {
						if (vf.ID == ID) {
							return vf;
						}
					}
				}
			} else {
				List<VirtualFile> shared = allFiles.get("shared");
				if (shared != null) {
					for (VirtualFile vf : shared) {
						if (vf.ID == ID) {
							return vf;
						}
					}
				}
				List<VirtualFile> toolchainFiles = allFiles.get(toolchain.getName());
				if (toolchainFiles != null) {
					for (VirtualFile vf : toolchainFiles) {
						if (vf.ID == ID) {
							return vf;
						}
					}
				}
			}
		}
		return null;
	}

	private final Map<String, List<VirtualFile>> allFiles = new HashMap<>();

	public void registerVirtualFile(VirtualFile vf) {
		synchronized (allFiles) {
			List<VirtualFile> lst = allFiles.get(vf.Toolchain.getName());
			if (lst == null) {
				lst = new ArrayList<>();
				allFiles.put(vf.Toolchain.getName(), lst);
			}
			if (!lst.contains(vf)) {
				lst.add(vf);
			}
		}
	}

	public void unregisterVirtualFile(VirtualFile vf) {
		if (allFiles.containsKey(vf.Toolchain.getName())) {
			List<VirtualFile> lst = allFiles.get(vf.Toolchain.getName());
			lst.remove(vf);
			if (lst.isEmpty()) {
				allFiles.remove(vf.Toolchain.getName());
			}
		}
	}

	public void getFiles(List<VirtualFile> files, UserContext context) {
		Toolchain toolchain = context.getToolchain();
		synchronized (allFiles) {
			if (toolchain.IsRoot) {
				for (Entry<String, List<VirtualFile>> entry : allFiles.entrySet()) {
					files.addAll(entry.getValue());
				}
			} else {
				List<VirtualFile> shared = allFiles.get("shared");
				List<VirtualFile> tcF = allFiles.get(toolchain.getName());
				if (shared != null) {
					files.addAll(shared);
				}
				if (tcF != null) {
					files.addAll(tcF);
				}
			}
		}
	}

	public static abstract class VirtualFile {
		private static Set<Integer> allocatedIDs = new HashSet<>();

		public final int ID;
		public final Toolchain Toolchain;
		public final String Name;

		private VirtualFile(int ID, String name, Toolchain toolchain) {
			this.Toolchain = toolchain;
			this.ID = ID;
			this.Name = name;
		}

		@Override
		public String toString() {
			return Toolchain.getName() + ": [" + ID + "] " + Name;
		}

		private static int getNextFreeID() {
			synchronized (allocatedIDs) {
				int sz = allocatedIDs.size();
				for (int i = 0; i < sz; i++) {
					if (!allocatedIDs.contains(i)) {
						allocatedIDs.add(i);
						return -i;
					}
				}
				allocatedIDs.add(sz + 1);
				return -(sz + 1);
			}
		}

		public VirtualFile(String name, Toolchain toolchain) {
			this(getNextFreeID(), name, toolchain);
		}

		public abstract String read(UserContext context) throws VirtualFileException;

		public abstract boolean write(UserContext context, String newName, String value) throws VirtualFileException;

		public static class VirtualFileException extends Exception {
			private static final long serialVersionUID = 1L;

			private VirtualFileException(DatabaseException e) {
				super(e);
			}
		}

		public ReadVirtualFile getRead(UserContext context) {
			try {
				String contents = read(context);
				if (contents != null) {
					return new ReadVirtualFile(this, contents);
				}
			} catch (VirtualFileException e) {
				e.printStackTrace();

			}
			return null;
		}
	}

	public static class ReadVirtualFile {
		public final VirtualFile File;
		public final String Contents;

		private ReadVirtualFile(VirtualFile vf, String c) {
			this.File = vf;
			this.Contents = c;
		}

	}

	public static class VirtualDatabaseFile extends VirtualFile {

		public final DatabaseFileManipulator Source;

		public VirtualDatabaseFile(int ID, String name, Toolchain toolchain, DatabaseFileManipulator source) {
			super(ID, name, toolchain);
			this.Source = source;
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			try {
				return Source.read(this.ID, context);
			} catch (DatabaseException e) {
				throw new VirtualFileException(e);
			}
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			try {
				return Source.write(this, newName, value, context);
			} catch (DatabaseException e) {
				throw new VirtualFileException(e);
			}
		}

		public static interface DatabaseFileManipulator {
			public String read(int ID, UserContext context) throws DatabaseException;

			public boolean write(VirtualDatabaseFile file, String newName, String data, UserContext context) throws DatabaseException;
		}
	}
}
