package cz.rion.buildserver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.WindowConstants;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredStaticDB.ToolchainCallback;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.server.HTTPServer;
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

	public static void main_recalcstats(String[] args) {
		try {
			VirtualFileManager files = new VirtualFileManager();
			StaticDB sdb = new StaticDB(new DatabaseInitData("static.sqlite", files));
			RuntimeDB db = new RuntimeDB(new DatabaseInitData("data.sqlite", files), sdb);

			sdb.registerToolchainListener(new ToolchainCallback() {

				@Override
				public void toolchainRemoved(Toolchain t) {
				}

				@Override
				public void toolchainAdded(Toolchain t) {
					try {
						db.updateStatsForAllUsers(t);
					} catch (DatabaseException e) {
						e.printStackTrace();
					}
				}
			});
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public static void main_rt(String[] args) {
		try {
			// Retester rt = new Retester();
			// String test_id = "test09_03";
			// rt.runTests(true);
			// rt.backupData();
			// rt.redo();
			// rt.updateData(test_id);
			// rt.restoreData();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void main_compress(String[] args) {
		Recompressor rec;
		try {
			rec = new Recompressor();
			rec.runDynamic();
			// rec.runStatic();
		} catch (DatabaseException | CompressionException e) {
			e.printStackTrace();
		}
	}

	public static void main_plagiatus(String[] args) {
		try {
			new Plagiatus();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InvocationTargetException, InterruptedException {
		if (Settings.RunOnlyUI()) {
			setUI();
			onlyUI();
		} else {
			if (Settings.showUI()) {
				setUI();
				new MainWindow("127.0.0.1", Settings.GetHTTPServerPort(), Settings.getPasscode());
			}
			HTTPServer server;
			try {
				server = new HTTPServer(Settings.GetHTTPServerPort(), Settings.GetHTTPSServerPort());
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
