package cz.rion.buildserver.db.layers.staticDB;

import java.util.Date;

import cz.rion.buildserver.exceptions.DatabaseException;

public class LayeredAdminLogDB extends LayeredThreadDB {

	public LayeredAdminLogDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("admin_log", KEY("ID"), TEXT("login"), TEXT("address"), TEXT("command"), DATE("creation_time"), BIGTEXT("full"));
	}

	@Override
	public boolean tableWriteable(String tableName) {
		if (tableName.toLowerCase().equals("admin_log")) {
			return false;
		}
		return super.tableWriteable(tableName);
	}

	public void adminLog(String address, String login, String command, String full) {
		final String tableName = "admin_log";
		try {
			TableField f_login = this.getField(tableName, "login");
			TableField f_address = this.getField(tableName, "address");
			TableField f_command = this.getField(tableName, "command");
			TableField f_creation_time = this.getField(tableName, "creation_time");
			TableField f_full = this.getField(tableName, "full");
			this.insert(tableName, new ValuedField(f_login, login), new ValuedField(f_command, command), new ValuedField(f_creation_time, new Date().getTime()), new ValuedField(f_full, full), new ValuedField(f_address, address));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}
}
