package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredSettingsDB extends LayeredToolchainMappingDB {

	private static final String SettingsFileName = "settings.ini";

	public LayeredSettingsDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.registerVirtualFile(new VirtualFile() {

			@Override
			public String read() throws DatabaseException {
				try {
					return MyFS.readFile(SettingsFileName);
				} catch (FileReadException e) {
					e.printStackTrace();
					throw new DatabaseException("Failed to read settings file", e);
				}
			}

			@Override
			public void write(String data) {
				try {
					MyFS.writeFile(SettingsFileName, data);
					Settings.reload();
				} catch (FileWriteException e) {
					e.printStackTrace();
				}
			}

			@Override
			public String getName() {
				return SettingsFileName;
			}

		});
	}

}
