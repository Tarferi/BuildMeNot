package cz.rion.buildserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class MySocketClient {

	private final SocketChannel sock;
	private final Socket rsock;

	public void register(Selector selector, int operations, Object attach) throws ClosedChannelException {
		sock.register(selector, operations, attach);
	}

	public MySocketClient(SocketChannel sc) {
		this.sock = sc;
		this.rsock = null;
		try {
			sc.configureBlocking(true);
		} catch (IOException e) {
			e.printStackTrace();
			close(); // Error will be throw later in code
		}
	}

	public MySocketClient(Socket sc) {
		this.sock = null;
		this.rsock = sc;
	}

	public void close() {
		if (sock != null) {
			try {
				sock.close();
			} catch (Throwable t) {
			}
		}
		if (rsock != null) {
			try {
				rsock.close();
			} catch (Throwable t) {
			}
		}
	}

	public SocketAddress getRemoteSocketAddress() {
		return rsock == null ? sock.socket().getRemoteSocketAddress() : rsock.getRemoteSocketAddress();
	}

	private OutputStream getOutputStream() throws IOException {
		return rsock == null ? sock.socket().getOutputStream() : rsock.getOutputStream();
	}

	private InputStream getInputStream() throws IOException {
		return rsock == null ? sock.socket().getInputStream() : rsock.getInputStream();
	}

	public void configureBlocking(boolean b) throws IOException {
		sock.configureBlocking(b);
	}

	public int asyncRead(ByteBuffer asyncRawBuffer) throws IOException {
		return sock.read(asyncRawBuffer);
	}

	public void writeSync(byte[] data) throws IOException {
		getOutputStream().write(data);
	}

	public void writeAsync(byte[] data) throws IOException {
		sock.write(ByteBuffer.wrap(data));
	}

	public int readSync(byte[] target, int position, int length) throws IOException {
		return getInputStream().read(target, position, length);
	}

	public int readAsync(byte[] target, int i, int needed) {
		// TODO Auto-generated method stub
		return 0;
	}

	public int readSync() throws IOException {
		return getInputStream().read();
	}

	public void flush() throws IOException {
		if (sock != null) {
			getOutputStream().flush();
		}
	}

	public void writeSync(int i) throws IOException {
		getOutputStream().write(i);
	}

	public boolean isConnected() {
		return rsock == null ? sock.isConnected() : rsock.isConnected();
	}

}
