package cz.rion.buildserver;

import java.util.List;

import cz.rion.buildserver.compression.Compressor;
import cz.rion.buildserver.compression.Decompressor;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.SQLiteDB.Field;
import cz.rion.buildserver.db.SQLiteDB.FieldType;
import cz.rion.buildserver.db.SQLiteDB.TableField;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;

public class Recompressor {

	private RuntimeDB db;
	private StaticDB sdb;

	public Recompressor() throws DatabaseException {
		this.sdb = new StaticDB(Settings.getStaticDB());
		this.db = new RuntimeDB(Settings.getMainDB(), sdb);
	}

	private TableField[] getFields(LayeredMetaDB db, String tableName) {
		List<TableField> flds = db.getFields(tableName);
		TableField[] fields = new TableField[flds.size()];
		for (int i = 0; i < fields.length; i++) {
			fields[i] = flds.get(i);
		}
		return fields;
	}

	private void handle(LayeredMetaDB db, TableField field) throws DatabaseException, CompressionException {
		JsonArray allData = db.select_raw("SELECT * FROM " + field.table).getJSON(false, getFields(db, field.table));
		int all = allData.Value.size();
		int done = 0;
		for (JsonValue val : allData.Value) {
			int id = val.asObject().getNumber("ID").Value;
			String value = val.asObject().getString(field.field.name).Value;
			int compressedRatio = 0;
			if (value != null) {
				String compressed = Compressor.compress(value, 1);
				String original = Decompressor.decompress(compressed, 1);
				if (!original.equals(value)) {
					throw new CompressionException("Compression failed: cannot decompress");
				}
				compressedRatio = value.length() > 0 ? (compressed.length() * 100) / value.length() : 0;
				db.execute_raw("UPDATE " + field.table + " SET " + field.field.name + " = '?' WHERE ID = ?", compressed, id);
			}
			done++;
			System.out.println("Updating " + field.table + "." + field.field.name + ": " + done + "/" + all + " compressed " + compressedRatio);
		}
	}

	public void runDynamic() throws DatabaseException, CompressionException {
		LayeredMetaDB db = this.db;
		List<String> tables = db.getTables();
		for (String table : tables) {
			List<TableField> fields = db.getFields(table);
			for (TableField field : fields) {
				if (field.field.type == FieldType.BIGSTRING) {
					handle(db, field);
				}
			}
		}
	}

	public void runStatic() throws DatabaseException, CompressionException {
		LayeredMetaDB db = sdb;
		List<String> tables = db.getTables();
		for (String table : tables) {
			List<TableField> fields = db.getFields(table);
			for (TableField field : fields) {
				if (field.field.type == FieldType.BIGSTRING) {
					handle(db, field);
				}
			}
		}
	}

}
