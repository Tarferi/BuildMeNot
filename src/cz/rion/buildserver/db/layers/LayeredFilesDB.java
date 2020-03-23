package cz.rion.buildserver.db.layers;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public abstract class LayeredFilesDB extends LayeredStaticDB {

	private final Object fileTable = new Object();

	public LayeredFilesDB(String fileName) throws DatabaseException {
		super(fileName);
		this.makeTable("files", KEY("ID"), TEXT("name"), TEXT("contents"));
	}

	private final char[] hexData = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private String encode(String data) {
		byte[] b = data.getBytes(Settings.getDefaultCharset());
		byte[] n = new byte[b.length * 2];
		for (int i = 0; i < b.length; i++) {
			int c = b[i] & 0xff;
			int c1 = (c >> 4) & 0b1111;
			int c2 = c & 0b1111;
			n[(i * 2)] = (byte) hexData[c1];
			n[(i * 2) + 1] = (byte) hexData[c2];
		}
		return new String(n, Settings.getDefaultCharset());
	}

	private static int fromHex(int i) {
		if (i >= '0' && i <= '9') {
			return i - '0';
		} else {
			return i - 'A' + 10;
		}
	}

	private String decode(String data) {
		byte[] b = data.getBytes(Settings.getDefaultCharset());
		byte[] n = new byte[b.length / 2];
		for (int i = 0; i < n.length; i++) {
			int c1 = b[(i * 2)] & 0xff;
			int c2 = b[(i * 2) + 1] & 0xff;
			c1 = fromHex(c1);
			c2 = fromHex(c2);
			int c = (c1 << 4) | c2;
			n[i] = (byte) c;
		}
		return new String(n, Settings.getDefaultCharset());
	}

	public FileInfo createFile(String name, String contents) throws DatabaseException {
		synchronized (fileTable) {
			try {
				this.execute("INSERT INTO files (name, contents) VALUES ('?', '?')", name, encode(contents));
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
			JsonArray res = select("SELECT ID, name, contents FROM files ORDER BY ID Desc LIMIT 1").getJSON();
			if (res != null) {
				if (res.Value.size() == 1) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
							int id = obj.getNumber("ID").Value;
							String fname = obj.getString("name").Value;
							String c = decode(obj.getString("contents").Value);
							return new FileInfo(id, fname, c);
						}
					}
				}
			}
		}
		return null;
	}

	public static class DatabaseFile {
		public final int ID;
		public final String FileName;

		public DatabaseFile(int id, String fileName) {
			this.ID = id;
			this.FileName = fileName;
		}

		@Override
		public String toString() {
			return "[" + ID + "] " + FileName;
		}
	}

	public FileInfo getFile(String fileName) throws DatabaseException {
		JsonArray res = select("SELECT ID, name, contents FROM files WHERE name = '?'", fileName).getJSON();
		if (res != null) {
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String contents = decode(obj.getString("contents").Value);
						return new FileInfo(id, name, contents);
					}
				}
			}
		}
		return null;
	}

	public FileInfo getFile(int fileID) throws DatabaseException {
		JsonArray res = select("SELECT ID, name, contents FROM files WHERE ID = ?", fileID).getJSON();
		if (res != null) {
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String contents = decode(obj.getString("contents").Value);
						return new FileInfo(id, name, contents);
					}
				}
			}
		}
		return null;
	}

	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> result = new ArrayList<>();
		try {
			JsonArray res = select("SELECT ID, name FROM files").getJSON();
			if (res != null) {
				for (JsonValue val : res.Value) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name")) {
							int id = obj.getNumber("ID").Value;
							String name = obj.getString("name").Value;
							result.add(new DatabaseFile(id, name));
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return result;
	}

	public final void storeFile(DatabaseFile file, String newContents) {
		try {
			this.execute("UPDATE files SET name = '?', contents = '?' WHERE ID = ?", file.FileName, encode(newContents), file.ID);
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public final FileInfo loadFile(String name) {
		try {
			JsonArray res = this.select("SELECT * FROM files WHERE name = '?'", name).getJSON();
			if (res != null) {
				if (!res.Value.isEmpty()) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
							int id = obj.getNumber("ID").Value;
							String fname = obj.getString("name").Value;
							String contents = decode(obj.getString("contents").Value);
							return new FileInfo(id, fname, contents);
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

}
