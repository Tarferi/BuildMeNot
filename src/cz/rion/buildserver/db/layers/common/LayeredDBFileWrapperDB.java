package cz.rion.buildserver.db.layers.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredImportDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public abstract class LayeredDBFileWrapperDB extends LayeredImportDB {

	public static final Pattern FreeSQLSyntaxMatcher = Pattern.compile("(LOGIN|TEXT|BIGTEXT|INT|DATE)\\((\\w+)\\)", Pattern.MULTILINE);

	private static final String dbDirPrefix = "database/";
	private static final String dbFileSuffix = ".table";
	private static final String viewFileSuffix = ".view";
	public final String dbFilePrefix;

	public LayeredDBFileWrapperDB(String fileName) throws DatabaseException {
		super(fileName);
		this.dbFilePrefix = getDBFilePrefix(this);
	}

	private static final String getDBFilePrefix(LayeredMetaDB db) {
		return dbDirPrefix + db.metaDatabaseName + "/";
	}

	private static FileInfo loadDBFile(LayeredMetaDB db, int id, String tableName, boolean decodeBigString) {
		JsonArray res;
		try {
			res = db.readTable(tableName, decodeBigString);
			JsonObject result = new JsonObject();
			if (res != null) {
				if (res.isArray()) {
					JsonArray arr = res.asArray(); // Row data of table contents
					if (!arr.Value.isEmpty()) {
						List<TableField> columns = db.getFields(tableName); // Fields definitions
						if (columns != null) {
							List<JsonValue> columnsjsn = new ArrayList<>();
							for (TableField column : columns) {
								columnsjsn.add(new JsonString(column.field.getDecodableRepresentation()));
							}
							JsonValue first = arr.Value.get(0);
							if (first.isObject()) {
								JsonArray resultData = new JsonArray(new ArrayList<JsonValue>());
								result.add("columns", new JsonArray(columnsjsn));
								result.add("data", resultData);
								for (JsonValue val : arr.Value) {
									if (val.isObject()) {
										List<JsonValue> values = new ArrayList<>();
										JsonObject vobj = val.asObject();
										for (TableField col : columns) {
											JsonValue colValue = vobj.get(col.field.name);
											values.add(colValue);
										}
										resultData.add(new JsonArray(values));
									}
								}
							}
						}
					}
				}
			}
			return new FileInfo(id, getDBFilePrefix(db) + tableName + dbFileSuffix, result.getJsonString());
		} catch (DatabaseException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public FileInfo createFile(String name, String contents) throws DatabaseException {
		if (name.startsWith(dbFilePrefix)) {
			if (name.endsWith(viewFileSuffix)) {
				return super.createFile(name, contents);
			} else {
				return null;
			}
		} else {
			return super.createFile(name, contents);
		}
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		if (fileID >= DB_FILE_FIRST_ID && fileID < DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			return loadDBFile(this, fileID, this.getTables().get(fileID - DB_FILE_FIRST_ID), decodeBigString);
		} else {
			FileInfo fo = super.getFile(fileID, decodeBigString);
			return fo;
		}
	}

	public static FileInfo processPostLoadedFile(LayeredMetaDB db, FileInfo fi, boolean decodeBigString) {
		if (fi != null) {
			if (fi.FileName.startsWith(dbDirPrefix + db.metaDatabaseName + "/") && fi.FileName.endsWith(viewFileSuffix)) {
				return handleView(db, fi, fi.FileName, fi.ID, decodeBigString);
			}
		}
		return fi;
	}

	public static FileInfo getFile(LayeredMetaDB db, int fileID, boolean decodeBigString) throws DatabaseException {
		if (fileID >= db.DB_FILE_FIRST_ID && fileID < db.DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			List<String> tables = db.getTables();
			if (fileID - db.DB_FILE_FIRST_ID < tables.size()) { // Valid
				return loadDBFile(db, fileID, tables.get(fileID - db.DB_FILE_FIRST_ID), decodeBigString);
			} else if (db instanceof RuntimeDB) { // Something fucky
				return ((RuntimeDB) db).getSpecialFile(fileID, false);
			}
		}
		return null;
	}

	public static final void loadDatabaseFiles(LayeredMetaDB db, List<DatabaseFile> files) {
		if (files != null) {
			int index = 0;
			for (String name : db.getTables()) {
				files.add(new DatabaseFile(index + db.DB_FILE_FIRST_ID, dbDirPrefix + db.metaDatabaseName + "/" + name + dbFileSuffix));
				index++;
			}
			if (db instanceof RuntimeDB) {
				((RuntimeDB) db).addSpecialFiles(files, index, dbDirPrefix + db.metaDatabaseName + "/", viewFileSuffix);
			}
		}
	}

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		loadDatabaseFiles(this, lst);
		return lst;
	}

	public static final boolean editRow(LayeredMetaDB db, FileInfo file, JsonObject contents) {
		return editRow(null, null, null, db, file, contents);
	}

	public static final boolean editRow(StaticDB sdb, String login, String address, LayeredMetaDB db, FileInfo file, JsonObject contents) {
		if (file.ID >= db.DB_FILE_FIRST_ID && file.ID < db.DB_FILE_FIRST_ID + DB_FILE_SIZE) { // Owned by the DB -> table exists in db
			if (contents.containsNumber("ID")) {
				String tableName = db.getTables().get(file.ID - db.DB_FILE_FIRST_ID);
				if (!db.tableWriteable(tableName)) {
					return false;
				}
				ValuedField[] values = new ValuedField[contents.getEntries().size() - 1];
				TableField[] fields = new TableField[values.length];
				TableField idField = null;
				int ID = -1;
				int index = 0;
				try {
					for (Entry<String, JsonValue> entry : contents.getEntries()) {
						String name = entry.getKey();
						JsonValue value = entry.getValue();
						if (name.equals("ID")) {
							if (!value.isNumber()) { // Non numeric ID
								return false;
							}
							ID = value.asNumber().Value;
							idField = db.getField(tableName, name);
							continue;
						}
						if (index == values.length) { // No ID
							return false;
						}
						Object val = null;
						if (value.isNumber()) {
							val = value.asNumber().asLong();
						} else if (value.isString()) {
							val = value.asString().Value;
						} else {
							return false;
						}
						try {
							fields[index] = db.getField(tableName, name);
							values[index] = new ValuedField(fields[index], val);
						} catch (DatabaseException e) { // No such column
							e.printStackTrace();
							return false;
						}
						index++;
					}
					if (index != values.length) { // Multiple IDs
						return false;
					}
					// Get current data

					if (sdb != null && login != null && address != null) {
						JsonArray original = db.select(tableName, fields, true, new ComparisionField(idField, ID));
						JsonObject obj = new JsonObject();
						obj.add("original", original);
						obj.add("new", contents);
						obj.add("ID", new JsonNumber(ID));
						obj.add("table", new JsonString(tableName));
						sdb.adminLog(address, login, "editRow:" + file.ID + ":" + file.FileName, obj.getJsonString());
					}

					db.update(tableName, ID, values);
					return true;
				} catch (DatabaseException e) {
					e.printStackTrace();
					return false;
				}
			}
		}
		return false;
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.ID >= DB_FILE_FIRST_ID && file.ID < DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			return;
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	private static FileInfo handleSpecialView(LayeredMetaDB db, String name, int fileID) {
		if (db instanceof RuntimeDB) {
			return ((RuntimeDB) db).handleSpecialView(name, fileID, true);
		}
		return null;
	}

	private static FileInfo handleView(LayeredMetaDB db, FileInfo sqlFile, String name, int fileID, boolean decodeBigString) {
		if (db instanceof RuntimeDB && name != null) {
			if (((RuntimeDB) db).ownsFile(name)) {
				return handleSpecialView(db, name, fileID);
			}
		} else if (sqlFile == null) { // Pass error
			return handleSpecialView(db, name, fileID);
		}
		JsonValue result = null;
		int code = 1; // Error

		String SQL = sqlFile.Contents; // Strip all metas
		String freeSQL = FreeSQLSyntaxMatcher.matcher(SQL).replaceAll("$2");

		try {
			freeSQL = Pattern.compile("\\%NOW\\%", Pattern.MULTILINE).matcher(freeSQL).replaceAll(new Date().getTime() + "");
			String tSQL = freeSQL.toLowerCase();
			final String[] bannedKW = new String[] {
					"update",
					"insert",
					"delete",
					"drop",
					"alter",
					"create"
			};
			for (String banned : bannedKW) {
				if (tSQL.contains(banned)) {
					throw new DatabaseException("Forbidden command: " + banned);
				}
			}
			@SuppressWarnings("deprecation")
			DatabaseResult res = db.select_raw(freeSQL); // TODO

			// Parse query
			List<TableField> fields_lst = new ArrayList<>();
			Matcher matcher = LayeredDBFileWrapperDB.FreeSQLSyntaxMatcher.matcher(SQL);
			while (matcher.find()) {
				String fn = matcher.group(1);
				String field = matcher.group(2);
				if (fn.equals("BIGTEXT")) {
					fields_lst.add(new TableField(new Field(field, "", FieldType.BIGSTRING), ""));
				}
			}

			TableField[] fields = new TableField[fields_lst.size()];
			for (int i = 0; i < fields.length; i++) {
				fields[i] = fields_lst.get(i);
			}

			result = res.getJSON(decodeBigString, fields);
			if (result != null) { // The only non-error scenario
				code = 0;
			}
		} catch (Exception e) { // No need to print exception, not our SQL to handle
			result = new JsonString(e.getMessage());
		}
		JsonObject robj = new JsonObject();
		robj.add("SQL", new JsonString(SQL));
		robj.add("freeSQL", new JsonString(freeSQL));
		robj.add("code", new JsonNumber(code));
		robj.add("result", result);
		return new FileInfo(sqlFile.ID, sqlFile.FileName, robj.getJsonString());
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString) {
		if (name.startsWith(dbFilePrefix)) {
			if (name.endsWith(viewFileSuffix)) { // SQL view
				FileInfo sqlFile = super.loadFile(name, decodeBigString);
				return handleView(this, sqlFile, name, -1, decodeBigString);
			}

			int index = super.getTables().indexOf(name);
			if (index < 0) {
				return null;
			}
			name = name.substring(dbFilePrefix.length());
			if (!name.endsWith(dbFileSuffix)) {
				return null;
			}
			name = name.substring(0, name.length() - dbFileSuffix.length());
			return loadDBFile(this, index + DB_FILE_FIRST_ID, name, decodeBigString);
		} else {
			return super.loadFile(name, decodeBigString);
		}
	}
}
