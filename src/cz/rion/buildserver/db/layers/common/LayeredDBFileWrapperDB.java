package cz.rion.buildserver.db.layers.common;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualDatabaseFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredImportDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.json.JsonValue.JsonNumber;

public abstract class LayeredDBFileWrapperDB extends LayeredImportDB {

	public static final Pattern FreeSQLSyntaxMatcher = Pattern.compile("(LOGIN|TEXT|BIGTEXT|INT|DATE)\\((\\w+)\\)", Pattern.MULTILINE);

	private static final String dbDirPrefix = "database/";
	private static final String dbFileSuffix = ".table";
	public static final String viewFileSuffix = ".view";
	public final String dbFilePrefix;
	private TestManager testManager = null;

	public void setTestManager(TestManager m) {
		this.testManager = m;
	}

	public TestManager getTestManager() {
		return testManager;
	}

	private final DatabaseInitData dbData;

	private VirtualViewManipulator RuntimeDBViewManipulator = null;

	@Override
	public void afterInit() {
		super.afterInit();
		initTableFiles((StaticDB) this, this, dbData.Files, this.getRootToolchain(), this.getSharedToolchain());
	}

	public LayeredDBFileWrapperDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.dbFilePrefix = getDBFilePrefix(this);
		this.dbData = dbData;
	}

	public static void initTableFiles(StaticDB sdb, LayeredMetaDB db, VirtualFileManager files, Toolchain rootToolchain, Toolchain sharedToolchain) {
		if (db instanceof RuntimeDB) { // Ugliest piece of interconnection, but it works
			((LayeredDBFileWrapperDB) sdb).RuntimeDBViewManipulator = db.ViewManipulator;
		}
		String prefix = getDBFilePrefix(db);
		for (String name : db.getTables()) {
			Toolchain toolchain = db.isRootOnly(name) ? rootToolchain : sharedToolchain;
			files.registerVirtualFile(new VirtualTableFile(sdb, db, prefix + name + dbFileSuffix, toolchain, name));
		}
	}

	public static interface VirtualViewManipulator {
		public DatabaseResult select_raw(String sql, Object... objects) throws DatabaseException;
	}

	private static class VirtualTableFile extends VirtualFile {

		private final String tableName;
		private final Toolchain toolchain;
		private final LayeredMetaDB db;
		private final StaticDB sdb;

		public VirtualTableFile(StaticDB sdb, LayeredMetaDB db, String name, Toolchain toolchain, String tableName) {
			super(name, toolchain);
			this.tableName = tableName;
			this.toolchain = toolchain;
			this.db = db;
			this.sdb = sdb;
		}

		private boolean editRow(JsonObject contents, UserContext context) {
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

				if (sdb != null) {
					JsonArray original = db.select(tableName, fields, true, new ComparisionField(idField, ID));
					JsonObject obj = new JsonObject();
					obj.add("original", original);
					obj.add("new", contents);
					obj.add("ID", new JsonNumber(ID));
					obj.add("table", new JsonString(tableName));
					sdb.adminLog(toolchain, context.getAddress(), context.getLogin(), "editRow:" + ID + ":" + Name, obj.getJsonString());
				}

				db.update(tableName, ID, values);
				return true;
			} catch (DatabaseException e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			Toolchain toolchain = context.getToolchain();
			JsonArray res;
			try {
				res = db.readTable(tableName, true, context);
				JsonObject result = new JsonObject();
				if (res != null) {
					if (res.isArray()) {
						JsonArray arr = res.asArray(); // Row data of table contents
						if (!arr.Value.isEmpty()) {
							List<TableField> columns = db.getFields(tableName); // Fields definitions
							if (columns != null) {
								List<JsonValue> columnsjsn = new ArrayList<>();
								for (TableField column : columns) {
									if (!column.field.name.equals("toolchain") || toolchain.IsRoot) { // No toolchain columns for non roots
										columnsjsn.add(new JsonString(column.field.getDecodableRepresentation()));
									}
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
												if (!col.field.name.equals("toolchain") || toolchain.IsRoot) { // No toolchain columns for non roots
													JsonValue colValue = vobj.get(col.field.name);
													values.add(colValue);
												}
											}
											resultData.add(new JsonArray(values));
										}
									}
								}
							}
						}
					}
				}
				return result.getJsonString();
			} catch (DatabaseException e) {
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			JsonValue val = JsonValue.parse(value);
			if (val != null) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (editRow(obj, context)) {
						if (tableName.equals("files")) {
							sdb.reloadFiles();
							TestManager testManager = ((LayeredDBFileWrapperDB) sdb).getTestManager();
							if (testManager != null) {
								testManager.reloadTests();
							}
						} else if (tableName.equals("toolchain") || tableName.equals("tools") || tableName.equals("builders")) {
							sdb.reloadToolchains();
						}
						return true;
					}
				}
			}
			return false;
		}
	}

	public class VirtualViewFile extends VirtualDatabaseFile {

		public VirtualViewFile(int fileID, String name, Toolchain toolchain, DatabaseFileManipulator source) {
			super(fileID, name, toolchain, source);
		}

		private final DatabaseResult select_raw(String sql) throws DatabaseException {
			VirtualViewManipulator rawSQL = Name.startsWith(dbDirPrefix + metaDatabaseName) ? ViewManipulator : RuntimeDBViewManipulator;
			if (rawSQL == null) {
				return null;
			} else {
				return rawSQL.select_raw(sql);
			}
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			String SQL = super.read(context);
			if (SQL != null) {
				JsonValue result = null;
				int code = 1; // Error

				String freeSQL = FreeSQLSyntaxMatcher.matcher(SQL).replaceAll("$2");

				try {
					freeSQL = Pattern.compile("\\%NOW\\%", Pattern.MULTILINE).matcher(freeSQL).replaceAll(new Date().getTime() + "");
					String tSQL = freeSQL.toLowerCase();
					final String[] bannedKW = new String[] { "update", "insert", "delete", "drop", "alter", "create" };
					for (String banned : bannedKW) {
						if (tSQL.contains(banned)) {
							throw new DatabaseException("Forbidden command: " + banned);
						}
					}
					DatabaseResult res = select_raw(freeSQL); // TODO

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

					result = res.getJSON(true, fields, context.getToolchain());
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
				return robj.getJsonString();
			}
			return null;
		}

	}

	private static final String getDBFilePrefix(LayeredMetaDB db) {
		return dbDirPrefix + db.metaDatabaseName + "/";
	}

	@Override
	public VirtualDatabaseFile createFile(UserContext context, String name, String contents) throws DatabaseException {
		if (name.startsWith(dbFilePrefix)) {
			if (name.endsWith(viewFileSuffix)) {
				return super.createFile(context, name, contents);
			} else {
				return null;
			}
		} else {
			return super.createFile(context, name, contents);
		}
	}

}
