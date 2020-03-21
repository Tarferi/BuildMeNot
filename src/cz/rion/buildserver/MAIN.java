package cz.rion.buildserver;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.MainWindow;

public class MAIN {

	public static void main(String[] args) {
		if (Settings.showUI()) {
			new MainWindow();
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
