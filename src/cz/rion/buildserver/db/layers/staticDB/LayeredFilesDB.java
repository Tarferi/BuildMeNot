package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
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

public abstract class LayeredFilesDB extends LayeredStaticDB {

	private final Object fileTable = new Object();

	private final DatabaseFileManipulator manipulator = new DatabaseFileManipulator() {

		@Override
		public String read(int fileID) throws DatabaseException {
			synchronized (fileTable) {
				final String tableName = "files";
				JsonArray res = select(tableName, new TableField[] { getField(tableName, "contents") }, true, new ComparisionField(getField(tableName, "ID"), fileID));
				if (res != null) {
					for (JsonValue val : res.Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsString("contents")) {
								return decodeFileContents(obj.getString("contents").Value);
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
			} else if(newName.endsWith(".view") && !context.getToolchain().IsRoot) {
				return false;
			}
			synchronized (fileTable) {
				final String tableName = "files";
				return update(tableName, file.ID, new ValuedField(getField(tableName, "name"), newName), new ValuedField(getField(tableName, "contents"), encodeFileContents(newContents)));
			}
		}

	};

	private VirtualFileManager files;

	@Override
	public void afterInit() {
		final Set<String> loadedToolchains = new HashSet<>();
		initFiles(this.getRootToolchain());
		initFiles(this.getSharedToolchain());
		loadedToolchains.add(this.getRootToolchain().getName());
		loadedToolchains.add(this.getSharedToolchain().getName());

		this.registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainAdded(Toolchain t) {
				if (!loadedToolchains.contains(t.getName())) {
					loadedToolchains.add(t.getName());
					initFiles(t);
				}
			}

			@Override
			public void toolchainRemoved(Toolchain t) {
				if (loadedToolchains.contains(t.getName())) {
					loadedToolchains.remove(t.getName());
					uninitFiles(t);
				}
			}

		});
	}

	public LayeredFilesDB(DatabaseInitData fileData) throws DatabaseException {
		super(fileData);
		this.makeTable("files", false, KEY("ID"), TEXT("name"), BIGTEXT("contents"), NUMBER("deleted"), TEXT("toolchain"));
		this.files = fileData.Files;
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

	private static String encodeFileContents(String data) {
		return encodeFileContents(data.getBytes(Settings.getDefaultCharset()));
	}

	private static String encodeFileContents(byte[] b) {
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

	private static String decodeFileContents(String data) {
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

			if (insert(tableName, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "contents"), encodeFileContents(contents)))) {
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
		List<VirtualFile> file = this.files.getFile(name, rootContext);
		if (file.isEmpty()) {
			return null;
		}
		return file.get(0).getRead(rootContext);
	}
}
