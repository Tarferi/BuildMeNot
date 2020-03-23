package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class FileCreatedEvent extends Event {

	public static final int ID = 91;

	public static class FileCreationInfo {
		public final FileInfo FileInfo;
		public final boolean Created;

		public FileCreationInfo(FileInfo fileInfo, boolean created) {
			this.FileInfo = fileInfo;
			this.Created = created;
		}
	}

	public static void addFileLoadedListener(EventManager m, FileCreatedListener l) {
		synchronized (m.fileCreatedListeners) {
			if (!m.fileCreatedListeners.contains(l)) {
				m.fileCreatedListeners.add(l);
			}
		}
	}

	public FileCreatedEvent(FileCreationInfo info) {
		super(info);
	}

	public static interface FileCreatedListener {

		void fileCreated(FileCreationInfo file);
	}

	public void dispatch(EventManager m) {
		synchronized (m.bulidersAvailableListeners) {
			final FileCreationInfo data = (FileCreationInfo) super.data;
			for (final FileCreatedListener fileCreatedListener : m.fileCreatedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fileCreatedListener.fileCreated(data);
					}

				});
			}
		}
	}
}
