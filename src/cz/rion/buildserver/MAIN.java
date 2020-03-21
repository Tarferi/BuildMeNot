package cz.rion.buildserver;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.MainWindow;

public class MAIN {

	public static void main3(String[] args) throws DatabaseException {
		RuntimeDB db = new RuntimeDB(Settings.getMainDB());
		db.storeSession("EC297BC1225172FB614E6E4B6445312B227D3A4122F16B6B351168557398556E713741FC389822632CF6225663A36FD464CE65A7227B3A4030D12CA622A2749D69FC6D66655622B43AD2316535E138EF34D638D430FF377234C8360630A92C58227C729D61486EF1648C32D622583AC6227650753539515764AA71E479BA67D362DA22EE2C60229D6CD96F4E671869986E7F22BD3A74226B69B864DE76F16F7C72D5616D6B75741622792C7E222972F8615F6E6C6408330E22183A6422426150382C610D386C69B862727A4775654E0A54D56F3C42E954D374F473F75199583B551070994D2A554A67646BAE6ED4310F41DA463A77AB6629582C6A7747B8227B7D");

	}

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
