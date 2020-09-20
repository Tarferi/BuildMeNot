package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class LayeredVirtualFilesDB extends LayeredFilesDB {

	private static final int FIRST_VIRTUAL_FILE = 0x1FFFFF;
	private int next_free_offset = FIRST_VIRTUAL_FILE;

	private Map<Integer, VirtualFile> filesById = new HashMap<>();
	private Map<String, VirtualFile> filesByName = new HashMap<>();
	private Map<String, Integer> IdsByName = new HashMap<>();

	private final Object syncer = new Object();

	public LayeredVirtualFilesDB(String fileName) throws DatabaseException {
		super(fileName);
	}

	protected boolean registerVirtualFile(VirtualFile vf) {
		synchronized (syncer) {
			if (filesByName.containsKey(vf.getName())) {
				return false;
			}
			filesByName.put(vf.getName(), vf);
			filesById.put(next_free_offset, vf);
			IdsByName.put(vf.getName(), next_free_offset);
			next_free_offset++;
			return true;
		}
	}

	public static interface VirtualFile {
		public String read() throws DatabaseException;

		public void write(String data) throws DatabaseException;

		public String getName();
	}

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		for (Entry<Integer, VirtualFile> files : filesById.entrySet()) {
			lst.add(new DatabaseFile(files.getKey(), files.getValue().getName()));
		}
		return lst;
	}

	@Override
	public FileInfo createFile(String name, String contents) throws DatabaseException {
		if (filesByName.containsKey(name)) {
			throw new DatabaseException("Cannnot create " + name + ": reserved file name");
		}
		return super.createFile(name, contents);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
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

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString) {
		if (filesByName.containsKey(name)) {
			try {
				return new FileInfo(IdsByName.get(name), name, filesByName.get(name).read());
			} catch (DatabaseException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return super.loadFile(name, decodeBigString);
		}
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		if (filesById.containsKey(fileID)) {
			VirtualFile vf = filesById.get(fileID);
			return new FileInfo(fileID, vf.getName(), vf.read());
		} else {
			return super.getFile(fileID, decodeBigString);
		}
	}
}
