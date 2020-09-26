package cz.rion.buildserver.utils;

public interface CachedDataGetter<T> {

	T update();
	
}
