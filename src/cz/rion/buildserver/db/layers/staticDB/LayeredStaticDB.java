package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredStaticDB extends LayeredMetaDB {

	public static class RuntimeClientSession {
		public final String login;
		public final String session;
		public final String fullName;
		public final String group;

		private RuntimeClientSession(String login, String session, String fullName, String group) {
			this.login = login;
			this.session = session;
			this.fullName = fullName;
			this.group = group;
		}
	}

	public LayeredStaticDB(DatabaseInitData fileName) throws DatabaseException {
		super(fileName, "StaticDB");
	}

	public static interface ToolchainCallback {
		void toolchainAdded(Toolchain t);

		void toolchainRemoved(Toolchain t);
	}

	private List<ToolchainCallback> callbacks = new ArrayList<>();

	public void registerToolchainListener(ToolchainCallback cb) {
		synchronized (callbacks) {
			callbacks.add(cb);
			for (Toolchain t : lastKnownToolchains) {
				cb.toolchainAdded(t);
			}
		}
	}

	private Set<Toolchain> lastKnownToolchains = new HashSet<>();
	private Set<String> lastKnownToolchainNames = new HashSet<>();

	protected void toolchainsKnownUpdate(Map<String, Toolchain> toolchains) {
		synchronized (callbacks) {
			List<Toolchain> toAdd = new ArrayList<>();
			Map<String, Toolchain> toRemove = new HashMap<>();
			for (Toolchain known : lastKnownToolchains) {
				toRemove.put(known.getName(), known);
			}
			for (Entry<String, Toolchain> entry : toolchains.entrySet()) {
				Toolchain t = entry.getValue();
				if (t.IsRoot) {
					continue;
				}
				if (!lastKnownToolchainNames.contains(t.getName())) {
					toAdd.add(t);
				} else {
					toRemove.remove(t.getName());
				}
			}
			for (Toolchain add : toAdd) {
				lastKnownToolchainNames.add(add.getName());
				lastKnownToolchains.add(add);
				for (ToolchainCallback callback : callbacks) {
					callback.toolchainAdded(add);
				}
			}
			for (Entry<String, Toolchain> entry : toRemove.entrySet()) {
				lastKnownToolchains.remove(entry.getValue());
				lastKnownToolchainNames.remove(entry.getKey());
				for (ToolchainCallback callback : callbacks) {
					callback.toolchainRemoved(entry.getValue());
				}
			}
		}
	}

	public abstract void afterInit();
}
