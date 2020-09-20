package cz.rion.buildserver.db;

import java.util.Map;

import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB;
import cz.rion.buildserver.exceptions.DatabaseException;

public class StaticDB extends LayeredPermissionDB {

	public StaticDB(String dbName) throws DatabaseException {
		super(dbName);
	}
}
