package cz.rion.buildserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.wrappers.NasmWrapper;
import cz.rion.buildserver.wrappers.NasmWrapper.RunResult;

public class HTTPClient {

	public static class HTTPResponse {
		public final String protocol;
		public final int code;
		public final String codeDescription;
		public final byte[] data;

		private HTTPResponse(String protocol, int code, String codeDescription, byte[] data) {
			this.protocol = protocol;
			this.code = code;
			this.codeDescription = codeDescription;
			this.data = data;
		}
	}

	public static class HTTPRequest {
		public final String method;
		public final String protocol;
		public final String path;
		public final byte[] data;
		public final Map<String, String> headers;

		private HTTPRequest(String method, String protocol, String path, byte[] data, Map<String, String> headers) {
			this.method = method;
			this.protocol = protocol;
			this.path = path;
			this.data = data;
			this.headers = headers;
		}
	}

	private final Socket client;

	public void close() {
		try {
			client.close();
		} catch (IOException e) {
		}
	}

	public HTTPClient(Socket client) {
		this.client = client;
		try {
			handle(handle(handle()));
		} catch (HTTPClientException e) {
			e.printStackTrace();
		}
		close();
	}

	private String readLine() throws HTTPClientException {
		StringBuilder sb = new StringBuilder();
		try {
			InputStream input = client.getInputStream();
			char predchozi = 0;
			while (true) {
				int i = input.read();
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

	private void handle(HTTPResponse response) throws HTTPClientException {
		try {
			client.getOutputStream().write((response.protocol + " " + response.code + " " + response.codeDescription + "\r\n").getBytes());
			client.getOutputStream().write(("Connection: close\r\n").getBytes());
			client.getOutputStream().write(("Content-Length: " + response.data.length + "\r\n").getBytes());
			client.getOutputStream().write(("\r\n").getBytes());
			client.getOutputStream().write(response.data);
		} catch (IOException e) {
			throw new HTTPClientException("Failed to write response", e);
		}
	}

	private HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		int returnCode = 200;
		String returnCodeDescription = "OK";
		byte[] data = ("\"" + request.path + "\" neumim!").getBytes();
		if (!request.path.equals("/test") && request.method.equals("POST") && request.data.length > 0) {
			data = handleTest(request.data);
		}
		return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data);
	}

	private static int fromHex(char c) throws HTTPClientException {
		if (c >= '0' && c <= '9') {
			return c - '0';
		} else if (c >= 'A' && c <= 'F') {
			return 10 + c - 'A';
		} else if (c >= 'a' && c <= 'f') {
			return 10 + c - 'a';
		} else {
			throw new HTTPClientException("Invalid data item: " + c);
		}
	}

	private static String decode(byte[] data) throws HTTPClientException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < data.length / 2; i++) {
			char c1 = (char) data[i * 2];
			char c2 = (char) data[(i * 2) + 1];
			int x = (fromHex(c1) << 4) | fromHex(c2);
			char c = (char) x;
			sb.append(c);
		}
		return sb.toString();
	}

	private static char toHex(byte c) throws HTTPClientException {
		if (c >= 0 && c <= 9) {
			return (char) (c + '0');
		} else if (c >= 10 && c <= 15) {
			return (char) ((c - 10) + 'a');
		} else {
			throw new HTTPClientException("Invlaid by to encode");
		}
	}

	private static byte[] encode(String data) throws HTTPClientException {
		byte[] bdata = data.getBytes();
		byte[] result = new byte[bdata.length * 2];

		for (int i = 0; i < bdata.length; i++) {
			byte c = bdata[i];
			char c1 = toHex((byte) (c >> 4));
			char c2 = toHex((byte) (c & 4));
			result[i * 2] = (byte) c1;
			result[(i * 2) + 1] = (byte) c2;
		}
		return result;
	}

	private byte[] handleTest(byte[] data) throws HTTPClientException {
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));
		if (data.length % 2 == 0) {
			String jsn = decode(data);
			JsonValue json = JsonValue.parse(jsn);
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					if (obj.containsString("asm")) {

						String asm = obj.getString("asm").Value;
						String stdin = "";
						if (obj.containsString("stdin")) {
							stdin = obj.getString("stdin").Value;
						}

						returnValue = new JsonObject();

						int code = 0;
						String codeDescription = "OK";

						JsonObject nasm = new JsonObject();
						JsonObject link = new JsonObject();
						JsonObject run = new JsonObject();

						run.add("stdin", new JsonString(stdin));
						try {
							RunResult result = NasmWrapper.run("./test02", asm, stdin, 2000);
							run.add("returnCode", new JsonNumber(result.runtime.returnCode));
							run.add("stdout", new JsonString(result.runtime.stdout));
							run.add("stderr", new JsonString(result.runtime.stderr));

							nasm.add("returnCode", new JsonNumber(result.nasm.returnCode));
							nasm.add("stdout", new JsonString(result.nasm.stdout));
							nasm.add("stderr", new JsonString(result.nasm.stderr));

							link.add("returnCode", new JsonNumber(result.golink.returnCode));
							link.add("stdout", new JsonString(result.golink.stdout));
							link.add("stderr", new JsonString(result.golink.stderr));

							returnValue.add("run", run);
							returnValue.add("nasm", nasm);
							returnValue.add("link", link);

						} catch (NasmExecutionException e) {
							code = 1;
							codeDescription = "Failed to run NASM";
							nasm.add("error", new JsonString(e.description));
							returnValue.add("nasm", nasm);
						} catch (GoLinkExecutionException e) {
							code = 1;
							codeDescription = "Failed to run GoLink";
							link.add("error", new JsonString(e.description));
							returnValue.add("link", link);
						} catch (RuntimeExecutionException e) {
							code = 1;
							codeDescription = "Failed to run compiled binary";
							run.add("error", new JsonString(e.description));
							returnValue.add("run", run);
						}

						returnValue.add("code", new JsonNumber(code));
						returnValue.add("result", new JsonString(codeDescription));
					}
				}
			}
		}
		String resutJson = returnValue.getJsonString();
		return encode(resutJson);
	}

	private HTTPRequest handle(String method, String path, String protocol) throws HTTPClientException {
		Map<String, String> header = new HashMap<>();
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
			try {
				client.getInputStream().read(data);
			} catch (IOException e) {
				throw new HTTPClientException("Failed to read request", e);
			}
		}
		return new HTTPRequest(method, protocol, path, data, header);
	}

	private HTTPRequest handle() throws HTTPClientException {
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

}
