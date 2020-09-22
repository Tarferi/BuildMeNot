package cz.rion.buildserver.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.exceptions.SwitchClientException;

public abstract class HTTPParserClient extends HTTPPermissionClient {

	public final int BuilderID;
	private final CompatibleSocketClient client;
	private Toolchain toolchain = null;
	private final StaticDB sdb;

	protected HTTPParserClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		super(client, BuilderID, rdb, sdb);
		this.client = client;
		this.BuilderID = BuilderID;
		this.sdb = sdb;
	}

	protected boolean objectionsAgainstRedirection(HTTPRequest request) {
		return false;
	}

	private Toolchain getToolchain(HTTPRequest request) {
		if (toolchain == null) {
			String toolchain = sdb.getToolchainMapping(request.host);
			try {
				this.toolchain = sdb.getToolchain(toolchain == null ? "" : toolchain);
			} catch (NoSuchToolchainException e) {
				e.printStackTrace();
			}
		}
		return toolchain;
	}

	protected void handle(HTTPResponse response) throws HTTPClientException {
		try {
			client.writeSync((response.protocol + " " + response.code + " " + response.codeDescription + "\r\n").getBytes(Settings.getDefaultCharset()));
			client.writeSync(("Connection: close\r\n").getBytes(Settings.getDefaultCharset()));
			if (response.contentType != null) {
				client.writeSync(("Content-Type: " + response.contentType + "\r\n").getBytes(Settings.getDefaultCharset()));
			}
			for (Entry<String, String> entry : response.additionalHeaderFields) {
				client.writeSync((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes(Settings.getDefaultCharset()));
			}
			client.writeSync(("Content-Length: " + response.data.length + "\r\n").getBytes(Settings.getDefaultCharset()));
			client.writeSync(("\r\n").getBytes(Settings.getDefaultCharset()));
			client.writeSync(response.data);
		} catch (IOException e) {
			throw new HTTPClientException("Failed to write response", e);
		}
	}

	private String readLine() throws HTTPClientException {
		StringBuilder sb = new StringBuilder();
		try {
			char predchozi = 0;
			while (true) {
				int i = client.readSync();
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

	private boolean read(byte[] target) {
		try {
			int needed = target.length;
			while (needed > 0) {
				int read = client.readSync(target, target.length - needed, needed);
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

	protected String handleJSManipulation(String host, String path, String js) {
		return js;
	}

	protected String handleHTMLManipulation(String host, String path, String html) {
		return html;
	}

	protected HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		int returnCode = 200;
		String type = "multipart/form-data;";
		String returnCodeDescription = "OK";
		return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, "Not implemented", type, request.cookiesLines);
	}

	protected HTTPRequest handle(String method, String path, String protocol) throws HTTPClientException, SwitchClientException {
		Map<String, String> header = new HashMap<>();
		List<String> cookiesLines = new ArrayList<>();
		while (true) {
			String headerLine = readLine();
			if (headerLine.isEmpty()) {
				break;
			}
			if (!headerLine.contains(":")) {
				throw new HTTPClientException("Missing header item delimiter");
			}
			String[] headerData = headerLine.split(":", 2);
			if (headerData.length != 2) {
				throw new HTTPClientException("Missing header item delimiter");
			}
			String name = headerData[0].trim();
			String value = headerData[1].trim();
			header.put(name.toLowerCase(), value);
			if (name.toLowerCase().equals("cookie")) {
				cookiesLines.add(value);
			}
		}
		if (method.equals("AUTH")) {
			if (path.equals(Settings.getPasscode())) {
				try {
					client.writeSync(42);
				} catch (IOException e) {
					throw new HTTPClientException("Socket write error", e);
				}
				this.setIntention(HTTPClientIntentType.ADMIN);
				throw new SwitchClientException(client);
			} else {
				throw new HTTPClientException("Invalid authentication");
			}
		}
		if ((!method.equals("GET") && !method.equals("POST")) || !protocol.equals("HTTP/1.1")) {
			throw new HTTPClientException("Invalid method or protocol");
		}
		byte[] data = new byte[0];
		if (header.containsKey("content-length")) {
			String strLen = header.get("content-length");
			boolean parsed = false;
			int dataLength = 0;
			try {
				dataLength = Integer.parseInt(strLen);
				parsed = true;
			} catch (Exception e) {
			}
			if (!parsed) {
				throw new HTTPClientException("Failed to parse content-length: " + strLen);
			}
			int maxLength = 1024 * 1024 * 10;
			if (dataLength > maxLength) {
				throw new HTTPClientException("Request too big: " + strLen);
			}
			data = new byte[dataLength];
			if (!read(data)) {
				throw new HTTPClientException("Failed to read request");
			}
		}
		if (!header.containsKey("host")) {
			throw new HTTPClientException("Invalid hostname");
		}
		String host = header.get("host");
		HTTPRequest req = new HTTPRequest(method, host, protocol, path, data, header, cookiesLines);
		ToolChainKnown(getToolchain(req));
		return req;
	}

	private HTTPRequest handle() throws HTTPClientException, SwitchClientException {
		String header = readLine();
		String[] headerSplit = header.split(" ");
		if (headerSplit.length < 3) {
			throw new HTTPClientException("Not a HTTP request");
		}
		String method = headerSplit[0];
		String protocol = headerSplit[headerSplit.length - 1];
		String path = header.substring(method.length() + 1);
		path = path.substring(0, path.length() - (protocol.length() + 1));
		return handle(method, path, protocol);
	}

	public void run() throws SwitchClientException, DatabaseException, IOException {
		boolean keepAlive = false;
		try {
			try {
				handle(handle(handle()));
			} catch (HTTPClientException e) {
			} catch (SwitchClientException e) {
				keepAlive = true;
				throw e;
			}

		} finally {
			if (!keepAlive) {
				try {
					synchronized (this) {
						this.wait(10);
					}
					client.flush();
				} catch (IOException e) {
				} catch (InterruptedException e) {
				} finally {
					close();
				}
			}
		}
	}

	private void close() {
		try {
			client.close();
		} catch (Throwable t) {
		}
	}

	public String getAddress() {
		return client.getRemoteSocketAddress();
	}

}
