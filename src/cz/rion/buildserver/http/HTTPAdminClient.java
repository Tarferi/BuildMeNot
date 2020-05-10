package cz.rion.buildserver.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredFilesDB.DatabaseFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class HTTPAdminClient extends HTTPParserClient {

	private final StaticDB sdb;
	private final RuntimeDB db;

	protected HTTPAdminClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		super(client, BuilderID, rdb, sdb);
		this.sdb = sdb;
		this.db = rdb;
	}

	private JsonValue collectFiles() {
		JsonArray arr = new JsonArray(new ArrayList<JsonValue>());

		List<DatabaseFile> lst = sdb.getFiles();
		LayeredDBFileWrapperDB.loadDatabaseFiles(db, lst);

		for (DatabaseFile f : lst) {
			JsonObject obj = new JsonObject();
			obj.add("name", new JsonString(f.FileName));
			obj.add("ID", new JsonNumber(f.ID));
			arr.add(obj);
		}
		return arr;
	}

	private FileInfo getFile(int fileID) {
		FileInfo fo = null;
		try {
			fo = sdb.getFile(fileID, true);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		if (fo == null) {
			try {
				fo = LayeredDBFileWrapperDB.getFile(db, fileID, true);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		return fo;
	}

	private JsonValue loadFile(int fileID, boolean log) {
		FileInfo fo = LayeredDBFileWrapperDB.processPostLoadedFile(db, LayeredDBFileWrapperDB.processPostLoadedFile(sdb, getFile(fileID), true), true);
		JsonObject obj = new JsonObject();
		if (fo == null) {
			if (log) {
				sdb.adminLog(getAddress(), getPermissions().Login, "load", "load:" + fileID);
			}
			obj.add("fo", new JsonNumber(1));
			return obj;
		} else {
			obj.add("fo", new JsonNumber(0));
			obj.add("name", new JsonString(fo.FileName));
			obj.add("ID", new JsonNumber(fo.ID));
			obj.add("contents", new JsonString(new String(fo.Contents.getBytes(Settings.getDefaultCharset()), StandardCharsets.UTF_8)));
			if (log) {
				sdb.adminLog(getAddress(), getPermissions().Login, "load", "load:" + fileID + ":" + fo.FileName);
			}
			return obj;
		}
	}

	private byte fromHex(char c) {
		byte b = (byte) c;
		if (b >= '0' && b <= '9') {
			return (byte) (b - '0');
		} else if (b >= 'A' && b <= 'F') {
			return (byte) (b + 10 - 'A');
		} else if (b >= 'a' && b <= 'f') {
			return (byte) (b + 10 - 'a');
		} else {
			return 0;
		}
	}

	private static final short[] js_codingOffsets = new short[] {
			228,
			196,
			225,
			193,
			269,
			268,
			271,
			270,
			235,
			203,
			233,
			201,
			283,
			282,
			237,
			205,
			239,
			207,
			314,
			313,
			328,
			327,
			246,
			214,
			243,
			211,
			345,
			344,
			341,
			340,
			353,
			352,
			357,
			356,
			252,
			220,
			250,
			218,
			367,
			366,
			253,
			221,
			255,
			376,
			382,
			381

	};

	private static final int[] java_codingOffsets = new int[] {
			50084,
			50052,
			50081,
			50049,
			50317,
			50316,
			50319,
			50318,
			50091,
			50059,
			50089,
			50057,
			50331,
			50330,
			50093,
			50061,
			50095,
			50063,
			50362,
			50361,
			50568,
			50567,
			50102,
			50070,
			50099,
			50067,
			50585,
			50584,
			50581,
			50580,
			50593,
			50592,
			50597,
			50596,
			50108,
			50076,
			50106,
			50074,
			50607,
			50606,
			50109,
			50077,
			50111,
			50616,
			50622,
			50621
	};

	private static final char[] codingTable = new char[] {
			'ä',
			'Ä',
			'á',
			'Á',
			'č',
			'Č',
			'ď',
			'Ď',
			'ë',
			'Ë',
			'é',
			'É',
			'ě',
			'Ě',
			'í',
			'Í',
			'ï',
			'Ï',
			'ĺ',
			'Ĺ',
			'ň',
			'Ň',
			'ö',
			'Ö',
			'ó',
			'Ó',
			'ř',
			'Ř',
			'ŕ',
			'Ŕ',
			'š',
			'Š',
			'ť',
			'Ť',
			'ü',
			'Ü',
			'ú',
			'Ú',
			'ů',
			'Ů',
			'ý',
			'Ý',
			'ÿ',
			'Ÿ',
			'ž',
			'Ž'
	};

	private String specialDec(String data) {
		byte[] result = new byte[data.length() / 2];
		for (int i = 0; i < result.length; i++) {
			char c1 = data.charAt(i * 2);
			char c2 = data.charAt((i * 2) + 1);
			byte b1 = fromHex(c1);
			byte b2 = fromHex(c2);
			byte b = (byte) ((b1 << 4) | b2);
			result[i] = b;
		}
		String utf = new String(result, StandardCharsets.UTF_8);
		return new String(utf.getBytes(StandardCharsets.UTF_8), Settings.getDefaultCharset());
	}

	protected void handleAdminEvent(JsonObject obj, JsonObject result) {
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid admin command"));

		if (obj.containsString("admin_data")) {
			String admin_data = obj.getString("admin_data").Value;
			if (admin_data.equals("getFiles")) {
				result.add("code", new JsonNumber(0));
				result.add("result", new JsonString(collectFiles().getJsonString()));
				sdb.adminLog(getAddress(), getPermissions().Login, "getFiles", admin_data);
			} else if (admin_data.startsWith("load:")) {
				int fileID;
				try {
					fileID = Integer.parseInt(admin_data.substring("load:".length()));
				} catch (Exception e) {
					return;
				}
				result.add("code", new JsonNumber(0));
				result.add("result", new JsonString(loadFile(fileID, true).getJsonString()));
			} else if (admin_data.startsWith("save:") || admin_data.startsWith("tableEdit")) {
				String[] parts = admin_data.split(":", 3);
				if (parts.length == 3) {
					int fileID;
					try {
						fileID = Integer.parseInt(parts[1]);
					} catch (Exception e) {
						return;
					}
					String newContents = specialDec(parts[2]);
					boolean isSavingRawFile = admin_data.startsWith("save:");
					boolean isEditingRow = admin_data.startsWith("tableEdit");

					if (isSavingRawFile) { // Log handled internally
						if (this.saveFile(fileID, newContents)) {
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File saved"));
						} else {
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to save file"));
						}
					} else if (isEditingRow) { // Log handled internally
						if (this.saveRow(fileID, newContents)) {
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("Row saved"));
						} else {
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to save file"));
						}
					}
				}
			} else if (admin_data.startsWith("create:")) {
				String[] parts = admin_data.split(":", 3);
				if (parts.length == 2) {
					String fileName = parts[1];
					if (!fileName.isEmpty()) {
						try {
							sdb.createFile(fileName, "");
							result.add("code", new JsonNumber(0));
							result.add("result", new JsonString("File created"));
							sdb.adminLog(getAddress(), getPermissions().Login, "create", "create:0:" + fileName);
						} catch (DatabaseException e) {
							e.printStackTrace();
							result.add("code", new JsonNumber(1));
							result.add("result", new JsonString("Failed to create new file: " + fileName));
							sdb.adminLog(getAddress(), getPermissions().Login, "create", "create:1:" + fileName);
						}
					} else {
						result.add("code", new JsonNumber(1));
						result.add("result", new JsonString("Cannot create new file: " + fileName));
						sdb.adminLog(getAddress(), getPermissions().Login, "create", "create:1:" + fileName);
					}
				}
			}
		}
		return;
	}

	private boolean saveRow(int fileID, String jsn) { // Logs itself
		JsonValue val = JsonValue.parse(jsn);
		final String login = getPermissions().Login;
		final String address = getAddress();
		if (val != null) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				FileInfo f;
				try {
					f = this.sdb.getFile(fileID, false);
					if (f != null) { // SDB database
						if (LayeredDBFileWrapperDB.editRow(sdb, login, address, sdb, f, obj)) {
							return true;
						}
					} else { // DB database ?
						f = LayeredDBFileWrapperDB.getFile(db, fileID, false);
						if (f != null) {
							if (LayeredDBFileWrapperDB.editRow(sdb, login, address, db, f, obj)) {
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

	private boolean saveFile(int fileID, String newContents) {
		List<DatabaseFile> lst = sdb.getFiles();
		for (DatabaseFile f : lst) {
			if (f.ID == fileID) {
				FileInfo fo = null;
				try {
					fo = sdb.getFile(fileID, true);
				} catch (DatabaseException e) {
					e.printStackTrace();
					return false;
				}
				if (fo != null) {
					JsonObject obj = new JsonObject();
					obj.add("original", new JsonString(fo.Contents));
					obj.add("new", new JsonString(newContents));
					sdb.adminLog(getAddress(), getPermissions().Login, "saveFile:" + fileID + ":" + fo.FileName, obj.getJsonString());

					sdb.storeFile(f, f.FileName, newContents);
					return true;
				} else {
					sdb.adminLog(getAddress(), getPermissions().Login, "saveFile:" + fileID, "saveFile:" + fileID);
				}
			}
		}
		return false;
	}
}
