package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class DatabaseTableRowEditEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.DatabaseTableTowEdit.code;

	public static void addFileListChangeListener(EventManager m, DatabaseRowEditEventListener l) {
		synchronized (m.databaseRowEditEventListeners) {
			if (!m.databaseRowEditEventListeners.contains(l)) {
				m.databaseRowEditEventListeners.add(l);
			}
		}
	}

	public DatabaseTableRowEditEvent(boolean ok) {
		super(ok);
	}

	public static interface DatabaseRowEditEventListener {

		void editOK();

		void editFailed();

	}

	public void dispatch(EventManager m) {
		synchronized (m.databaseRowEditEventListeners) {
			final boolean ok = (boolean) super.data;
			for (final DatabaseRowEditEventListener databaseRowEditEventListener : m.databaseRowEditEventListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						if (ok) {
							databaseRowEditEventListener.editOK();
						} else {
							databaseRowEditEventListener.editFailed();
						}
					}

				});
			}
		}
	}
}
