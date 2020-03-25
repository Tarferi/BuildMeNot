package cz.rion.buildserver.db.layers;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public abstract class LayeredTestDB extends LayeredDBFileWrapperDB {

	public LayeredTestDB(String dbName) throws DatabaseException {
		super(dbName);
	}

	public List<String> getAllowedTests() {
		List<String> allowed = new ArrayList<>();
		JsonArray res;
		try {
			res = this.select("SELECT * FROM files WHERE name = '?'", "tests/allowed.cfg").getJSON();
			if (res.Value.size() == 1) { // No such user, create
				// Have result with ID
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("contents")) {
						String contents = decodeFileContents(obj.getString("contents").Value);
						if (contents != null) {
							String[] cnt = contents.split("\n");
							for (String c : cnt) {
								allowed.add(c.trim());
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return allowed;
	}
}
