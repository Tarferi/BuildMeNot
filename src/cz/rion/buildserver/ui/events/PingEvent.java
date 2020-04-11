package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class PingEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.Ping.code;

	public static void addPingEventListener(EventManager m, PingEventListener l) {
		synchronized (m.pingEventListeners) {
			if (!m.pingEventListeners.contains(l)) {
				m.pingEventListeners.add(l);
			}
		}
	}

	public PingEvent(String data) {
		super(data);
	}

	public static interface PingEventListener {

		void PingReceived(String pingData);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			final String data = (String) super.data;
			for (final PingEventListener pingEventListener : m.pingEventListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						pingEventListener.PingReceived(data);
					}

				});
			}
		}
	}
}
