package cz.rion.buildserver.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.AsmTest;
import cz.rion.buildserver.test.TestManager;

public class MyDB {

	private final Object syncer = new Object();
	private final FileOutputStream fo;
	private FileInputStream fi = null;
	private final File dbFile;

	protected class TestIdDetector {

		private class ExecWorker {

			private final SyncStorage storage;
			private final int index;

			public ExecWorker(SyncStorage storage, int index) {
				this.storage = storage;
				this.index = index;
				thread.start();
			}

			private final Thread thread = new Thread() {

				@Override
				public void run() {
					async();
				}
			};

			private void async() {
				while (true) {
					SyncStorage.SyncStorageItem job = storage.get();
					if (job == null) {
						return;
					}
					if (job.Index == 898) {
					} else {
					}
					String test_id = getTestID(index, storage.totalTests, storage.tests, job.Result);
					storage.hasGood(job, test_id);
				}
			}

			public void join() throws InterruptedException {
				thread.join();
			}

		}

		private class SyncStorage {

			private final List<CompilationResult> result;
			private int next = 0;
			private final int last;
			private final int totalTests;
			private final List<String> tests;
			private final RuntimeDB db2;

			private SyncStorage(List<CompilationResult> res, int totalTests, List<String> tests, RuntimeDB db2) {
				this.result = res;
				this.totalTests = totalTests;
				this.tests = tests;
				this.db2 = db2;
				last = res.size();
			}

			private final Object syncer = new Object();

			public void hasGood(SyncStorageItem item, String test_id) {
				synchronized (syncer) {
					CompilationResult d = item.Result;
					try {
						db2.storev1Compilation(d.address, d.time, d.code, test_id, d.resultCode, d.resultText, d.resObj.getJsonString());
					} catch (DatabaseException e) {
						e.printStackTrace();
					}
				}
			}

			public SyncStorageItem get() {
				synchronized (result) {
					if (next == last) {
						return null;
					} else {
						next++;
						synchronized (syncer) {
							System.out.print("Done: " + next + "/" + last + "\r\n");
						}
						return new SyncStorageItem(result.remove(0), next - 1);
					}
				}
			}

			private class SyncStorageItem {
				public final CompilationResult Result;
				public final int Index;

				private SyncStorageItem(CompilationResult result, int index) {
					this.Result = result;
					this.Index = index;
				}
			}

		}

		private TestIdDetector() throws DatabaseException, InterruptedException {
			MyDB db = new MyDB("dbV1.sqlite");
			RuntimeDB db2 = new RuntimeDB("data.sqlite");
			List<CompilationResult> data = db.getResults();
			List<String> tests = new ArrayList<>();
			for (AsmTest test : tm.getAllTests()) {
				tests.add(test.getID());
			}

			if (data != null) {
				int threads = 4;
				SyncStorage storage = new SyncStorage(data, tests.size(), tests, db2);
				ExecWorker[] workers = new ExecWorker[threads];

				for (int i = 0; i < threads; i++) {
					workers[i] = new ExecWorker(storage, i);
				}
				for (int i = 0; i < threads; i++) {
					workers[i].join();
				}
			}
			return;
		}

		private final TestManager tm = new TestManager("./web/tests/");

		private String getTestID(int builderID, int totalTests, List<String> tests, CompilationResult d) {
			if (d.resultCode != 0 || (!d.resultText.equals("OK") && !d.resultText.contains(":)"))) {
				if (d.code.contains("secti")) {
					return "test07_01";
				} else if (d.code.contains("fact")) {
					return "test07_03";
				} else if (d.code.contains("fib")) {
					return "test07_04";
				}
			}
			String deb_id = null;
			for (String test_id : tests) {
				JsonObject res = tm.run(builderID, test_id, d.code);
				if (res.getNumber("code").Value == 0) {
					if (test_id.contains("debug")) {
						deb_id = test_id;
						continue;
					}
					return test_id;
				}
			}
			if (deb_id != null) {
				return deb_id;
			}
			if (d.resultText.contains(":)")) {
				if (d.code.contains("secti")) {
					return "test07_01";
				} else if (d.code.contains("fact")) {
					return "test07_03";
				} else if (d.code.contains("fib")) {
					return "test07_04";
				}
				return "unknown";
			}
			return null;
		}

	}

	private void writeInt(int x) throws IOException {
		byte[] data = new byte[4];
		data[0] = (byte) ((x >> 24) & 0xff);
		data[1] = (byte) ((x >> 16) & 0xff);
		data[2] = (byte) ((x >> 8) & 0xff);
		data[3] = (byte) (x & 0xff);
		fo.write(data);
	}

	private final int readInt() throws IOException {
		byte[] data = new byte[4];
		fi.read(data);
		return ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | ((data[3] & 0xff) << 0);
	}

	private void writeLong(long x) throws IOException {
		writeInt((int) ((x >> 32) & 0xffffffff));
		writeInt((int) (x & 0xffffffff));
	}

	private long readLong() throws IOException {
		long l1 = readInt() & 0xffffffff;
		long l2 = readInt() & 0xffffffff;
		return 0xffffffff & ((l1 << 32) | l2);
	}

	private void writeString(String str) throws IOException {
		writeInt(str.getBytes(Settings.getDefaultCharset()).length);
		fo.write(str.getBytes(Settings.getDefaultCharset()));
	}

	private String readString() throws IOException {
		int length = readInt();
		byte[] str = new byte[length];
		fi.read(str);
		return new String(str, Settings.getDefaultCharset());
	}

	public MyDB(String fileName) throws DatabaseException {
		if (fileName != null) {
			dbFile = new File(fileName);
			try {
				fo = new FileOutputStream(dbFile, true);

			} catch (IOException e) {
				throw new DatabaseException("Failed to open database: " + fileName, e);
			}
		} else {
			fo = null;
			dbFile = null;
		}
	}

	public static final class CompilationResult {
		public final String address;
		public final String code;
		public final Date time;
		public final JsonObject resObj;
		public final int resultCode;
		public final String resultText;

		private CompilationResult(String address, String code, Date time, String result) throws IOException {
			this.address = address;
			this.code = code;
			this.time = time;
			JsonValue val = JsonValue.parse(result);
			if (val == null) {
				throw new IOException();
			}
			if (!val.isObject()) {
				throw new IOException();
			}
			this.resObj = val.asObject();

			if (!resObj.containsNumber("code") || !resObj.containsString("result")) {
				throw new IOException();
			}

			this.resultCode = resObj.getNumber("code").Value;
			this.resultText = resObj.getString("result").Value;
		}
	}

	public List<CompilationResult> getResults() {
		List<CompilationResult> res = new ArrayList<>();
		if (dbFile != null) {
			synchronized (syncer) {
				try {
					fi = new FileInputStream(dbFile);
					while (fi.available() > 0) {
						String ver = readString();
						if (ver.equals("dbV1")) {
							String address = readString();
							long time = readLong();
							String code = readString();
							String result = readString();
							Date d = new Date(time);
							res.add(new CompilationResult(address, code, d, result));
						} else {
							return null;
						}
					}

					return res;
				} catch (IOException e) {
					return null;
				} finally {
					try {
						fi.close();
					} catch (Exception e) {
					} finally {
						fi = null;
					}
				}
			}
		} else {
			return null;
		}
	}

	public void storeCompilation(String remoteAddress, Date time, String asm, String result) throws DatabaseException {
		synchronized (syncer) {
			try {
				writeString("dbV1");
				writeString(remoteAddress);
				writeLong(time.getTime());
				writeString(asm);
				writeString(result);
				fo.flush();
			} catch (IOException e) {
				throw new DatabaseException("Failed to store data", e);
			}
		}
	}

}
