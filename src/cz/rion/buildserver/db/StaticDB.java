package cz.rion.buildserver.db;

import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB;
import cz.rion.buildserver.exceptions.DatabaseException;

public class StaticDB extends LayeredPermissionDB {

	public StaticDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.afterInit();
		this.getRootToolchain();
	}
}
