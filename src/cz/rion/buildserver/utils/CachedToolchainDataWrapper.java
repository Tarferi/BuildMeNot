package cz.rion.buildserver.utils;

public class CachedToolchainDataWrapper<T> extends CachedToolchainData<T> {

	private CachedToolchainDataGetter<T> getter;

	public CachedToolchainDataWrapper(int refreshIntervalInSeconds, CachedToolchainDataGetter<T> getter) {
		super(refreshIntervalInSeconds);
		this.getter = getter;
	}

	@Override
	protected CachedData<T> createData(int refreshIntervalInSeconds, String toolchain) {
		return getter.createData(refreshIntervalInSeconds, toolchain);
	}

}
