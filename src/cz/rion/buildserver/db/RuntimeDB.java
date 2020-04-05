package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.crypto.Crypto;
import cz.rion.buildserver.db.crypto.CryptoManager;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public class RuntimeDB extends LayeredMetaDB {

	private final Crypto crypto;

	private final Object syncer_compilations = new Object();
	private final Object syncer_compilations_v1 = new Object();
	private final Object syncer_users = new Object();
	private final Object syncer_sessions = new Object();
	private final Object syncer_compilation_stats = new Object();

	public RuntimeDB(String fileName, StaticDB sdb) throws DatabaseException {
		super(fileName, "RuntimeDB");
		crypto = CryptoManager.getCrypto(sdb);
		makeTable("users", KEY("ID"), TEXT("login"));
		makeTable("session", KEY("ID"), TEXT("address"), TEXT("hash"), NUMBER("live"), NUMBER("user_id"), DATE("last_action"), DATE("creation_time"));
		makeTable("compilations", KEY("ID"), NUMBER("session_id"), TEXT("test_id"), NUMBER("user_id"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"), NUMBER("good_tests"), NUMBER("bad_tests"), BIGTEXT("bad_tests_details"));
		makeTable("pageLoads", KEY("ID"), NUMBER("session_id"), TEXT("address"), NUMBER("port"), TEXT("target"), DATE("creation_time"), NUMBER("result"));
		makeTable("dbV1", KEY("ID"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), TEXT("test_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"));
		makeTable("dbV1Good", KEY("ID"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), TEXT("test_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"));
		makeCompilationStatsTable();
		makeRetestsTable();
	}

	private void makeCompilationStatsTable() throws DatabaseException {
		makeTable("compilation_stats", KEY("ID"), NUMBER("user_id"), NUMBER("good_tests"), NUMBER("good_unique_tests"), NUMBER("total_tests"));
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
		synchronized (syncer_compilations_v1) {
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

	private void createCompilationStats(int user_id) throws DatabaseException {
		synchronized (syncer_compilations) {
			final String compilationsTableName = "compilations";
			final String statsTableName = "compilation_stats";
			int total = 0;
			int unique = 0;
			int good = 0;
			List<String> known = new ArrayList<>();
			JsonArray res = this.select(compilationsTableName, new TableField[] { getField(compilationsTableName, "user_id"), getField(compilationsTableName, "test_id"), getField(compilationsTableName, "code") }, false, new ComparisionField(getField(compilationsTableName, "user_id"), user_id));
			for (JsonValue val : res.Value) {
				int code = val.asObject().getNumber("code").Value;
				String test_id = val.asObject().getString("test_id").Value;
				if (code == 0 && !test_id.equals("")) {
					good++;
				}
				if (!known.contains(test_id)) {
					known.add(test_id);
					unique++;
				}
				total++;
			}
			synchronized (syncer_compilation_stats) {
				this.insert(statsTableName, new ValuedField(getField(statsTableName, "user_id"), user_id), new ValuedField(getField(statsTableName, "good_unique_tests"), unique), new ValuedField(getField(statsTableName, "good_tests"), good), new ValuedField(getField(statsTableName, "total_tests"), total));
			}
		}
	}

	private void storeCompilationStats(int user_id, int code, String test_id, List<CompletedTest> knownCompleted) {
		synchronized (syncer_compilation_stats) {
			final String tableName = "compilation_stats";
			try {
				JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "user_id"), getField(tableName, "good_tests"), getField(tableName, "total_tests"), getField(tableName, "good_unique_tests") }, false);
				if (res.Value.isEmpty()) { // No stats for this user -> create stats from sratch
					createCompilationStats(user_id);
					// Reselect and update
					res = this.select(tableName, new TableField[] { getField(tableName, "user_id"), getField(tableName, "good_tests"), getField(tableName, "total_tests"), getField(tableName, "good_unique_tests") }, false);
				}
				if (res.Value.isEmpty()) { // Failed and shouldn't have
					throw new DatabaseException("Failed to create user stats for user " + user_id);
				}

				// Stats exist -> update
				int id = res.Value.get(0).asObject().getNumber("ID").Value;
				int total = res.Value.get(0).asObject().getNumber("total_tests").Value;
				int good = res.Value.get(0).asObject().getNumber("good_tests").Value;
				int good_unique = res.Value.get(0).asObject().getNumber("good_unique_tests").Value;
				total++;
				good = code == 0 && !test_id.equals("") ? good + 1 : good;
				boolean hasFinishedThisOne = false;
				for (CompletedTest test : knownCompleted) {
					if (test.TestID.equals(test_id)) {
						hasFinishedThisOne = true;
						break;
					}
				}
				if (!hasFinishedThisOne) {
					good_unique++;
				}
				this.update(tableName, id, new ValuedField(getField(tableName, "good_tests"), good), new ValuedField(getField(tableName, "good_unique_tests"), good_unique), new ValuedField(getField(tableName, "total_tests"), total));
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
	}

	public void updateStatsForAllUsers() throws DatabaseException {
		synchronized (syncer_users) {
			synchronized (syncer_compilation_stats) {
				final String tableName = "users";
				this.dropTable("compilation_stats");
				makeCompilationStatsTable();
				JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false);
				int index = 0;
				int total = res.Value.size();
				for (JsonValue val : res.Value) {
					int id = val.asObject().getNumber("ID").Value;
					createCompilationStats(id);
					index++;
					System.out.println("Creating compilations... " + index + "/" + total);
				}
			}
		}
	}

	public void storeCompilation(List<CompletedTest> knownCompleted, String remoteAddress, Date time, String asm, int session_id, String test_id, int resultCode, String result, String full, int user_id, String details, int good_test, int bad_tests) throws DatabaseException {
		synchronized (syncer_compilations) {
			storeCompilationStats(user_id, resultCode, test_id, knownCompleted);
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
		synchronized (syncer_users) {

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
					throw new Exception("Crypto auth too old (" + diff + " seconds)");
				}

				String login = obj.getString("login").Value;
				final String tableName = "session";
				synchronized (syncer_sessions) {
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
		synchronized (syncer_sessions) {
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
		synchronized (syncer_sessions) {
			synchronized (syncer_users) {
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
		synchronized (syncer_sessions) {
			this.update(tableName, new String[] { "hash" }, new Object[] { cookiSeession, }, new ValuedField(this.getField(tableName, "live"), 0));
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
		final String usersTableName = "users";
		final String sessionsTableName = "session";
		final String compilationsTableName = "compilations";
		JsonArray users = null;
		JsonArray sessions = null;
		JsonArray compilations = null;
		synchronized (syncer_users) {
			try {
				users = this.select(usersTableName, new TableField[] { getField(usersTableName, "ID"), getField(usersTableName, "login") }, false);
			} catch (DatabaseException e1) {
				e1.printStackTrace();
			}
		}
		synchronized (syncer_sessions) {
			try {
				sessions = this.select(sessionsTableName, new TableField[] { getField(sessionsTableName, "creation_time"), getField(sessionsTableName, "last_action"), getField(sessionsTableName, "user_id") }, false);
			} catch (DatabaseException e1) {
				e1.printStackTrace();
			}
		}
		synchronized (syncer_compilations) {
			try {
				compilations = this.select(compilationsTableName, new TableField[] { getField(compilationsTableName, "test_id"), getField(compilationsTableName, "user_id"), getField(compilationsTableName, "creation_time") }, false);
			} catch (DatabaseException e1) {
				e1.printStackTrace();
			}
		}
		Map<Integer, String> logins = new HashMap<>();
		Map<Integer, Object[]> compilation_details = new HashMap<>();
		Map<Integer, Object[]> session_details = new HashMap<>();

		for (JsonValue user : users.Value) {
			String login = user.asObject().getString("login").Value;
			int id = user.asObject().getNumber("ID").Value;
			logins.put(id, login);
		}

		for (JsonValue session : sessions.Value) {
			long creation_time = session.asObject().getNumber("creation_time").asLong();
			long last_action = session.asObject().getNumber("last_action").asLong();
			int user_id = session.asObject().getNumber("user_id").Value;
			if (!session_details.containsKey(user_id)) {
				session_details.put(user_id, new Object[] { creation_time, last_action, creation_time });
			} else { // Update last action and creation time
				Object[] obj = session_details.get(user_id);
				long other_creation_time = (long) obj[0];
				long other_last_action = (long) obj[1];
				long other_registration_time = (long) obj[2];
				obj[0] = other_creation_time < creation_time ? other_creation_time : creation_time;
				obj[1] = other_last_action > last_action ? other_last_action : last_action;
				obj[2] = other_registration_time < creation_time ? other_registration_time : creation_time;
			}
		}

		for (JsonValue compilation : compilations.Value) {
			String test_id = compilation.asObject().getString("test_id").Value;
			int user_id = compilation.asObject().getNumber("user_id").Value;
			long creation_time = compilation.asObject().getNumber("creation_time").asLong();
			if (!compilation_details.containsKey(user_id)) {
				compilation_details.put(user_id, new Object[] { creation_time, test_id, 1 });
			} else {
				Object[] obj = compilation_details.get(user_id);
				long other_creation_time = (long) obj[0];
				String other_test_id = (String) obj[1];
				int total_compilations = (int) obj[2];
				obj[0] = other_creation_time > creation_time ? other_creation_time : creation_time;
				obj[1] = other_creation_time > creation_time ? other_test_id : test_id;
				total_compilations++;
				obj[2] = total_compilations;
			}
		}

		for (Entry<Integer, String> entry : logins.entrySet()) {
			int user_id = entry.getKey();
			String login = entry.getValue();
			if (session_details.containsKey(user_id)) {
				if (compilation_details.containsKey(user_id)) {
					Object[] session_obj = session_details.get(user_id);
					Date session_creation_time = fromInt((long) session_obj[0]);
					Date session_last_action = fromInt((long) session_obj[1]);
					Date session_registration_time = fromInt((long) session_obj[2]);

					Object[] compilation_obj = compilation_details.get(user_id);
					Date compilation_creation_time = fromInt((long) compilation_obj[0]);
					String compilation_test_id = (String) compilation_obj[1];
					int total_compilations = (int) compilation_obj[2];
					stats.add(new RuntimeUserStats(user_id, login, total_compilations, session_registration_time, session_creation_time, session_last_action, compilation_test_id, compilation_creation_time));
				}
			}
		}

		stats.sort(new Comparator<RuntimeUserStats>() {

			@Override
			public int compare(RuntimeUserStats o1, RuntimeUserStats o2) {
				return o1.Login.compareTo(o2.Login);
			}
		});
		return stats;
	}
}
