package cz.rion.buildserver.ui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import cz.rion.buildserver.ui.events.PingEvent;
import cz.rion.buildserver.ui.events.StatusChangeEvent;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.events.PingEvent.PingEventListener;
import cz.rion.buildserver.ui.events.StatusChangeEvent.StatusChangeListener;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class MainWindow extends JFrame implements StatusChangeListener, PingEventListener {
	private final ConnectionPanel pnlConnect;
	private final StatusPanel pnlStatus;
	private final UsersPanel pnlUsers;
	private final FilesPanel pnlFiles;
	private final UIDriver driver;
	private Status status = Status.DISCONNECTED;
	private JTabbedPane tabbedPane;

	private void update() {
		pnlConnect.update(status);
		pnlStatus.update(status);
		pnlUsers.update(status);
		pnlFiles.update(status);
	}

	public MainWindow(String remoteAddress, int remotePort, String remotePasscode) {
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
			}
		});
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
		PingEvent.addPingEventListener(driver.EventManager, this);
		this.setLocationRelativeTo(null);
		this.pack();
	}

	@Override
	public void statusChanged(Status newStatus) {
		this.status = newStatus;
		update();
		if (newStatus == Status.DISCONNECTED) {
			tabbedPane.setSelectedIndex(0);
		}
	}

	@Override
	public void PingReceived(String pingData) {
		driver.sengPing(pingData);
	}

}
