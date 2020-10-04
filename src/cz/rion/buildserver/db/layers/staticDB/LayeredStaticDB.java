package cz.rion.buildserver.db.layers.staticDB;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredStaticDB extends LayeredMetaDB {

	public static class RuntimeClientSession {
		public final String login;
		public final String session;
		public final String fullName;
		public final String group;

		private RuntimeClientSession(String login, String session, String fullName, String group) {
			this.login = login;
			this.session = session;
			this.fullName = fullName;
			this.group = group;
		}
	}

	public LayeredStaticDB(DatabaseInitData fileName) throws DatabaseException {
		super(fileName, "StaticDB");
	}
}
