package cz.rion.buildserver.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.SettingsLoadedEvent;
import cz.rion.buildserver.ui.events.SettingsLoadedEvent.SettingsCategory;
import cz.rion.buildserver.ui.events.SettingsLoadedEvent.SettingsLoadedListener;
import net.miginfocom.swing.MigLayout;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class SettingsPanel extends JPanel implements SettingsLoadedListener {

	private static final long serialVersionUID = 1L;
	private JPanel panel;

	private final List<SettingsCategoryPanel> panels = new ArrayList<>();
	private JButton btnSaveSettings;
	private JButton btnLoadSettings;
	private JScrollPane scrollPnl;
	private List<SettingsCategory> lastData = null;

	public SettingsPanel(UIDriver driver) {
		SettingsLoadedEvent.addSettingsLoadedListener(driver.EventManager, this);
		setLayout(new MigLayout("", "[][13.00][][grow]", "[36.00][grow]"));

		btnLoadSettings = new JButton("Load settings");
		btnLoadSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				lastData = null;
				setComponentsVisible(false);
				driver.loadSettings();
			}
		});
		add(btnLoadSettings, "cell 0 0,grow");

		btnSaveSettings = new JButton("Save settings");
		btnSaveSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String data = get();
				lastData = null;
				setComponentsVisible(false);
				driver.saveSettings(data);
			}
		});
		add(btnSaveSettings, "cell 2 0,grow");

		panel = new JPanel();
		scrollPnl = new JScrollPane(panel);
		add(scrollPnl, "cell 0 1 4 1,grow");
		scrollPnl.getVerticalScrollBar().setUnitIncrement(16);
		panel.setLayout(new MigLayout("", "[grow]", "[][][][][]"));

		setComponentsVisible(false);
	}

	protected String get() {
		for (SettingsCategoryPanel panel : panels) {
			panel.save();
		}
		return SettingsLoadedEvent.format(lastData);
	}

	private void setComponentsVisible(boolean vis) {
		panel.setVisible(vis);
		btnLoadSettings.setVisible(vis);
		btnSaveSettings.setVisible(false);
		if (lastData != null) {
			btnSaveSettings.setVisible(vis);
		}
		scrollPnl.setVisible(vis);
		this.invalidate();
		this.revalidate();
		this.repaint();
	}

	@Override
	public void settingsLoaded(List<SettingsCategory> lst) {
		this.lastData = lst;
		panel.removeAll();
		panels.clear();
		if (lst == null) {
			return;
		}
		setComponentsVisible(true);
		StringBuilder costrRows = new StringBuilder();
		for (int i = 0, o = lst.size(); i < o; i++) {
			costrRows.append("[]");
		}
		panel.setLayout(new MigLayout("", "[grow]", costrRows.toString()));

		int i = 0;
		for (SettingsCategory cat : lst) {
			SettingsCategoryPanel pnl = new SettingsCategoryPanel(cat);
			panels.add(pnl);
			panel.add(pnl, "cell 0 " + i + ", growx");
			i++;
		}
	}

	public void update(Status status) {
		settingsLoaded(null);
		if (status == Status.CONNECTED) {
			setComponentsVisible(true);
		} else {
			setComponentsVisible(false);
		}
	}

	public String getTabName() {
		return "Settings";
	}
}
