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
		setLayout(new MigLayout("", "[][][80:n,grow][][][][][80:n,grow][][][grow]", "[][][][grow]"));

		JLabel lblBulider = new JLabel("Builder#" + thr.ID);
		lblBulider.setFont(new Font("Tahoma", Font.PLAIN, 16));
		add(lblBulider, "cell 0 0");

		JLabel adsfasdf = new JLabel("Status:");
		add(adsfasdf, "cell 0 1,alignx right");

		JLabel lblStatus = new JLabel(thr.Status.title);
		add(lblStatus, "cell 1 1");

		JLabel lblTotalProcessed = new JLabel("Tests evaluated:");
		add(lblTotalProcessed, "cell 3 1,alignx right");

		JLabel lblPassed = new JLabel(thr.Stats.getTotalJobsPassed() + "");
		lblPassed.setForeground(new Color(0, 153, 102));
		add(lblPassed, "cell 4 1");

		JLabel label = new JLabel("/");
		add(label, "cell 5 1");

		JLabel lblAll = new JLabel("" + (thr.Stats.getTotalJobsFinished() - thr.Stats.getTotalJobsPassed()));
		lblAll.setForeground(new Color(204, 0, 0));
		add(lblAll, "cell 6 1");

		JLabel lblNewLabel = new JLabel("Resource load:");
		add(lblNewLabel, "cell 8 1,alignx right");

		JLabel lblQueueSize = new JLabel("Queue size:");
		add(lblQueueSize, "cell 0 2,alignx right");

		JLabel lblQueue = new JLabel("" + thr.QueueSize);
		add(lblQueue, "cell 1 2");

		JLabel lblPageLoaded = new JLabel("Page loads:");
		add(lblPageLoaded, "cell 3 2,alignx right");

		JLabel lblPageLoad = new JLabel(thr.Stats.getHTMLJobs() + "");
		add(lblPageLoad, "cell 4 2 3 1");

		JLabel lblNewLabel_1 = new JLabel("Hack attempts:");
		add(lblNewLabel_1, "cell 8 2,alignx right,aligny baseline");

		JLabel lblResLoad = new JLabel(thr.Stats.getTotalResourceJobs() + "");
		add(lblResLoad, "cell 9 1");

		JLabel lblHacks = new JLabel(thr.Stats.getHTMLJobs() + "");
		add(lblHacks, "cell 9 2,aligny bottom");
	}

}
