package cz.rion.buildserver.utils;

public interface CachedToolchainDataGetter<T> {

	CachedData<T> createData(int refreshIntervalInSeconds, String toolchain);

}
