package cz.rion.buildserver.db.layers.staticDB;

import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class LayeredSettingsDB extends LayeredConsoleOutputDB {

	private static int DB_FILE_SETTINGS_BASE = 0x000FFFFF;
	private static final String SettingsFileName = "settings.ini";
	private static final Object syncer = new Object();

	public LayeredSettingsDB(String dbName) throws DatabaseException {
		super(dbName);
	}

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		lst.add(new DatabaseFile(DB_FILE_SETTINGS_BASE, SettingsFileName));
		return lst;
	}

	@Override
	public FileInfo createFile(String name, String contents) throws DatabaseException {
		if (name.equals(SettingsFileName)) {
			throw new DatabaseException("Cannnot create " + name + ": reserved file name");
		}
		return super.createFile(name, contents);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.ID == DB_FILE_SETTINGS_BASE) {
			synchronized (syncer) {
				try {
					MyFS.writeFile(SettingsFileName, newContents);
					Settings.reload();
				} catch (FileWriteException e) {
					e.printStackTrace();
				}
			}
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	private FileInfo getFile() throws DatabaseException {
		synchronized (syncer) {
			try {
				String fc = MyFS.readFile(SettingsFileName);
				return new FileInfo(DB_FILE_SETTINGS_BASE, SettingsFileName, fc);
			} catch (FileReadException e) {
				e.printStackTrace();
				throw new DatabaseException("Failed to read settings file", e);
			}
		}
	}

	@Override
	public FileInfo loadFile(String name) {
		if (name.equals(SettingsFileName)) {
			try {
				return getFile();
			} catch (DatabaseException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return super.loadFile(name);
		}
	}

	@Override
	public FileInfo getFile(int fileID) throws DatabaseException {
		if (fileID == DB_FILE_SETTINGS_BASE) {
			return getFile();
		} else {
			return super.getFile(fileID);
		}
	}

}
