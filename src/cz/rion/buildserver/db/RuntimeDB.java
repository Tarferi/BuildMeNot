package cz.rion.buildserver.db;

import java.util.Date;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.crypto.Crypto;
import cz.rion.buildserver.db.crypto.CryptoManager;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public class RuntimeDB extends SQLiteDB {

	private final Crypto crypto = CryptoManager.getCrypto();

	private final Object syncer = new Object();

	public RuntimeDB(String fileName) throws DatabaseException {
		super(fileName);
		makeTable("users", KEY("ID"), TEXT("login"));
		makeTable("session", KEY("ID"), TEXT("hash"), TEXT("secret"), NUMBER("live"), NUMBER("user_id"), NUMBER("last_action"), NUMBER("creation_time"));
		makeTable("compilations", KEY("ID"), NUMBER("session_id"), TEXT("test_id"), NUMBER("user_id"), TEXT("address"), NUMBER("port"), TEXT("asm"), NUMBER("creation_time"), NUMBER("code"), TEXT("result"), TEXT("full"));
		makeTable("pageLoads", KEY("ID"), NUMBER("session_id"), TEXT("address"), NUMBER("port"), TEXT("target"), NUMBER("creation_time"), NUMBER("result"));
		makeTable("dbV1", KEY("ID"), TEXT("address"), NUMBER("port"), TEXT("asm"), TEXT("test_id"), NUMBER("creation_time"), NUMBER("code"), TEXT("result"), TEXT("full"));
		makeTable("dbV1Good", KEY("ID"), TEXT("address"), NUMBER("port"), TEXT("asm"), TEXT("test_id"), NUMBER("creation_time"), NUMBER("code"), TEXT("result"), TEXT("full"));
	}

	private String reverse(String str) {
		StringBuilder sb = new StringBuilder();
		int len = str.length();
		for (int i = len - 1; i >= 0; i--) {
			char c = str.charAt(i);
			sb.append(c);
		}
		return sb.toString();
	}

	public void storev1Compilation(String remoteAddress, Date time, String asm, String test_id, int resultCode, String result, String full) throws DatabaseException {
		synchronized (syncer) {
			String[] addrParts = reverse(remoteAddress).split(":", 2);
			String address = remoteAddress;
			int port = 0;
			if (addrParts.length == 2) {
				address = reverse(addrParts[1]);
				port = Integer.parseInt(reverse(addrParts[0]));
			}
			String table = result.contains(":)") ? "dbV1Good" : "dbV1";
			executeExc("INSERT INTO " + table + " (address, port, asm, test_id, creation_time, code, result, full) VALUES ('?', ?, '?', '?', ?, ?, '?', '?');", address, port, asm, test_id, time.getTime(), resultCode, result, full);
		}
	}

	public void storeCompilation(String remoteAddress, Date time, String asm, int session_id, String test_id, int resultCode, String result, String full, int user_id) throws DatabaseException {
		synchronized (syncer) {
			String[] addrParts = reverse(remoteAddress).split(":", 2);
			String address = remoteAddress;
			int port = 0;
			if (addrParts.length == 2) {
				address = reverse(addrParts[1]);
				port = Integer.parseInt(reverse(addrParts[0]));
			}
			executeExc("INSERT INTO compilations (address, port, asm, test_id, user_id, session_id, creation_time, code, result, full) VALUES ('?', ?, '?', '?', ?, ?, ?, ?, '?', '?');", address, port, asm, test_id, user_id, session_id, time.getTime(), resultCode, result, full);
		}
	}

	public int getUserIDFromLogin(String login) throws DatabaseException {
		synchronized (syncer) {
			JsonArray res = this.select("SELECT * FROM users WHERE login = '?'", login).getJSON();
			int user_id = 0;
			if (res.Value.size() == 0) { // No such user, create
				if (!execute("INSERT INTO users (login) VALUES ('?')", login)) {
					return -1;
				}
				// Get the ID
				res = this.select("SELECT * FROM users WHERE login = '?'", login).getJSON();
				if (res.Value.size() == 0) {
					return -1;
				}
			}
			// Have result with ID
			JsonValue val = res.Value.get(0);
			if (!val.isObject()) {
				return -1;
			}
			JsonObject obj = val.asObject();
			if (!obj.containsNumber("ID")) {
				return -1;
			}
			user_id = obj.getNumber("ID").Value;
			return user_id;
		}
	}

	private String randomstr(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt((int) (chars.length() * Math.random())));
		}
		return sb.toString();
	}

	public String storeSession(String authToken) throws DatabaseException {
		if (crypto == null) {
			return null;
		}
		String dec = crypto.decrypt(Settings.getAuthKeyFilename(), authToken);
		if (dec == null) {
			return null;
		}
		JsonValue val = JsonValue.parse(dec);
		if (val == null) {
			return null;
		}
		if (val.isObject()) {
			JsonObject obj = val.asObject();
			if (obj.containsString("login")) {
				String login = obj.getString("login").Value;
				synchronized (syncer) {
					int user_id = getUserIDFromLogin(login);
					if (user_id == -1) {
						return null;
					}
					long now = new Date().getTime();
					// Get live sessions
					JsonArray res = this.select("SELECT * FROM session WHERE user_id = ? AND live = ?", user_id, 1).getJSON();
					if (res.Value.size() == 0) { // No such session, create new
						String newSessionToken = randomstr(32);
						while (true) { // Must not exist already
							res = this.select("SELECT * FROM session WHERE hash = '?'", newSessionToken).getJSON();
							if (res.Value.size() == 0) {
								break;
							}
							newSessionToken = randomstr(32);
						}
						if (!this.execute("INSERT INTO session (hash, secret, live, user_id, last_action, creation_time) VALUES ('?', '?', ?, ?, ?, ?)", newSessionToken, "", 1, user_id, now, now)) {
							return null;
						}
						res = this.select("SELECT * FROM session WHERE user_id = ?", user_id).getJSON();
						if (res.Value.size() == 0) {
							return null;
						}
					} else { // Already exists, update last_action
						this.execute("UPDATE session SET last_action = ? WHERE user_id = ?", now, user_id);
					}
					val = res.Value.get(0);
					if (val.isObject()) {
						obj = val.asObject();
						if (obj.containsString("hash")) {
							String hash = obj.getString("hash").Value;
							return hash;
						}
					}
				}
			}
		}
		return null;
	}

	public int getSessionIDFromSession(String session) throws DatabaseException {
		synchronized (syncer) {
			JsonArray res = this.select("SELECT * FROM session WHERE hash = '?' AND live = ?", session, 1).getJSON();
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID")) {
						return obj.getNumber("ID").Value;
					}
				}
			}
		}
		return -1;
	}

	public String getLogin(String sessionToken) throws DatabaseException {
		synchronized (syncer) {
			JsonArray res = this.select("SELECT * FROM session WHERE hash = '?' AND live = ?", sessionToken, 1).getJSON();
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("user_id")) {
						int user_id = obj.getNumber("user_id").Value;
						res = this.select("SELECT * FROM users WHERE ID = ?", user_id).getJSON();
						if (res.Value.size() == 1) {
							val = res.Value.get(0);
							if (val.isObject()) {
								obj = val.asObject();
								if (obj.containsString("login")) {
									String login = obj.getString("login").Value;
									if (login != null) {
										return login;
									}
								}
							}
						}
					}
				}
			}
			return null;
		}
	}

	public void deleteSession(String cookiSeession) throws DatabaseException {
		synchronized (syncer) {
			execute("UPDATE session SET live = ? WHERE hash = '?'", 0, cookiSeession);
		}
	}
}
