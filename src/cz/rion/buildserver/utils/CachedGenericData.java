package cz.rion.buildserver.utils;

import java.util.HashMap;
import java.util.Map;

public abstract class CachedGenericData<X, T> {

	private Map<X, CachedData<T>> data = new HashMap<>();
	private int refreshIntervalInSeconds;

	public CachedGenericData(int refreshIntervalInSeconds) {
		this.refreshIntervalInSeconds = refreshIntervalInSeconds;
	}

	public void clear() {
		synchronized (data) {
			data.clear();
		}
	}

	public T get(X key) {
		synchronized (data) {
			if (data.containsKey(key)) {
				return data.get(key).get();
			} else {
				CachedData<T> d = createData(refreshIntervalInSeconds, key);
				data.put(key, d);
				return d.get();
			}
		}
	}

	protected abstract CachedData<T> createData(int refreshIntervalInSeconds, X key);
}
