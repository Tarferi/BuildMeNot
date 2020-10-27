package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public abstract class LayeredVirtualFilesDB extends LayeredFilesDB {

	private static final int FIRST_VIRTUAL_FILE = 0x1FFFFF;
	private int next_free_offset = FIRST_VIRTUAL_FILE;

	private class ToolchainFiles {
		private Map<String, VirtualFile> filesByName = new HashMap<>();
		private Map<String, Integer> IdsByName = new HashMap<>();
	}

	private Map<Integer, VirtualFile> filesById = new HashMap<>();

	private class ToolchainFilesManager {
		private final Map<String, ToolchainFiles> files = new HashMap<>();

		private ToolchainFiles get(String toolchainName) {
			ToolchainFiles f = files.get(toolchainName);
			if (f == null) {
				f = new ToolchainFiles();
				files.put(toolchainName, f);
			}
			return f;
		}
	}

	private final ToolchainFilesManager manager = new ToolchainFilesManager();
	private final Object syncer = new Object();

	public LayeredVirtualFilesDB(DatabaseInitData fileName) throws DatabaseException {
		super(fileName);
	}

	protected boolean unregisterVirtualFile(VirtualFile vf) {
		synchronized (syncer) {
			ToolchainFiles files = manager.get(vf.getToolchain());
			if (!files.filesByName.containsKey(vf.getName())) {
				return false;
			}
			files.filesByName.remove(vf.getName());
			Integer index = null;
			for (Entry<Integer, VirtualFile> entry : filesById.entrySet()) {
				if (entry.getValue() == vf) {
					index = entry.getKey();
				}
			}
			if (index != null) {
				filesById.remove(index);
			}
			files.IdsByName.remove(vf.getName());
			return true;
		}
	}

	protected boolean registerVirtualFile(VirtualFile vf) {
		synchronized (syncer) {
			ToolchainFiles files = manager.get(vf.getToolchain());
			if (files.filesByName.containsKey(vf.getName())) {
				return false;
			}
			files.filesByName.put(vf.getName(), vf);
			filesById.put(next_free_offset, vf);
			files.IdsByName.put(vf.getName(), next_free_offset);
			next_free_offset++;
			return true;
		}
	}

	public static interface VirtualFile {
		public String read() throws DatabaseException;

		public void write(String data) throws DatabaseException;

		public String getName();

		public String getToolchain();
	}

	@Override
	public List<DatabaseFile> getFiles(Toolchain toolchain) {
		synchronized (syncer) {
			List<DatabaseFile> lst = super.getFiles(toolchain);
			for (Entry<Integer, VirtualFile> files : filesById.entrySet()) {
				lst.add(new DatabaseFile(files.getKey(), files.getValue().getName(), files.getValue().getToolchain()));
			}
			return lst;
		}
	}

	@Override
	public FileInfo createFile(Toolchain toolchain, String name, String contents, boolean overwriteExisting) throws DatabaseException {
		synchronized (syncer) {
			if (manager.get(toolchain.getName()).filesByName.containsKey(name)) {
				throw new DatabaseException("Cannnot create " + name + ": reserved file name");
			}
		}
		return super.createFile(toolchain, name, contents, overwriteExisting);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		synchronized (syncer) {
			if (filesById.containsKey(file.ID)) {
				try {
					filesById.get(file.ID).write(newContents);
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			} else {
				super.storeFile(file, newFileName, newContents);
			}
		}
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString, Toolchain toolchain) {
		synchronized (syncer) {
			ToolchainFiles files = manager.get(toolchain.getName());
			if (files.filesByName.containsKey(name)) {
				try {
					return new FileInfo(files.IdsByName.get(name), name, files.filesByName.get(name).read(), toolchain.getName());
				} catch (DatabaseException e) {
					e.printStackTrace();
					return null;
				}
			} else {
				return super.loadFile(name, decodeBigString, toolchain);
			}
		}
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString, Toolchain toolchain) throws DatabaseException {
		synchronized (syncer) {
			if (filesById.containsKey(fileID)) {
				VirtualFile vf = filesById.get(fileID);
				return new FileInfo(fileID, vf.getName(), vf.read(), vf.getToolchain());
			} else {
				return super.getFile(fileID, decodeBigString, toolchain);
			}
		}
	}
}
