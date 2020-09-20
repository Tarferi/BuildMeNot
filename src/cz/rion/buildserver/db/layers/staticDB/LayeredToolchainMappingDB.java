package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class LayeredToolchainMappingDB extends LayeredPHPAuthDB {

	private final Map<String, String> mapping = new HashMap<>();

	private void refresh() throws DatabaseException {
		synchronized (mapping) {
			mapping.clear();
			final String tableName = "hostname_mapping";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "host"), getField(tableName, "toolchain") }, false, new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("host") && obj.containsString("toolchain")) {
						String host = obj.getString("host").Value;
						String toolchain = obj.getString("toolchain").Value;
						mapping.put(host, toolchain);
					}
				}
			}
		}
	}

	public String getToolchainMapping(String host) {
		if (mapping.containsKey(host.toLowerCase())) {
			return mapping.get(host.toLowerCase());
		}
		return null;
	}

	public LayeredToolchainMappingDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("hostname_mapping", KEY("ID"), TEXT("host"), TEXT("toolchain"), NUMBER("valid"));
		refresh();
	}

	private static int DB_FILE_SETTINGS_BASE = 0x00039FFF;
	private static final String SettingsFileName = "toolchains.ini";
	private static final Object syncer = new Object();

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		lst.add(new DatabaseFile(DB_FILE_SETTINGS_BASE, SettingsFileName));
		return lst;
	}

	@Override
	public FileInfo createFile(String name, String contents) throws DatabaseException {
		if (name.equals(SettingsFileName)) {
			throw new DatabaseException("Cannnot create " + name + ": reserved file name");
		}
		return super.createFile(name, contents);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.ID == DB_FILE_SETTINGS_BASE) {
			synchronized (syncer) {
				final String tableName = "hostname_mapping";
				try {
					this.execute_raw("DELETE FROM " + tableName + " WHERE valid = 1");
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
				String[] lines = newContents.split("\n");
				for (String line : lines) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					String components[] = line.split("=", 2);
					if (components.length == 2) {
						String host = components[0].trim();
						String toolchain = components[1].trim();
						try {
							this.insert(tableName, new ValuedField(getField(tableName, "host"), host), new ValuedField(getField(tableName, "toolchain"), toolchain), new ValuedField(getField(tableName, "valid"), 1));
						} catch (DatabaseException e) {
							e.printStackTrace();
						}
					}
				}

				try {
					refresh();
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	private FileInfo getFile() throws DatabaseException {
		synchronized (syncer) {
			final String tableName = "hostname_mapping";

			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "host"), getField(tableName, "toolchain") }, false, new ComparisionField(getField(tableName, "valid"), 1));
			StringBuilder sb = new StringBuilder();
			sb.append("# Pridavejte prosim ve formatu <hostname>:<toolchain>\n");
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("host") && obj.containsString("toolchain")) {
						String host = obj.getString("host").Value;
						String toolchain = obj.getString("toolchain").Value;
						sb.append(host + " = " + toolchain + "\n");
					}
				}
			}
			String str = sb.toString();
			str = str.substring(0, str.length() - 1);
			return new FileInfo(DB_FILE_SETTINGS_BASE, SettingsFileName, str);
		}
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString) {
		if (name.equals(SettingsFileName)) {
			try {
				return getFile();
			} catch (DatabaseException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return super.loadFile(name, decodeBigString);
		}
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		if (fileID == DB_FILE_SETTINGS_BASE) {
			return getFile();
		} else {
			return super.getFile(fileID, decodeBigString);
		}
	}

}
