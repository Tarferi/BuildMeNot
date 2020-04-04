package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.crypto.Crypto;
import cz.rion.buildserver.db.crypto.CryptoManager;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.CompressionException;
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
		makeTable("session", KEY("ID"), TEXT("address"), TEXT("hash"), NUMBER("live"), NUMBER("user_id"), DATE("last_action"), DATE("creation_time"));
		makeTable("compilations", KEY("ID"), NUMBER("session_id"), TEXT("test_id"), NUMBER("user_id"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"), NUMBER("good_tests"), NUMBER("bad_tests"), BIGTEXT("bad_tests_details"));
		makeTable("pageLoads", KEY("ID"), NUMBER("session_id"), TEXT("address"), NUMBER("port"), TEXT("target"), DATE("creation_time"), NUMBER("result"));
		makeTable("dbV1", KEY("ID"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), TEXT("test_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"));
		makeTable("dbV1Good", KEY("ID"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), TEXT("test_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"));
		makeRetestsTable();
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

		try {
			final String tableName1 = "users";
			final String tableName2 = "compilations";
			TableField[] selectFields = new TableField[] {
					getField(tableName1, "login"),
					getField(tableName2, "ID"),
					getField(tableName2, "test_id"),
					getField(tableName2, "creation_time"),
					getField(tableName2, "asm"),
					getField(tableName2, "code")
			};
			ComparisionField[] comparators = new ComparisionField[] {
					new ComparisionField(getField(tableName2, "code"), 0),
					new ComparisionField(getField(tableName2, "test_id"), "", FieldComparator.NotEquals),
					new ComparisionField(getField(tableName1, "login"), login)
			};
			TableJoin[] joins = new TableJoin[] {
					new TableJoin(getField(tableName1, "ID"), getField(tableName2, "user_id"))
			};
			JsonArray jsn = select(tableName1, selectFields, comparators, joins, true);
			for (JsonValue val : jsn.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("login") && obj.containsString("test_id") && obj.containsString("asm") && obj.containsNumber("code")) {
						int entry_id = obj.getNumber("ID").Value;
						String test_id = obj.getString("test_id").Value;
						String asm = obj.getString("asm").Value;
						long completion_date = obj.getNumber("creation_time").asLong();
						result.add(new CompletedTest(entry_id, test_id, asm, completion_date));
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
			final String tableName = result.contains(":)") ? "dbV1Good" : "dbV1";

			ValuedField[] updateData = new ValuedField[] {
					new ValuedField(this.getField(tableName, "address"), address),
					new ValuedField(this.getField(tableName, "port"), port),
					new ValuedField(this.getField(tableName, "asm"), asm),
					new ValuedField(this.getField(tableName, "test_id"), test_id),
					new ValuedField(this.getField(tableName, "creation_time"), time.getTime()),
					new ValuedField(this.getField(tableName, "code"), resultCode),
					new ValuedField(this.getField(tableName, "result"), result),
					new ValuedField(this.getField(tableName, "full"), full) };
			insert(tableName, updateData);
			// executeExc("INSERT INTO " + table + " (address, port, asm, test_id,
			// creation_time, code, result, full) VALUES ('?', ?, '?', '?', ?, ?, '?',
			// '?');", address, port, asm, test_id, time.getTime(), resultCode, result,
			// full);
		}
	}

	public void storeCompilation(String remoteAddress, Date time, String asm, int session_id, String test_id, int resultCode, String result, String full, int user_id, String details, int good_test, int bad_tests) throws DatabaseException {
		synchronized (syncer) {
			String[] addrParts = reverse(remoteAddress).split(":", 2);
			String address = remoteAddress;
			int port = 0;
			if (addrParts.length == 2) {
				address = reverse(addrParts[1]);
				port = Integer.parseInt(reverse(addrParts[0]));
			}

			final String tableName = "compilations";

			ValuedField[] updateData = new ValuedField[] {
					new ValuedField(this.getField(tableName, "address"), address),
					new ValuedField(this.getField(tableName, "port"), port),
					new ValuedField(this.getField(tableName, "asm"), asm),
					new ValuedField(this.getField(tableName, "test_id"), test_id),
					new ValuedField(this.getField(tableName, "user_id"), user_id),
					new ValuedField(this.getField(tableName, "session_id"), session_id),
					new ValuedField(this.getField(tableName, "creation_time"), time.getTime()),
					new ValuedField(this.getField(tableName, "code"), resultCode),
					new ValuedField(this.getField(tableName, "result"), result),
					new ValuedField(this.getField(tableName, "full"), full),
					new ValuedField(this.getField(tableName, "bad_tests_details"), details),
					new ValuedField(this.getField(tableName, "bad_tests"), bad_tests),
					new ValuedField(this.getField(tableName, "good_tests"), good_test) };
			insert(tableName, updateData);
			// executeExc("INSERT INTO compilations (address, port, asm, test_id, user_id,
			// session_id, creation_time, code, result, full, bad_tests_details, bad_tests,
			// good_tests) VALUES ('?', ?, '?', '?', ?, ?, ?, ?, '?', '?', '?', ?, ?);",
			// address, port, asm, test_id, user_id, session_id, time.getTime(), resultCode,
			// result, full, details, bad_tests, good_test);
		}

	}

	private void makeRetestsTable() throws DatabaseException {
		this.makeTable("retests", KEY("ID"), NUMBER("compilation_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"), NUMBER("good_tests"), NUMBER("bad_tests"), BIGTEXT("bad_tests_details"), NUMBER("original_code"), BIGTEXT("original_result"), BIGTEXT("original_full"));
	}

	public void resetRetestsDB() throws DatabaseException {
		this.dropTable("retests");
		makeRetestsTable();
	}

	public int getUserIDFromLogin(String login) throws DatabaseException {
		synchronized (syncer) {

			final String tableName = "users";
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID") }, false, new ComparisionField(this.getField(tableName, "login"), login));

			int user_id = 0;
			if (res.Value.size() == 0) { // No such user, create
				if (!this.insert("users", new ValuedField(this.getField("users", "login"), login))) {
					return -1;
				}
				// Get the ID
				res = this.select(tableName, new TableField[] { this.getField(tableName, "ID") }, false, new ComparisionField(this.getField(tableName, "login"), login));
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
				if (diff > 5 || diff < -5) { // Auth generated in the future or 15 seconds ago, too old
					if (!obj.getString("login").asString().Value.equals("idvorakt")) {
						throw new Exception("Crypto auth too old (" + diff + " seconds)");
					}
				}

				String login = obj.getString("login").Value;
				final String tableName = "session";
				synchronized (syncer) {
					int user_id = getUserIDFromLogin(login);
					if (user_id == -1) {
						throw new Exception("Invalid user (looking for " + login + ")");
					}
					// Get live sessions

					JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, new ComparisionField(this.getField(tableName, "user_id"), user_id), new ComparisionField(this.getField(tableName, "live"), 1), new ComparisionField(this.getField(tableName, "address"), address));
					if (res.Value.size() == 0) { // No such session, create new
						String newSessionToken = randomstr(32);
						while (true) { // Must not exist already

							res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, new ComparisionField(this.getField(tableName, "hash"), newSessionToken));
							if (res.Value.size() == 0) {
								break;
							}
							newSessionToken = randomstr(32);
						}

						ValuedField[] updateData = new ValuedField[] {
								new ValuedField(this.getField(tableName, "hash"), newSessionToken),
								new ValuedField(this.getField(tableName, "address"), address),
								new ValuedField(this.getField(tableName, "live"), 1),
								new ValuedField(this.getField(tableName, "user_id"), user_id),
								new ValuedField(this.getField(tableName, "last_action"), now),
								new ValuedField(this.getField(tableName, "creation_time"), now) };

						if (!insert(tableName, updateData)) {
							// if (!this.execute("INSERT INTO session (hash, address, live, user_id,
							// last_action, creation_time) VALUES ('?', '?', ?, ?, ?, ?)", newSessionToken,
							// address, 1, user_id, now, now)) {
							return null;
						}
						res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, new ComparisionField(this.getField(tableName, "user_id"), user_id));
						if (res.Value.size() == 0) {
							throw new Exception("Failed to update session in database");
						}
					} else {

						ValuedField[] updateData = new ValuedField[] {
								new ValuedField(this.getField(tableName, "last_action"), now) };
						this.update(tableName, "user_id", user_id, updateData);
						// this.execute("UPDATE session SET last_action = ? WHERE user_id = ?", now,
						// user_id);
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
			final String tableName = "session";
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "address") }, false, new ComparisionField(this.getField(tableName, "hash"), session), new ComparisionField(this.getField(tableName, "live"), 1));
			if (res.Value.size() == 1) {
				refreshSession(session);
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
			final String tableName = "session";
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "address"), this.getField(tableName, "user_id"), this.getField(tableName, "address") }, false, new ComparisionField(this.getField(tableName, "hash"), sessionToken), new ComparisionField(this.getField(tableName, "live"), 1));
			if (res.Value.size() == 1) {
				this.refreshSession(sessionToken);
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
							final String usersTableName = "users";
							res = this.select(usersTableName, new TableField[] { this.getField(usersTableName, "login") }, false, new ComparisionField(this.getField(usersTableName, "ID"), user_id));
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

	private void refreshSession(String sessionToken) {
		final String tableName = "session";
		try {
			this.update(tableName, new String[] { "hash", "live" }, new Object[] { sessionToken, 1 }, new ValuedField(this.getField(tableName, "last_action"), new Date().getTime()));
			// this.execute("UPDATE session SET last_action = ? WHERE hash = '?' AND live =
			// ?", new Date().getTime(), sessionToken, 1);
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public void deleteSession(String cookiSeession) throws DatabaseException {
		final String tableName = "session";
		synchronized (syncer) {
			this.update(tableName, new String[] { "hash" }, new Object[] { cookiSeession, }, new ValuedField(this.getField(tableName, "live"), 0));
			// execute("UPDATE session SET live = ? WHERE hash = '?'", 0, cookiSeession);
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
				DatabaseResult res = select_raw(getUserStatsSQL); // TODO:
				TableField[] fields = new TableField[] {};
				JsonArray ar = res.getJSON(true, fields);
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
			} catch (DatabaseException | CompressionException e) {
				e.printStackTrace();
				return stats;
			}
		}
		return stats;
	}
}
