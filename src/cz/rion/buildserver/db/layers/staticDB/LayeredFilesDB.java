package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.VirtualFileManager.ReadVirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualDatabaseFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualDatabaseFile.DatabaseFileManipulator;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager;

public abstract class LayeredFilesDB extends LayeredStaticDB {

	private final Object fileTable = new Object();

	private String[] getCompressedDataStreams(String newName, String contents) {
		if (newName.endsWith(".js")) {
			Compiler compiler = new Compiler();
			CompilerOptions options = new CompilerOptions();
			CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
			compiler.compile(SourceFile.fromCode("a.js", ""), SourceFile.fromCode("index.js", contents), options);
			String compressed = compiler.toSource();
			return new String[] { contents, compressed };
		} else {
			return new String[] { contents };
		}
	}

	final Map<String, Toolchain> loadedToolchains = new HashMap<>();

	public void reloadFiles() {
		synchronized (loadedToolchains) {
			for (Entry<String, Toolchain> entry : loadedToolchains.entrySet()) {
				Toolchain tc = entry.getValue();
				uninitFiles(tc);
				initFiles(tc);
			}
		}
	}

	private final DatabaseFileManipulator manipulator = new DatabaseFileManipulator() {

		@Override
		public String read(int fileID, UserContext context) throws DatabaseException {
			synchronized (fileTable) {
				final String tableName = "files";
				JsonArray res = select(tableName, new TableField[] { getField(tableName, "contents") }, true, new ComparisionField(getField(tableName, "ID"), fileID));
				if (res != null) {
					for (JsonValue val : res.Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsString("contents")) {
								String[] streams = decodeFileContents(obj.getString("contents").Value, true);
								if (streams.length > 0) {
									if (context.wantCompressedData() && streams.length > 1) {
										return streams[1];
									}
									return streams[0];
								}
							}
						}
					}
				}
			}
			return null;
		}

		@Override
		public boolean write(VirtualDatabaseFile file, String newName, String newContents, UserContext context) throws DatabaseException {
			if (file.Name.endsWith(".view") != newName.endsWith(".view")) {
				return false;
			} else if (newName.endsWith(".table")) {
				return false;
			} else if (newName.endsWith(".view") && !context.getToolchain().IsRoot) {
				return false;
			}
			synchronized (fileTable) {
				StaticDB sdb = (StaticDB) LayeredFilesDB.this;
				TestManager tests = sdb.getTestManager();
				if (tests != null) {
					tests.reloadTests();
				}
				final String tableName = "files";
				return update(tableName, file.ID, new ValuedField(getField(tableName, "name"), newName), new ValuedField(getField(tableName, "contents"), encodeFileContents(getCompressedDataStreams(newName, newContents), true)));
			}
		}

	};

	private VirtualFileManager files;

	@Override
	public void afterInit() {
		synchronized (loadedToolchains) {
			initFiles(this.getRootToolchain());
			initFiles(this.getSharedToolchain());
			loadedToolchains.put(this.getRootToolchain().getName(), this.getRootToolchain());
			loadedToolchains.put(this.getSharedToolchain().getName(), this.getSharedToolchain());

			this.registerToolchainListener(new ToolchainCallback() {

				@Override
				public void toolchainAdded(Toolchain t) {
					if (!loadedToolchains.containsKey(t.getName())) {
						loadedToolchains.put(t.getName(), t);
						initFiles(t);
					}
				}

				@Override
				public void toolchainRemoved(Toolchain t) {
					if (loadedToolchains.containsKey(t.getName())) {
						loadedToolchains.remove(t.getName());
						uninitFiles(t);
					}
				}

			});
		}
	}

	private void convertFiles() throws DatabaseException {

		final String tableName = "files";
		JsonArray res = select(tableName, new TableField[] { getField(tableName, "contents"), getField(tableName, "name"), getField(tableName, "ID") }, true);
		if (res != null) {
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("contents") && obj.containsNumber("ID") && obj.containsString("name")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String[] streams = decodeFileContents(obj.getString("contents").Value, false);
						if (streams.length != 1) {
							throw new DatabaseException("Invalid streams?");
						}

						if (update(tableName, id, new ValuedField(getField(tableName, "contents"), encodeFileContents(getCompressedDataStreams(name, streams[0]), true)))) {
							continue;
						}
					}
				}
				throw new DatabaseException("Unknown");
			}
		}

	}

	public LayeredFilesDB(DatabaseInitData fileData) throws DatabaseException {
		super(fileData);
		this.makeTable("files", false, KEY("ID"), TEXT("name"), BIGTEXT("contents"), NUMBER("deleted"), TEXT("toolchain"));
		this.files = fileData.Files;
		// Convert files
		// convertFiles();
		// throw new DatabaseException("End");
	}

	private final UserContext rootContext = new UserContext() {

		@Override
		public Toolchain getToolchain() {
			return getRootToolchain();
		}

		@Override
		public String getLogin() {
			return "root";
		}

		@Override
		public String getAddress() {
			return "0.0.0.0";
		}

		@Override
		public boolean wantCompressedData() {
			return false;
		}

	};

	private void uninitFiles(Toolchain tc) {
		List<VirtualFile> lst = new ArrayList<>();
		files.getFiles(lst, rootContext);
		List<VirtualDatabaseFile> toUnregister = new ArrayList<>();
		for (VirtualFile file : lst) {
			if (file instanceof VirtualDatabaseFile && file.Toolchain.getName().equals(tc.getName())) {
				toUnregister.add((VirtualDatabaseFile) file);
			}
		}
		for (VirtualDatabaseFile file : toUnregister) {
			files.unregisterVirtualFile(file);
		}
	}

	private void initFiles(Toolchain tc) {
		synchronized (fileTable) {
			try {
				final String tableName = "files";

				JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "toolchain") }, true, new ComparisionField(getField(tableName, "deleted"), 0), new ComparisionField(getField(tableName, "toolchain"), tc.getName()));
				if (res != null) {
					for (JsonValue val : res.Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("toolchain")) {
								int id = obj.getNumber("ID").Value;
								String fname = obj.getString("name").Value;
								VirtualFile vf;
								if (fname.endsWith(".view")) {
									LayeredDBFileWrapperDB instance = (LayeredDBFileWrapperDB) LayeredFilesDB.this;
									vf = instance.new VirtualViewFile(id, fname, tc, manipulator);
								} else {
									vf = new VirtualDatabaseFile(id, fname, tc, manipulator);
								}
								files.registerVirtualFile(vf);
							}
						}
					}
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
	}

	private static final char[] hexData = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private static String encodeFileContents(String[] data, boolean containStreams) throws DatabaseException {
		byte[] result;
		if (containStreams) {
			int totalSize = 8 + (data.length * 8);
			byte[][] bs = new byte[data.length][];
			for (int i = 0; i < data.length; i++) {
				byte[] b = data[i].getBytes(Settings.getDefaultCharset());
				int chunkSize = b.length * 2;
				totalSize += chunkSize;
				bs[i] = b;
			}

			result = new byte[totalSize];
			int lastPosition = 0;
			setInt(result, lastPosition, data.length);
			lastPosition += 8;
			for (int chunkIndex = 0; chunkIndex < data.length; chunkIndex++) {
				byte[] b = bs[chunkIndex];
				setInt(result, lastPosition, b.length * 2);
				lastPosition += 8;
				for (int i = 0; i < b.length; i++) {
					int c = b[i] & 0xff;
					int c1 = (c >> 4) & 0b1111;
					int c2 = c & 0b1111;
					result[lastPosition + (i * 2)] = (byte) hexData[c1];
					result[lastPosition + (i * 2) + 1] = (byte) hexData[c2];
				}
				lastPosition += b.length * 2;
			}
		} else {
			byte[] b = data[0].getBytes(Settings.getDefaultCharset());
			result = new byte[b.length * 2];
			for (int i = 0; i < b.length; i++) {
				int c = b[i] & 0xff;
				int c1 = (c >> 4) & 0b1111;
				int c2 = c & 0b1111;
				result[(i * 2)] = (byte) hexData[c1];
				result[(i * 2) + 1] = (byte) hexData[c2];
			}
		}
		String res = new String(result, Settings.getDefaultCharset());
		{
			String[] resDecoded = decodeFileContents(res, true);
			if (resDecoded.length != data.length) {
				throw new DatabaseException("Invalid coding");
			}
			for (int i = 0; i < resDecoded.length; i++) {
				if (!resDecoded[i].equals(data[i])) {
					throw new DatabaseException("Invalid coding");
				}
			}
		}

		return res;
	}

	private static int fromHex(int i) {
		if (i >= '0' && i <= '9') {
			return i - '0';
		} else {
			return i - 'A' + 10;
		}
	}

	private static int getInt(byte[] data, int position) {
		int x1 = (fromHex(data[position + 0]) << 4) | (fromHex(data[position + 1]));
		int x2 = (fromHex(data[position + 2]) << 4) | (fromHex(data[position + 3]));
		int x3 = (fromHex(data[position + 4]) << 4) | (fromHex(data[position + 5]));
		int x4 = (fromHex(data[position + 6]) << 4) | (fromHex(data[position + 7]));
		return (x1 << 24) | (x2 << 16) | (x3 << 8) | x4;
	}

	private static void setInt(byte[] data, int position, int value) {
		int x1 = ((value >> 24) & 0xff);
		int x2 = ((value >> 16) & 0xff);
		int x3 = ((value >> 8) & 0xff);
		int x4 = (value & 0xff);
		data[position + 0] = (byte) hexData[(x1 >> 4) & 0xf];
		data[position + 1] = (byte) hexData[(x1) & 0xf];
		data[position + 2] = (byte) hexData[(x2 >> 4) & 0xf];
		data[position + 3] = (byte) hexData[(x2) & 0xf];
		data[position + 4] = (byte) hexData[(x3 >> 4) & 0xf];
		data[position + 5] = (byte) hexData[(x3) & 0xf];
		data[position + 6] = (byte) hexData[(x4 >> 4) & 0xf];
		data[position + 7] = (byte) hexData[(x4) & 0xf];
	}

	private static String[] decodeFileContents(String data, boolean containsStreams) {
		byte[] b = data.getBytes(Settings.getDefaultCharset());
		if (containsStreams) {
			int lastPosition = 0;
			int totalChunks = getInt(b, 0);
			lastPosition += 8;
			String[] result = new String[totalChunks];
			for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
				int chunkSize = getInt(b, lastPosition);
				lastPosition += 8;
				byte[] n = new byte[chunkSize / 2];
				for (int i = 0; i < n.length; i++) {
					int c1 = b[lastPosition + (i * 2)] & 0xff;
					int c2 = b[lastPosition + (i * 2) + 1] & 0xff;
					c1 = fromHex(c1);
					c2 = fromHex(c2);
					int c = (c1 << 4) | c2;
					n[i] = (byte) c;
				}
				lastPosition += chunkSize;
				String chunk = new String(n, Settings.getDefaultCharset());
				result[chunkIndex] = chunk;
			}
			return result;
		} else {
			byte[] n = new byte[b.length / 2];
			for (int i = 0; i < n.length; i++) {
				int c1 = b[(i * 2)] & 0xff;
				int c2 = b[(i * 2) + 1] & 0xff;
				c1 = fromHex(c1);
				c2 = fromHex(c2);
				int c = (c1 << 4) | c2;
				n[i] = (byte) c;
			}
			String chunk = new String(n, Settings.getDefaultCharset());
			return new String[] { chunk };
		}
	}

	@SuppressWarnings("deprecation")
	public VirtualDatabaseFile createFile(UserContext context, String name, String contents) throws DatabaseException {
		final Toolchain toolchain = context.getToolchain();
		synchronized (fileTable) {
			final String tableName = "files";
			JsonArray pres;
			try {
				pres = select_raw("SELECT ID FROM files WHERE name=? ORDER BY ID DESC LIMIT 1", name).getJSON(false, new TableField[] { getField(tableName, "ID") }, toolchain);
			} catch (CompressionException e) {
				throw new DatabaseException("Nepodaøilo se rozkódovat název souboru", e);
			}
			int prevID = -1;
			if (!pres.Value.isEmpty()) {
				JsonValue val = pres.Value.get(0);
				if (val.isObject()) {
					if (val.asObject().containsNumber("ID")) {
						prevID = val.asObject().getNumber("ID").Value;
					}
				}
			}

			if (insert(tableName, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "contents"), encodeFileContents(getCompressedDataStreams(name, contents), true)))) {
				JsonArray res;
				try {
					res = select_raw("SELECT ID FROM files WHERE name=? ORDER BY ID DESC LIMIT 1", name).getJSON(false, new TableField[] { getField(tableName, "ID") }, toolchain);
				} catch (CompressionException e) {
					throw new DatabaseException("Nepodaøilo se rozkódovat název souboru", e);
				}
				int nextID = -1;
				if (!res.Value.isEmpty()) {
					JsonValue val = res.Value.get(0);
					if (val.isObject()) {
						if (val.asObject().containsNumber("ID")) {
							nextID = val.asObject().getNumber("ID").Value;
						}
					}
				}
				if (nextID != prevID && nextID != -1) {
					VirtualDatabaseFile vf = new VirtualDatabaseFile(nextID, name, toolchain, manipulator);
					files.registerVirtualFile(vf);
					return vf;
				}
			}
		}
		throw new DatabaseException("Failed to save new file");
	}

	public ReadVirtualFile loadRootFile(int ID) {
		VirtualFile file = this.files.getFile(ID, rootContext);
		if (file == null) {
			return null;
		}
		return file.getRead(rootContext);
	}

	public ReadVirtualFile loadRootFile(String name) {
		return loadRootFile(name, null, null);
	}

	public ReadVirtualFile loadRootFile(String name, UserContext contextForFindingFile, UserContext contextForReadingFile) {
		List<VirtualFile> file = this.files.getFile(name, contextForFindingFile == null ? rootContext : contextForFindingFile);
		if (file.isEmpty()) {
			return null;
		}
		return file.get(0).getRead(contextForReadingFile == null ? rootContext : contextForReadingFile);
	}
}
