package cz.rion.buildserver.http;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.permissions.WebPermission;
import cz.rion.buildserver.test.TestManager;

public class HTTPAuthClient extends HTTPTestClient {

	private final CompatibleSocketClient client;
	private final RuntimeDB db;

	protected HTTPAuthClient(CompatibleSocketClient client, int BuilderID, RuntimeDB db, StaticDB sdb, TestManager tests) {
		super(client, BuilderID, db, sdb, tests);
		this.client = client;
		this.db = db;
	}

	protected HTTPResponse handleLogout(HTTPRequest request) {
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

	@Override
	protected String handleJSManipulation(String js) {
		js = super.handleJSManipulation(js);
		js = js.replace("$IDENTITY_TOKEN$", getPermissions().getIdentity().getJsonString());
		return js;
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

	protected HTTPResponse handleAuth(HTTPRequest request) {
		if (!Settings.isAuth()) {
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
						String login = db.getLogin(client.getRemoteSocketAddress().toString(), cookieSession);
						if (login != null) { // Valid session
							int session_id = db.getSessionIDFromSession(client.getRemoteSocketAddress().toString(), cookieSession);
							loadPermissions(session_id, login);
							goodCookie = cookieSession;
							continue;
						}
					} catch (ChangeOfSessionAddressException e) { // Compromised session, delete
						try {
							this.db.deleteSession(cookieSession);
						} catch (DatabaseException e1) {
						}
						toDelete.add(cookieSession);
					} catch (DatabaseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
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

		String redirectLocation = Settings.getAuthURL() + "?cache=" + RuntimeDB.randomstr(32);
		String redirectMessage = "OK but login first";
		List<String> cookieLines = request.cookiesLines;

		// Validate token session (right after login)
		if (request.authData != null) {
			try {
				String session = db.storeSession(client.getRemoteSocketAddress().toString(), request.authData);
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
				e.printStackTrace();
			}
		}

		byte[] data = new byte[0];
		HTTPResponse resp = new HTTPResponse(request.protocol, 307, redirectMessage, data, null, cookieLines);
		resp.addAdditionalHeaderField("Location", redirectLocation);
		return resp;
	}

	@Override
	protected HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		if (request.path.equals("/logout")) {
			return handleLogout(request);
		}

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
			} else if (!allow(WebPermission.SeeFireFox)) {
				supportedClient = true;
			}
		}
		if (!supportedClient) {
			String type = "text/html; charset=UTF-8";
			int returnCode = 200;
			String returnCodeDescription = "Meh";
			String data = "Nepodporovany prohlizec! Pouzij Internet Explorer nebo Google Chrome!";
			return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type, request.cookiesLines);
		}

		if (authRedirect != null && !objectionsAgainstRedirectoin(request)) {
			return authRedirect;
		}

		return super.handle(request);
	}
}