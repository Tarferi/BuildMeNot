package cz.rion.buildserver.utils;

public abstract class CachedToolchainData<T> extends CachedGenericData<String, T> {

	public CachedToolchainData(int refreshIntervalInSeconds) {
		super(refreshIntervalInSeconds);
	}

}
