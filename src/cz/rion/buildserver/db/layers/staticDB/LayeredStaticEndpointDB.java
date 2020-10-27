package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;

public abstract class LayeredStaticEndpointDB extends LayeredDynDocDB {

	public LayeredStaticEndpointDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("static_endpoints", true, KEY("ID"), TEXT("path"), BIGTEXT("contents"));
		this.registerVirtualFile(new VirtualFile() {

			private String getHeader() {
				StringBuilder sb = new StringBuilder();
				sb.append("# JSON objekt, kde klíèem je \"path\" a hodnotou je textový obsah enpointu\n");
				return sb.toString();
			}

			private String stripHeader(String data) {
				StringBuilder sb = new StringBuilder();
				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					sb.append(line + "\n");
				}
				return sb.toString();
			}

			@Override
			public String read() throws DatabaseException {
				JsonObject obj = new JsonObject();
				for (StaticEndpoint endpoint : getStaticEndpoints()) {
					obj.add(endpoint.path, new JsonString(endpoint.contents));
				}
				return getHeader() + JsonValue.getPrettyJsonString(obj);
			}

			private List<StaticEndpoint> convert(JsonValue val) {
				List<StaticEndpoint> lst = null;
				if (val != null) {
					if (val.isObject()) {
						lst = new ArrayList<>();
						for (Entry<String, JsonValue> entry : val.asObject().getEntries()) {
							String path = entry.getKey();
							JsonValue c = entry.getValue();
							if (c.isString()) {
								String contents = c.asString().Value;
								lst.add(new StaticEndpoint(path, contents));
							}
						}
					}
				}
				return lst;
			}

			@Override
			public void write(String data) throws DatabaseException {
				JsonValue val = JsonValue.parse(stripHeader(data));
				List<StaticEndpoint> nw = convert(val);
				if (nw != null) {
					updateStaticEndpoints(nw);
				}
			}

			@Override
			public String getName() {
				return "static_endpoints.ini";
			}

			@Override
			public String getToolchain() {
				return Settings.getRootToolchain();
			}

		});
	}

	private void update(int id, String contents) throws DatabaseException {
		final String tableName = "static_endpoints";
		LayeredStaticEndpointDB.this.update(tableName, id, new ValuedField(getField(tableName, "contents"), contents));
	}

	@SuppressWarnings("deprecation")
	private void delete(int id) throws DatabaseException {
		LayeredStaticEndpointDB.this.execute_raw("DELETE FROM static_endpoints WHERE id = ?", id);
	}

	private void create(String path, String contents) throws DatabaseException {
		final String tableName = "static_endpoints";
		LayeredStaticEndpointDB.this.insert(tableName, new ValuedField(getField(tableName, "path"), path), new ValuedField(getField(tableName, "contents"), contents));
	}

	public void updateStaticEndpoints(List<StaticEndpoint> nw) throws DatabaseException {
		try {
			Map<String, StoredStaticEndpoint> existing = new HashMap<>();
			for (StoredStaticEndpoint ex : getStaticEndpoints()) {
				existing.put(ex.path, ex);
			}
			for (StaticEndpoint ep : nw) {
				if (existing.containsKey(ep.path)) {
					if (!existing.get(ep.path).contents.equals(ep.contents)) {
						update(existing.get(ep.path).ID, ep.contents);
					}
					existing.remove(ep.path);
				} else {
					create(ep.path, ep.contents);
				}
			}
			for (Entry<String, StoredStaticEndpoint> entry : existing.entrySet()) {
				delete(entry.getValue().ID);
			}
		} finally {
			clearCache();
		}
	}

	public static final class StoredStaticEndpoint extends StaticEndpoint {
		private final int ID;

		private StoredStaticEndpoint(int ID, String path, String contents) {
			super(path, contents);
			this.ID = ID;
		}
	}

	public static class StaticEndpoint {
		public final String path;
		public final String contents;

		public StaticEndpoint(String path, String contents) {
			this.path = path;
			this.contents = contents;
		}
	}

	public void addStaticEndpoint(String path, String contents) throws DatabaseException {
		try {
			for (StoredStaticEndpoint ep : getStaticEndpoints()) {
				if (ep.path.equals(path)) {
					update(ep.ID, contents);
					return;
				}
			}
			create(path, contents);
		} finally {
			clearCache();
		}
	}

	public List<StoredStaticEndpoint> getStaticEndpoints() {
		List<StoredStaticEndpoint> lst = new ArrayList<>();
		final String tableName = "static_endpoints";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "path"), getField(tableName, "ID"), getField(tableName, "contents") }, true);
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("path") && obj.containsString("contents") && obj.containsNumber("ID")) {
						String path = obj.getString("path").Value;
						String contents = obj.getString("contents").Value;
						int id = obj.getNumber("ID").Value;
						lst.add(new StoredStaticEndpoint(id, path, contents));
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;

	}
}
