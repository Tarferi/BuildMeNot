package cz.rion.buildserver.ui.events;

import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.ui.provider.RemoteUIClient;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class BuildersLoadedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.BuildersLoad.code;

	public static class BuildThreadInfo {
		public final int ID;
		public final int QueueSize;
		public final BuilderStats Stats;
		public final BuilderStatus Status;

		public BuildThreadInfo(int id, int size, BuilderStats stats, BuilderStatus bs) {
			this.ID = id;
			this.QueueSize = size;
			this.Stats = stats;
			this.Status = bs;
		}
	}

	public static void addStatusChangeListener(EventManager m, BuilderAvailableListener l) {
		synchronized (m.buildersAvailableListeners) {
			if (!m.buildersAvailableListeners.contains(l)) {
				m.buildersAvailableListeners.add(l);
			}
		}
	}

	public BuildersLoadedEvent(List<BuildThreadInfo> data) {
		super(data);
	}

	public static interface BuilderAvailableListener {

		void buildersAvailable(List<BuildThreadInfo> builders);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<BuildThreadInfo> data = (List<BuildThreadInfo>) super.data;
			for (final BuilderAvailableListener buildersAvailableListener : m.buildersAvailableListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						buildersAvailableListener.buildersAvailable(data);
					}

				});
			}
		}
	}
}
