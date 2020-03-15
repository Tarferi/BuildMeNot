package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.BuildThread;
import cz.rion.buildserver.ui.UIDriver.BuildThreadInfo;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import java.awt.Font;
import javax.swing.border.LineBorder;
import java.awt.Color;

public class BuilderPanel extends JPanel {

	private final BuildThreadInfo thr;

	public BuilderPanel(BuildThreadInfo thr) {
		setBorder(new LineBorder(new Color(0, 0, 0)));
		this.thr = thr;
		this.setOpaque(false);
		setLayout(new MigLayout("", "[][][120][][][grow]", "[][][][grow]"));

		JLabel lblBulider = new JLabel("Bulider#" + thr.ID);
		lblBulider.setFont(new Font("Tahoma", Font.PLAIN, 16));
		add(lblBulider, "cell 0 0");

		JLabel adsfasdf = new JLabel("Status:");
		add(adsfasdf, "cell 0 1,alignx right");

		JLabel lblStatus = new JLabel(thr.Status.title);
		add(lblStatus, "cell 1 1");

		JLabel lblTotalProcessed = new JLabel("Total processed:");
		add(lblTotalProcessed, "cell 3 1,alignx right");

		JLabel lblTotal = new JLabel("" + thr.TotalJobsFinished);
		add(lblTotal, "cell 4 1");

		JLabel lblQueueSize = new JLabel("Queue size:");
		add(lblQueueSize, "cell 0 2,alignx right");

		JLabel lblQueue = new JLabel("" + thr.QueueSize);
		add(lblQueue, "cell 1 2");

		JLabel lblPastHours = new JLabel("Past 24 hours:");
		add(lblPastHours, "cell 3 2,alignx right");

		JLabel lblRecent = new JLabel("<recent>");
		add(lblRecent, "cell 4 2");
	}

}
