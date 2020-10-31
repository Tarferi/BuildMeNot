package cz.rion.buildserver.http.stateless;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.test.TestManager;

public class StatelessClient extends StatelessAuthClient {

	public StatelessClient(RuntimeDB db, StaticDB sdb, TestManager tests, VirtualFileManager files) {
		super(new StatelessInitData(db, sdb, tests, files));
	}

}
