package cz.rion.buildserver.db;

import java.util.List;

import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.utils.Pair;

public class StaticDB extends LayeredPermissionDB {

	public StaticDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.afterInit();
		this.getRootToolchain();
	}
}
