package cz.rion.buildserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.MyDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.AsmTest;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;
import cz.rion.buildserver.wrappers.NasmWrapper;
import cz.rion.buildserver.wrappers.NasmWrapper.RunResult;

public class HTTPClient {

	public static class HTTPResponse {
		public final String protocol;
		public final int code;
		public final String codeDescription;
		public final byte[] data;
		public final String contentType;

		private HTTPResponse(String protocol, int code, String codeDescription, byte[] data, String contentType) {
			this.protocol = protocol;
			this.code = code;
			this.codeDescription = codeDescription;
			this.data = data;
			this.contentType = contentType;
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
	private int builderID;
	private final TestManager tests;

	private void close() {
		try {
			client.close();
		} catch (IOException e) {
		}
	}

	public HTTPClient(TestManager tests, Socket client) {
		this.client = client;
		this.tests = tests;
	}

	private JsonObject returnValue = null;
	private String asm = "";
	private String test_id = "";

	public void run(MyDB db, int builderID) throws SwitchClientException, DatabaseException {
		this.builderID = builderID;
		try {
			try {
				handle(handle(handle()));
			} catch (HTTPClientException e) {
			}
			db.storeCompilation(client.getRemoteSocketAddress().toString(), new Date(), asm, returnValue.getJsonString());
		} finally {
			close();
		}
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
			client.getOutputStream().write(("Content-Type: " + response.contentType + "\r\n").getBytes());
			client.getOutputStream().write(("Content-Length: " + response.data.length + "\r\n").getBytes());
			client.getOutputStream().write(("\r\n").getBytes());
			client.getOutputStream().write(response.data);
		} catch (IOException e) {
			throw new HTTPClientException("Failed to write response", e);
		}
	}

	private HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		int returnCode = 200;
		String type = "multipart/form-data;";
		String returnCodeDescription = "OK";
		byte[] data = ("\"" + request.path + "\" neumim!").getBytes();
		if (request.path.equals("/test") && request.method.equals("POST") && request.data.length > 0) {
			data = handleTest(request.data);
		} else if (request.path.startsWith("/") && request.method.equals("GET")) {
			String endPoint = request.path.substring(1);
			if (endPoint.equals("")) {
				endPoint = "index.html";
			}
			String[] allowed = new String[] { "index.html", "index.css", "index.js" };
			for (String allow : allowed) {
				if (allow.equals(endPoint)) {
					try {
						String fileContents = MyFS.readFile("./web/" + endPoint);
						data = fileContents.getBytes();
						if (allow.endsWith(".html")) {
							type = "text/html; charset=UTF-8";
						} else if (allow.endsWith(".js")) {
							type = "text/js; charset=UTF-8";
						} else if (allow.endsWith(".css")) {
							type = "text/css";
						}
					} catch (FileReadException e) {
						returnCode = 404;
						returnCodeDescription = "Not Found";
						data = ("Nemuzu precist: " + endPoint).getBytes();
					}
					break;
				}
			}
		}

		return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type);
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

	private static char toHex(int c) throws HTTPClientException {
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
			int c = bdata[i] & 0xff;
			char c1 = toHex((byte) (c >> 4));
			char c2 = toHex((byte) (c & 0b1111));
			result[i * 2] = (byte) c1;
			result[(i * 2) + 1] = (byte) c2;
		}
		return result;
	}

	private byte[] handleTest(byte[] data) throws HTTPClientException {
		if (data[0] == 'q' && data[1] == '=') {
			byte[] newData = new byte[data.length - 2];
			System.arraycopy(data, 2, newData, 0, data.length - 2);
			data = newData;
		}
		returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));
		if (data.length % 2 == 0) {
			String jsn = decode(data);
			JsonValue json = JsonValue.parse(jsn);
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					if (obj.containsString("asm") && obj.containsString("id")) {

						test_id = obj.getString("id").Value;
						asm = obj.getString("asm").Value;

						returnValue = tests.run(builderID, test_id, asm);

						/*
						 * String stdin = ""; if (obj.containsString("stdin")) { stdin =
						 * obj.getString("stdin").Value; }
						 * 
						 * returnValue = new JsonObject();
						 * 
						 * int code = 0; String codeDescription = "OK";
						 * 
						 * JsonObject nasm = new JsonObject(); JsonObject link = new JsonObject();
						 * JsonObject run = new JsonObject();
						 * 
						 * run.add("stdin", new JsonString(stdin)); try { RunResult result =
						 * NasmWrapper.run("./test" + builderID, asm, stdin, 2000);
						 * run.add("returnCode", new JsonNumber(result.runtime.returnCode));
						 * run.add("stdout", new JsonString(result.runtime.stdout)); run.add("stderr",
						 * new JsonString(result.runtime.stderr));
						 * 
						 * nasm.add("returnCode", new JsonNumber(result.nasm.returnCode));
						 * nasm.add("stdout", new JsonString(result.nasm.stdout)); nasm.add("stderr",
						 * new JsonString(result.nasm.stderr));
						 * 
						 * link.add("returnCode", new JsonNumber(result.golink.returnCode));
						 * link.add("stdout", new JsonString(result.golink.stdout)); link.add("stderr",
						 * new JsonString(result.golink.stderr));
						 * 
						 * returnValue.add("run", run); returnValue.add("nasm", nasm);
						 * returnValue.add("link", link);
						 * 
						 * } catch (NasmExecutionException e) { code = 1; codeDescription =
						 * "Failed to run NASM"; nasm.add("error", new JsonString(e.description));
						 * returnValue.add("nasm", nasm); } catch (GoLinkExecutionException e) { code =
						 * 1; codeDescription = "Failed to run GoLink"; link.add("error", new
						 * JsonString(e.description)); returnValue.add("link", link); } catch
						 * (RuntimeExecutionException e) { code = 1; codeDescription =
						 * "Failed to run compiled binary"; run.add("error", new
						 * JsonString(e.description)); returnValue.add("run", run); }
						 * 
						 * returnValue.add("code", new JsonNumber(code)); returnValue.add("result", new
						 * JsonString(codeDescription));
						 */
					} else if (obj.containsString("action")) {
						String act = obj.getString("action").Value;
						if (act.equals("COLLECT")) {

							List<AsmTest> tsts = tests.getAllTests();
							List<JsonValue> d = new ArrayList<>();

							for (AsmTest tst : tsts) {
								JsonObject tobj = new JsonObject();
								// {"title":"TEST1", "init": "tohle je uvodni cast", "zadani":"Implementujte XXX
								// YYY", "id": "test01"}
								tobj.add("title", new JsonString(tst.getTitle()));
								tobj.add("zadani", new JsonString(tst.getDescription()));
								tobj.add("init", new JsonString(tst.getInitialCode()));
								tobj.add("id", new JsonString(tst.getID()));
								d.add(tobj);
							}

							returnValue.add("code", new JsonNumber(0));
							returnValue.add("tests", new JsonArray(d));
						}
					}
				}
			}
		}
		String resutJson = returnValue.getJsonString();
		return encode(resutJson);
	}

	private HTTPRequest handle(String method, String path, String protocol) throws HTTPClientException, SwitchClientException {
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
		if (method.equals("AUTH"))
			if (path.equals(Settings.getPasscode())) {
				try {
					client.getOutputStream().write(42);
				} catch (IOException e) {
					throw new HTTPClientException("Socket write error", e);
				}
				throw new SwitchClientException(client);
			} else {
				throw new HTTPClientException("Invalid authentication");
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

}
