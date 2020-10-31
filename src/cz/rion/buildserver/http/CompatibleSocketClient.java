package cz.rion.buildserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cz.rion.buildserver.utils.ByteBufferWrapper;

public class CompatibleSocketClient {

	private final SocketChannel sock;
	private final Socket rsock;
	private String address;

	public void register(Selector selector, int operations, Object attach) throws ClosedChannelException {
		sock.register(selector, operations, attach);
	}

	public CompatibleSocketClient(SocketChannel sc) {
		try {
			this.address = sc.getRemoteAddress().toString();
		} catch (IOException e1) {
			e1.printStackTrace();
			this.address = "???";
		}
		this.sock = sc;
		this.rsock = null;
		try {
			sc.configureBlocking(true);
		} catch (IOException e) {
			e.printStackTrace();
			close(); // Error will be throw later in code
		}
	}

	public CompatibleSocketClient(Socket sc) {
		this.sock = null;
		this.rsock = sc;
		this.address = sc.getRemoteSocketAddress().toString();
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

	public String getRemoteSocketAddress() {
		return address;
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

	public int asyncRead(ByteBufferWrapper asyncRawBuffer) throws IOException {
		return sock.read(asyncRawBuffer.getBuffer());
	}

	public void writeSync(byte[] data) throws IOException {
		getOutputStream().write(data);
	}

	public void writeAsync(byte[] data) throws IOException {
		int written = 0;
		while (written != data.length) {
			int writ = sock.write(ByteBufferWrapper.wrap(data, written, data.length - written).getBuffer());
			if (writ < 0) {
				throw new IOException("Socket write error");
			}
			written += writ;
		}
	}

	public int readSync(byte[] target, int position, int length) throws IOException {
		return getInputStream().read(target, position, length);
	}

	public int readAsync(byte[] target, int i, int needed) throws IOException {
		throw new IOException("Invalid read");
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
