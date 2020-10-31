package cz.rion.buildserver.db.layers.staticDB;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;

public abstract class LayeredConsoleOutputDB extends LayeredAdminLogDB {

	private static final String STDOUTFileName = "stdout.stream";
	private static final String STDERRFileName = "stderr.stream";

	private static final Object syncer = new Object();

	private final StringBuilder stdout = new StringBuilder();
	private final StringBuilder stderr = new StringBuilder();
	private final DatabaseInitData dbData;

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

	private final class DatabaseFileStream extends VirtualFile {

		private BufferedStream stream;

		public DatabaseFileStream(BufferedStream stream) {
			super(stream.getName(), stream.getToolchain());
			this.stream = stream;
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			return stream.read();
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			return stream.write(value);
		}

	}

	private final class BufferedStream extends OutputStream {

		private byte[] buffer = new byte[64];
		private int position = 0;
		private final StringBuilder sb;
		private final Object syncer;
		private final OutputStream originalStream;
		private final String name;
		private final Toolchain tc;

		private BufferedStream(OutputStream originalStream, StringBuilder sb, Object syncer, String name, Toolchain tc) {
			this.sb = sb;
			this.tc = tc;
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

		public String read() {
			return sb.toString();
		}

		public boolean write(String data) {
			sb.setLength(0);
			sb.append(data);
			return true;
		}

		public String getName() {
			return name;
		}

		public Toolchain getToolchain() {
			return tc;
		}

	}

	@Override
	public void afterInit() {
		super.afterInit();
		BufferedStream berr = new BufferedStream(System.err, stderr, syncer, STDERRFileName, this.getSharedToolchain());
		BufferedStream bout = new BufferedStream(System.out, stdout, syncer, STDOUTFileName, this.getSharedToolchain());
		System.setErr(new PrintStream(berr, true));
		System.setOut(new PrintStream(bout, true));
		dbData.Files.registerVirtualFile(new DatabaseFileStream(berr));
		dbData.Files.registerVirtualFile(new DatabaseFileStream(bout));
	}

	public LayeredConsoleOutputDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.dbData = dbData;
	}

}
