package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.ui.events.SettingsLoadedEvent.SettingsCategory;
import cz.rion.buildserver.ui.events.SettingsLoadedEvent.SettingsEntry;
import net.miginfocom.swing.MigLayout;
import javax.swing.border.TitledBorder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JLabel;
import javax.swing.JTextField;

public class SettingsCategoryPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	Map<SettingsEntry, JTextField> data = new HashMap<>();

	private SettingsCategory cat;

	public SettingsCategoryPanel(SettingsCategory cat) {
		this.cat = cat;
		setBorder(new TitledBorder(null, cat.Name, TitledBorder.LEADING, TitledBorder.TOP, null, null));
		setLayout(new MigLayout("", "[grow][grow]", "[]"));

		int i = 0;
		for (SettingsEntry e : cat.Entries) {
			JLabel lblNewLabel = new JLabel(e.Description);
			add(lblNewLabel, "cell 0 " + i + ",alignx trailing");

			JTextField textField = new JTextField();
			add(textField, "cell 1 " + i + ",growx");
			textField.setColumns(100);
			textField.setText(e.getValue());
			data.put(e, textField);
			i++;
		}
	}

	public void save() {
		for (Entry<SettingsEntry, JTextField> entry : data.entrySet()) {
			SettingsEntry e = entry.getKey();
			JTextField txt = entry.getValue();
			String val = txt.getText().trim();
			e.update(val);
		}
	}

	public SettingsCategory getCategory() {
		return cat;
	}
}
