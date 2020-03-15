package cz.rion.buildserver.ui;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class MainWindow extends JFrame {
	private ConnectionPanel pnlConnect;
	private StatusPanel pnlStatus;
	private final UIDriver driver;

	private void update() {
		pnlConnect.update();
		pnlStatus.update();
	}

	public MainWindow() {
		setSize(new Dimension(640, 480));
		this.driver = new UIDriver(this);

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		pnlConnect = new ConnectionPanel(driver);
		tabbedPane.addTab(pnlConnect.getTabName(), null, pnlConnect, null);

		pnlStatus = new StatusPanel(driver);
		tabbedPane.addTab(pnlStatus.getTabName(), null, pnlStatus, null);

		this.update();
		this.setVisible(true);
	}

	public void onLoggedIn() {
		pnlStatus.update();
	}

}
