package cz.rion.buildserver.ui.events;

import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.BuildThread.BuilderStats;
import cz.rion.buildserver.ui.provider.RemoteUIProviderServer.BuilderStatus;

public class BuildersLoadedEvent extends Event {
	
	public static final int ID = 6;

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
		synchronized (m.bulidersAvailableListeners) {
			if (!m.bulidersAvailableListeners.contains(l)) {
				m.bulidersAvailableListeners.add(l);
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
		synchronized (m.bulidersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<BuildThreadInfo> data = (List<BuildThreadInfo>) super.data;
			for (final BuilderAvailableListener bulidersAvailableListener : m.bulidersAvailableListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						bulidersAvailableListener.buildersAvailable(data);
					}

				});
			}
		}
	}
}
