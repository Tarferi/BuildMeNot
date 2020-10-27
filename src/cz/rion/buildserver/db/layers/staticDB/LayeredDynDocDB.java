package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public abstract class LayeredDynDocDB extends LayeredConsoleOutputDB {

	private static final class ToolchainMapping {
		public final String ToolchainName;
		public final String Host;

		public ToolchainMapping(String toolchainName, String host) {
			this.ToolchainName = toolchainName;
			this.Host = host;
		}

		public String getCoveredPart(FileMapping f) {
			String ep = f.Endpoint;
			if (ep.contains("$TOOLCHAIN$")) {
				ep = ep.replace("$TOOLCHAIN$", ToolchainName);
			}
			return ep;
		}

		public boolean covers(FileMapping f) {
			return getCoveredPart(f).split("/")[0].endsWith(Host);
		}
	}

	private static final class FileMapping {
		public final String Endpoint;

		public FileMapping(String endpoint) {
			this.Endpoint = endpoint;
		}
	}

	private static final class MappingManager {
		private List<ToolchainMapping> tcm = new ArrayList<>();
		private List<FileMapping> fm = new ArrayList<>();

		private List<String> getAffected(Toolchain toolchain) {
			List<String> lst = new ArrayList<>();
			for (FileMapping f : fm) {
				for (ToolchainMapping tc : tcm) {
					if (tc.covers(f) && (tc.ToolchainName.equals(toolchain.getName())) || toolchain.IsRoot) {
						String fp = tc.getCoveredPart(f);
						if (!lst.contains(fp)) {
							lst.add(fp);
						}
						continue;
					}
				}
			}
			return lst;
		}

		private void load(StaticDB sdb) {
			FileInfo tc = sdb.loadFile("file_mapping.ini", true, sdb.getRootToolchain());
			if (tc != null) {
				for (String line : tc.Contents.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					String[] parts = line.split("=>", 2);
					if (parts.length == 2) {
						String endp = parts[0].trim();
						fm.add(new FileMapping(endp));
					}
				}
			}
			tc = sdb.loadFile("toolchains.ini", true, sdb.getRootToolchain());
			if (tc != null) {
				for (String line : tc.Contents.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String host = parts[0].trim();
						String toolchain = parts[1].trim();
						tcm.add(new ToolchainMapping(toolchain, host));
					}
				}
			}
		}

		private MappingManager(StaticDB sdb) {
			load(sdb);
		}
	}

	private static class DynDocVf implements VirtualFile {

		private Toolchain toolchain;
		private StaticDB sdb;

		private DynDocVf(StaticDB sdb, Toolchain tc) {
			this.sdb = sdb;
			this.toolchain = tc;
		}

		@Override
		public String read() throws DatabaseException {
			StringBuilder sb = new StringBuilder();
			sb.append("# Soubor je jen pro ètení\n\n");
			List<String> data = new MappingManager(sdb).getAffected(toolchain);
			for (String f : data) {
				sb.append(f + "\n");
			}
			return sb.toString();
		}

		@Override
		public void write(String data) throws DatabaseException {
		}

		@Override
		public String getName() {
			return "dynamic_doc.ini";
		}

		@Override
		public String getToolchain() {
			return toolchain.getName();
		}

	};

	public LayeredDynDocDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);

		final Map<String, VirtualFile> map = new HashMap<>();

		this.registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainAdded(final Toolchain t) {
				synchronized (map) {
					if (!map.containsKey(t.getName())) {
						DynDocVf vf = new DynDocVf((StaticDB) LayeredDynDocDB.this, t);
						registerVirtualFile(vf);
						map.put(t.getName(), vf);
					}
				}
			}

			@Override
			public void toolchainRemoved(Toolchain t) {
				synchronized (map) {
					if (map.containsKey(t.getName())) {
						VirtualFile vf = map.get(t.getName());
						map.remove(t.getName());
						unregisterVirtualFile(vf);
					}
				}
			}

		});
	}

	@Override
	public void afterInit() {
		StaticDB sdb = (StaticDB) this;
		DynDocVf vf = new DynDocVf(sdb, sdb.getRootToolchain());
		registerVirtualFile(vf);
	}
}
