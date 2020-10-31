package cz.rion.buildserver.ui.events;

import java.util.List;

import javax.swing.SwingUtilities;

import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.provider.RemoteUIClient;

public class SettingsLoadedEvent extends Event {

	public static final int ID = RemoteUIClient.RemoteOperation.LoadSettings.code;

	public static void addSettingsLoadedListener(EventManager m, SettingsLoadedListener l) {
		synchronized (m.settingsLoadedListeners) {
			if (!m.settingsLoadedListeners.contains(l)) {
				m.settingsLoadedListeners.add(l);
			}
		}
	}

	public SettingsLoadedEvent(List<SettingsCategory> info) {
		super(info);
	}

	public static String format(List<SettingsCategory> cats) {
		JsonObject obj = new JsonObject();
		for (SettingsCategory cat : cats) {
			JsonArray catData = cat.format();
			if (!catData.Value.isEmpty()) {
				obj.add(cat.Name, catData);
			}
		}
		return obj.getJsonString();
	}

	public static final class SettingsCategory {
		public final String Name;
		public final SettingsEntry[] Entries;

		private SettingsCategory(String name, SettingsEntry[] e) {
			this.Name = name;
			this.Entries = e;
		}

		public JsonArray format() {
			JsonArray arr = new JsonArray();
			for (SettingsEntry e : Entries) {
				if (e.IsChanged()) {
					JsonObject obj = new JsonObject();
					obj.add("Name", e.Name);
					obj.add("Value", e.getValue());
					arr.add(obj);
				}
			}
			return arr;
		}

		public static SettingsCategory get(String name, JsonValue val) {
			if (val.isArray()) {
				SettingsEntry[] e = new SettingsEntry[val.asArray().Value.size()];
				int i = 0;
				for (JsonValue value : val.asArray().Value) {
					if (!value.isObject()) {
						return null;
					}
					JsonObject o = value.asObject();
					if (!o.containsString("Name") || !o.containsString("Description") || !o.containsString("Value")) {
						return null;
					}
					e[i] = new SettingsEntry(o.getString("Name").Value, o.getString("Value").Value, o.getString("Description").Value);
					i++;
				}
				return new SettingsCategory(name, e);
			}
			return null;
		}
	}

	public static final class SettingsEntry {
		public final String Name;
		private String value;
		public final String Description;

		private SettingsEntry(String name, String value, String description) {
			this.Name = name;
			this.value = value;
			this.Description = description;
		}

		public String getValue() {
			return value;
		}

		public boolean changed = false;

		public boolean IsChanged() {
			return changed;
		}

		public void update(String val) {
			this.changed = !val.trim().equals(this.value.trim());
			this.value = val;
		}
	}

	public static interface SettingsLoadedListener {

		void settingsLoaded(List<SettingsCategory> data);
	}

	public void dispatch(EventManager m) {
		synchronized (m.buildersAvailableListeners) {
			@SuppressWarnings("unchecked")
			final List<SettingsCategory> data = (List<SettingsCategory>) super.data;
			for (final SettingsLoadedListener userListLoadedListener : m.settingsLoadedListeners) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						userListLoadedListener.settingsLoaded(data);
					}

				});
			}
		}
	}
}
