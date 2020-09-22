package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public abstract class LayeredToolchainMappingDB extends LayeredPHPAuthDB {

	private final Map<String, String> mapping = new HashMap<>();

	private static final String SettingsFileName = "toolchains.ini";

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
		this.registerVirtualFile(new VirtualFile() {

			@Override
			public String read() throws DatabaseException {
				final String tableName = "hostname_mapping";

				JsonArray res = select(tableName, new TableField[] { getField(tableName, "host"), getField(tableName, "toolchain") }, false, new ComparisionField(getField(tableName, "valid"), 1));
				StringBuilder sb = new StringBuilder();
				sb.append("# Pridavejte prosim ve formatu <hostname> = <toolchain>\n");
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
				return str;
			}

			@Override
			public void write(String data) throws DatabaseException {
				final String tableName = "hostname_mapping";
				try {
					execute_raw("DELETE FROM " + tableName + " WHERE valid = 1");
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
				String[] lines = data.split("\n");
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
							insert(tableName, new ValuedField(getField(tableName, "host"), host), new ValuedField(getField(tableName, "toolchain"), toolchain), new ValuedField(getField(tableName, "valid"), 1));
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

			@Override
			public String getName() {
				return SettingsFileName;
			}
			
		});
	}
}
