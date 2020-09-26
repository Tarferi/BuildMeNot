package cz.rion.buildserver.utils;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class CachedToolchainData2<T> {

	private Map<String, CachedData<T>> data = new HashMap<>();
	private int refreshIntervalInSeconds;

	public CachedToolchainData2(int refreshIntervalInSeconds) {
		this.refreshIntervalInSeconds = refreshIntervalInSeconds;
	}

	public void clear() {
		synchronized (data) {
			data.clear();
		}
	}

	public T get(Toolchain toolchain) {
		synchronized (data) {
			if (data.containsKey(toolchain.getName())) {
				return data.get(toolchain.getName()).get();
			} else {
				CachedData<T> d = createData(refreshIntervalInSeconds, toolchain);
				data.put(toolchain.getName(), d);
				return d.get();
			}
		}
	}

	protected abstract CachedData<T> createData(int refreshIntervalInSeconds, Toolchain toolchain);
}
