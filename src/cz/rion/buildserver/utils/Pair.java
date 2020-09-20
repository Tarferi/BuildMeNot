package cz.rion.buildserver.utils;

public class Pair<R, L> {

	public final R Key;
	public final L Value;

	public Pair(R r, L l) {
		Key = r;
		Value = l;
	}
}
