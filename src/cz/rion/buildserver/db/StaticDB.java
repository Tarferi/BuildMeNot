package cz.rion.buildserver.db;

import cz.rion.buildserver.exceptions.DatabaseException;

public class StaticDB extends SQLiteDB {

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

	public StaticDB(String fileName) throws DatabaseException {
		super(fileName);
	}

}
