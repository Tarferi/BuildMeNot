package cz.rion.buildserver.db.layers;

import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredTestDB extends LayeredFilesDB {

	public LayeredTestDB(String dbName) throws DatabaseException {
		super(dbName);
	}

}
