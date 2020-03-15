package cz.rion.buildserver.db;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import cz.rion.buildserver.exceptions.DatabaseException;

public class MyDB {

	private final Object syncer = new Object();
	final FileOutputStream fo;

	private void writeInt(int x) throws IOException {
		byte[] data = new byte[4];
		data[0] = (byte) ((x >> 24) & 0xff);
		data[1] = (byte) ((x >> 16) & 0xff);
		data[2] = (byte) ((x >> 8) & 0xff);
		data[3] = (byte) (x & 0xff);
		fo.write(data);
	}

	private void writeLong(long x) throws IOException {
		writeInt((int) ((x >> 32) & 0xffffffff));
		writeInt((int) (x & 0xffffffff));
	}

	private void writeString(String str) throws IOException {
		writeInt(str.getBytes().length);
		fo.write(str.getBytes());
	}

	public MyDB(String fileName) throws DatabaseException {
		File dbFile = new File(fileName);
		try {
			fo = new FileOutputStream(dbFile, true);
		} catch (IOException e) {
			throw new DatabaseException("Failed to open database: " + fileName, e);
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
