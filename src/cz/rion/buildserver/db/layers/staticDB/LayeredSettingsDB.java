package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredSettingsDB extends LayeredToolchainMappingDB {

	private static final String SettingsFileName = "settings.ini";
	private final DatabaseInitData dbData;

	@Override
	public void afterInit() {
		super.afterInit();
		dbData.Files.registerVirtualFile(new VirtualFile(SettingsFileName, this.getRootToolchain()) {

			@Override
			public String read(UserContext context) {
				try {
					return MyFS.readFile(SettingsFileName);
				} catch (FileReadException e) {
					e.printStackTrace();
					return null;
				}
			}

			@Override
			public boolean write(UserContext context, String newName, String data) {
				try {
					MyFS.writeFile(SettingsFileName, data);
					Settings.reload();
					return true;
				} catch (FileWriteException e) {
					e.printStackTrace();
					return false;
				}
			}

		});
	}

	public LayeredSettingsDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.dbData = dbData;
	}

}
