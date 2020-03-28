package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class FileSavedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.FileSaved.code;

	public static class FileSaveResult {
		public final FileInfo File;
		public final boolean Saved;

		public FileSaveResult(FileInfo info, boolean saved) {
			this.File = info;
			this.Saved = saved;
		}
	}

	public static void addFileSavedListener(EventManager m, FileSavedListener l) {
		synchronized (m.fileSavedListeners) {
			if (!m.fileSavedListeners.contains(l)) {
				m.fileSavedListeners.add(l);
			}
		}
	}

	public FileSavedEvent(FileSaveResult result) {
		super(result);
	}

	public static interface FileSavedListener {

		void fileSaved(FileInfo file, boolean saved);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			final FileSaveResult data = (FileSaveResult) super.data;
			for (final FileSavedListener userListLoadedListener : m.fileSavedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						userListLoadedListener.fileSaved(data.File, data.Saved);
					}

				});
			}
		}
	}
}
