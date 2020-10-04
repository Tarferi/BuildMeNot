package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredTestDB extends LayeredCodeModifiersDB {

	public LayeredTestDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
	}
}
