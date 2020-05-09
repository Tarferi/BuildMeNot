package cz.rion.buildserver.db.layers.staticDB;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;

public class LayeredConsoleOutputDB extends LayeredAdminLogDB {

	private static int DB_FILE_CONSOLE_OUTPUT_BASE = 0x001FFFFF;
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

	private final class BufferedStream extends OutputStream {

		private byte[] buffer = new byte[64];
		private int position = 0;
		private final StringBuilder sb;
		private final Object syncer;
		private final OutputStream originalStream;

		private BufferedStream(OutputStream originalStream, StringBuilder sb, Object syncer) {
			this.sb = sb;
			this.syncer = syncer;
			this.originalStream = originalStream;
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

	}

	public LayeredConsoleOutputDB(String dbName) throws DatabaseException {
		super(dbName);
		System.setErr(new PrintStream(new BufferedStream(System.err, stderr, syncer), true));
		System.setOut(new PrintStream(new BufferedStream(System.out, stdout, syncer), true));
	}

	@Override
	public List<DatabaseFile> getFiles() {
		List<DatabaseFile> lst = super.getFiles();
		lst.add(new DatabaseFile(DB_FILE_CONSOLE_OUTPUT_BASE, STDOUTFileName));
		lst.add(new DatabaseFile(DB_FILE_CONSOLE_OUTPUT_BASE + 1, STDERRFileName));
		return lst;
	}

	@Override
	public FileInfo createFile(String name, String contents) throws DatabaseException {
		if (name.equals(STDOUTFileName) || name.equals(STDERRFileName)) {
			throw new DatabaseException("Cannnot create " + name + ": reserved file name");
		}
		return super.createFile(name, contents);
	}

	@Override
	public void storeFile(DatabaseFile file, String newFileName, String newContents) {
		if (file.ID == DB_FILE_CONSOLE_OUTPUT_BASE) {
			synchronized (syncer) {
				stdout.setLength(0);
				stdout.append(newContents);
			}
		} else if (file.ID == DB_FILE_CONSOLE_OUTPUT_BASE + 1) {
			synchronized (syncer) {
				stderr.setLength(0);
				stderr.append(newContents);
			}
		} else {
			super.storeFile(file, newFileName, newContents);
		}
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString) {
		if (name.equals(STDOUTFileName)) {
			synchronized (syncer) {
				return new FileInfo(DB_FILE_CONSOLE_OUTPUT_BASE, STDOUTFileName, stdout.toString());
			}
		} else if (name.equals(STDERRFileName)) {
			synchronized (syncer) {
				return new FileInfo(DB_FILE_CONSOLE_OUTPUT_BASE + 1, STDERRFileName, stderr.toString());
			}
		} else {
			return super.loadFile(name, decodeBigString);
		}
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		if (fileID == DB_FILE_CONSOLE_OUTPUT_BASE) {
			synchronized (syncer) {
				return new FileInfo(DB_FILE_CONSOLE_OUTPUT_BASE, STDOUTFileName, stdout.toString());
			}
		} else if (fileID == DB_FILE_CONSOLE_OUTPUT_BASE + 1) {
			synchronized (syncer) {
				return new FileInfo(DB_FILE_CONSOLE_OUTPUT_BASE + 1, STDERRFileName, stderr.toString());
			}
		} else {
			return super.getFile(fileID, decodeBigString);
		}
	}

}
