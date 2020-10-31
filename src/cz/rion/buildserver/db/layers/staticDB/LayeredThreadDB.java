package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.wrappers.MyThread;
import cz.rion.buildserver.wrappers.MyThread.MyThreadObserver;

public abstract class LayeredThreadDB extends LayeredUserDB {

	private static final String ThreadsDir = "threads/";
	private static final String ThreadsExtension = ".thread";
	private static final String ThreadsAllThreads = "All Threads";

	private final DatabaseInitData dbData;

	@Override
	public void afterInit() {
		super.afterInit();
		Map<MyThread, VirtualFile> known = new HashMap<MyThread, VirtualFile>();
		MyThread.addThreadObserver(new MyThreadObserver() {

			@Override
			public void ThreadStarted(MyThread thread) {
				synchronized (known) {
					if (!known.containsKey(thread)) {
						VirtualFile vf = new VirtualFile(ThreadsDir + thread.getName() + ThreadsExtension, LayeredThreadDB.this.getSharedToolchain()) {

							@Override
							public String read(UserContext context) throws VirtualFileException {
								return thread.getStackTrace();
							}

							@Override
							public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
								return false;
							}

						};
						dbData.Files.registerVirtualFile(vf);
						known.put(thread, vf);
					}
				}
			}

			@Override
			public void ThreadFinished(MyThread thread) {
				synchronized (known) {
					if (known.containsKey(thread)) {
						VirtualFile vf = known.get(thread);
						dbData.Files.unregisterVirtualFile(vf);
						known.remove(thread);
					}
				}
			}

		});
		dbData.Files.registerVirtualFile(new VirtualFile(ThreadsDir + ThreadsAllThreads + ThreadsExtension, this.getSharedToolchain()) {

			@Override
			public String read(UserContext context) throws VirtualFileException {
				return getAllThreadsData(known);
			}

			@Override
			public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
				return false;
			}
		});

	}

	public LayeredThreadDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.dbData = dbData;
	}

	private String getAllThreadsData(Map<MyThread, VirtualFile> known) {
		StringBuilder sb = new StringBuilder();
		synchronized (known) {
			int i = 0;
			for (MyThread thr : known.keySet()) {
				if (i > 0) {
					sb.append("\r\n\r\n\r\n");
				}
				sb.append("================================== " + thr.getName() + " ==================================\r\n\r\n");
				sb.append(thr.getStackTrace() + "\r\n");
				sb.append("=================================================================================\r\n\r\n");
				i++;
			}
		}
		return sb.toString();
	}
}
