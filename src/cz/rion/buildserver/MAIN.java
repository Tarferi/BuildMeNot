package cz.rion.buildserver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.WindowConstants;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.MainWindow;

public class MAIN {

	private static void onlyUI() throws InvocationTargetException, InterruptedException {
		SwingUtilities.invokeAndWait(new Runnable() {

			@Override
			public void run() {
				MainWindow wnd = new MainWindow(Settings.GetOnlyUIAddress(), Settings.GetOnlyUIPort(), Settings.GetOnlyUIPasscode());
				wnd.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			}
		});
	}

	public static void main_retest(String[] args) {
		try {
			Retester rt = new Retester();
			String test_id = "test07_04";
			//rt.runTests(test_id);
			//rt.backupData();
			rt.updateData();
			// rt.restoreData();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InvocationTargetException, InterruptedException {
		setUI();
		if (Settings.RunOnlyUI()) {
			onlyUI();
		} else {

			if (Settings.showUI()) {
				new MainWindow("127.0.0.1", Settings.GetHTTPServerPort(), Settings.getPasscode());
			}
			HTTPServer server;
			try {
				server = new HTTPServer(Settings.GetHTTPServerPort());
				server.run();
			} catch (HTTPServerException | DatabaseException | IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void setUI() {
		try {
			LookAndFeelInfo[] lnfs = UIManager.getInstalledLookAndFeels();
			for (LookAndFeelInfo lnf : lnfs) {
				if (lnf.getName().equals("Nimbus")) {
					UIManager.setLookAndFeel(lnf.getClassName());
					return;
				} else if (lnf.getName().equals("Windows")) {
					UIManager.setLookAndFeel(lnf.getClassName());
					return;
				} else if (lnf.getName().equals("Windows Classic")) {
					UIManager.setLookAndFeel(lnf.getClassName());
					return;
				} else if (lnf.getName().equals("CDE/Motif")) {
					UIManager.setLookAndFeel(lnf.getClassName());
					return;
				}
			}
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
