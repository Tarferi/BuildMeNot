package cz.rion.buildserver.db.layers.staticDB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;

import javax.net.ssl.SSLSocketFactory;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.VirtualFileManager.ReadVirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;

public abstract class LayeredPHPAuthDB extends LayeredPresenceDB {

	private static final String AuthFileName = "auth/index.php";
	private final DatabaseInitData dbData;

	@Override
	public void afterInit() {
		super.afterInit();
		dbData.Files.registerVirtualFile(new VirtualFile(AuthFileName, this.getRootToolchain()) {

			@Override
			public String read(UserContext context) {
				JsonObject obj = new JsonObject();
				obj.add("key", new JsonString(getKeyHash()));
				obj.add("action", new JsonString("GET"));
				String request = obj.getJsonString();
				String str = TinyHTTPSClient.send("raw=" + encode(request));
				if (str != null) {
					if (str.startsWith("OK")) {
						return str.substring(2);
					}
				}
				return "Failed to read remote file";
			}

			@Override
			public boolean write(UserContext context, String newName, String data) {
				JsonObject obj = new JsonObject();
				obj.add("key", new JsonString(getKeyHash()));
				obj.add("action", new JsonString("PUT"));
				obj.add("data", new JsonString(data));
				String request = obj.getJsonString();
				return TinyHTTPSClient.send("raw=" + encode(request)).equals("OK");
			}

		});
	}

	public LayeredPHPAuthDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.dbData = dbData;
	}

	private String getKeyHash() {
		ReadVirtualFile fo = this.loadRootFile("enc.key");
		if (fo != null) {
			try {
				java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
				byte[] array = md.digest(fo.Contents.getBytes(Settings.getDefaultCharset()));
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < array.length; ++i) {
					sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
				}
				return sb.toString().toUpperCase();
			} catch (java.security.NoSuchAlgorithmException e) {
			}
		}
		return "";
	}

	private char toHex(int c) {
		c = c & 0b1111;
		if (c >= 0 && c <= 9) {
			return (char) (c + '0');
		} else if (c >= 10 && c <= 15) {
			return (char) ((c - 10) + 'a');
		}
		return 0;
	}

	private String encode(String data) {
		byte[] bdata = data.getBytes(Settings.getDefaultCharset());
		byte[] result = new byte[bdata.length * 2];

		for (int i = 0; i < bdata.length; i++) {
			int c = bdata[i] & 0xff;
			char c1 = toHex((byte) (c >> 4));
			char c2 = toHex((byte) (c & 0b1111));
			result[i * 2] = (byte) c1;
			result[(i * 2) + 1] = (byte) c2;
		}
		return new String(result, Settings.getDefaultCharset());
	}

	private static class TinyHTTPSClient {

		private String response = "";

		private static SSLSocketFactory sf = null;

		private void ensureSF() {
			if (sf == null) {
				System.out.println("Loading SSL...");
				sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
				System.out.println("Loading SSL finished");
			}
		}

		private TinyHTTPSClient(String data) {
			ensureSF();
			Socket s = null;
			final String endpoint = Settings.getRemoteAuthAPIEndpoint();
			try {
				URI uri = new URI(endpoint);
				s = sf.createSocket(uri.getHost(), 443);
				StringBuilder sb = new StringBuilder();
				sb.append("POST " + uri.getPath() + " HTTP/1.1\r\n");
				sb.append("HOST: " + uri.getHost() + "\r\n");
				sb.append("Content-Length: " + data.length() + "\r\n");
				sb.append("Content-Type: " + "application/x-www-form-urlencoded" + "\r\n");
				sb.append("Connection: Close\r\n");
				sb.append("\r\n");
				sb.append(data);
				OutputStream out = s.getOutputStream();
				out.write(sb.toString().getBytes(Settings.getDefaultCharset()));
				out.flush();
				InputStream in = s.getInputStream();

				// Read header
				boolean chunked = false;
				boolean hasLength = false;
				int length = 0;
				while (true) {
					String line = readLine(in);
					if (line.isEmpty()) {
						break;
					}
					if (line.toLowerCase().contains("Transfer-Encoding".toLowerCase())) {
						if (line.toLowerCase().contains("chunked")) {
							chunked = true;
						}
					} else if (line.toLowerCase().contains("Content-Length".toLowerCase())) {
						hasLength = true;
						line = line.toLowerCase().replace("Content-Length".toLowerCase(), "");
						line = line.replace(":", "").trim();
						length = Integer.parseInt(line);
					}
				}
				StringBuilder sbb = new StringBuilder();
				if (hasLength) {
					readLength(in, length, sbb);
				} else if (chunked) {
					while (true) {
						String line = readLine(in).trim();
						int len = Integer.parseInt(line, 16);
						if (len == 0) {
							break;
						} else {
							readLength(in, len, sbb);
							readLength(in, 2, null);
						}
					}
				}
				response = sbb.toString();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				try {
					s.close();
				} catch (Throwable t) {
				}
			}
		}

		private void readLength(InputStream in, int length, StringBuilder sbb) throws IOException {
			int leftToRead = length;
			while (leftToRead > 0) {
				int i = in.read();
				if (i < 0) {
					return;
				} else {
					if (sbb != null) {
						sbb.append((char) i);
					}
					leftToRead--;
				}
			}
		}

		private String readLine(InputStream in) throws HTTPClientException {
			StringBuilder sb = new StringBuilder();
			try {
				char predchozi = 0;
				while (true) {
					int i = in.read();
					if (i == -1) {
						break;
					}
					char c = (char) i;
					if (c == '\n' && predchozi == '\r') {
						if (sb.length() > 0) {
							sb.setLength(sb.length() - 1);
						}
						break;
					}
					sb.append(c);
					predchozi = c;
				}
				return sb.toString();
			} catch (IOException e) {
				throw new HTTPClientException("Failed to get input stream", e);
			}
		}

		private String read() {
			return response;
		}

		public static String send(String data) {
			return new TinyHTTPSClient(data).read();
		}

	}
}
