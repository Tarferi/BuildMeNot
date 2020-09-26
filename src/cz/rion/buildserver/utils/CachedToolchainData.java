package cz.rion.buildserver.utils;

import java.util.HashMap;
import java.util.Map;

public abstract class CachedToolchainData<T> {

	private Map<String, CachedData<T>> data = new HashMap<>();
	private int refreshIntervalInSeconds;

	public CachedToolchainData(int refreshIntervalInSeconds) {
		this.refreshIntervalInSeconds = refreshIntervalInSeconds;
	}

	public void clear() {
		synchronized (data) {
			data.clear();
		}
	}

	public T get(String toolchain) {
		synchronized (data) {
			if (data.containsKey(toolchain)) {
				return data.get(toolchain).get();
			} else {
				CachedData<T> d = createData(refreshIntervalInSeconds, toolchain);
				data.put(toolchain, d);
				return d.get();
			}
		}
	}

	protected abstract CachedData<T> createData(int refreshIntervalInSeconds, String toolchain);
}
