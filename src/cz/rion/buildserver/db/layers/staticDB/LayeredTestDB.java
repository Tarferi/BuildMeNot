package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredTestDB extends LayeredSettingsDB {

	public LayeredTestDB(String dbName) throws DatabaseException {
		super(dbName);
	}
}
