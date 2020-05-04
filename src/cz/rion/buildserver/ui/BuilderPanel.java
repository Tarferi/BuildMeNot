package cz.rion.buildserver.ui;

import javax.swing.JPanel;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.ui.events.BuildersLoadedEvent.BuildThreadInfo;
import cz.rion.buildserver.ui.utils.MyLabel;
import net.miginfocom.swing.MigLayout;
import java.awt.Font;
import javax.swing.border.LineBorder;
import java.awt.Color;

public class BuilderPanel extends JPanel {

	private BuildThreadInfo thr;
	private MyLabel lblQueue;
	private MyLabel lblPageLoad;
	private MyLabel lblResourcesLoaded;
	private MyLabel lblHackAttempts;
	private MyLabel lblAll;
	private MyLabel lblPassed;

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

		MyLabel lblBuilder = new MyLabel("Builder#" + thr.ID);
		lblBuilder.setFont(new Font("Tahoma", Font.PLAIN, Settings.getFontSize()));
		add(lblBuilder, "cell 0 0");

		MyLabel adsfasdf = new MyLabel("Status:");
		add(adsfasdf, "cell 0 1,alignx right");

		MyLabel lblStatus = new MyLabel(thr.Status.title);
		add(lblStatus, "cell 1 1");

		MyLabel lblTotalProcessed = new MyLabel("Tests evaluated:");
		add(lblTotalProcessed, "cell 3 1,alignx right");

		lblPassed = new MyLabel("<Passed>");
		lblPassed.setForeground(new Color(0, 153, 102));
		add(lblPassed, "cell 4 1");

		MyLabel label = new MyLabel("/");
		add(label, "cell 5 1");

		lblAll = new MyLabel("<All>");
		lblAll.setForeground(new Color(204, 0, 0));
		add(lblAll, "cell 6 1");

		MyLabel lblNewLabel = new MyLabel("Resource load:");
		add(lblNewLabel, "cell 8 1,alignx right");

		MyLabel lblQueueSize = new MyLabel("Queue size:");
		add(lblQueueSize, "cell 0 2,alignx right");

		lblQueue = new MyLabel("<Queue size>");
		add(lblQueue, "cell 1 2");

		MyLabel lblPageLoaded = new MyLabel("Page loads:");
		add(lblPageLoaded, "cell 3 2,alignx right");

		lblPageLoad = new MyLabel("<Page loads>");
		add(lblPageLoad, "cell 4 2 3 1");

		MyLabel lblNHack = new MyLabel("Hack attempts:");
		add(lblNHack, "cell 8 2,alignx right,aligny baseline");

		lblResourcesLoaded = new MyLabel("<Resources loaded>");
		add(lblResourcesLoaded, "cell 9 1");

		lblHackAttempts = new MyLabel("<Hack attempts>");
		add(lblHackAttempts, "cell 9 2,aligny bottom");
		updateBuilder(thr);
	}

}
