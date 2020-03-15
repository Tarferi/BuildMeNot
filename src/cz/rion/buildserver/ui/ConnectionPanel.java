package cz.rion.buildserver.ui;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;

import cz.rion.buildserver.ui.UIDriver.LoginCallback;
import cz.rion.buildserver.ui.UIDriver.Status;

import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ConnectionPanel extends JPanel implements UIDriver.LoginCallback {
	private JTextField txtServer;
	private JTextField txtAuth;
	private final UIDriver driver;
	private JButton btnConnect;

	public ConnectionPanel(final UIDriver driver) {
		this.driver = driver;
		setOpaque(false);
		setLayout(new MigLayout("", "[133.00][300:300:300][]", "[][]"));

		JLabel lblServer = new JLabel("Server:");
		add(lblServer, "cell 0 0,alignx trailing");

		txtServer = new JTextField();
		add(txtServer, "cell 1 0,growx");
		txtServer.setColumns(10);

		btnConnect = new JButton("Connect");
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Status status = driver.getStatus();
				if (status == UIDriver.Status.CONNECTED) {
					// driver.disconnect();
				} else if (status == UIDriver.Status.DISCONNECTED) {
					String serverStr = txtServer.getText();
					String[] serverData = serverStr.split(":");
					int serverPort = 0;
					try {
						serverPort = Integer.parseInt(serverData[1]);
					} catch (Exception ee) {
						return;
					}
					disableComponents();
					driver.login(serverData[0], serverPort, txtAuth.getText(), ConnectionPanel.this);
				} else if (status == UIDriver.Status.CONNECTING) {
					btnConnect.setText("Disconnect");
				}
			}
		});
		add(btnConnect, "cell 2 0 1 2,grow");

		JLabel lblAuthentication = new JLabel("Authentication:");
		add(lblAuthentication, "cell 0 1,alignx trailing");

		txtAuth = new JTextField();
		add(txtAuth, "cell 1 1,growx,aligny top");
		txtAuth.setColumns(10);
		txtServer.setText("127.0.0.1:8000");
		txtAuth.setText("abc");
	}

	public String getTabName() {
		return "Connection";
	}

	private void disableComponents() {
		txtAuth.setEnabled(false);
		txtServer.setEnabled(false);
		btnConnect.setEnabled(false);
	}

	public void update() {
		disableComponents();
		Status status = driver.getStatus();
		if (status == UIDriver.Status.CONNECTED) {
			btnConnect.setText("Disconnect");
			btnConnect.setEnabled(true);
		} else if (status == UIDriver.Status.DISCONNECTED) {
			btnConnect.setText("Connect");
			txtAuth.setEnabled(true);
			txtServer.setEnabled(true);
			btnConnect.setEnabled(true);
		} else if (status == UIDriver.Status.CONNECTING) {
			btnConnect.setText("Disconnect");
		}
	}

	@Override
	public void loggedIn() {
		update();
		driver.wnd.onLoggedIn();
	}

	@Override
	public void loginFailed() {
		update();
	}

}
