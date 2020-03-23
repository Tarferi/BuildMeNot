package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import java.awt.Font;
import javax.swing.border.LineBorder;
import java.awt.Color;

public class BuilderPanel extends JPanel {

	private BuildThreadInfo thr;
	private JLabel lblQueue;
	private JLabel lblPageLoad;
	private JLabel lblResourcesLoaded;
	private JLabel lblHackAttempts;
	private JLabel lblAll;
	private JLabel lblPassed;

	public final int getID() {
		return thr.ID;
	}

	public void updateBuilder(BuildThreadInfo thr) {
		this.thr = thr;

		lblQueue.setText("" + thr.QueueSize);
		lblPageLoad.setText(thr.Stats.getHTMLJobs() + "");
		lblResourcesLoaded.setText(thr.Stats.getTotalResourceJobs() + "");
		lblHackAttempts.setText(thr.Stats.getTotlaHackJobs() + "");
		lblAll.setText("" + (thr.Stats.getTotalJobsFinished() - thr.Stats.getTotalJobsPassed()));
		lblPassed.setText(thr.Stats.getTotalJobsPassed() + "");

	}

	public BuilderPanel(BuildThreadInfo thr) {
		this.thr = thr;
		setBorder(new LineBorder(new Color(0, 0, 0)));
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

		lblPassed = new JLabel("<Passed>");
		lblPassed.setForeground(new Color(0, 153, 102));
		add(lblPassed, "cell 4 1");

		JLabel label = new JLabel("/");
		add(label, "cell 5 1");

		lblAll = new JLabel("<All>");
		lblAll.setForeground(new Color(204, 0, 0));
		add(lblAll, "cell 6 1");

		JLabel lblNewLabel = new JLabel("Resource load:");
		add(lblNewLabel, "cell 8 1,alignx right");

		JLabel lblQueueSize = new JLabel("Queue size:");
		add(lblQueueSize, "cell 0 2,alignx right");

		lblQueue = new JLabel("<Queue size>");
		add(lblQueue, "cell 1 2");

		JLabel lblPageLoaded = new JLabel("Page loads:");
		add(lblPageLoaded, "cell 3 2,alignx right");

		lblPageLoad = new JLabel("<Page loads>");
		add(lblPageLoad, "cell 4 2 3 1");

		JLabel lblNHack = new JLabel("Hack attempts:");
		add(lblNHack, "cell 8 2,alignx right,aligny baseline");

		lblResourcesLoaded = new JLabel("<Resources loaded>");
		add(lblResourcesLoaded, "cell 9 1");

		lblHackAttempts = new JLabel("<Hack attempts>");
		add(lblHackAttempts, "cell 9 2,aligny bottom");
		updateBuilder(thr);
	}

}
