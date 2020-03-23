package cz.rion.buildserver.ui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import cz.rion.buildserver.ui.events.StatusChangeEvent;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.StatusChangeEvent.StatusChangeListener;

import java.awt.BorderLayout;
import java.awt.Dimension;

public class MainWindow extends JFrame implements StatusChangeListener {
	private final ConnectionPanel pnlConnect;
	private final StatusPanel pnlStatus;
	private final UsersPanel pnlUsers;
	private final FilesPanel pnlFiles;
	private final UIDriver driver;
	private Status status;
	private JTabbedPane tabbedPane;

	private void update() {
		pnlConnect.update(status);
		pnlStatus.update(status);
		pnlUsers.update(status);
		pnlFiles.update(status);
	}

	public MainWindow(String remoteAddress, int remotePort, String remotePasscode) {
		setSize(new Dimension(640, 480));
		this.driver = new UIDriver(this);

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		pnlConnect = new ConnectionPanel(driver, remoteAddress, remotePort, remotePasscode);
		tabbedPane.addTab(pnlConnect.getTabName(), null, pnlConnect, null);

		pnlStatus = new StatusPanel(driver);
		tabbedPane.addTab(pnlStatus.getTabName(), null, pnlStatus, null);

		pnlUsers = new UsersPanel(driver);
		tabbedPane.addTab(pnlUsers.getTabName(), null, pnlUsers, null);

		pnlFiles = new FilesPanel(driver);
		tabbedPane.addTab(pnlFiles.getTabName(), null, pnlFiles, null);

		this.update();
		this.setVisible(true);
		StatusChangeEvent.addStatusChangeListener(driver.EventManager, this);
	}

	@Override
	public void statusChanged(Status newStatus) {
		this.status = newStatus;
		update();
		if (newStatus == Status.DISCONNECTED) {
			tabbedPane.setSelectedIndex(0);
		}
	}

}
