package cz.rion.buildserver.ui.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;

public abstract class FilterModel<T> extends AbstractListModel<T> {

	private T[] data;
	private String filter = "";
	private final List<T> filteredData = new ArrayList<>();

	public FilterModel(T[] data, String filter) {
		this.data = data;
		this.filter(filter);
	}

	private static class FilterModelConverter<X extends Object> {

		private X[] fromList(List<X> data, Class<X> cls) {
			@SuppressWarnings("unchecked")
			final X[] result = (X[]) Array.newInstance(cls, data.size());
			for (int i = 0; i < result.length; i++) {
				result[i] = data.get(i);
			}
			return result;
		}
	}

	public FilterModel(Class<T> cls, List<T> data, String filter) {
		this(new FilterModelConverter<T>().fromList(data, cls), filter);
	}

	public String getFilter() {
		return filter;
	}

	public abstract boolean show(T item, String filter);

	public void filter(String string) {
		this.filter = string.toLowerCase();
		if (!filter.isEmpty()) {
			filteredData.clear();
			for (T i : data) {
				if (show(i, filter)) {
					filteredData.add(i);
				}
			}
		}
	}

	@Override
	public int getSize() {
		return filter.isEmpty() ? data.length : filteredData.size();
	}

	@Override
	public T getElementAt(int index) {
		return filter.isEmpty() ? data[index] : filteredData.get(index);
	}

}