package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public abstract class LayeredToolchainMappingDB extends LayeredPHPAuthDB {

	private final Map<String, Toolchain> mapping = new HashMap<>();

	private static final String SettingsFileName = "toolchains.ini";

	private void refresh() throws DatabaseException {
		synchronized (mapping) {
			mapping.clear();

			Map<String, Toolchain> cache = new HashMap<>();

			final String tableName = "hostname_mapping";
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "host"), getField(tableName, "toolchain") }, false, new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("host") && obj.containsString("toolchain")) {
						String host = obj.getString("host").Value;
						String toolchain = obj.getString("toolchain").Value;

						Toolchain tc = cache.get(toolchain);
						if (tc == null) {
							StaticDB sdb = (StaticDB) this;
							try {
								tc = sdb.getToolchain(toolchain);
							} catch (NoSuchToolchainException e) {
								continue;
							}
							cache.put(toolchain, tc);
						}
						mapping.put(host, tc);
					}
				}
			}
		}
	}

	public Toolchain getToolchainMapping(String host) {
		if (mapping.containsKey(host.toLowerCase())) {
			return mapping.get(host.toLowerCase());
		}
		return null;
	}

	public LayeredToolchainMappingDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("hostname_mapping", true, KEY("ID"), TEXT("host"), TEXT("toolchain"), NUMBER("valid"));
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

			@SuppressWarnings("deprecation")
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

			@Override
			public String getToolchain() {
				return Settings.getRootToolchain();
			}

		});
	}

	@Override
	public void afterInit() {
		try {
			refresh();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		super.afterInit();
	}
}
