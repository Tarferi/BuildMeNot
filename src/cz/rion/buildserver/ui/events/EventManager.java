package cz.rion.buildserver.ui.events;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.ui.events.BuilderUpdateEvent.BuilderUpdateListener;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuilderAvailableListener;
import cz.rion.buildserver.ui.events.DatabaseTableRowEditEvent.DatabaseRowEditEventListener;
import cz.rion.buildserver.ui.events.FileCreatedEvent.FileCreatedListener;
import cz.rion.buildserver.ui.events.FileListLoadedEvent.FileListLoadedListener;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileLoadedListener;
import cz.rion.buildserver.ui.events.FileSavedEvent.FileSavedListener;
import cz.rion.buildserver.ui.events.StatusChangeEvent.StatusChangeListener;
import cz.rion.buildserver.ui.events.StatusMessageEvent.StatusMessageListener;
import cz.rion.buildserver.ui.events.UsersLoadedEvent.UserListLoadedListener;

public class EventManager {

	protected final List<StatusChangeListener> statusChangeListeners = new ArrayList<>();
	protected final List<BuilderAvailableListener> buildersAvailableListeners = new ArrayList<>();
	protected final List<StatusMessageListener> statusMessageListeners = new ArrayList<>();
	protected final List<BuilderUpdateListener> builderUpdateListeners = new ArrayList<>();
	protected final List<UserListLoadedListener> userListLoadedListeners = new ArrayList<>();
	protected final List<FileLoadedListener> fileLoadedListeners = new ArrayList<>();
	protected final List<FileListLoadedListener> fileListLoadedListeners = new ArrayList<>();
	protected final List<FileSavedListener> fileSavedListeners = new ArrayList<>();
	protected final List<FileCreatedListener> fileCreatedListeners = new ArrayList<>();
	protected final List<DatabaseRowEditEventListener> databaseRowEditEventListeners = new ArrayList<>();

	protected Status status = Status.DISCONNECTED;

	private final Object statusSyncer = new Object();

	public enum Status {
		DISCONNECTED, CONNECTING, CONNECTED
	}

	public Status getStatus() {
		return status;
	}

	public Object getStatusSyncer() {
		return statusSyncer;
	}

}
