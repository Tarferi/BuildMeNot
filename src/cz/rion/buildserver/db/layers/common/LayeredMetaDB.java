package cz.rion.buildserver.db.layers.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.SQLiteDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public class LayeredMetaDB extends SQLiteDB {

	private final Map<String, String> tables = new HashMap<>();
	private final List<String> lstTables = new ArrayList<>();

	private static final Object incSyncer = new Object();
	private static int DB_FILE_FIRST_ID_ALL = 0x0FFFFFFF;
	protected static final int DB_FILE_SIZE = 10000; // 10k tables per database
	protected final int DB_FILE_FIRST_ID;

	protected final String metaDatabaseName;

	private final Field dataField;
	private final Field nameField;
	private final Field idField;

	public LayeredMetaDB(String fileName, String metaDatabaseName) throws DatabaseException {
		super(fileName);
		this.metaDatabaseName = metaDatabaseName;
		this.dataField = BIGTEXT("data");
		this.nameField = TEXT("name");
		this.idField = KEY("ID");
		this.makeTable("meta_tables", idField, nameField, dataField);
		synchronized (incSyncer) {
			this.DB_FILE_FIRST_ID = DB_FILE_FIRST_ID_ALL;
			DB_FILE_FIRST_ID_ALL += DB_FILE_SIZE;
		}
	}

	public List<String> getTables() {
		List<String> ret = new ArrayList<>();
		for (String table : lstTables) {
			if (table.toLowerCase().equals("meta_tables")) {
				continue;
			}
			ret.add(table);
		}
		return ret;
	}

	private JsonArray getFields(Field[] fields) {
		JsonArray arr = new JsonArray(new ArrayList<JsonValue>());
		for (Field f : fields) {
			arr.add(new JsonString(f.getDecodableRepresentation()));
		}
		return arr;
	}

	public boolean isTable(String name) {
		return tables.containsKey(name.toLowerCase());
	}

	public final JsonArray readTable(String tableName, boolean decodeBigString) throws DatabaseException {
		List<TableField> fields = this.getFields(tableName);
		TableField[] fld = new TableField[fields.size()];
		for (int i = 0; i < fld.length; i++) {
			TableField f = fields.get(i);
			fld[i] = f;
		}
		return this.select(tableName, fld, decodeBigString);
	}

	private final Map<String, Map<String, TableField>> __fieldsCache = new HashMap<>();

	public TableField getField(String tableName, String fieldName) throws DatabaseException {
		tableName = tableName.toLowerCase();
		fieldName = fieldName.toLowerCase();
		Map<String, TableField> tableCache;
		if (!__fieldsCache.containsKey(tableName)) {
			tableCache = new HashMap<String, TableField>();
			__fieldsCache.put(tableName, tableCache);
		} else {
			tableCache = __fieldsCache.get(tableName);
		}
		if (tableCache.containsKey(fieldName)) {
			return tableCache.get(fieldName);
		}
		List<TableField> fields = getFields(tableName);
		if (fields == null) {
			throw new DatabaseException("No valid fields for " + tableName);
		}
		TableField resultField = null;
		for (TableField ff : fields) {
			tableCache.put(ff.field.name.toLowerCase(), ff);
			if (ff.field.name.toLowerCase().equals(fieldName)) {
				resultField = ff;
			}
		}
		if (resultField == null) {
			throw new DatabaseException("Invalid field in table " + tableName + ": " + fieldName);
		}
		return resultField;
	}


	public boolean tableWriteable(String tableName) {
		return true;
	}
	
	public final List<TableField> getFields(String name) {
		JsonArray res;
		try {
			final String tableName = "meta_tables";
			if (tableName.equals(name)) {
				List<TableField> lst = new ArrayList<>();
				lst.add(new TableField(idField, name));
				lst.add(new TableField(dataField, name));
				lst.add(new TableField(nameField, name));
				return lst;
			}
			res = this.select(tableName, new TableField[] { new TableField(dataField, tableName) }, true, new ComparisionField(new TableField(nameField, tableName), name));
			if (res.Value.size() != 0) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					if (val.asObject().containsString("data")) {
						String data = val.asObject().getString("data").Value;
						// data= Decompressor.decompress(data);
						val = JsonValue.parse(data);

						if (val != null) {
							if (val.isArray()) {
								List<TableField> lst = new ArrayList<>();
								for (JsonValue vavl : val.asArray().Value) {
									lst.add(new TableField(Field.fromDecodableRepresentation(vavl.asString().Value), name));
								}
								return lst;
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected void makeTable(String name, Field... fields) throws DatabaseException {
		super.makeTable(name, fields);
		String dataStr = getFields(fields).getJsonString();
		this.tables.put(name.toLowerCase(), dataStr);
		this.lstTables.add(name);
		final String tableName = "meta_tables";

		JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "data") }, false, new ComparisionField(getField(tableName, "name"), name));
		if (res.Value.size() == 0) {
			this.insert(tableName, new ValuedField(this.getField(tableName, "name"), name), new ValuedField(this.getField(tableName, "data"), dataStr));
		} else {
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID")) {
					int db_id = obj.getNumber("ID").Value;
					this.update(tableName, db_id, new ValuedField(this.getField(tableName, "data"), dataStr));
				}
			}
		}
	}
}
