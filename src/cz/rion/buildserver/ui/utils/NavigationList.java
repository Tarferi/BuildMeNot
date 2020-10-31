package cz.rion.buildserver.ui.utils;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import cz.rion.buildserver.ui.utils.ListPathItem.ListPathItemFile;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.utils.ListPathItem.ListPathItemDirectory;

public class NavigationList extends JList<ListPathItem> {

	private static final long serialVersionUID = 1L;

	private ListPathItemDirectory root = new ListPathItemDirectory("root", null);

	private ListPathItemDirectory cwd = root;

	private List<FileSelectedListener> listeners = new ArrayList<>();

	public NavigationList() {
		super();
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() >= 2) {
					int index = locationToIndex(evt.getPoint());
					if (index >= 0) {
						open(index);
					}
				}
			}
		});
		addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				int selIndex = getSelectedIndex();
				if (selIndex >= 0) {
				}
			}
		});
	}

	private void open(int index) {
		if (index >= 0) {
			ListPathItem item = getModel().getElementAt(index);
			if (item.isDirectory) {
				if (item.name.equals("..") && item.parent == null) { // Go up
					cwd = (ListPathItemDirectory) cwd.parent;
				} else { // Go down
					cwd = (ListPathItemDirectory) item;
				}
				setListItems(cwd);
			} else {
				this.dispatchSelectedFile(((ListPathItemFile) item).File);
			}
		}
	}

	public void setItems(FileInfo[] items, boolean resetCWD) {
		root.items.clear();
		for (FileInfo file : items) {
			new ListPathItemFile(root, file);
		}
		if (resetCWD) {
			this.cwd = root;
		} else {
			String path = cwd.getPath();
			ListPathItemDirectory item = root.find(path);
			cwd = item == null ? root : cwd;
		}
		setListItems(cwd);
	}

	private void setListItems(ListPathItemDirectory dir) {
		String filter = "";
		ListModel<ListPathItem> model = (ListModel<ListPathItem>) getModel();

		if (model != null) {
			if (model instanceof FilterModel) {
				filter = ((FilterModel<ListPathItem>) model).getFilter();
			}
		}
		dir.items.sort(new Comparator<ListPathItem>() {

			@Override
			public int compare(ListPathItem o1, ListPathItem o2) {
				if ((o1.isDirectory && o2.isDirectory) || (!o1.isDirectory && !o2.isDirectory)) {
					return o1.name.compareTo(o2.name);
				} else if (o1.isDirectory) {
					return -1;
				} else {
					return 1;
				}
			}
		});
		setModel(new FilterModel<ListPathItem>(ListPathItem.class, dir.items, filter) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean show(ListPathItem item, String filter) {
				return item.name.contains(filter) || item.name.equals("..");
			}
		});
	}

	public void refilter(String string) {
		((FilterModel<ListPathItem>) getModel()).filter(string);
	}

	public void addFileSelectedListener(FileSelectedListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	private void dispatchSelectedFile(FileInfo f) {
		for (FileSelectedListener listener : listeners) {
			listener.FileSelected(f);
		}
	}

	public static interface FileSelectedListener {

		public void FileSelected(FileInfo file);
	}
}
