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

	protected final Map<String, String> tables = new HashMap<>();
	protected final List<String> lstTables = new ArrayList<>();

	private static final Object incSyncer = new Object();
	private static int DB_FILE_FIRST_ID_ALL = 0x0FFFFFFF;
	protected static final int DB_FILE_SIZE = 10000; // 10k tables per database
	protected final int DB_FILE_FIRST_ID;

	protected final String metaDatabaseName;

	public LayeredMetaDB(String fileName, String metaDatabaseName) throws DatabaseException {
		super(fileName);
		this.metaDatabaseName = metaDatabaseName;
		this.makeTable("meta_tables", KEY("ID"), TEXT("name"), BIGTEXT("data"));
		synchronized (incSyncer) {
			this.DB_FILE_FIRST_ID = DB_FILE_FIRST_ID_ALL;
			DB_FILE_FIRST_ID_ALL += DB_FILE_SIZE;
		}
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

	public final List<Field> getFields(String name) {
		JsonArray res;
		try {
			res = this.select("SELECT * FROM meta_tables WHERE name = '?'", name).getJSON();
			if (res != null) {
				if (res.Value.size() != 0) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						if (val.asObject().containsString("data")) {
							String data = val.asObject().getString("data").Value;
							val = JsonValue.parse(data);
							if (val != null) {
								if (val.isArray()) {
									List<Field> lst = new ArrayList<>();
									for (JsonValue vavl : val.asArray().Value) {
										lst.add(Field.fromDecodableRepresentation(vavl.asString().Value));
									}
									return lst;
								}
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
		JsonArray res = this.select("SELECT * FROM meta_tables WHERE name = '?'", name).getJSON();
		if (res != null) {
			if (res.Value.size() == 0) {
				this.execute("INSERT INTO meta_tables (name, data) VALUES ('?', '?')", name, dataStr);
			} else {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID")) {
						int db_id = obj.getNumber("ID").Value;
						this.execute("UPDATE meta_tables SET data = '?' WHERE ID = ?", dataStr, db_id);
					}
				}
			}
		}
	}
}
