package cz.rion.buildserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.SQLiteDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
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

public class HTTPClient {

	public static enum HTTPClientIntentType {
		GET_RESOURCE, GET_HTML, PERFORM_TEST, HACK, ADMIN
	}

	public static class HTTPResponse {
		public final String protocol;
		public final int code;
		public final String codeDescription;
		public final byte[] data;
		public final String contentType;
		public final List<Entry<String, String>> additionalHeaderFields = new ArrayList<>();

		private HTTPResponse(String protocol, int code, String codeDescription, byte[] data, String contentType, List<String> cookieLines) {
			this.protocol = protocol;
			this.code = code;
			this.codeDescription = codeDescription;
			this.data = data;
			this.contentType = contentType;
			for (String str : cookieLines) {
				this.addAdditionalHeaderField("Set-Cookie", str);
			}
		}

		public final void addAdditionalHeaderField(final String key, final String value) {
			Entry<String, String> entry = new Entry<String, String>() {

				private String _key = key;
				private String _value = value;

				@Override
				public String getKey() {
					return _key;
				}

				@Override
				public String getValue() {
					return _value;
				}

				@Override
				public String setValue(String value) {
					_value = value;
					return value;
				}
			};
			additionalHeaderFields.add(entry);
		}
	}

	public static class HTTPRequest {
		public final String method;
		public final String protocol;
		public final String path;
		public final byte[] data;
		public final String authData;
		public final Map<String, String> headers;
		public final List<String> cookiesLines;

		private HTTPRequest(String method, String protocol, String path, byte[] data, Map<String, String> headers, List<String> cookiesLines) {
			String authData = null;
			if (path.contains("/auth/")) {
				String[] np = path.split("/auth/", 2);
				path = np[0] + "/";
				authData = np[1];
				np = authData.split("/", 2);
				if (np.length == 2) { // "http://xxx.yyy/test/abc/auth/123/def
					path += np[1];
					authData = np[0];
				}
			}

			this.method = method;
			this.protocol = protocol;
			this.path = path;
			this.data = data;
			this.headers = headers;
			this.authData = authData;
			this.cookiesLines = cookiesLines;
		}
	}

	private final Socket client;
	private int builderID;
	private final TestManager tests;
	private final RuntimeDB db;
	private final StaticDB sdb;

	private void close() {
		try {
			client.close();
		} catch (IOException e) {
		}
	}

	public HTTPClient(RuntimeDB db, StaticDB sdb, TestManager tests, Socket client) {
		this.client = client;
		this.tests = tests;
		this.db = db;
		this.sdb = sdb;
	}

	private JsonObject returnValue = null;
	private String asm = "";
	private String test_id = "";
	private HTTPClientIntentType intentType = HTTPClientIntentType.HACK;
	private boolean testsPassed = false;
	private int user_id = 0;
	private int session_id = 0;
	private String login = null;

	private String getReducedResult() {
		if (returnValue != null) {
			JsonObject nobj = new JsonObject();
			nobj.add("code", returnValue.asObject().getNumber("code"));
			nobj.add("result", returnValue.asObject().getString("result"));
			nobj.add("test_id", new JsonString(test_id));
			return nobj.getJsonString();
		} else {
			return "{\"code\":1, \"result\":\"compilation failure\"}";
		}
	}

	public void run(int builderID) throws SwitchClientException, DatabaseException {
		this.builderID = builderID;
		boolean keepAlive = false;
		try {
			try {
				handle(handle(handle()));
			} catch (HTTPClientException e) {
			} catch (SwitchClientException e) {
				keepAlive = true;
				throw e;
			}
			if (this.intentType == HTTPClientIntentType.PERFORM_TEST) {
				int code = returnValue.asObject().getNumber("code").Value;
				String result = returnValue.asObject().getString("result").Value;
				db.storeCompilation(client.getRemoteSocketAddress().toString(), new Date(), asm, session_id, test_id, code, result, getReducedResult(), user_id);
			}
		} finally {
			if (!keepAlive) {
				try {
					synchronized (this) {
						this.wait(1000);
					}
					client.getOutputStream().flush();
				} catch (IOException e) {
				} catch (InterruptedException e) {
				} finally {
					close();
				}
			}
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
			if (response.contentType != null) {
				client.getOutputStream().write(("Content-Type: " + response.contentType + "\r\n").getBytes());
			}
			for (Entry<String, String> entry : response.additionalHeaderFields) {
				client.getOutputStream().write((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes());
			}
			client.getOutputStream().write(("Content-Length: " + response.data.length + "\r\n").getBytes());
			client.getOutputStream().write(("\r\n").getBytes());
			client.getOutputStream().write(response.data);
		} catch (IOException e) {
			throw new HTTPClientException("Failed to write response", e);
		}
	}

	private HTTPResponse handleLogout(HTTPRequest request) {
		List<String> cookieSessions = getSessionFromCookie(request);
		for (String cookieSession : cookieSessions) {
			try {
				db.deleteSession(cookieSession);
			} catch (DatabaseException e) {
			}
		}

		List<String> cookieLines = new ArrayList<>();
		cookieLines.add("token=deleted; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT");

		HTTPResponse resp = new HTTPResponse(request.protocol, 307, "Logout", new byte[0], null, cookieLines);
		resp.addAdditionalHeaderField("Location", Settings.getAuthURL() + "?action=logout");
		return resp;
	}

	private List<String> getSessionFromCookie(HTTPRequest request) {
		List<String> vl = new ArrayList<>();
		for (String cookieLine : request.cookiesLines) {
			String[] cookieBunch = cookieLine.split(";");

			for (int i = 0; i < cookieBunch.length; i++) {
				if (cookieBunch[i].contains("=")) {
					String[] kv = cookieBunch[i].split("=", 2);
					if (kv.length == 2) {
						String name = kv[0].trim();
						String value = kv[1].trim();
						if (name.equals(Settings.getCookieName())) { // Session value exists, check if it's still valid
							vl.add(value);
						}
					}
				}
			}
		}
		return vl;
	}

	private HTTPResponse handleAuth(HTTPRequest request) {
		if (!Settings.isAuth()) {
			login = "Anonoymous";
			return null;
		}

		if (request.headers.containsKey("host")) {
			String host = request.headers.get("host");
			if (host.contains("antares.rion.cz")) {
				host = host.replace("antares.rion.cz", "isu.rion.cz");
				HTTPResponse resp = new HTTPResponse(request.protocol, 301, "No more antares", new byte[0], null, new ArrayList<String>());
				resp.addAdditionalHeaderField("Location", "http://" + host);
				return resp;
			}
		}

		// Validate cookie session
		List<String> cookieSeessions = getSessionFromCookie(request);
		if (!cookieSeessions.isEmpty()) {
			List<String> toDelete = new ArrayList<>();
			String goodCookie = null;
			for (String cookieSession : cookieSeessions) {
				if (goodCookie == null) {
					try {
						String login = db.getLogin(cookieSession);
						if (login != null) { // Valid session
							this.session_id = db.getSessionIDFromSession(cookieSession);
							this.login = login;
							this.user_id = db.getUserIDFromLogin(login);
							goodCookie = cookieSession;
							continue;
						}
					} catch (DatabaseException e) {
					}
					toDelete.add(cookieSession);
				} else {
					toDelete.add(cookieSession);
				}
			}

			if (goodCookie != null) { // At least 1 session valid
				return null;
			}
		}

		String redirectLocation = Settings.getAuthURL();
		String redirectMessage = "OK but login first";
		List<String> cookieLines = request.cookiesLines;

		// Validate token session (right after login)
		if (request.authData != null) {
			try {
				String session = db.storeSession(request.authData);
				if (session != null) { // Logged in, set cookie and redirect once more to here
					String host = request.headers.containsKey("host") ? request.headers.get("host") : null;
					if (host != null) {
						redirectLocation = "http://" + host + request.path;
						redirectMessage = "Logged in, redirect once more";
						// Create new cookies
						cookieLines = new ArrayList<>();
						cookieLines.add(Settings.getCookieName() + "=" + session + "; Max-Age=2592000; Domain=isu.rion.cz; Path=/");
					}
				}
			} catch (Exception e) {
			}
		}

		byte[] data = new byte[0];
		HTTPResponse resp = new HTTPResponse(request.protocol, 307, redirectMessage, data, null, cookieLines);
		resp.addAdditionalHeaderField("Location", redirectLocation);
		return resp;
	}

	private HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		int returnCode = 200;
		String type = "multipart/form-data;";
		String returnCodeDescription = "OK";
		HTTPResponse authRedirect = handleAuth(request);

		if (authRedirect != null && request.authData != null) { // Redirect loop
			if (authRedirect.codeDescription.contains("login")) {
				return handleLogout(request);
			}
		}

		boolean supportedClient = false;
		if (request.headers.containsKey("user-agent")) {
			String agent = request.headers.get("user-agent");
			if (agent.contains("Chrome")) {
				supportedClient = true;
			} else if (agent.contains("Trident")) {
				supportedClient = true;
			}
		}

		byte[] data = ("\"" + request.path + "\" neumim!").getBytes();
		if (supportedClient) {
			if (request.path.startsWith("/test?cache=") && request.method.equals("POST") && request.data.length > 0) {
				data = handleTest(request.data, authRedirect != null, request.authData);
				authRedirect = null; // Do not send redirection
			} else if (request.path.equals("/logout")) {
				return handleLogout(request);
			} else if (request.path.startsWith("/") && request.method.equals("GET")) {
				if (authRedirect != null) {
					return authRedirect;
				}

				String endPoint = request.path.substring(1);
				if (endPoint.equals("")) {
					endPoint = "index.html";
				}
				String[] allowed = new String[] { "index.html", "index.css", "index.js" };
				boolean isAllowed = false;
				for (String allow : allowed) {
					if (allow.equals(endPoint)) {
						try {
							String fileContents = MyFS.readFile("./web/" + endPoint);
							data = fileContents.getBytes();
							intentType = HTTPClientIntentType.GET_RESOURCE;
							if (allow.endsWith(".html")) {
								type = "text/html; charset=UTF-8";
								intentType = HTTPClientIntentType.GET_HTML;
							} else if (allow.endsWith(".js")) {
								type = "text/js; charset=UTF-8";
							} else if (allow.endsWith(".css")) {
								type = "text/css";
							}
							if (allow.equals("index.js")) {
								data = fileContents.replace("$IDENTITY_TOKEN$", login).getBytes();
							}
						} catch (FileReadException e) {
							returnCode = 404;
							returnCodeDescription = "Not Found";
							data = ("Nemuzu precist: " + endPoint).getBytes();
						}
						isAllowed = true;
						break;
					}
				}
				if (!isAllowed) {
					intentType = HTTPClientIntentType.GET_RESOURCE;
				}
			}
		} else {
			type = "text/html; charset=UTF-8";
			returnCode = 200;
			returnCodeDescription = "Meh";
			data = "Nepodporovany prohlizec! Pouzij Internet Explorer nebo Google Chrome!".getBytes();
		}
		if (authRedirect != null) {
			return authRedirect;
		}
		return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type, request.cookiesLines);
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

	private byte[] handleTest(byte[] data, boolean authenticationRequired, String authData) throws HTTPClientException {
		if (data[0] == 'q' && data[1] == '=') {
			byte[] newData = new byte[data.length - 2];
			System.arraycopy(data, 2, newData, 0, data.length - 2);
			data = newData;
		}
		intentType = HTTPClientIntentType.PERFORM_TEST;
		returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		if (data.length % 2 == 0) {
			String jsn = decode(data);
			JsonValue json = JsonValue.parse(jsn);
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					boolean authenticated = true;
					if (authenticationRequired) {
						authenticated = false;
						if (authData != null) { // Without auth data, invalid client
							returnValue.add("code", new JsonNumber(53));
							returnValue.add("result", new JsonString("Not logged in"));
						}
					}

					if (authenticated) {
						if (obj.containsString("asm") && obj.containsString("id")) {

							test_id = obj.getString("id").Value;
							asm = obj.getString("asm").Value;

							returnValue = tests.run(builderID, test_id, asm);
							testsPassed = returnValue.containsNumber("code") ? returnValue.getNumber("code").Value == 0 : false;

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
					} else { // Not authenticated
						returnValue.add("code", new JsonNumber(53));
						returnValue.add("result", new JsonString("Not logged in"));
						returnValue.add("authUrl", new JsonString(Settings.getAuthURL()));
					}
				}
			}
		}
		String resutJson = returnValue.getJsonString();
		return encode(resutJson);
	}

	private HTTPRequest handle(String method, String path, String protocol) throws HTTPClientException, SwitchClientException {
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
					client.getOutputStream().write(42);
				} catch (IOException e) {
					throw new HTTPClientException("Socket write error", e);
				}
				intentType = HTTPClientIntentType.ADMIN;
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
			try {
				client.getInputStream().read(data);
			} catch (IOException e) {
				throw new HTTPClientException("Failed to read request", e);
			}
		}
		return new HTTPRequest(method, protocol, path, data, header, cookiesLines);
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

	public HTTPClientIntentType getIntent() {
		return intentType;
	}

	public boolean haveTestsPassed() {
		return testsPassed;
	}

	public int getUserID() {
		return user_id;
	}
}
