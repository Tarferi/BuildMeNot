package cz.rion.buildserver.utils;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class CachedToolchainDataWrapperTotal2<T> extends CachedToolchainDataWrapper2<T> {

	public CachedToolchainDataWrapperTotal2(int refreshIntervalInSeconds) {
		super(refreshIntervalInSeconds, new CachedToolchainDataGetter2<T>() {

			@Override
			public CachedData<T> createData(int refreshIntervalInSeconds, Toolchain toolchain) {
				return createData(refreshIntervalInSeconds, toolchain);
			}

		});
	}

	public abstract T update(Toolchain toolchain);

	@Override
	public CachedData<T> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
		return new CachedDataWrapper2<>(refreshIntervalInSeconds, new CachedDataGetter<T>() {

			@Override
			public T update() {
				return CachedToolchainDataWrapperTotal2.this.update(toolchain);
			}
		});
	}

}
