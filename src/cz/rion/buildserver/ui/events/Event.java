package cz.rion.buildserver.ui.events;

public abstract class Event {
	protected final Object data;

	protected Event(Object data) {
		this.data = data;
	}
	
	public abstract void dispatch(EventManager ev);

}
