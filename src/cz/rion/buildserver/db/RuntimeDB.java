package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.crypto.Crypto;
import cz.rion.buildserver.db.crypto.CryptoManager;
import cz.rion.buildserver.db.layers.LayeredMetaDB;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public class RuntimeDB extends LayeredMetaDB {

	private final Crypto crypto;

	private final Object syncer = new Object();

	public RuntimeDB(String fileName, StaticDB sdb) throws DatabaseException {
		super(fileName, "RuntimeDB");
		crypto = CryptoManager.getCrypto(sdb);
		makeTable("users", KEY("ID"), TEXT("login"));
		makeTable("session", KEY("ID"), TEXT("address"), TEXT("hash"), NUMBER("live"), NUMBER("user_id"), NUMBER("last_action"), NUMBER("creation_time"));
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

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy - HH:mm");

	public class CompletedTest {
		public final int EntryID;
		public final String TestID;
		public final String Code;
		public final String CompletionDateStr;

		private CompletedTest(int entry_id, String test_id, String asm, long completion_date) {
			this.EntryID = entry_id;
			this.TestID = test_id;
			this.Code = asm;
			this.CompletionDateStr = dateFormat.format(new Date(completion_date));
		}
	}

	public List<CompletedTest> getCompletedTests(String login) {
		List<CompletedTest> result = new ArrayList<>();
		String SQL = "SELECT \r\n" + "	users.login as Login,\r\n" + "	compilations.ID as CodeID,\r\n" + "	compilations.test_id as TestID,\r\n" + "	compilations.creation_time as TestTime,\r\n" + "	compilations.asm as Code,\r\n" + "	compilations.code as ResultCode\r\n" + "FROM\r\n" + "	users,\r\n" + "	compilations\r\n" + "\r\n" + "WHERE\r\n" + "	users.ID = compilations.user_id\r\n" + "\r\n" + "AND\r\n" + "	ResultCode = 0\r\n" + "\r\n" + "AND\r\n" + "	TestID != \"\"\r\n" + "AND\r\n" + "	Login = '?'";
		try {
			DatabaseResult res = this.select(SQL, login);
			if (res != null) {
				JsonArray jsn = res.getJSON();
				if (jsn != null) {
					for (JsonValue val : jsn.Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsNumber("CodeID") && obj.containsString("Login") && obj.containsString("TestID") && obj.containsString("Code") && obj.containsNumber("ResultCode")) {
								int entry_id = obj.getNumber("CodeID").Value;
								String test_id = obj.getString("TestID").Value;
								String asm = obj.getString("Code").Value;
								long completion_date = obj.getNumber("TestTime").asLong();
								result.add(new CompletedTest(entry_id, test_id, asm, completion_date));
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return result;
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

	public static String randomstr(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvxyz";
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt((int) (chars.length() * Math.random())));
		}
		return sb.toString();
	}

	private String getAddress(String address) {
		String[] add = address.replaceAll("/", "").split(":");
		if (add.length == 2) { // IPv4 ?
			address = add[0];
		} else { // IPv6 ?
		}
		return address;
	}

	public String storeSession(String address, String authToken) throws Exception {
		address = getAddress(address);

		if (crypto == null) {
			throw new Exception("No crypto");
		}
		String dec = crypto.decrypt(Settings.getAuthKeyFilename(), authToken);
		if (dec == null) {
			throw new Exception("Crypto failed");
		}
		JsonValue val = JsonValue.parse(dec);
		if (val == null) {
			throw new Exception("Auth data not in JSON: " + dec);
		}
		if (val.isObject()) {
			JsonObject obj = val.asObject();
			if (obj.containsString("login") && obj.containsNumber("time")) {
				long time = (obj.getNumber("time").Value & 0xffffffff);
				long now = new Date().getTime() / 1000;
				long diff = now - time;
				if (diff > 5 || diff < 0) { // Auth generated in the future or 15 seconds ago, too old
					throw new Exception("Crypto auth too old (" + diff + " seconds)");
				}

				String login = obj.getString("login").Value;
				synchronized (syncer) {
					int user_id = getUserIDFromLogin(login);
					if (user_id == -1) {
						throw new Exception("Invalid user (looking for " + login + ")");
					}
					// Get live sessions
					JsonArray res = this.select("SELECT * FROM session WHERE user_id = ? AND live = ? AND address = ?", user_id, 1, address).getJSON();
					if (res.Value.size() == 0) { // No such session, create new
						String newSessionToken = randomstr(32);
						while (true) { // Must not exist already
							res = this.select("SELECT * FROM session WHERE hash = '?'", newSessionToken).getJSON();
							if (res.Value.size() == 0) {
								break;
							}
							newSessionToken = randomstr(32);
						}
						if (!this.execute("INSERT INTO session (hash, address, live, user_id, last_action, creation_time) VALUES ('?', '?', ?, ?, ?, ?)", newSessionToken, address, 1, user_id, now, now)) {
							return null;
						}
						res = this.select("SELECT * FROM session WHERE user_id = ?", user_id).getJSON();
						if (res.Value.size() == 0) {
							throw new Exception("Failed to update session in database");
						}
					} else {
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
					throw new Exception("Database error?");
				}
			} else {
				throw new Exception("Invalid auth structure: missing fields");
			}
		} else {
			throw new Exception("No crypto");
		}
	}

	public int getSessionIDFromSession(String address, String session) throws DatabaseException, ChangeOfSessionAddressException {
		address = getAddress(address);
		synchronized (syncer) {
			JsonArray res = this.select("SELECT * FROM session WHERE hash = '?' AND live = ?", session, 1).getJSON();
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("address")) {
						String addr = obj.getString("address").Value;
						if (!addr.equals(address)) {
							throw new ChangeOfSessionAddressException();
						}
						if (obj.containsNumber("ID")) {
							return obj.getNumber("ID").Value;
						}
					}
				}
			}
		}
		return -1;
	}

	public String getLogin(String address, String sessionToken) throws DatabaseException, ChangeOfSessionAddressException {
		address = getAddress(address);
		synchronized (syncer) {
			JsonArray res = this.select("SELECT * FROM session WHERE hash = '?' AND live = ?", sessionToken, 1).getJSON();
			if (res.Value.size() == 1) {
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("address")) {
						String addr = obj.getString("address").Value;
						if (!addr.equals(address)) {
							throw new ChangeOfSessionAddressException();
						}
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
			}
			return null;
		}
	}

	public void deleteSession(String cookiSeession) throws DatabaseException {
		synchronized (syncer) {
			execute("UPDATE session SET live = ? WHERE hash = '?'", 0, cookiSeession);
		}
	}

	public final class RuntimeUserStats {
		public final int UserID;
		public final String Login;

		public final Date RegistrationDate;
		public final Date LastActiveDate;
		public final Date lastLoginDate;
		public final int TotalTestsSubmitted;
		public final String LastTestID;
		public final Date LastTestDate;

		private RuntimeUserStats(int userID, String login, int totalCompilations, Date registrationDate, Date lastLoginDate, Date lastActiveDate, String lastTestID, Date LastTestDate) {
			this.UserID = userID;
			this.Login = login;
			this.RegistrationDate = registrationDate;
			this.LastActiveDate = lastActiveDate;
			this.lastLoginDate = lastLoginDate;
			this.TotalTestsSubmitted = totalCompilations;
			this.LastTestID = lastTestID;
			this.LastTestDate = LastTestDate;
		}
	}

	private static final String getUserStatsSQL = "SELECT\r\n" + "	users.id as UserID,\r\n" + "	users.login as Login,\r\n" + "	s1.creation_time as RegistrationTime,\r\n" + "	s2.creation_time as LastLoginTime,\r\n" + "	cmpLast.test_id as LastTestID,\r\n" + "	cmpLast.creation_time as LastTestTime,\r\n" + "	s2.last_action as LastActionTime,\r\n" + "	count(*) as TotalCompilations\r\n" + "\r\n" + "FROM\r\n" + "	users,\r\n" + "	compilations as cmpAggr,\r\n" + "	compilations as cmpLast,\r\n" + "	session as s1,\r\n" + "	session as s2\r\n" + "\r\n" + "WHERE\r\n" + "	userID = cmpAggr.user_id\r\n" + "AND\r\n" + "	userID = cmpLast.user_id\r\n" + "AND\r\n" + "	cmpLast.ID = (SELECT cmpC.ID FROM compilations as cmpC WHERE user_id = userID ORDER BY creation_time DESC LIMIT 1)\r\n"
			+ "AND\r\n" + "	userID = s1.user_id\r\n" + "AND\r\n" + "	userID = s2.user_id\r\n" + "AND\r\n" + "	s1.ID = (SELECT sc.ID FROM session as sc WHERE user_id = userID ORDER BY sc.last_action ASC LIMIT 1)\r\n" + "AND\r\n" + "	s2.ID = (SELECT sc.ID FROM session as sc WHERE user_id = userID ORDER BY sc.last_action DESC LIMIT 1)\r\n" + "\r\n" + "GROUP BY Login";

	private Date fromInt(long l) {
		int numLen = (l + "").length();
		int msLen = "0000000000000".length();
		if (numLen < msLen) { // stored in seconds, convert to ms
			l *= 1000;
		}
		return new Date(l);
	}

	public List<RuntimeUserStats> getUserStats() {
		List<RuntimeUserStats> stats = new ArrayList<>();
		synchronized (syncer) {
			try {
				DatabaseResult res = select(getUserStatsSQL);
				JsonArray ar = res.getJSON();
				if (ar != null) {
					for (JsonValue val : ar.Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsNumber("UserID") && obj.containsString("Login") && obj.containsNumber("RegistrationTime") && obj.containsNumber("LastLoginTime") && obj.containsString("LastTestID") && obj.containsNumber("LastTestTime") && obj.containsNumber("LastActionTime") && obj.containsNumber("TotalCompilations")) {
								int UserID = obj.getNumber("UserID").Value;
								String Login = obj.getString("Login").Value;
								Date RegistrationTime = fromInt(obj.getNumber("RegistrationTime").asLong());
								Date LastLoginTime = fromInt(obj.getNumber("LastLoginTime").asLong());
								String LastTestID = obj.getString("LastTestID").Value;
								Date LastTestTime = fromInt(obj.getNumber("LastTestTime").asLong());
								Date LastActionTime = fromInt(obj.getNumber("LastActionTime").asLong());
								int TotalCompilations = obj.getNumber("TotalCompilations").Value;
								stats.add(new RuntimeUserStats(UserID, Login, TotalCompilations, RegistrationTime, LastLoginTime, LastActionTime, LastTestID, LastTestTime));
							}
						}
					}
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
				return stats;
			}
		}
		return stats;
	}
}
