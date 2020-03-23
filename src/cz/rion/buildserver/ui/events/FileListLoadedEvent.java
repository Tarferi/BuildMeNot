package cz.rion.buildserver.ui.events;

import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.db.StaticDB.DatabaseFile;

public class FileListLoadedEvent extends Event {

	public static final int ID = 80;

	public static void addFileListChangeListener(EventManager m, FileListLoadedListener l) {
		synchronized (m.fileListLoadedListeners) {
			if (!m.fileListLoadedListeners.contains(l)) {
				m.fileListLoadedListeners.add(l);
			}
		}
	}

	public FileListLoadedEvent(List<DatabaseFile> info) {
		super(info);
	}

	public static interface FileListLoadedListener {

		void fileListLoaded(List<DatabaseFile> file);
	}

	public void dispatch(EventManager m) {
		synchronized (m.bulidersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<DatabaseFile> data = (List<DatabaseFile>) super.data;
			for (final FileListLoadedListener userListLoadedListener : m.fileListLoadedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						userListLoadedListener.fileListLoaded(data);
					}

				});
			}
		}
	}
}
