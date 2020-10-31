package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile.VirtualFileException;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;

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
		UserContext context = state.getContext();
		Toolchain toolchain = context.getToolchain();
		JsonArray arr = new JsonArray();

		List<VirtualFile> lst = new ArrayList<>();
		state.Data.Files.getFiles(lst, context);

		for (VirtualFile f : lst) {
			if (f.Toolchain.equals(toolchain) || toolchain.IsRoot || f.Toolchain.IsShared) {
				String name;
				if (toolchain.IsRoot) {
					if (!f.Toolchain.equals(toolchain) && !f.Toolchain.IsShared) {
						name = "data/" + f.Toolchain + "/" + f.Name;
					} else {
						name = f.Name;
					}
				} else if (f.Toolchain.equals(toolchain) || f.Toolchain.IsShared) {
					name = f.Name;
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

	private VirtualFile getFile(ProcessState state, int fileID) {
		UserContext context = state.getContext();
		VirtualFile fo = state.Data.Files.getFile(fileID, context);
		return fo;
	}

	private JsonValue loadFile(ProcessState state, int fileID, boolean log) {
		JsonObject obj = new JsonObject();
		Toolchain toolchain = state.getContext().getToolchain();
		VirtualFile rawFile = getFile(state, fileID);
		if (rawFile != null) {
			if (rawFile.Toolchain.IsShared || (rawFile.Toolchain.equals(toolchain) || toolchain.IsRoot)) {
				String contents;
				try {
					contents = rawFile.read(state.getContext());
					if (contents != null) {
						obj.add("fo", new JsonNumber(0));
						obj.add("name", new JsonString(rawFile.Name));
						obj.add("ID", new JsonNumber(rawFile.ID));
						obj.add("contents", contents);
						if (log) {
							state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID + ":" + rawFile.Name);
						}
						return obj;
					}
				} catch (VirtualFileException e) {
					e.printStackTrace();
				}
			}
		}
		if (log) {
			state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, "load", "load:" + fileID);
		}
		obj.add("fo", new JsonNumber(1));
		return obj;
	}

	protected JsonObject handleAdminEvent(ProcessState state, JsonObject obj) {
		JsonObject result = new JsonObject();
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid admin command"));
		StaticDB sdb = state.Data.StaticDB;

		JsonObject idata = new JsonObject();
		idata.add("invalid", true);
		if (obj.containsObject("admin_data")) {
			state.setContextToolchain(getToolchain(state));
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
							sdb.createFile(state.getContext(), fileName, "");
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
		Toolchain toolchain = state.getContext().getToolchain();
		JsonValue val = JsonValue.parse(jsn);
		if (val != null) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("toolchain") && !toolchain.IsRoot) { // Only root can change toolchain
					return false;
				}
				VirtualFile f = state.Data.Files.getFile(fileID, state.getContext());
				if (f == null) {
					return false;
				}
				try {
					return f.write(state.getContext(), f.Name, jsn);
				} catch (VirtualFileException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	private boolean saveFile(ProcessState state, int fileID, String newContents, StringBuilder log) {
		VirtualFile f = state.Data.Files.getFile(fileID, state.getContext());
		if (f == null) {
			log.append("No such file exists");
			return false;
		}
		try {
			return f.write(state.getContext(), f.Name, newContents);
		} catch (VirtualFileException e) {
			e.printStackTrace();
			log.append(e.getMessage());
		}
		return false;
	}
}
