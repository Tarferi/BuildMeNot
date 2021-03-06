package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class FileLoadedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.FileLoaded.code;

	public static class FileInfo {
		public final int FileID;
		public final String Name;
		public final String Contents;

		public FileInfo(int id, String fileName, String contents, String toolchainName) {
			this.FileID = id;
			this.Name = fileName;
			this.Contents = contents;
		}
	}

	public static void addFileLoadedListener(EventManager m, FileLoadedListener l) {
		synchronized (m.fileLoadedListeners) {
			if (!m.fileLoadedListeners.contains(l)) {
				m.fileLoadedListeners.add(l);
			}
		}
	}

	public FileLoadedEvent(FileInfo info) {
		super(info);
	}

	public static interface FileLoadedListener {

		void fileLoaded(FileInfo file);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			final FileInfo data = (FileInfo) super.data;
			for (final FileLoadedListener userListLoadedListener : m.fileLoadedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						userListLoadedListener.fileLoaded(data);
					}

				});
			}
		}
	}
}
