package cz.rion.buildserver.ui.provider;

import java.util.Date;

import cz.rion.buildserver.Settings;

public abstract class MemoryBuffer {

	private byte[] buf;
	private int position = 0;
	private int size;
	private RemoteUIClient client;

	public boolean isForSingleClient() {
		return client != null;
	}

	public RemoteUIClient getClient() {
		return client;
	}

	private MemoryBuffer(int expectedSize, RemoteUIClient client) {
		this.size = expectedSize;
		this.client = client;
		buf = new byte[size];
	}

	public byte[] get() {
		byte[] ret = new byte[position];
		System.arraycopy(buf, 0, ret, 0, ret.length);
		return ret;
	}

	public void write(byte[] data, int dataPos, int dataLength) {
		while (position + dataLength > size) {
			this.size *= 2;
			byte[] nw = new byte[size];
			System.arraycopy(buf, 0, nw, 0, position);
			buf = nw;
		}
		System.arraycopy(data, dataPos, buf, position, dataLength);
		position += dataLength;
	}

	public void write(byte[] data) {
		write(data, 0, data.length);
	}

	public void writeString(String str) {
		byte[] raw = str.getBytes(Settings.getDefaultCharset());
		writeInt(raw.length);
		write(raw);
	}

	public void writeDate(Date d) {
		long x = d.getTime();
		byte[] data = new byte[8];
		data[0] = (byte) ((x >> 56) & 0xff);
		data[1] = (byte) ((x >> 48) & 0xff);
		data[2] = (byte) ((x >> 40) & 0xff);
		data[3] = (byte) ((x >> 32) & 0xff);
		data[4] = (byte) ((x >> 24) & 0xff);
		data[5] = (byte) ((x >> 16) & 0xff);
		data[6] = (byte) ((x >> 8) & 0xff);
		data[7] = (byte) (x & 0xff);
		write(data);
	}

	public void writeInt(int x) {
		byte[] data = new byte[4];
		data[0] = (byte) ((x >> 24) & 0xff);
		data[1] = (byte) ((x >> 16) & 0xff);
		data[2] = (byte) ((x >> 8) & 0xff);
		data[3] = (byte) (x & 0xff);
		write(data);

	}

	public static class BroadcastMemoryBuffer extends MemoryBuffer {

		public BroadcastMemoryBuffer(int expectedSize) {
			super(expectedSize, null);
		}

	}

	public static class SignleClientMemoryBuffer extends MemoryBuffer {

		public SignleClientMemoryBuffer(int expectedSize, RemoteUIClient client) {
			super(expectedSize, client);
		}

	}

	public int getWrittenDataSize() {
		return position;
	}

	public void set(byte[] buffer, int baseSize) {
		this.position = 0;
		if (buffer.length < baseSize && buffer.length > 0) { // No need for reallocation
			this.buf = buffer;
			this.size = buffer.length;
		} else { // Reallocation required
			this.buf = new byte[baseSize];
			this.size = baseSize;
			this.write(buffer);
		}

	}

}
