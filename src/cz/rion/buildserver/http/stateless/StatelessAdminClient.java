package cz.rion.buildserver.http.stateless;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
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

	private Toolchain getToolchain(ProcessState state) {
		if (Settings.getRootUser().equals(state.getPermissions().Login)) {
			return state.Data.StaticDB.getRootToolchain();
		} else {
			return state.Toolchain;
		}
	}

	private JsonValue collectFiles(ProcessState state) {
		Toolchain toolchain = getToolchain(state);
		JsonArray arr = new JsonArray();

		List<DatabaseFile> lst = state.Data.StaticDB.getFiles(toolchain);
		LayeredDBFileWrapperDB.loadDatabaseFiles(state.Data.RuntimeDB, lst, toolchain);

		for (DatabaseFile f : lst) {
			if (f.ToolchainName.equals(toolchain.getName()) || toolchain.IsRoot || f.ToolchainName.equals("shared")) {
				String name;
				if (toolchain.IsRoot) {
					if (!f.ToolchainName.equals(toolchain.getName()) && !f.ToolchainName.equals("shared")) {
						name = "data/" + f.ToolchainName + "/" + f.FileName;
					} else {
						name = f.FileName;
					}
				} else if (f.ToolchainName.equals(toolchain.getName()) || f.ToolchainName.equals("shared")) {
					name = f.FileName;
				} else {
					continue;
				}
				JsonObject obj = new JsonObject();
				obj.add("name", new JsonString(name));
				obj.add("ID", new JsonNumber(f.ID));
				arr.add(obj);
			}
		}
		return arr;
	}

	private FileInfo getFile(ProcessState state, int fileID) {
		Toolchain toolchain = getToolchain(state);
		FileInfo fo = null;
		try {
			fo = state.Data.StaticDB.getFile(fileID, true, toolchain);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		if (fo == null) {
			try {
				fo = LayeredDBFileWrapperDB.getFile(state.Data.RuntimeDB, fileID, true, toolchain);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		if (fo.ToolchainName.equals(toolchain.getName()) || toolchain.IsRoot || fo.ToolchainName.equals("shared")) {
			return fo;
		}
		return null;
	}

	private JsonValue loadFile(ProcessState state, int fileID, boolean log) {
		Toolchain toolchain = getToolchain(state);
		FileInfo fo = LayeredDBFileWrapperDB.processPostLoadedFile(state.Data.RuntimeDB, LayeredDBFileWrapperDB.processPostLoadedFile(state.Data.StaticDB, getFile(state, fileID), true, toolchain), true, toolchain);
		JsonObject obj = new JsonObject();
		if (fo == null) {
			if (log) {
				state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID);
			}
			obj.add("fo", new JsonNumber(1));
			return obj;
		} else {
			if (fo.ToolchainName.equals(toolchain.getName()) || toolchain.IsRoot || fo.ToolchainName.equals("shared")) {
				obj.add("fo", new JsonNumber(0));
				obj.add("name", new JsonString(fo.FileName));
				obj.add("ID", new JsonNumber(fo.ID));
				obj.add("contents", new JsonString(new String(fo.Contents.getBytes(Settings.getDefaultCharset()), StandardCharsets.UTF_8)));
				if (log) {
					state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID + ":" + fo.FileName);
				}
				return obj;
			} else {
				obj.add("fo", new JsonNumber(1));
				return obj;
			}
		}
	}

	protected JsonObject handleAdminEvent(ProcessState state, JsonObject obj) {
		JsonObject result = new JsonObject();
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid admin command"));
		StaticDB sdb = state.Data.StaticDB;

		JsonObject idata = new JsonObject();
		idata.add("invalid", true);
		if (obj.containsObject("admin_data")) {
			JsonObject data = obj.getObject("admin_data");
			if (data.containsString("action")) {
				String action = data.getString("action").Value;
				if (action.equals("getFiles")) {
					idata.add("action", action);
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectFiles(state).getJsonString()));
					sdb.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "getFiles", data.getJsonString());
				} else if (action.equals("load") && data.containsNumber("fileID")) {
					idata.add("action", action);
					idata.add("fileID", data.getNumber("fileID").Value);
					int fileID = data.getNumber("fileID").Value;
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(loadFile(state, fileID, true).getJsonString()));
				} else if ((action.equals("save") || action.equals("tableEdit")) && data.containsNumber("fileID") && data.containsString("contents")) {
					int fileID = data.getNumber("fileID").Value;
					String newContents = data.getString("contents").Value;
					boolean isSavingRawFile = action.equals("save");
					boolean isEditingRow = action.equals("tableEdit");
					idata.add("action", action);
					idata.add("fileID", fileID);
					idata.add("contents", newContents);
					StringBuilder log = new StringBuilder();
					if (isSavingRawFile) { // Log handled internally
						if (this.saveFile(state, fileID, newContents, log)) {
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File saved"));
						} else {
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString(log.length() == 0 ? "Failed to save file" : log.toString()));
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
					idata.add("action", action);
					idata.add("name", fileName);
					if (!fileName.isEmpty()) {
						try {
							sdb.createFile(state.Toolchain, fileName, "", false);
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File created"));
							sdb.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "create", "create:0:" + fileName);
						} catch (DatabaseException e) {
							e.printStackTrace();
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to create new file: " + fileName));
							sdb.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "create", "create:1:" + fileName);
						}
					} else {
						idata.add("action", action);
						result.add("code", new JsonNumber(1));
						result.add("result", new JsonString("Cannot create new file: " + fileName));
						sdb.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "create", "create:1:" + fileName);
					}
				}
			}
		}
		state.setIntention(Intention.ADMIN_COMMAND, idata);
		return result;
	}

	private boolean saveRow(ProcessState state, int fileID, String jsn) { // Logs itself
		Toolchain toolchain = getToolchain(state);
		JsonValue val = JsonValue.parse(jsn);
		final String login = state.getPermissions().Login;
		final String address = state.Request.remoteAddress;
		if (val != null) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("toolchain") && !toolchain.IsRoot) { // Only root can change toolchain
					return false;
				}
				FileInfo f;
				try {
					f = state.Data.StaticDB.getFile(fileID, false, toolchain);
					if (f != null) { // SDB database
						state.Data.StaticDB.isRootOnly(f.FileName);
						String tn = LayeredDBFileWrapperDB.getTableNameForFile(state.Data.StaticDB, f);
						if (tn != null) {
							if ((state.Data.StaticDB.isRootOnly(tn) && toolchain.IsRoot) || !state.Data.StaticDB.isRootOnly(tn)) {
								if (LayeredDBFileWrapperDB.editRow(state.Data.StaticDB, login, address, state.Data.StaticDB, f, obj, state.Toolchain)) {
									return true;
								}
							}
						}
					} else { // DB database ?
						f = LayeredDBFileWrapperDB.getFile(state.Data.RuntimeDB, fileID, false, toolchain);
						if (f != null) {

							String tn = LayeredDBFileWrapperDB.getTableNameForFile(state.Data.RuntimeDB, f);
							if (tn != null) {
								if (state.Data.RuntimeDB.isRootOnly(tn) && toolchain.IsRoot || !state.Data.RuntimeDB.isRootOnly(tn)) {
									if (LayeredDBFileWrapperDB.editRow(state.Data.StaticDB, login, address, state.Data.RuntimeDB, f, obj, state.Toolchain)) {
										return true;
									}
								}
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

	private boolean saveFile(ProcessState state, int fileID, String newContents, StringBuilder log) {
		Toolchain toolchain = getToolchain(state);
		List<DatabaseFile> lst = state.Data.StaticDB.getFiles(toolchain);
		for (DatabaseFile f : lst) {
			if (f.ID == fileID) {
				FileInfo fo = null;
				try {
					fo = state.Data.StaticDB.getFile(fileID, true, toolchain);
				} catch (DatabaseException e) {
					log.append("Chyba databáze: " + e.toString());
					e.printStackTrace();
					return false;
				}
				if (fo != null) {
					if (fo.ToolchainName.equals(toolchain.getName()) || toolchain.IsRoot || fo.ToolchainName.equals("shared")) {
						if (!fo.FileName.endsWith(LayeredDBFileWrapperDB.viewFileSuffix) || toolchain.IsRoot) { // Only root can edit views
							JsonObject obj = new JsonObject();
							obj.add("original", new JsonString(fo.Contents));
							obj.add("new", new JsonString(newContents));
							state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "saveFile:" + fileID + ":" + fo.FileName, obj.getJsonString());
							state.Data.StaticDB.storeFile(f, f.FileName, newContents);
							return true;
						} else {
							log.append("Pouze root mohou upravovat pohledy");
							return false;
						}
					}
				} else {
					log.append("Zadaný soubor nelze uložit, protože se ho nepodařilo načíst");
					state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "saveFile:" + fileID, "saveFile:" + fileID);
					return false;
				}
			}
		}
		log.append("Zadaný soubor nelze uložit, protože neexistuje");
		return false;
	}

}
