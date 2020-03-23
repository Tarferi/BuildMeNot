package cz.rion.buildserver;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
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

	public static void main(String[] args) throws InvocationTargetException, InterruptedException {
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
			} catch (HTTPServerException | DatabaseException e) {
				e.printStackTrace();
			}
		}
	}
}
