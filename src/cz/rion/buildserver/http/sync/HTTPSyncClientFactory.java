package cz.rion.buildserver.http.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.NoFurhterParseException;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.CompatibleSocketClient;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.http.HTTPResponseWriter;
import cz.rion.buildserver.http.server.HTTPClientFactory;

public class HTTPSyncClientFactory implements HTTPClientFactory, HTTPResponseWriter {

	private final CompatibleSocketClient client;
	private final boolean isSSL;

	public HTTPSyncClientFactory(CompatibleSocketClient client, boolean isSSL) {
		this.client = client;
		this.isSSL = isSSL;
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

	private String getAddress(String address) {
		String[] add = address.replaceAll("/", "").split(":");
		if (add.length == 2) { // IPv4 ?
			address = add[0];
		} else { // IPv6 ?
			address = address.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("/", "");
			String[] parts = address.split(":");
			int last = Integer.parseInt(parts[parts.length - 1]);
			if (last > 0xff) {
				address = address.substring(0, address.lastIndexOf(":"));
			}
		}
		return address;
	}

	private HTTPRequest handle(String method, String path, String protocol) throws HTTPClientException, SwitchClientException, NoFurhterParseException {
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
				throw new SwitchClientException(client);
			} else {
				throw new HTTPClientException("Invalid authentication");
			}
		}
		if ((!method.equals("GET") && !method.equals("POST")) || (!protocol.equals("HTTP/1.1") && !protocol.equals("HTTP/1.0"))) {
			throw new HTTPClientException("Invalid method or protocol (method is \"" + method + "\", protocol is \"" + protocol + "\")");
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
		HTTPRequest req = new HTTPRequest(method, host, protocol, path, data, header, cookiesLines, getAddress(client.getRemoteSocketAddress()), isSSL);
		return req;
	}

	private HTTPRequest handle() throws HTTPClientException, SwitchClientException, NoFurhterParseException {
		String header = readLine();
		String[] headerSplit = header.split(" ");
		if (headerSplit.length < 3) {
			throw new HTTPClientException("Not a HTTP request (Header line: \"" + header + "\"");
		}
		String method = headerSplit[0];
		String protocol = headerSplit[headerSplit.length - 1];
		String path = header.substring(method.length() + 1);
		path = path.substring(0, path.length() - (protocol.length() + 1));
		return handle(method, path, protocol);
	}

	@Override
	public HTTPRequest getRequest() throws HTTPClientException, SwitchClientException {
		try {
			return handle();
		} catch (NoFurhterParseException e) {
			throw new HTTPClientException("Failed to create request", e);
		}
	}

	@Override
	public void close() {
		try {
			client.close();
		} catch (Throwable t) {
		}
	}

	@Override
	public void writeResponse(HTTPResponse response) throws HTTPClientException {
		response.write(this);
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		client.writeSync(bytes);
	}

	@Override
	public String getRemoteAddress() {
		return client.getRemoteSocketAddress();
	}

}
