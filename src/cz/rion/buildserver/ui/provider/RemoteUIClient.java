package cz.rion.buildserver.ui.provider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;

import cz.rion.buildserver.http.MySocketClient;

public class RemoteUIClient {

	public static enum RemoteOperation {
		BuildersLoad(0), BuildersUpdate(1), FileCreated(2), FileListLoaded(3), FileLoaded(4), FileSaved(5), StatusChanged(6), StatusMessage(7), UsersLoaded(8);

		public final int code;

		private RemoteOperation(int code) {
			this.code = code;
		}

		public static String fromCode(int code) {
			if (code >= RemoteOperation.values().length) {
				return "Unknown operation";
			} else {
				return RemoteOperation.values()[code].toString();
			}
		}
	}

	private final MySocketClient client;

	public void register(Selector selector, int operations, Object attach) throws ClosedChannelException {
		client.register(selector, operations, attach);
	}

	private boolean read(byte[] target, boolean sync) {
		try {
			int needed = target.length;
			while (needed > 0) {
				int read;
				if (sync) {
					read = client.readSync(target, target.length - needed, needed);
				} else {
					read = client.readAsync(target, target.length - needed, needed);
				}
				if (read < 0) {
					return false;
				}
				needed -= read;
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private int intFromBytes(byte[] data) {
		return ((data[0] & 0xff) << 24) | ((data[1] & 0xff) << 16) | ((data[2] & 0xff) << 8) | (data[3] & 0xff) & 0xffffffff;
	}

	private int readInt(boolean sync) throws IOException {
		byte[] data = new byte[4];
		if (!read(data, sync)) {
			throw new IOException("Read exception");
		}
		return intFromBytes(data);
	}

	private boolean write(byte[] data, boolean sync) {
		try {
			if (sync) {
				client.writeSync(data);
			} else {
				client.writeAsync(data);
			}
			return true;
		} catch (Throwable t) {
			t.printStackTrace();
			return false;
		}
	}

	private boolean writeInt(int x, boolean sync) {
		byte[] data = new byte[4];
		data[0] = (byte) ((x >> 24) & 0xff);
		data[1] = (byte) ((x >> 16) & 0xff);
		data[2] = (byte) ((x >> 8) & 0xff);
		data[3] = (byte) (x & 0xff);
		return write(data, sync);
	}

	public RemoteUIClient(MySocketClient client) {
		this.client = client;
	}

	public void close() {
		try {
			client.close();
		} catch (Throwable t) {

		}
	}

	public boolean write(MemoryBuffer buffer, boolean sync) {
		byte[] data = buffer.get();
		if (writeInt(data.length, sync)) {
			return write(data, sync);
		}
		return false;
	}

	public InputPacketRequest getNext(boolean sync) {
		try {
			int length = readInt(sync);
			byte[] data = new byte[length];
			if (read(data, sync)) {
				return new InputPacketRequest(data);
			}
		} catch (Throwable t) { // Irrelevant
		}
		return null;
	}

	private MemoryBuffer asyncBuffer = new MemoryBuffer.BroadcastMemoryBuffer(32);
	private final ByteBuffer asyncRawBuffer = ByteBuffer.allocate(512);

	public void processAvailableBytes() throws IOException {
		asyncRawBuffer.clear();
		int available = client.asyncRead(asyncRawBuffer);
		if (available < 0) {
			throw new IOException("Socket closed");
		}
		if (available > 0) {
			byte[] data = asyncRawBuffer.array();
			asyncBuffer.write(data, 0, available);
		}
	}

	public boolean hasNextAsyncEvent() {
		int knownBytes = asyncBuffer.getWrittenDataSize();
		if (knownBytes >= 4) { // Known length
			byte[] bytes = asyncBuffer.get();
			int length = intFromBytes(bytes);
			if (knownBytes - 4 >= length) { // Next packet available
				return true;
			}
		}
		return false;
	}

	public InputPacketRequest getNextAsync() { // Has next for sure
		int knownBytes = asyncBuffer.getWrittenDataSize();
		if (knownBytes >= 4) { // Known length
			byte[] allData = asyncBuffer.get();
			int length = intFromBytes(allData);
			if (knownBytes - 4 >= length) { // Next packet available
				byte[] packetData = new byte[length];
				System.arraycopy(allData, 4, packetData, 0, packetData.length);

				// Copy remaining data into new memory buffer
				byte[] remainingData = new byte[allData.length - (packetData.length + 4)];
				if (remainingData.length > 0) {
					System.arraycopy(allData, 4 + packetData.length, remainingData, 0, remainingData.length);
				}
				asyncBuffer.set(remainingData, 64); // Reset next event buffer
				return new InputPacketRequest(packetData);
			}
		}
		return null;
	}

	public boolean isConnected() {
		return client.isConnected();
	}
}
