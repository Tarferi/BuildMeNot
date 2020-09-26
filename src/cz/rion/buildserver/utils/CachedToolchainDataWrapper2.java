package cz.rion.buildserver.utils;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public class CachedToolchainDataWrapper2<T> extends CachedToolchainData2<T> {

	private CachedToolchainDataGetter2<T> getter;

	public CachedToolchainDataWrapper2(int refreshIntervalInSeconds, CachedToolchainDataGetter2<T> getter) {
		super(refreshIntervalInSeconds);
		this.getter = getter;
	}

	@Override
	protected CachedData<T> createData(int refreshIntervalInSeconds, Toolchain toolchain) {
		return getter.createData(refreshIntervalInSeconds, toolchain);
	}

}
