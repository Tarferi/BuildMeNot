package cz.rion.buildserver.utils;

public abstract class CachedData<T> {

	protected abstract T update();

	private T cache = null;
	private final long msToRefresh;
	private long lastRefresh = 0;

	public T get() {
		long now = System.currentTimeMillis();
		if (now - lastRefresh > msToRefresh || cache == null) {
			lastRefresh = now;
			cache = update();
		}
		return cache;
	}

	public CachedData(int secondsToRefresh) {
		msToRefresh = secondsToRefresh * 1000;
	}

}
