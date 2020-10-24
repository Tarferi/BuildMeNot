package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.RuntimeDB.BypassedClient;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.http.HTTPResponse;

public class StatelessAuthClient extends StatelessTestClient {

	protected StatelessAuthClient(StatelessInitData data) {
		super(data);
	}

	private HTTPResponse handleLogout(ProcessState state) {
		List<String> cookieSessions = getSessionFromCookie(state);
		for (String cookieSession : cookieSessions) {
			try {
				state.Data.RuntimeDB.deleteSession(cookieSession);
			} catch (DatabaseException e) {
			}
		}

		List<String> cookieLines = new ArrayList<>();
		cookieLines.add("token=deleted; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT");

		HTTPResponse resp = new HTTPResponse(state.Request.protocol, 307, "Logout", new byte[0], null, cookieLines);
		String tc = state.Request.host.trim().equals("meet.rion.cz") ? "bongo" : state.Toolchain.getName();
		resp.addAdditionalHeaderField("Location", Settings.getAuthURL(tc, state.Request.host) + "?action=logout");
		return resp;
	}

	@Override
	protected String handleJSManipulation(ProcessState state, String path, String js) {
		js = super.handleJSManipulation(state, path, js);
		js = js.replace("$IDENTITY_TOKEN$", state.getPermissions().getIdentity().getJsonString());
		return js;
	}

	private List<String> getSessionFromCookie(ProcessState state) {
		List<String> vl = new ArrayList<>();
		for (String cookieLine : state.Request.cookiesLines) {
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

	protected HTTPResponse handleAuth(ProcessState state) {
		if (!Settings.isAuth()) {
			loadPermissions(state, 0, Settings.GetDefaultUsername());
			return null;
		}

		if (state.Request.headers.containsKey("host")) {
			String host = state.Request.headers.get("host");
			if (host.contains("antares.rion.cz")) {
				host = host.replace("antares.rion.cz", "isu.rion.cz");
				HTTPResponse resp = new HTTPResponse(state.Request.protocol, 301, "No more antares", new byte[0], null, new ArrayList<String>());
				resp.addAdditionalHeaderField("Location", "https://" + host);
				return resp;
			}
		}

		// Validate cookie session
		List<String> cookieSeessions = getSessionFromCookie(state);
		if (!cookieSeessions.isEmpty()) {
			List<String> toDelete = new ArrayList<>();
			String goodCookie = null;
			for (String cookieSession : cookieSeessions) {
				if (goodCookie == null) {
					try {
						String login = state.Data.RuntimeDB.getLogin(state.Request.remoteAddress, cookieSession);
						if (login != null) { // Valid session
							int session_id = state.Data.RuntimeDB.getSessionIDFromSession(state.Request.remoteAddress, cookieSession, false);
							loadPermissions(state, session_id, login);
							goodCookie = cookieSession;
							continue;
						}
					} catch (ChangeOfSessionAddressException e) { // Compromised session, delete
						try {
							state.Data.RuntimeDB.deleteSession(cookieSession);
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

		String tc = state.Request.host.trim().equals("meet.rion.cz") ? "bongo" : state.Toolchain.getName();
		String redirectLocation = Settings.getAuthURL(tc, state.Request.host) + "?cache=" + RuntimeDB.randomstr(32);

		String redirectMessage = "OK but login first";
		List<String> cookieLines = state.Request.cookiesLines;

		try {
			BypassedClient bypass = state.Data.RuntimeDB.getBypassedClientData(state.Request.remoteAddress);
			if (bypass != null) {
				String cookieSession = state.Data.RuntimeDB.storeSessionKnownLogin(state.Request.remoteAddress, "Bypassed", bypass.Login, true);
				int session_id = state.Data.RuntimeDB.getSessionIDFromSession(state.Request.remoteAddress, cookieSession, true);
				loadPermissions(state, session_id, bypass.Login);
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Validate token session (right after login)
		if (state.Request.authData != null) {
			try {
				String session = state.Data.RuntimeDB.storeSession(state.Request.remoteAddress, state.Request.authData);
				if (session != null) { // Logged in, set cookie and redirect once more to here
					String host = state.Request.headers.containsKey("host") ? state.Request.headers.get("host") : null;
					if (host != null) {
						redirectLocation = "https://" + host + state.Request.path;
						redirectMessage = "Logged in, redirect once more";
						// Create new cookies
						cookieLines = new ArrayList<>();
						cookieLines.add(Settings.getCookieName() + "=" + session + "; Max-Age=2592000; Domain=" + host + "; Path=/");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		byte[] data = new byte[0];
		HTTPResponse resp = new HTTPResponse(state.Request.protocol, 307, redirectMessage, data, null, cookieLines);
		resp.addAdditionalHeaderField("Location", redirectLocation);

		return resp;
	}

	@Override
	protected HTTPResponse handle(ProcessState state) {
		if (state.Request.path.equals("/logout")) {
			return handleLogout(state);
		}

		HTTPResponse authRedirect = handleAuth(state);

		if (authRedirect != null && state.Request.authData != null) { // Redirect loop
			if (authRedirect.codeDescription.contains("login")) {
				return handleLogout(state);
			}
		}

		if (authRedirect != null && !objectionsAgainstAuthRedirection(state)) {
			return authRedirect;
		}

		return super.handle(state);
	}

}
