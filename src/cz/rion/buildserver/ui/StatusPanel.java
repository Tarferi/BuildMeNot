package cz.rion.buildserver.ui;

import java.util.List;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.ui.UIDriver.BuildThreadInfo;
import cz.rion.buildserver.ui.UIDriver.GetBuilderCallback;
import cz.rion.buildserver.ui.UIDriver.Status;

import javax.swing.JTextPane;

public class StatusPanel extends JPanel {

	private JPanel pnlBuilders;
	private final UIDriver driver;

	public String getTabName() {
		return "Status";
	}

	private void setBuilders(List<BuildThreadInfo> builders) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < builders.size(); i++) {
			sb.append("[]");
		}
		pnlBuilders.setLayout(new MigLayout("", "[grow]", sb.toString()));
		int i = 0;
		for (BuildThreadInfo builder : builders) {
			pnlBuilders.add(new BuilderPanel(builder), "cell 0 " + i + ",growx");
			i++;
		}
	}

	public StatusPanel(UIDriver driver) {
		this.driver = driver;
		setOpaque(false);
		setLayout(new MigLayout("", "[grow]", "[grow][grow]"));

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBorder(new TitledBorder(null, "Builders", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		add(scrollPane, "cell 0 0,grow");

		pnlBuilders = new JPanel();
		scrollPane.setViewportView(pnlBuilders);
		pnlBuilders.setLayout(new MigLayout("", "[grow]", "[][][][][]"));

		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1.setBorder(new TitledBorder(null, "Status", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		add(scrollPane_1, "cell 0 1,grow");

		JTextPane txtStatus = new JTextPane();
		txtStatus.setEditable(false);
		txtStatus.setOpaque(false);
		scrollPane_1.setViewportView(txtStatus);
	}

	public void update() {
		Status status = driver.getStatus();
		if (status == UIDriver.Status.CONNECTED) {
			driver.getBuilders(new GetBuilderCallback() {

				@Override
				public void haveBuilders(List<BuildThreadInfo> builders) {
					Status status = driver.getStatus();
					if (status == UIDriver.Status.CONNECTED) {
						pnlBuilders.removeAll();
						setBuilders(builders);
						StatusPanel.this.invalidate();
						StatusPanel.this.repaint();
					}
				}

				@Override
				public void noBuildersBecauseOfError() {
					// TODO Auto-generated method stub
					
				}
			});
		} else if (status == UIDriver.Status.DISCONNECTED) {
			pnlBuilders.removeAll();
		} else if (status == UIDriver.Status.CONNECTING) {
			pnlBuilders.removeAll();
		}
	}

}
