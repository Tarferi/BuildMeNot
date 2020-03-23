package cz.rion.buildserver.ui.events;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;

public class BuilderUpdateEvent extends Event {

	public static final int ID = 12;

	public static void addBuilderUpdateListener(EventManager m, BuilderUpdateListener l) {
		synchronized (m.builderUpdateListeners) {
			if (!m.builderUpdateListeners.contains(l)) {
				m.builderUpdateListeners.add(l);
			}
		}
	}

	public BuilderUpdateEvent(BuildThreadInfo obj) {
		super(obj);
	}

	public static interface BuilderUpdateListener {

		void buildersUpdateAvailable(BuildThreadInfo builder);
	}

	public void dispatch(EventManager m) {
		synchronized (m.builderUpdateListeners) {
			if (data != null) {
				final BuildThreadInfo data = (BuildThreadInfo) super.data;
				for (final BuilderUpdateListener builderUpdateListener : m.builderUpdateListeners) {
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							builderUpdateListener.buildersUpdateAvailable(data);
						}

					});
				}
			}
		}
	}
}
