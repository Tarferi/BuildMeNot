package cz.rion.buildserver.db.layers.staticDB;

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
		this.makeTable("files", KEY("ID"), TEXT("name"), BIGTEXT("contents"), NUMBER("deleted"));
	}

	private static final char[] hexData = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	public static String encodeFileContents(String data) {
		return encodeFileContents(data.getBytes(Settings.getDefaultCharset()));
	}

	public static String encodeFileContents(byte[] b) {
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

	public static String decodeFileContents(String data) {
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

	public FileInfo createFile(String name, String contents, boolean overwriteExisting) throws DatabaseException {
		final String tableName = "files";
		synchronized (fileTable) {
			try {
				boolean insertNew = true;
				if (overwriteExisting) {
					JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "name"), name), new ComparisionField(getField(tableName, "deleted"), 0));
					if (res.Value.size() == 1) { // Existing
						JsonValue val = res.Value.get(0);
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
								int id = obj.getNumber("ID").Value;
								insertNew = false;
								this.update(tableName, id, new ValuedField(this.getField(tableName, "contents"), encodeFileContents(contents)));
							}
						}
					}
				}
				if (insertNew) {
					this.insert(tableName, new ValuedField(this.getField(tableName, "name"), name), new ValuedField(this.getField(tableName, "deleted"), 0), new ValuedField(this.getField(tableName, "contents"), encodeFileContents(contents)));
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "contents") }, true);
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
						int id = obj.getNumber("ID").Value;
						String fname = obj.getString("name").Value;
						String c = decodeFileContents(obj.getString("contents").Value);
						return new FileInfo(id, fname, c);
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

	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		final String tableName = "files";
		JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "contents") }, true, new ComparisionField(getField(tableName, "ID"), fileID));
		if (res != null) {
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String contents = decodeFileContents(obj.getString("contents").Value);
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
			final String tableName = "files";
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "contents") }, true, new ComparisionField(getField(tableName, "deleted"), 0));
			if (res != null) {
				for (JsonValue val : res.Value) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name")) {
							int id = obj.getNumber("ID").Value;
							String name = obj.getString("name").Value;
							if (name.equals("tests/test09_05.json")) {
								continue;
							}
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

	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		try {
			final String tableName = "files";
			this.update(tableName, file.ID, new ValuedField(this.getField(tableName, "name"), newFileName), new ValuedField(this.getField(tableName, "contents"), encodeFileContents(newContents)));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public FileInfo loadFile(String name, boolean decodeBigString) {
		try {
			final String tableName = "files";
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "contents") }, true, new ComparisionField(getField(tableName, "name"), name));
			if (res != null) {
				if (!res.Value.isEmpty()) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("contents")) {
							int id = obj.getNumber("ID").Value;
							String fname = obj.getString("name").Value;
							String contents = decodeFileContents(obj.getString("contents").Value);
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
