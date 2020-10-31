package cz.rion.buildserver.utils;

import java.nio.ByteBuffer;

public class ByteBufferWrapper {

	private ByteBuffer bb;

	private ByteBufferWrapper(ByteBuffer bb) {
		this.bb = bb;
	}

	public static ByteBufferWrapper allocate(int i) {
		return new ByteBufferWrapper(ByteBuffer.allocate(i));
	}

	public void clear() {
		try {
			bb.clear();
		} catch (NoSuchMethodError e) {
			bb = ByteBuffer.wrap(array());
		}
	}

	public static ByteBufferWrapper wrap(byte[] data, int written, int i) {
		return new ByteBufferWrapper(ByteBuffer.wrap(data, written, i));
	}

	public ByteBuffer getBuffer() {
		return bb;
	}

	public byte[] array() {
		return bb.array();
	}

}
