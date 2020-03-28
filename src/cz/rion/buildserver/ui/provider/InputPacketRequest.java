package cz.rion.buildserver.ui.provider;

import java.io.IOException;
import java.util.Date;

import cz.rion.buildserver.Settings;

public class InputPacketRequest {

	private final byte[] data;
	private int position = 0;

	public InputPacketRequest(byte[] data) {
		this.data = data;
	}
	
	public void read(byte[] result) throws IOException {
		if (position + result.length > data.length) {
			throw new IOException("End of packet");
		}
		System.arraycopy(data, position, result, 0, result.length);
		position += result.length;
	}

	public int readInt() throws IOException {
		byte[] data = new byte[4];
		read(data);
		return ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff) & 0xffffffff;
	}

	public Date readDate() throws IOException {
		byte[] data = new byte[8];
		read(data);
		long l = 0;
		l |= data[0] & 0xff;
		l <<= 8;
		l |= data[1] & 0xff;
		l <<= 8;
		l |= data[2] & 0xff;
		l <<= 8;
		l |= data[3] & 0xff;
		l <<= 8;
		l |= data[4] & 0xff;
		l <<= 8;
		l |= data[5] & 0xff;
		l <<= 8;
		l |= data[6] & 0xff;
		l <<= 8;
		l |= data[7] & 0xff;
		return new Date(l);
	}

	public String readString() throws IOException {
		int length = readInt();
		byte[] raw = new byte[length];
		read(raw);
		return new String(raw, Settings.getDefaultCharset());
	}

}
