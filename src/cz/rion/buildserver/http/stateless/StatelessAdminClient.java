package cz.rion.buildserver.http.stateless;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class StatelessAdminClient extends StatelessPermissionClient {

	protected StatelessAdminClient(StatelessInitData data) {
		super(data);
	}

	private JsonValue collectFiles(ProcessState state) {
		JsonArray arr = new JsonArray(new ArrayList<JsonValue>());

		List<DatabaseFile> lst = state.Data.StaticDB.getFiles();
		LayeredDBFileWrapperDB.loadDatabaseFiles(state.Data.RuntimeDB, lst);

		for (DatabaseFile f : lst) {
			JsonObject obj = new JsonObject();
			obj.add("name", new JsonString(f.FileName));
			obj.add("ID", new JsonNumber(f.ID));
			arr.add(obj);
		}
		return arr;
	}

	private FileInfo getFile(ProcessState state, int fileID) {
		FileInfo fo = null;
		try {
			fo = state.Data.StaticDB.getFile(fileID, true, state.Toolchain);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		if (fo == null) {
			try {
				fo = LayeredDBFileWrapperDB.getFile(state.Data.RuntimeDB, fileID, true, state.Toolchain);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		return fo;
	}

	private JsonValue loadFile(ProcessState state, int fileID, boolean log) {
		FileInfo fo = LayeredDBFileWrapperDB.processPostLoadedFile(state.Data.RuntimeDB, LayeredDBFileWrapperDB.processPostLoadedFile(state.Data.StaticDB, getFile(state, fileID), true, state.Toolchain), true, state.Toolchain);
		JsonObject obj = new JsonObject();
		if (fo == null) {
			if (log) {
				state.Data.StaticDB.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID);
			}
			obj.add("fo", new JsonNumber(1));
			return obj;
		} else {
			obj.add("fo", new JsonNumber(0));
			obj.add("name", new JsonString(fo.FileName));
			obj.add("ID", new JsonNumber(fo.ID));
			obj.add("contents", new JsonString(new String(fo.Contents.getBytes(Settings.getDefaultCharset()), StandardCharsets.UTF_8)));
			if (log) {
				state.Data.StaticDB.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID + ":" + fo.FileName);
			}
			return obj;
		}
	}

	protected JsonObject handleAdminEvent(ProcessState state, JsonObject obj) {
		JsonObject result = new JsonObject();
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid admin command"));
		StaticDB sdb = state.Data.StaticDB;

		if (obj.containsObject("admin_data")) {
			JsonObject data = obj.getObject("admin_data");
			if (data.containsString("action")) {
				String action = data.getString("action").Value;
				if (action.equals("getFiles")) {
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectFiles(state).getJsonString()));
					sdb.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "getFiles", data.getJsonString());
				} else if (action.equals("load") && data.containsNumber("fileID")) {
					int fileID = data.getNumber("fileID").Value;
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(loadFile(state, fileID, true).getJsonString()));
				} else if ((action.equals("save") || action.equals("tableEdit")) && data.containsNumber("fileID") && data.containsString("contents")) {
					int fileID = data.getNumber("fileID").Value;
					String newContents = data.getString("contents").Value;
					boolean isSavingRawFile = action.equals("save");
					boolean isEditingRow = action.equals("tableEdit");

					if (isSavingRawFile) { // Log handled internally
						if (this.saveFile(state, fileID, newContents)) {
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File saved"));
						} else {
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to save file"));
						}
					} else if (isEditingRow) { // Log handled internally
						if (this.saveRow(state, fileID, newContents)) {
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("Row saved"));
						} else {
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to save file"));
						}
					}
				} else if (action.equals("create") && data.containsString("name")) {
					String fileName = data.getString("name").Value;
					if (!fileName.isEmpty()) {
						try {
							sdb.createFile(fileName, "", false);
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File created"));
							sdb.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "create", "create:0:" + fileName);
						} catch (DatabaseException e) {
							e.printStackTrace();
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to create new file: " + fileName));
							sdb.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "create", "create:1:" + fileName);
						}
					} else {
						result.add("code", new JsonNumber(1));
						result.add("result", new JsonString("Cannot create new file: " + fileName));
						sdb.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "create", "create:1:" + fileName);
					}
				}
			}
		}
		return result;
	}

	private boolean saveRow(ProcessState state, int fileID, String jsn) { // Logs itself
		JsonValue val = JsonValue.parse(jsn);
		final String login = state.getPermissions().Login;
		final String address = state.Request.remoteAddress;
		if (val != null) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				FileInfo f;
				try {
					f = state.Data.StaticDB.getFile(fileID, false, state.Toolchain);
					if (f != null) { // SDB database
						if (LayeredDBFileWrapperDB.editRow(state.Data.StaticDB, login, address, state.Data.StaticDB, f, obj)) {
							return true;
						}
					} else { // DB database ?
						f = LayeredDBFileWrapperDB.getFile(state.Data.RuntimeDB, fileID, false, state.Toolchain);
						if (f != null) {
							if (LayeredDBFileWrapperDB.editRow(state.Data.StaticDB, login, address, state.Data.RuntimeDB, f, obj)) {
								return true;
							}
						}
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	private boolean saveFile(ProcessState state, int fileID, String newContents) {
		List<DatabaseFile> lst = state.Data.StaticDB.getFiles();
		for (DatabaseFile f : lst) {
			if (f.ID == fileID) {
				FileInfo fo = null;
				try {
					fo = state.Data.StaticDB.getFile(fileID, true, state.Toolchain);
				} catch (DatabaseException e) {
					e.printStackTrace();
					return false;
				}
				if (fo != null) {
					JsonObject obj = new JsonObject();
					obj.add("original", new JsonString(fo.Contents));
					obj.add("new", new JsonString(newContents));
					state.Data.StaticDB.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "saveFile:" + fileID + ":" + fo.FileName, obj.getJsonString());

					state.Data.StaticDB.storeFile(f, f.FileName, newContents);
					return true;
				} else {
					state.Data.StaticDB.adminLog(state.Request.remoteAddress, state.getPermissions().Login, "saveFile:" + fileID, "saveFile:" + fileID);
				}
			}
		}
		return false;
	}

}
