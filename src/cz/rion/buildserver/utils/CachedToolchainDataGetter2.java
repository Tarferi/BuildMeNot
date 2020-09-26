package cz.rion.buildserver.utils;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public interface CachedToolchainDataGetter2<T> {

	CachedData<T> createData(int refreshIntervalInSeconds, Toolchain toolchain);

}
