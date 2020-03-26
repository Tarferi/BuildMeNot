package cz.rion.buildserver.db.layers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class LayeredDBFileWrapperDB extends LayeredFilesDB {

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

	private static FileInfo loadDBFile(LayeredMetaDB db, int id, String tableName) {
		JsonArray res;
		try {
			res = db.select("SELECT * FROM " + tableName).getJSON();
			JsonObject result = new JsonObject();
			if (res != null) {
				if (res.isArray()) {
					JsonArray arr = res.asArray(); // Row data of table contents
					if (!arr.Value.isEmpty()) {
						List<Field> columns = db.getFields(tableName); // Fields definitions
						if (columns != null) {
							List<JsonValue> columnsjsn = new ArrayList<>();
							for (Field column : columns) {
								columnsjsn.add(new JsonString(column.getDecodableRepresentation()));
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
										for (Field col : columns) {
											JsonValue colValue = vobj.get(col.name);
											// if (col.IsBigString) {
											// values.add(new JsonString(colValue.asString().Value.length() + " bytes"));
											// } else {
											values.add(colValue);
											// }
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
			return null;
		} else {
			return super.createFile(name, contents);
		}
	}

	@Override
	public FileInfo getFile(int fileID) throws DatabaseException {
		if (fileID >= DB_FILE_FIRST_ID && fileID < DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			return loadDBFile(this, fileID, this.lstTables.get(fileID - DB_FILE_FIRST_ID));
		} else {
			return super.getFile(fileID);
		}
	}

	public static FileInfo processPostLoadedFile(LayeredMetaDB db, FileInfo fi) {
		if (fi != null) {
			if (fi.FileName.startsWith(dbDirPrefix + db.metaDatabaseName + "/") && fi.FileName.endsWith(viewFileSuffix)) {
				return handleView(db, fi);
			}
		}
		return fi;
	}

	public static FileInfo getFile(LayeredMetaDB db, int fileID) throws DatabaseException {
		if (fileID >= db.DB_FILE_FIRST_ID && fileID < db.DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			return loadDBFile(db, fileID, db.lstTables.get(fileID - db.DB_FILE_FIRST_ID));
		} else {
			return null;
		}
	}

	public static final void loadDatabaseFiles(LayeredMetaDB db, List<DatabaseFile> files) {
		if (files != null) {
			int index = 0;
			for (String name : db.lstTables) {
				files.add(new DatabaseFile(index + db.DB_FILE_FIRST_ID, dbDirPrefix + db.metaDatabaseName + "/" + name + dbFileSuffix));
				index++;
			}
		}
	}

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		loadDatabaseFiles(this, lst);
		return lst;
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.ID >= DB_FILE_FIRST_ID && file.ID < DB_FILE_FIRST_ID + DB_FILE_SIZE) {
			return;
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	public static final Pattern FreeSQLSyntaxMatcher = Pattern.compile("(TEXT|BIGTEXT|INT|DATE)\\((\\w+)\\)", Pattern.MULTILINE);

	private static FileInfo handleView(LayeredMetaDB db, FileInfo sqlFile) {
		if (sqlFile == null) { // Pass error
			return null;
		}
		JsonValue result = null;
		int code = 1; // Error

		String SQL = sqlFile.Contents; // Strip all metas
		String freeSQL = FreeSQLSyntaxMatcher.matcher(SQL).replaceAll("$2");

		try {
			freeSQL = Pattern.compile("\\%NOW\\%", Pattern.MULTILINE).matcher(freeSQL).replaceAll(new Date().getTime() + "");
			DatabaseResult res = db.select(freeSQL);
			result = res.getJSON();
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
	public FileInfo loadFile(String name) {
		if (name.startsWith(dbFilePrefix)) {
			if (name.endsWith(viewFileSuffix)) { // SQL view
				FileInfo sqlFile = super.loadFile(name);
				return handleView(this, sqlFile);
			}

			int index = super.lstTables.indexOf(name);
			if (index < 0) {
				return null;
			}
			name = name.substring(dbFilePrefix.length());
			if (!name.endsWith(dbFileSuffix)) {
				return null;
			}
			name = name.substring(0, name.length() - dbFileSuffix.length());
			return loadDBFile(this, index + DB_FILE_FIRST_ID, name);
		} else {
			return super.loadFile(name);
		}
	}
}
