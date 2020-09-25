package cz.rion.buildserver.ui;

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

import cz.rion.buildserver.ui.events.EventManager;
import cz.rion.buildserver.ui.events.EventManager.Status;
import cz.rion.buildserver.ui.utils.MyButton;
import cz.rion.buildserver.ui.utils.MyLabel;
import cz.rion.buildserver.ui.utils.MyTextField;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ConnectionPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private MyTextField txtServer;
	private MyTextField txtAuth;
	private MyButton btnConnect;
	private Status status;

	public ConnectionPanel(final UIDriver driver, String remoteAddress, int remotePort, String remotePasscode) {

		setOpaque(false);
		setLayout(new MigLayout("", "[133.00][300:300:300][]", "[][]"));

		MyLabel lblServer = new MyLabel("Server:");
		add(lblServer, "cell 0 0,alignx trailing");

		txtServer = new MyTextField();
		add(txtServer, "cell 1 0,growx");
		txtServer.setColumns(10);

		btnConnect = new MyButton("Connect");
		btnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (status == EventManager.Status.CONNECTED) {
					driver.disconnect();
				} else if (status == EventManager.Status.DISCONNECTED) {
					String serverStr = txtServer.getText();
					String[] serverData = serverStr.split(":");
					int serverPort = 0;
					try {
						serverPort = Integer.parseInt(serverData[1]);
					} catch (Exception ee) {
						return;
					}
					disableComponents();
					driver.login(serverData[0], serverPort, txtAuth.getText());
				} else if (status == EventManager.Status.CONNECTING) {
					btnConnect.setText("Disconnect");
				}
			}
		});
		add(btnConnect, "cell 2 0 1 2,grow");

		MyLabel lblAuthentication = new MyLabel("Authentication:");
		add(lblAuthentication, "cell 0 1,alignx trailing");

		txtAuth = new MyTextField();
		add(txtAuth, "cell 1 1,growx,aligny top");
		txtAuth.setColumns(10);
		txtServer.setText(remoteAddress + ":" + remotePort);
		txtAuth.setText(remotePasscode);
	}

	public String getTabName() {
		return "Connection";
	}

	private void disableComponents() {
		txtAuth.setEnabled(false);
		txtServer.setEnabled(false);
		btnConnect.setEnabled(false);
	}

	public void update(Status status) {
		this.status = status;
		disableComponents();
		if (status == EventManager.Status.CONNECTED) {
			btnConnect.setText("Disconnect");
			btnConnect.setEnabled(true);
		} else if (status == EventManager.Status.DISCONNECTED) {
			btnConnect.setText("Connect");
			txtAuth.setEnabled(true);
			txtServer.setEnabled(true);
			btnConnect.setEnabled(true);
		} else if (status == EventManager.Status.CONNECTING) {
			btnConnect.setText("Disconnect");
		}
	}
}
