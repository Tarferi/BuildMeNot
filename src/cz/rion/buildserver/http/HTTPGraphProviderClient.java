package cz.rion.buildserver.http;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class HTTPGraphProviderClient extends HTTPFileProviderClient {

	private final StaticDB sdb;
	private final RuntimeDB db;

	protected HTTPGraphProviderClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		super(client, BuilderID, rdb, sdb);
		this.sdb = sdb;
		this.db = rdb;
	}

	private FileInfo getFile(int fileID) {
		FileInfo fo = null;
		try {
			fo = sdb.getFile(fileID);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		if (fo == null) {
			try {
				fo = LayeredDBFileWrapperDB.getFile(db, fileID);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		return fo;
	}

	private FileInfo loadView(Map<String, Integer> fileIds, String view) {
		if (fileIds.containsKey(view)) {
			int fileID = fileIds.get(view);
			FileInfo fo = LayeredDBFileWrapperDB.processPostLoadedFile(db, LayeredDBFileWrapperDB.processPostLoadedFile(sdb, getFile(fileID)));
			return fo;
		}
		return null;
	}

	private static JsonValue __cache = null;
	private static long cacheCreation = 0;

	protected JsonValue loadGraphs() {
		long now = new Date().getTime();
		long diff = now - cacheCreation;
		if (diff < 1000 * 60) {
			return __cache;
		}
		// Preload database files
		List<DatabaseFile> files = sdb.getFiles();
		LayeredDBFileWrapperDB.loadDatabaseFiles(db, files);
		Map<String, Integer> fileIds = new HashMap<>();
		for (DatabaseFile file : files) {
			fileIds.put(file.FileName, file.ID);
		}

		// Load tests
		FileInfo src = sdb.loadFile("graphs.cfg");

		if (src != null) {
			JsonValue val = JsonValue.parse(src.Contents);
			if (val != null) {
				if (val.isArray()) {
					JsonArray result = new JsonArray(new ArrayList<JsonValue>());
					for (JsonValue v : val.asArray().Value) {
						try {
							if (!v.isObject()) {
								continue;
							}
							JsonObject obj = v.asObject();
							String view = obj.getString("View").Value;
							String x = obj.getString("X").Value;
							JsonArray y = obj.getArray("Y");

							JsonObject res = new JsonObject();

							FileInfo loadedView = loadView(fileIds, view);
							if (loadedView == null) {
								continue;
							}
							JsonValue viewData = JsonValue.parse(loadedView.Contents);
							if (viewData == null) {
								continue;
							}
							JsonObject dataColumns = new JsonObject();

							if (!viewData.isObject()) {
								continue;
							}
							JsonObject vobj = viewData.asObject();
							if (!vobj.containsArray("result")) {
								continue;
							}
							List<JsonValue> vres = vobj.getArray("result").Value;

							JsonArray xData = new JsonArray(new ArrayList<JsonValue>());
							JsonArray yData = new JsonArray(new ArrayList<JsonValue>());

							String[] yNames = new String[y.Value.size()];
							JsonArray[] yVals = new JsonArray[y.Value.size()];
							int index = 0;
							for (JsonValue vy : y.Value) {
								yNames[index] = vy.asObject().getString("Column").Value;
								yVals[index] = new JsonArray(new ArrayList<JsonValue>());
								index++;
							}

							// Load values for all columns
							for (JsonValue vd : vres) {
								JsonValue xVal = vd.asObject().get(x);
								for (int i = 0; i < yNames.length; i++) {
									JsonValue yVal = vd.asObject().get(yNames[i]);
									yVals[i].add(yVal);
								}
								xData.add(xVal);
							}
							dataColumns.add("x", xData);

							for (int i = 0; i < yNames.length; i++) {
								JsonObject yObj = y.Value.get(i).asObject();
								yObj.add("data", yVals[i]);
								yData.add(yObj);
							}

							dataColumns.add("y", yData);
							res.add("Name", obj.getString("Name"));
							res.add("X-label", obj.getString("X-label"));
							res.add("Y-label", obj.getString("Y-label"));
							if (obj.contains("Options")) {
								res.add("Options", obj.get("Options"));
							}
							if (obj.contains("LibOptions")) {
								res.add("LibOptions", obj.get("LibOptions"));
							}
							res.add("Data", dataColumns);

							result.add(res);
						} catch (Throwable e) {
							e.printStackTrace();
							continue;
						}
					}
					__cache = result;
					cacheCreation = now;
					return result;
				}
			}
		}
		return null;
	}

	@Override
	protected HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		if (request.path.equals("/graph") && request.method.equals("GET")) {
			int returnCode = 200;
			String type = "text/json;";
			String returnCodeDescription = "OK";
			JsonValue graphs = loadGraphs();
			String data = graphs == null ? "[]" : graphs.getJsonString();
			this.setIntention(HTTPClientIntentType.GET_RESOURCE);
			return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type, request.cookiesLines);
		}

		return super.handle(request);
	}
}
