package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.MyThread;

public abstract class LayeredThreadDB extends LayeredUserDB {

	private static int DB_THREAD_BASE = 0x002FFFFF;
	private static final String ThreadsDir = "threads/";
	private static final String ThreadsExtension = ".thread";
	private static final String ThreadsAllThreads = "All Threads";

	private final List<MyThread> threads = new ArrayList<>();

	public LayeredThreadDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
	}

	@Override
	public List<DatabaseFile> getFiles(Toolchain toolchain) {
		List<DatabaseFile> lst = super.getFiles(toolchain);

		synchronized (threads) {
			threads.clear();
			MyThread.getThreads(threads);
			lst.add(new DatabaseFile(DB_THREAD_BASE, ThreadsDir + ThreadsAllThreads + ThreadsExtension, "shared"));
			int i = 1;
			for (MyThread thread : threads) {
				lst.add(new DatabaseFile(DB_THREAD_BASE + i, ThreadsDir + thread.getName() + ThreadsExtension, "shared"));
				i++;
			}
		}
		return lst;
	}

	@Override
	public FileInfo createFile(Toolchain toolchain, String name, String contents, boolean overwriteExisting) throws DatabaseException {
		if (name.startsWith(ThreadsDir) && name.endsWith(ThreadsExtension)) {
			throw new DatabaseException("Cannnot create " + name + ": reserved file name");
		}
		return super.createFile(toolchain, name, contents, overwriteExisting);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.FileName.startsWith(ThreadsDir) && file.FileName.endsWith(ThreadsExtension)) { // No edit
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString, Toolchain toolchain) {
		if (name.startsWith(ThreadsDir) && name.endsWith(ThreadsExtension)) {
			String threadName = name.substring(ThreadsDir.length());
			threadName = threadName.substring(0, threadName.length() - ThreadsExtension.length());
			if (threadName.equals(ThreadsAllThreads)) {
				return new FileInfo(DB_THREAD_BASE, name, getAllThreadsData(), toolchain.getName());
			}
			synchronized (threads) {
				int i = 0;
				for (MyThread thread : threads) {
					if (thread.getName().equals(threadName)) {
						return new FileInfo(DB_THREAD_BASE + i, name, thread.getStackTrace(), toolchain.getName());
					}
					i++;
				}
			}
			return new FileInfo(DB_THREAD_BASE, name, "Failed to load thread info: thread not found", toolchain.getName());
		} else {
			return super.loadFile(name, decodeBigString, toolchain);
		}
	}

	private String getAllThreadsData() {
		StringBuilder sb = new StringBuilder();
		synchronized (threads) {
			int i = 0;
			for (MyThread thr : threads) {
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

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString, Toolchain toolchain) throws DatabaseException {
		if (fileID == DB_THREAD_BASE) { // All threads
			return new FileInfo(fileID, ThreadsDir + ThreadsAllThreads + ThreadsExtension, getAllThreadsData(), toolchain.getName());
		} else if (fileID > DB_THREAD_BASE && fileID < DB_THREAD_BASE + threads.size() + 1) {
			synchronized (threads) {
				MyThread thread = threads.get(fileID - (DB_THREAD_BASE + 1));
				return new FileInfo(fileID, ThreadsDir + thread.getName() + ThreadsExtension, thread.getStackTrace(), toolchain.getName());
			}
		} else {
			return super.getFile(fileID, decodeBigString, toolchain);
		}
	}
}
