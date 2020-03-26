package cz.rion.buildserver.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;

public class SQLiteDB {

	private final Connection conn;

	public static class Field {
		public final String name;
		private final String modifiers;
		public final boolean IsDate;
		public final boolean IsBigString;

		private Field(String name, String modifiers) {
			this(name, modifiers, false, false);
		}

		private Field(String name, String modifiers, boolean isDate) {
			this(name, modifiers, isDate, false);
		}

		public Field(String name, String modifiers, boolean isDate, boolean isBigString) {
			this.name = name;
			this.modifiers = modifiers;
			this.IsDate = isDate;
			this.IsBigString = isBigString;
		}

		public String getDecodableRepresentation() {
			StringBuilder sb = new StringBuilder();
			sb.append(IsDate ? "1" : "0");
			sb.append(IsBigString ? "1" : "0");
			sb.append(modifiers);
			sb.append("@");
			sb.append(name);
			return sb.toString();
		}

		public static Field fromDecodableRepresentation(String str) {
			boolean date = str.charAt(0) == '1';
			boolean bigString = str.charAt(1) == '1';
			String[] parts = str.substring(2).split("@", 2);
			String modifiers = parts[0];
			String name = parts[1];
			return new Field(name, modifiers, date, bigString);
		}

		@Override
		public String toString() {
			return name + " " + modifiers;
		}
	}

	protected Field KEY(String name) {
		return new Field(name, "INTEGER PRIMARY KEY AUTOINCREMENT");
	}

	protected Field TEXT(String name) {
		return new Field(name, "TEXT");
	}

	protected Field BIGTEXT(String name) {
		return new Field(name, "TEXT", false, true);
	}

	protected Field NUMBER(String name) {
		return new Field(name, "INTEGER");
	}

	protected Field DATE(String name) {
		return new Field(name, "INTEGER", true);
	}

	public SQLiteDB(String fileName) throws DatabaseException {
		Connection c = null;
		try {
			String url = "jdbc:sqlite:" + fileName;
			c = DriverManager.getConnection(url);
		} catch (SQLException e) {
			throw new DatabaseException("Failed to open database file: " + fileName, e);
		}
		conn = c;

	}

	protected PreparedStatement prepareStatement(String sql, Object... params) throws DatabaseException {
		sql = sql.replaceAll("'", "");
		try {
			PreparedStatement stmt = conn.prepareStatement(sql);
			int index = 0;
			for (Object param : params) {
				index++;
				if (param instanceof String) {
					String val = (String) param;
					stmt.setString(index, val);
				} else if (param instanceof Integer) {
					int val = (int) param;
					stmt.setInt(index, val);
				} else if (param instanceof Long) {
					long val = (long) param;
					stmt.setLong(index, val);
				} else if (param == null) {
					stmt.setNull(index, Types.NULL);
				} else {
					throw new DatabaseException("Unknown type: " + param.toString());
				}
			}
			return stmt;
		} catch (SQLException | ArrayIndexOutOfBoundsException e) {
			throw new DatabaseException("Failed to prepare statement: " + sql, e);
		}
	}

	private final Object syncer = new Object();

	protected boolean execute(String sql, Object... params) throws DatabaseException {
		synchronized (syncer) {
			PreparedStatement stmt = null;
			try {
				stmt = prepareStatement(sql, params);
				stmt.execute();
				return true;
			} catch (SQLException e) {
				throw new DatabaseException("Failed to execute: " + sql, e);
			}
		}
	}

	protected void executeExc(String sql, Object... params) throws DatabaseException {
		synchronized (syncer) {
			if (!execute(sql, params)) {
				// throw new DatabaseException("Failed to execute: " + sql);
			}
		}
	}

	public DatabaseResult select(String sql, Object... params) throws DatabaseException {
		synchronized (syncer) {
			try {
				PreparedStatement stmt = prepareStatement(sql, params);
				return new DatabaseResult(stmt.executeQuery());
			} catch (SQLException e) {
				throw new DatabaseException("Failed to execute: " + sql, e);
			}
		}
	}

	protected void makeTable(String name, Field... fields) throws DatabaseException {
		StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + name + " (\r\n");
		if (fields.length >= 1) {
			sb.append(fields[0].toString());
		}
		for (int i = 1; i < fields.length; i++) {
			sb.append(",\r\n" + fields[i].toString());
		}
		sb.append("\r\n);");
		execute(sb.toString());
	}

	public final class DatabaseResult {

		private final ResultSet rs;

		private boolean closed = false;

		public JsonArray getJSON() {
			JsonArray ar = new JsonArray(new ArrayList<JsonValue>());
			try {
				ResultSetMetaData rsmd = rs.getMetaData();
				String[] colNames = new String[rsmd.getColumnCount()];
				int[] colTypes = new int[rsmd.getColumnCount()];

				for (int i = 0; i < colNames.length; i++) {
					colNames[i] = rsmd.getColumnName(i + 1);
					colTypes[i] = rsmd.getColumnType(i + 1);
				}

				while (rs.next()) {
					JsonObject obj = new JsonObject();

					for (int i = 0; i < colNames.length; i++) {
						String colName = colNames[i];
						int colType = colTypes[i];
						if (colType == Types.INTEGER) {
							obj.add(colName, new JsonNumber(rs.getInt(i + 1), rs.getLong(i + 1) + ""));
						} else if (colType == Types.VARCHAR) {
							obj.add(colName, new JsonString(rs.getString(i + 1)));
						}
					}
					ar.add(obj);
				}
			} catch (SQLException e) {
			}
			close();
			return ar;
		}

		public void close() {
			if (!closed) {
				closed = true;
				try {
					rs.close();
				} catch (SQLException e) {
				}
			}
		}

		private DatabaseResult(ResultSet rs) throws SQLException {
			this.rs = rs;
		}

	}

}
