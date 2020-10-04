package cz.rion.buildserver.db.layers.staticDB;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredConsoleOutputDB extends LayeredAdminLogDB {

	private static final String STDOUTFileName = "stdout.stream";
	private static final String STDERRFileName = "stderr.stream";

	private static final Object syncer = new Object();

	private final StringBuilder stdout = new StringBuilder();
	private final StringBuilder stderr = new StringBuilder();

	public void writeStderr(String data) {
		synchronized (syncer) {
			stderr.append(data);
		}
	}

	public void writeStdout(String data) {
		synchronized (syncer) {
			stdout.append(data);
		}
	}

	private final class BufferedStream extends OutputStream implements VirtualFile {

		private byte[] buffer = new byte[64];
		private int position = 0;
		private final StringBuilder sb;
		private final Object syncer;
		private final OutputStream originalStream;
		private final String name;

		private BufferedStream(OutputStream originalStream, StringBuilder sb, Object syncer, String name) {
			this.sb = sb;
			this.syncer = syncer;
			this.originalStream = originalStream;
			this.name = name;
		}

		@Override
		public void write(int b) throws IOException {
			if (position == buffer.length) {
				flush();
			}
			buffer[position] = (byte) b;
			position++;
			originalStream.write(b);
		}

		@Override
		public void flush() throws IOException {
			super.flush();
			originalStream.flush();
			if (position > 0) {
				byte[] data = new byte[position];
				System.arraycopy(buffer, 0, data, 0, data.length);
				String str = new String(data, Settings.getDefaultCharset());
				synchronized (syncer) {
					sb.append(str);
				}
				position = 0;
			}
		}

		@Override
		public String read() throws DatabaseException {
			return sb.toString();
		}

		@Override
		public void write(String data) throws DatabaseException {
			sb.setLength(0);
			sb.append(data);
		}

		@Override
		public String getName() {
			return name;
		}

	}

	public LayeredConsoleOutputDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		BufferedStream berr = new BufferedStream(System.err, stderr, syncer, STDERRFileName);
		BufferedStream bout = new BufferedStream(System.out, stdout, syncer, STDOUTFileName);
		System.setErr(new PrintStream(berr, true));
		System.setOut(new PrintStream(bout, true));
		this.registerVirtualFile(berr);
		this.registerVirtualFile(bout);
	}

}
