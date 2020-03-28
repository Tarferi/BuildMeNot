package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.provider.RemoteUIClient;

public final class StatusChangeEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.StatusChanged.code;

	public StatusChangeEvent(Status status) {
		super(status);
	}

	public static interface StatusChangeListener {

		void statusChanged(Status newStatus);
	}

	public Status getStatus() {
		return (Status) super.data;
	}

	public static void addStatusChangeListener(EventManager m, StatusChangeListener l) {
		synchronized (m.statusChangeListeners) {
			if (!m.statusChangeListeners.contains(l)) {
				m.statusChangeListeners.add(l);
				l.statusChanged(m.status);
			}
		}
	}

	public static void setStatus(EventManager m, final Status status) {
		new StatusChangeEvent(status).dispatch(m);
	}

	@Override
	public void dispatch(EventManager m) {
		synchronized (m.statusChangeListeners) {
			final Status status = (Status) data;
			if (m.status != status) {
				m.status = status;
				for (final StatusChangeListener statusChangeListener : m.statusChangeListeners) {
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							statusChangeListener.statusChanged(status);
						}

					});
				}
			}
		}
	}
}