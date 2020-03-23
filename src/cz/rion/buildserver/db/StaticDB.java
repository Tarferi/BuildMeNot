package cz.rion.buildserver.db;

import cz.rion.buildserver.db.layers.LayeredUserDB;
import cz.rion.buildserver.exceptions.DatabaseException;

public class StaticDB extends LayeredUserDB {

	public StaticDB(String dbName) throws DatabaseException {
		super(dbName);
	}

}
