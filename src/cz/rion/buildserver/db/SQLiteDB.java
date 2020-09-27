package cz.rion.buildserver.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.rion.buildserver.compression.Compressor;
import cz.rion.buildserver.compression.Decompressor;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;

public abstract class SQLiteDB {

	private final Connection conn;

	public static enum FieldType {
		INT(0, false), STRING(1, true), BIGSTRING(2, true), DATE(3, false);

		public final int code;
		private boolean isStoredAsString;

		private FieldType(int code, boolean isStoredAsString) {
			this.code = code;
			this.isStoredAsString = isStoredAsString;
		}
	}

	public static class Field {
		public final String name;
		private final String modifiers;
		public final FieldType type;
		public final boolean isStoredAsString;

		public boolean IsBigString() {
			return type == FieldType.BIGSTRING;
		}

		public boolean IsDate() {
			return type == FieldType.DATE;
		}

		public Field(String name, String modifiers, FieldType type) {
			this.name = name;
			this.modifiers = modifiers;
			this.type = type;
			this.isStoredAsString = type.isStoredAsString;
		}

		public String getDecodableRepresentation() {
			StringBuilder sb = new StringBuilder();
			sb.append(type.code);
			sb.append(modifiers);
			sb.append("@");
			sb.append(name);
			return sb.toString();
		}

		public static Field fromDecodableRepresentation(String str) {
			String typeStr = str.substring(0, 1);
			Integer typeInt = Integer.parseInt(typeStr);
			FieldType type = FieldType.values()[typeInt];
			String[] parts = str.substring(1).split("@", 2);
			String modifiers = parts[0];
			String name = parts[1];
			return new Field(name, modifiers, type);
		}

		@Override
		public String toString() {
			return name + " " + modifiers;
		}

	}

	public static class TableField {

		public final String table;
		public final Field field;

		public TableField(Field field, String tableName) {
			this.field = field;
			this.table = tableName;
		}

		public RenamedField getRenamedInstance(String name) {
			return new RenamedField(field, table, name);
		}
	}

	public static class RenamedField extends TableField {

		public final String newName;

		public RenamedField(Field field, String tableName, String newName) {
			super(field, tableName);
			this.newName = newName;
		}

	}

	public static class ValuedField {
		public final TableField field;
		public final Object value;

		public ValuedField(TableField field, Object value) {
			this.field = field;
			this.value = value;
		}
	}

	public enum FieldComparator {
		Equals("="), NotEquals("!="), Greater(">"), Lesser("<");

		private final String code;

		private FieldComparator(String sql) {
			this.code = sql;
		}
	}

	public static final class ComparisionField extends ValuedField {
		public final FieldComparator comparator;

		public ComparisionField(TableField field, Object value, FieldComparator comparator) {
			super(field, value);
			this.comparator = comparator;
		}

		public ComparisionField(TableField field, Object value) {
			this(field, value, FieldComparator.Equals);
		}
	}

	public static final class TableJoin {
		public final TableField field1;
		public final TableField field2;

		public TableJoin(TableField field1, TableField field2) {
			this.field1 = field1;
			this.field2 = field2;
		}
	}

	protected Field KEY(String name) {
		return new Field(name, "INTEGER PRIMARY KEY AUTOINCREMENT", FieldType.INT);
	}

	protected Field TEXT(String name) {
		return new Field(name, "TEXT", FieldType.STRING);
	}

	protected Field BIGTEXT(String name) {
		return new Field(name, "TEXT", FieldType.BIGSTRING);
	}

	protected Field NUMBER(String name) {
		return new Field(name, "INTEGER", FieldType.INT);
	}

	protected Field DATE(String name) {
		return new Field(name, "INTEGER", FieldType.DATE);
	}

	protected SQLiteDB(String fileName) throws DatabaseException {
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

	@Deprecated
	public boolean execute_raw(String sql, Object... params) throws DatabaseException {
		synchronized (syncer) {
			PreparedStatement stmt = null;
			try {
				stmt = prepareStatement(sql, params);
				stmt.execute();
				return true;
			} catch (SQLException e) {
				throw new DatabaseException("Failed to execute raw exec: " + sql, e);
			}
		}
	}

	public boolean dropTable(String tableName) throws DatabaseException {
		return this.execute_raw("DROP TABLE IF EXISTS " + tableName);
	}

	public boolean update(String tableName, int ID, ValuedField... fields) throws DatabaseException {
		return update(tableName, "ID", ID, fields);
	}

	public boolean update(String tableName, String IDColumnName, int ID, ValuedField... fields) throws DatabaseException {
		return update(tableName, new String[] { IDColumnName }, new Object[] { ID }, fields);

	}

	public boolean update(String tableName, String IDColumnName[], Object[] ID, ValuedField... fields) throws DatabaseException {
		StringBuilder sb = new StringBuilder();
		Object[] params = new Object[fields.length + ID.length];
		sb.append("UPDATE " + tableName + " SET ");
		for (int i = 0; i < fields.length; i++) {
			boolean compress = fields[i].field.field.IsBigString();
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(fields[i].field.field.name + " = ");
			sb.append(fields[i].field.field.isStoredAsString ? "'?'" : "?");
			try {
				params[i] = compress ? Compressor.compress(fields[i].value.toString()) : fields[i].value;
			} catch (CompressionException e) {
				e.printStackTrace();
				throw new DatabaseException("Failed to update " + tableName + ": Compression failed", e);
			}
		}
		sb.append(" WHERE ");// + IDColumnName + " = ?");
		for (int i = 0; i < IDColumnName.length; i++) {
			if (i > 0) {
				sb.append(" AND ");
			}
			sb.append(IDColumnName[i] + " = " + (ID[i] instanceof String ? "'?'" : "?"));
			params[fields.length + i] = ID[i];
		}
		return execute_raw(sb.toString(), params);
	}

	public boolean insert(String tableName, ValuedField... fields) throws DatabaseException {
		StringBuilder sb = new StringBuilder();
		Object[] params = new Object[fields.length];

		sb.append("INSERT INTO " + tableName + " (");
		for (int i = 0; i < fields.length; i++) {
			boolean compress = fields[i].field.field.IsBigString();

			if (i > 0) {
				sb.append(", ");
			}
			sb.append(fields[i].field.field.name);
			try {
				params[i] = compress ? Compressor.compress(fields[i].value == null ? "" : fields[i].value.toString()) : fields[i].value;
			} catch (CompressionException e) {
				e.printStackTrace();
				throw new DatabaseException("Failed to insert into " + tableName + ": Compression failed", e);
			}
		}
		sb.append(") VALUES (");
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(fields[i].field.field.isStoredAsString ? "'?'" : "?");
		}
		sb.append(")");
		return execute_raw(sb.toString(), params);
	}

	public JsonArray select(String tableName, TableField[] fields, boolean decodeBigString, ComparisionField... conjunctions) throws DatabaseException {
		return select(tableName, fields, conjunctions, new TableJoin[0], decodeBigString);
	}

	public JsonArray select(String tableName, TableField[] fields, ComparisionField[] conjunctions, TableJoin[] joins, boolean decodeBigString) throws DatabaseException {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ");
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(fields[i].table + "." + fields[i].field.name);
			if (fields[i] instanceof RenamedField) {
				sb.append(" as " + ((RenamedField) fields[i]).newName);
			}
		}
		if (joins.length > 0) { // Joined tables
			Set<String> includeTables = new HashSet<>();
			for (TableJoin join : joins) {
				if (!includeTables.contains(join.field1.table)) {
					includeTables.add(join.field1.table);
				}
				if (!includeTables.contains(join.field2.table)) {
					includeTables.add(join.field2.table);
				}
			}
			sb.append(" FROM ");
			int index = 0;
			for (String table : includeTables) {
				if (index > 0) {
					sb.append(", ");
				}
				sb.append(table);
				index++;
			}
		} else {
			sb.append(" FROM " + tableName);
		}
		Object params[] = new Object[conjunctions.length];

		if (conjunctions.length > 0 || joins.length > 0) {
			sb.append(" WHERE ");
		}
		if (joins.length > 0) {
			for (int i = 0; i < joins.length; i++) {
				if (i > 0) {
					sb.append(" AND ");
				}
				TableJoin join = joins[i];
				sb.append(join.field1.table + "." + join.field1.field.name + " = " + join.field2.table + "." + join.field2.field.name);
			}
		}
		if (conjunctions.length > 0) {
			for (int i = 0; i < conjunctions.length; i++) {
				if (i > 0 || joins.length > 0) {
					sb.append(" AND ");
				}
				sb.append(conjunctions[i].field.field.name + " " + conjunctions[i].comparator.code + " ");
				sb.append(conjunctions[i].field.field.isStoredAsString ? "'?'" : '?');
				params[i] = conjunctions[i].value;
			}
		}
		DatabaseResult res = this.select_raw(sb.toString(), params);
		if (res != null) {
			JsonArray ar;
			try {
				ar = res.getJSON(decodeBigString, fields);
			} catch (CompressionException e) {
				throw new DatabaseException("Failed to decode encoded string data", e);
			}
			if (ar != null) {
				return ar;
			}
		}
		throw new DatabaseException("Invalid select result");
	}

	@Deprecated
	public DatabaseResult select_raw(String sql, Object... params) throws DatabaseException {
		synchronized (syncer) {
			try {
				PreparedStatement stmt = prepareStatement(sql, params);
				return new DatabaseResult(stmt.executeQuery());
			} catch (SQLException e) {
				throw new DatabaseException("Failed to execute raw select: " + sql, e);
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
		execute_raw(sb.toString());

		if (this instanceof LayeredMetaDB) {
			List<TableField> oldFields = ((LayeredMetaDB) this).getFields(name);
			Set<String> oldFieldsMap = new HashSet<>();
			for (TableField oldField : oldFields) {
				oldFieldsMap.add(oldField.field.name);
			}
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (!oldFieldsMap.contains(f.name)) {
					try {
						execute_raw("ALTER TABLE " + name + " ADD " + f.toString());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

	}

	public final class DatabaseResult {

		private final ResultSet rs;

		private boolean closed = false;

		public JsonArray getJSON(boolean decodeBigString, TableField[] fields) throws CompressionException {
			JsonArray ar = new JsonArray(new ArrayList<JsonValue>());
			try {
				ResultSetMetaData rsmd = rs.getMetaData();
				Map<String, TableField> fieldsMap = new HashMap<>();
				for (TableField field : fields) {
					if (field instanceof RenamedField) {
						fieldsMap.put(((RenamedField) field).newName.toLowerCase(), field);
					} else {
						fieldsMap.put(field.field.name.toLowerCase(), field);
					}
				}

				String[] colNames = new String[rsmd.getColumnCount()];
				int[] colTypes = new int[rsmd.getColumnCount()];
				boolean[] colCompressed = new boolean[rsmd.getColumnCount()];

				for (int i = 0; i < colNames.length; i++) {
					colNames[i] = rsmd.getColumnName(i + 1);
					colTypes[i] = rsmd.getColumnType(i + 1);
					colCompressed[i] = fieldsMap.containsKey(colNames[i].toLowerCase()) ? fieldsMap.get(colNames[i].toLowerCase()).field.IsBigString() : false;
				}

				while (rs.next()) {
					JsonObject obj = new JsonObject();

					for (int i = 0; i < colNames.length; i++) {
						String colName = colNames[i];
						int colType = colTypes[i];
						if (colType == Types.INTEGER) {
							obj.add(colName, new JsonNumber(rs.getInt(i + 1), rs.getLong(i + 1) + ""));
						} else if (colType == Types.VARCHAR) {
							String str = rs.getString(i + 1);
							if (decodeBigString && colCompressed[i] && str != null) {
								str = Decompressor.decompress(str);
							}
							obj.add(colName, new JsonString(str));
						}
					}
					ar.add(obj);
				}
			} catch (SQLException e) {
				e.printStackTrace();
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
