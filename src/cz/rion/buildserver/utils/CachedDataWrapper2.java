package cz.rion.buildserver.utils;

public class CachedDataWrapper2<T> extends CachedData<T> {

	private final CachedDataGetter<T> getter;

	public CachedDataWrapper2(int secondsToRefresh, CachedDataGetter<T> getter) {
		super(secondsToRefresh);
		this.getter = getter;
	}

	@Override
	protected T update() {
		return getter.update();
	}

}
