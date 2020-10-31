package cz.rion.buildserver.ui.events;

import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class FileListLoadedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.FileListLoaded.code;

	public static void addFileListChangeListener(EventManager m, FileListLoadedListener l) {
		synchronized (m.fileListLoadedListeners) {
			if (!m.fileListLoadedListeners.contains(l)) {
				m.fileListLoadedListeners.add(l);
			}
		}
	}

	public FileListLoadedEvent(List<FileInfo> info) {
		super(info);
	}

	public static interface FileListLoadedListener {

		void fileListLoaded(List<FileInfo> file);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<FileInfo> data = (List<FileInfo>) super.data;
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
