package cz.rion.buildserver.db;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.VirtualFileManager.ReadVirtualFile;
import cz.rion.buildserver.db.crypto.Crypto;
import cz.rion.buildserver.db.crypto.CryptoException;
import cz.rion.buildserver.db.crypto.CryptoManager;
import cz.rion.buildserver.db.layers.common.LayeredDBFileWrapperDB;
import cz.rion.buildserver.db.layers.common.LayeredMetaDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredStaticDB.ToolchainCallback;
import cz.rion.buildserver.exceptions.ChangeOfSessionAddressException;
import cz.rion.buildserver.exceptions.CompressionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonValuable;
import cz.rion.buildserver.utils.CachedData;

public class RuntimeDB extends LayeredMetaDB {

	private final Crypto crypto;

	private final Object syncer_compilations = new Object();
	private final Object syncer_users = new Object();
	private final Object syncer_sessions = new Object();
	private final Object syncer_compilation_stats = new Object();
	private final Object syncer_user_timeouts = new Object();

	private final StaticDB sdb;

	private final DatabaseInitData data;

	public RuntimeDB(DatabaseInitData dbData, StaticDB sdb) throws DatabaseException {
		super(dbData, "RuntimeDB");
		this.sdb = sdb;
		this.data = dbData;
		crypto = CryptoManager.getCrypto(sdb);
		CachedBypassedClientData = new CachedBypassedClientDataCls(sdb);
		new StatDB(this);
		makeTable("users", false, KEY("ID"), TEXT("login"), TEXT("toolchain"));
		makeTable("session", false, KEY("ID"), TEXT("address"), TEXT("hash"), NUMBER("live"), NUMBER("user_id"), DATE("last_action"), DATE("creation_time"), TEXT("toolchain"));
		makeTable("compilations", false, KEY("ID"), NUMBER("session_id"), TEXT("toolchain"), TEXT("test_id"), NUMBER("user_id"), TEXT("address"), NUMBER("port"), BIGTEXT("asm"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"), NUMBER("good_tests"), NUMBER("bad_tests"), BIGTEXT("bad_tests_details"));
		makeTable("compilations_feedback", false, KEY("ID"), NUMBER("compilation_id"), NUMBER("author_id"), BIGTEXT("data"), DATE("creation_time"), NUMBER("valid"), TEXT("toolchain"));
		makeTable("page_loads", false, KEY("ID"), NUMBER("session_id"), TEXT("toolchain"), TEXT("address"), TEXT("host"), TEXT("path"), DATE("creation_time"), NUMBER("result"));
		makeTable("crypto_expire_log", true, KEY("ID"), TEXT("address"), BIGTEXT("crypto"), DATE("creation_time"), TEXT("description"), TEXT("toolchain"));
		makeTable("user_timeouts", false, KEY("ID"), NUMBER("user_id"), DATE("last_time"), DATE("allow_next"), NUMBER("bad_instr"), NUMBER("bad_segfaults"), NUMBER("bad_base"), NUMBER("bad_uncompilable"), NUMBER("live"), TEXT("toolchain"));
		makeCompilationStatsTable();
		makeRetestsTable();
		LayeredDBFileWrapperDB.initTableFiles(sdb, this, dbData.Files, sdb.getRootToolchain(), sdb.getSharedToolchain());
	}

	public final static class TestFeedback implements JsonValuable {
		public final int ID;
		public final int AuthorID;
		public final String AuthorLogin;
		public final String AuthorFullName;
		public final String Toolchain;
		public final JsonValue Data;
		public final long CreationTime;

		public final int CurrentUserID;
		public final boolean CanEditAnything;

		public TestFeedback(int id, int authorID, String authorLogin, String authorFullName, String toolchain, JsonValue data, long creationTime, int cuid, boolean admin) {
			this.ID = id;
			this.AuthorID = authorID;
			this.Toolchain = toolchain;
			this.AuthorFullName = authorFullName;
			this.AuthorLogin = authorLogin;
			this.Data = data;
			this.CreationTime = creationTime;
			this.CurrentUserID = cuid;
			this.CanEditAnything = admin;
		}

		@Override
		public JsonValue getValue() {
			JsonObject obj = new JsonObject();
			obj.add("ID", ID);
			obj.add("AuthorID", AuthorID);
			obj.add("AuthorLogin", AuthorLogin);
			obj.add("AuthorName", AuthorFullName);
			obj.add("Toolchain", Toolchain);
			obj.add("Data", Data);
			obj.add("Creation", CreationTime);
			obj.add("Editable", CurrentUserID == AuthorID || CanEditAnything);
			return obj;
		}
	}

	public final static class TestHistory implements JsonValuable {
		public final int ID;
		public final String Login;
		public final String Toolchain;
		public final String TestID;
		public final String Result;
		public final JsonNumber TotalFeedbacks;
		public final JsonNumber LastFeedbackDate;
		public final String LastFeedbackLogin;
		public final long CreationTime;
		public final boolean AllowProtocol;

		private TestHistory(int ID, String toolchain, String login, String testID, String result, long creation_time, JsonNumber totalFeedbacks, JsonNumber lastFeedbackDate, String lastFeedbackLogin, boolean allowProtocol) {
			this.ID = ID;
			this.Login = login;
			this.Toolchain = toolchain;
			this.TestID = testID;
			this.Result = result;
			this.TotalFeedbacks = totalFeedbacks;
			this.LastFeedbackDate = lastFeedbackDate;
			this.CreationTime = creation_time;
			this.LastFeedbackLogin = lastFeedbackLogin;
			this.AllowProtocol = allowProtocol;
		}

		@Override
		public JsonValue getValue() {
			JsonObject obj = new JsonObject();
			obj.add("ID", this.ID);
			obj.add("Login", this.Login);
			obj.add("Toolchain", this.Toolchain);
			obj.add("TestID", this.TestID);
			obj.add("Result", this.Result);
			obj.add("TotalComments", this.TotalFeedbacks);
			obj.add("LastComment", this.LastFeedbackDate);
			obj.add("LastCommentLogin", this.LastFeedbackLogin);
			obj.add("CreationTime", this.CreationTime);
			obj.add("Protocol", this.AllowProtocol);
			return obj;
		}
	}

	public final static class CodedHistory implements JsonValuable {
		public final String TestID;
		public final String Protocol;
		public final String Code;
		public final int AuthorID;

		private CodedHistory(String testID, String code, int authorID, String protocol) {
			this.TestID = testID;
			this.Code = code;
			this.AuthorID = authorID;
			this.Protocol = protocol;
		}

		@Override
		public JsonValue getValue() {
			JsonObject obj = new JsonObject();
			obj.add("TestID", this.TestID);
			obj.add("Code", this.Code);
			obj.add("AuthorID", this.AuthorID);
			return obj;
		}
	}

	public void updateFeedback(Toolchain toolchain, int feedbackID, JsonValue data, boolean delete) throws DatabaseException {
		final String tableName = "compilations_feedback";
		ValuedField[] fields = delete ? new ValuedField[] { new ValuedField(getField(tableName, "valid"), 0) } : new ValuedField[] { new ValuedField(getField(tableName, "data"), data.getJsonString()) };
		this.update(tableName, feedbackID, fields);
	}

	public void storeFeedback(Toolchain toolchain, int userID, int compilation_id, JsonValue data) throws DatabaseException {
		final String tableName = "compilations_feedback";
		ValuedField[] fields = new ValuedField[] { new ValuedField(getField(tableName, "compilation_id"), compilation_id), new ValuedField(getField(tableName, "data"), data.getJsonString()), new ValuedField(getField(tableName, "author_id"), userID), new ValuedField(getField(tableName, "compilation_id"), compilation_id), new ValuedField(getField(tableName, "creation_time"), System.currentTimeMillis()), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()) };
		this.insert(tableName, fields);
	}

	public List<TestFeedback> getFeedbacks(Toolchain toolchain, int compilationID, int currentUserID, boolean canEditAnything) throws DatabaseException {
		List<TestFeedback> lst = new ArrayList<>();
		final String tableName = "compilations_feedback";
		final String tableName2 = "users";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "author_id"), getField(tableName, "data"), getField(tableName, "creation_time"), getField(tableName, "toolchain"), getField(tableName2, "login") };
		ComparisionField t_valid = new ComparisionField(getField(tableName, "valid"), 1);
		ComparisionField t_compilation_id = new ComparisionField(getField(tableName, "compilation_id"), compilationID);
		ComparisionField[] conjunctions = new ComparisionField[] { t_valid, t_compilation_id };

		JsonArray res = this.select(tableName, fields, conjunctions, new TableJoin[] { new TableJoin(getField(tableName, "author_id"), getField(tableName2, "ID")) }, true);
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsNumber("author_id") && obj.containsString("data") && obj.containsNumber("creation_time") && obj.containsString("login") && obj.containsString("toolchain")) {
					int id = obj.getNumber("ID").Value;
					int author_id = obj.getNumber("author_id").Value;
					String data = obj.getString("data").Value;
					long creation = obj.getNumber("creation_time").asLong();
					String login = obj.getString("login").Value;
					String tc = obj.getString("toolchain").Value;
					JsonValue dataV = JsonValue.parse(data);
					if (dataV != null) {
						TestFeedback tf = new TestFeedback(id, author_id, login, "", tc, dataV, creation, currentUserID, canEditAnything);
						lst.add(tf);
					}
				}
			}
		}
		return lst;
	}

	private JsonValue getNumberFromResult(Toolchain toolchain, boolean dec, String sql, String table, String column, Object... params) throws DatabaseException {
		@SuppressWarnings("deprecation")
		DatabaseResult data = this.select_raw(sql, params);
		JsonArray res;
		try {
			res = data.getJSON(false, new TableField[] { getField(table, column) }, toolchain);
		} catch (CompressionException e) {
			throw new DatabaseException("Failed to decode compilation count data", e);
		}
		if (res.Value.size() == 1) {
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (dec) {
					if (obj.contains(column)) {
						return obj.get(column);
					}
				} else {
					return obj;
				}
			}
		}
		return new JsonNumber(0);
	}

	private Object[] getTotalFeedbacksWithLastFeedback(Toolchain toolchain, int compilationID) throws DatabaseException {
		JsonValue cnt = getNumberFromResult(toolchain, true, "SELECT count(*) as id FROM compilations_feedback WHERE compilation_id=? AND valid=1 AND toolchain=?", "compilations_feedback", "id", compilationID, toolchain.getName());
		JsonValue last = getNumberFromResult(toolchain, false, "SELECT creation_time as id, users.login as login FROM compilations_feedback, users WHERE users.id = compilations_feedback.author_id AND  compilations_feedback.compilation_id=? AND compilations_feedback.valid=1 AND compilations_feedback.toolchain=? ORDER BY compilations_feedback.creation_time DESC LIMIT 1", "compilations_feedback", "id", compilationID, toolchain.getName());
		if (last.isObject()) {
			JsonObject o = last.asObject();
			if (o.containsNumber("id") && o.containsString("login")) {
				return new Object[] { cnt, o.getNumber("id"), o.getString("login").Value };
			}
		}
		return new Object[] { cnt, new JsonNumber(0), "" };
	}

	public static class HistoryListPart extends ArrayList<TestHistory> {

		private static final long serialVersionUID = -501503368331905476L;
		private final int limit;

		private boolean has_more = false;
		private int size = 0;

		public HistoryListPart(int limit) {
			this.limit = limit;
		}

		@Override
		public boolean add(TestHistory item) {
			if (size < limit) {
				size++;
				return super.add(item);
			}
			has_more = true;
			return true;
		}

		public boolean hasMore() {
			return has_more;
		}
	}

	private TestHistory getTestHistoryFromResult(Toolchain toolchain, JsonValue val, boolean allowProtocol) throws DatabaseException {
		if (val.isObject()) {
			JsonObject obj = val.asObject();
			if (obj.containsNumber("ID") && obj.containsString("login") && obj.containsNumber("creation_time") && obj.containsString("toolchain") && obj.containsString("test_id") && obj.containsString("result")) {
				int id = obj.getNumber("ID").Value;
				String tc = obj.getString("toolchain").Value;
				String test_id = obj.getString("test_id").Value;
				String result = obj.getString("result").Value;
				String login = obj.getString("login").Value;
				long creation_time = obj.getNumber("creation_time").asLong();
				if (toolchain.IsRoot || toolchain.getName().equals(tc)) {
					Object[] x = getTotalFeedbacksWithLastFeedback(toolchain, id);
					JsonNumber cnt = (JsonNumber) x[0];
					JsonNumber last = (JsonNumber) x[1];
					String lastLogin = (String) x[2];
					return new TestHistory(id, tc, login, test_id, result, creation_time, cnt, last, lastLogin, allowProtocol);
				}
			}
		}
		return null;
	}

	public HistoryListPart getHistory(Toolchain toolchain, UsersPermission perms, String testID, boolean onlyMine, boolean allowProtocol, int limit, int offset, Set<String> logins) throws DatabaseException {
		HistoryListPart lst = new HistoryListPart(limit);
		final String tableName = "compilations";
		final String tableName2 = "users";

		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName2, "login"), getField(tableName, "toolchain"), getField(tableName, "result"), getField(tableName, "user_id"), getField(tableName, "creation_time"), getField(tableName, "test_id") };
		ComparisionField tidc = new ComparisionField(getField(tableName, "test_id"), testID);
		ComparisionField tuidc = new ComparisionField(getField(tableName, "user_id"), perms.getUserID());

		ComparisionField[] conjunctions = onlyMine ? new ComparisionField[] { tidc, tuidc } : new ComparisionField[] { tidc };

		final OrderField order = new OrderField(getField(tableName, "ID"), "DESC");

		final TableField f_login = getField(tableName2, "login");
		final int max_logins = 5;
		if (logins.size() > max_logins) {
			int neededIgnored = offset;
			int microOffset = 0;

			int need = limit + 1;
			while (need > 0) {
				JsonArray res = this.select(tableName, fields, conjunctions, new TableJoin[] { new TableJoin(getField(tableName, "user_id"), getField(tableName2, "ID")) }, true, limit + 1, microOffset, order);
				if (res.Value.isEmpty()) {
					break;
				}
				for (JsonValue val : res.Value) {
					microOffset++;
					TestHistory hist = getTestHistoryFromResult(toolchain, val, allowProtocol);
					if (hist != null) {
						if (logins.contains(hist.Login)) {
							if (neededIgnored > 0) {
								neededIgnored--;
							} else {
								lst.add(hist);
								need--;
								if (need == 0) {
									break;
								}
							}
						}
					}
				}
			}

		} else {
			ComparisionField[] loginFields = new ComparisionField[logins.size()];
			Object[] loginsA = logins.toArray();
			for (int i = 0; i < loginsA.length; i++) {
				loginFields[i] = new ComparisionField(f_login, loginsA[i].toString());
			}
			ComparisionFieldGroup loginsGroups = new ComparisionFieldGroup(loginFields, "OR");
			JsonArray res = this.select(tableName, fields, conjunctions, new TableJoin[] { new TableJoin(getField(tableName, "user_id"), getField(tableName2, "ID")) }, true, limit + 1, offset, order, loginsGroups);
			for (JsonValue val : res.Value) {
				TestHistory hist = getTestHistoryFromResult(toolchain, val, allowProtocol);
				if (hist != null) {
					lst.add(hist);
				}
			}
		}
		return lst;
	}

	public TestFeedback getSingleFeedback(Toolchain toolchain, int feedbackID, int currentUserID, boolean canEditAnything) throws DatabaseException {
		final String tableName = "compilations_feedback";
		final String tableName2 = "users";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "author_id"), getField(tableName, "data"), getField(tableName, "creation_time"), getField(tableName, "toolchain"), getField(tableName2, "login") };
		ComparisionField t_valid = new ComparisionField(getField(tableName, "valid"), 1);
		ComparisionField t_compilation_id = new ComparisionField(getField(tableName, "ID"), feedbackID);
		ComparisionField[] conjunctions = new ComparisionField[] { t_valid, t_compilation_id };

		JsonArray res = this.select(tableName, fields, conjunctions, new TableJoin[] { new TableJoin(getField(tableName, "author_id"), getField(tableName2, "ID")) }, true);
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsNumber("author_id") && obj.containsString("data") && obj.containsNumber("creation_time") && obj.containsString("login") && obj.containsString("toolchain")) {
					int id = obj.getNumber("ID").Value;
					int author_id = obj.getNumber("author_id").Value;
					String data = obj.getString("data").Value;
					long creation = obj.getNumber("creation_time").asLong();
					String login = obj.getString("login").Value;
					String tc = obj.getString("toolchain").Value;
					JsonValue dataV = JsonValue.parse(data);
					if (dataV != null) {
						TestFeedback tf = new TestFeedback(id, author_id, login, "", tc, dataV, creation, currentUserID, canEditAnything);
						return tf;
					}
				}
			}
		}
		return null;
	}

	public CodedHistory getSingleHistory(Toolchain toolchain, int compilationID, boolean includeProtocol) throws DatabaseException {
		final String tableName = "compilations";
		TableField[] fields = new TableField[] { getField(tableName, "test_id"), getField(tableName, "user_id"), getField(tableName, "asm"), getField(tableName, "toolchain"), getField(tableName, "full") };
		ComparisionField tidc = new ComparisionField(getField(tableName, "ID"), compilationID);

		ComparisionField[] conjunctions = new ComparisionField[] { tidc };
		JsonArray res = this.select(tableName, fields, true, conjunctions);
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("user_id") && obj.containsString("test_id") && obj.containsString("asm") && obj.containsString("full") && obj.containsString("toolchain")) {
					int user_id = obj.getNumber("user_id").Value;
					String test_id = obj.getString("test_id").Value;
					String asm = obj.getString("asm").Value;
					String tc = obj.getString("toolchain").Value;
					String full = obj.getString("full").Value;
					if (toolchain.IsRoot || toolchain.getName().equals(tc)) {
						return new CodedHistory(test_id, asm, user_id, full);
					}
				}
			}
		}
		return null;
	}

	public TestFeedback fetchSingleFeedback(Toolchain toolchain, int ID, int currentUserID, boolean canEditAnything) throws DatabaseException {
		final String tableName = "compilations_feedback";
		final String tableName2 = "users";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "author_id"), getField(tableName, "data"), getField(tableName, "creation_time"), getField(tableName, "toolchain"), getField(tableName2, "login") };
		ComparisionField t_valid = new ComparisionField(getField(tableName, "valid"), 1);
		ComparisionField t_compilation_id = new ComparisionField(getField(tableName, "id"), ID);
		ComparisionField[] conjunctions = new ComparisionField[] { t_valid, t_compilation_id };

		JsonArray res = this.select(tableName, fields, conjunctions, new TableJoin[] { new TableJoin(getField(tableName, "author_id"), getField(tableName2, "ID")) }, true);
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsNumber("author_id") && obj.containsString("data") && obj.containsNumber("creation_time") && obj.containsString("login") && obj.containsString("toolchain")) {
					int id = obj.getNumber("ID").Value;
					int author_id = obj.getNumber("author_id").Value;
					String data = obj.getString("data").Value;
					long creation = obj.getNumber("creation_time").asLong();
					String login = obj.getString("login").Value;
					String tc = obj.getString("toolchain").Value;
					JsonValue dataV = JsonValue.parse(data);
					if (dataV != null) {
						TestFeedback tf = new TestFeedback(id, author_id, login, "", tc, dataV, creation, currentUserID, canEditAnything);
						return tf;
					}
				}
			}
		}
		return null;
	}

	public void logPageLoad(int session_id, Toolchain toolchain, String address, String host, String path, int result) {
		final String tableName = "page_loads";
		try {
			this.insert(tableName, new ValuedField(getField(tableName, "session_id"), session_id), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "address"), address), new ValuedField(getField(tableName, "host"), host), new ValuedField(getField(tableName, "path"), path), new ValuedField(getField(tableName, "creation_time"), System.currentTimeMillis()), new ValuedField(getField(tableName, "result"), result));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public enum BadResultType {
		Good(0, 0, 0), BadTests(2, 0, 1), BadBase(10, 5, 2), Timeout(0, 0, 3), SegFault(10, 0, 6), BadInstructions(10, 15, 8), Uncompillable(30, 30, 10);

		private final int Severity;
		private final int FirstMinutes;
		private final int EveryOtherMinutes;

		private BadResultType(int first, int next, int severity) {
			this.FirstMinutes = first;
			this.EveryOtherMinutes = next;
			this.Severity = severity;
		}
	}

	public BadResults GetBadResultsForUser(int UserID, Toolchain toolchain) throws DatabaseException {
		synchronized (syncer_user_timeouts) {
			final String tableName = "user_timeouts";
			final TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "allow_next")

			};
			JsonArray res = select(tableName, fields, false, new ComparisionField(getField(tableName, "user_id"), UserID), new ComparisionField(getField(tableName, "live"), 1), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
			int row_id = -1;
			long nextDate = new Date().getTime() - (1000 * 10); // 10 seconds ago
			if (res.Value.size() > 0) {
				row_id = res.Value.get(0).asObject().getNumber("ID").Value;
				nextDate = res.Value.get(0).asObject().getNumber("allow_next").asLong();
			}
			return new BadResults(row_id, UserID, new Date(nextDate));
		}
	}

	public final class BadResults {
		private BadResultType next = BadResultType.Good;
		public final Date AllowNext;
		private final int RowID;
		private final int UserID;

		private static final long minute = (60 * 1000);

		public BadResults(int row_id, int user_id, Date allowNext) {
			this.RowID = row_id;
			this.UserID = user_id;
			this.AllowNext = allowNext;
		}

		public void setNext(BadResultType type) {
			if (type.Severity > next.Severity) {
				this.next = type;
			}
		}

		public void store(boolean newlyFinished, Toolchain toolchain) throws DatabaseException {
			synchronized (syncer_user_timeouts) {
				final String tableName = "user_timeouts";
				final TableField[] fields = new TableField[] { getField(tableName, "last_time"), getField(tableName, "allow_next"), getField(tableName, "bad_instr"), getField(tableName, "bad_segfaults"), getField(tableName, "bad_base"), getField(tableName, "bad_uncompilable") };
				boolean createNew = true;
				int segFaults = 0;
				int bads = 0;
				int bases = 0;
				int uncompilables = 0;
				long nextDate = AllowNext.getTime(); // 10 seconds ago
				if (RowID != -1) {
					JsonArray res = select(tableName, fields, false, new ComparisionField(getField(tableName, "ID"), RowID), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
					if (res.Value.size() > 0) {
						createNew = false;
						segFaults = res.Value.get(0).asObject().getNumber("bad_segfaults").Value;
						bads = res.Value.get(0).asObject().getNumber("bad_instr").Value;
						bases = res.Value.get(0).asObject().getNumber("bad_base").Value;
						uncompilables = res.Value.get(0).asObject().getNumber("bad_uncompilable").Value;
						nextDate = res.Value.get(0).asObject().getNumber("allow_next").asLong();
					}
				}
				int totalTimes = 0;
				if (next == BadResultType.BadBase) {
					bases++;
					totalTimes = bases;
				} else if (next == BadResultType.BadInstructions) {
					bads++;
					totalTimes = bads;
				} else if (next == BadResultType.SegFault) {
					segFaults++;
					totalTimes = segFaults;
				} else if (next == BadResultType.Uncompillable) {
					uncompilables++;
					totalTimes = uncompilables;
				} else if (next == BadResultType.BadTests) {
					totalTimes = 1;
				}
				int totalDelay = totalTimes > 0 ? (next.FirstMinutes) + ((totalTimes - 1) * next.EveryOtherMinutes) : 0;
				totalDelay *= minute;
				nextDate = totalDelay > 0 ? new Date().getTime() + totalDelay : nextDate;

				final ValuedField[] vfields = new ValuedField[] { new ValuedField(getField(tableName, "last_time"), new Date().getTime()), new ValuedField(getField(tableName, "bad_instr"), bads), new ValuedField(getField(tableName, "bad_segfaults"), segFaults), new ValuedField(getField(tableName, "bad_base"), bases), new ValuedField(getField(tableName, "bad_uncompilable"), uncompilables), new ValuedField(getField(tableName, "allow_next"), nextDate), new ValuedField(getField(tableName, "live"), 1), new ValuedField(getField(tableName, "user_id"), UserID), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()) };
				this.AllowNext.setTime(nextDate);
				if (next == BadResultType.Good && newlyFinished) {
					this.AllowNext.setTime(new Date().getTime() - 10000);
					if (!createNew) {
						update(tableName, RowID, new ValuedField(getField(tableName, "live"), 0));
					}
				} else {
					if (createNew) {
						insert(tableName, vfields);
					} else {
						update(tableName, RowID, vfields);
					}
				}
			}
		}
	}

	private void log_expired_crypto(String address, String crypto, String description, Toolchain toolchain) throws DatabaseException {
		final String tableName = "crypto_expire_log";
		this.insert(tableName, new ValuedField(getField(tableName, "description"), description), new ValuedField(getField(tableName, "address"), address), new ValuedField(getField(tableName, "crypto"), crypto), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "creation_time"), System.currentTimeMillis()));
	}

	private void makeCompilationStatsTable() throws DatabaseException {
		makeTable("compilation_stats", false, KEY("ID"), NUMBER("user_id"), TEXT("toolchain"), NUMBER("good_tests"), NUMBER("good_unique_tests"), NUMBER("total_tests"));
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
		public final String Toolchain;
		public final String TestID;
		public final String Code;
		public final String CompletionDateStr;

		private CompletedTest(int entry_id, String toolchain, String test_id, String asm, long completion_date) {
			this.EntryID = entry_id;
			this.Toolchain = toolchain;
			this.TestID = test_id;
			this.Code = asm;
			this.CompletionDateStr = dateFormat.format(new Date(completion_date));
		}
	}

	public List<CompletedTest> getCompletedTests(String login, Toolchain toolchain) {
		List<CompletedTest> result = new ArrayList<>();

		try {
			final String tableName1 = "users";
			final String tableName2 = "compilations";
			TableField[] selectFields = new TableField[] { getField(tableName1, "login"), getField(tableName2, "ID"), getField(tableName2, "toolchain"), getField(tableName2, "test_id"), getField(tableName2, "creation_time"), getField(tableName2, "asm"), getField(tableName2, "code") };
			ComparisionField[] comparators = new ComparisionField[] { new ComparisionField(getField(tableName2, "code"), 0), new ComparisionField(getField(tableName2, "test_id"), "", FieldComparator.NotEquals), new ComparisionField(getField(tableName2, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName1, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName1, "login"), login) };
			TableJoin[] joins = new TableJoin[] { new TableJoin(getField(tableName1, "ID"), getField(tableName2, "user_id")) };
			JsonArray jsn = select(tableName1, selectFields, comparators, joins, true);
			for (JsonValue val : jsn.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("login") && obj.containsString("test_id") && obj.containsString("asm") && obj.containsString("toolchain") && obj.containsNumber("code")) {
						int entry_id = obj.getNumber("ID").Value;
						String test_id = obj.getString("test_id").Value;
						String asm = obj.getString("asm").Value;
						String toolchainS = obj.getString("toolchain").Value;
						long completion_date = obj.getNumber("creation_time").asLong();
						result.add(new CompletedTest(entry_id, toolchainS, test_id, asm, completion_date));
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return result;
	}

	private void createCompilationStats(int user_id, Toolchain toolchain, boolean storeEvenEmptyStats) throws DatabaseException {
		synchronized (syncer_compilations) {
			final String compilationsTableName = "compilations";
			final String statsTableName = "compilation_stats";
			int total = 0;
			int unique = 0;
			int good = 0;
			List<String> known = new ArrayList<>();
			JsonArray res = this.select(compilationsTableName, new TableField[] { getField(compilationsTableName, "user_id"), getField(compilationsTableName, "test_id"), getField(compilationsTableName, "code") }, false, new ComparisionField(getField(compilationsTableName, "user_id"), user_id), new ComparisionField(getField(compilationsTableName, "toolchain"), toolchain.getName()));
			for (JsonValue val : res.Value) {
				int code = val.asObject().getNumber("code").Value;
				String test_id = val.asObject().getString("test_id").Value;
				if (code == 0 && !test_id.equals("")) {
					good++;
					if (!known.contains(test_id)) {
						known.add(test_id);
						unique++;
					}
				}
				total++;
			}
			if (total > 0 || storeEvenEmptyStats) {
				synchronized (syncer_compilation_stats) {
					this.insert(statsTableName, new ValuedField(getField(statsTableName, "user_id"), user_id), new ValuedField(getField(statsTableName, "good_unique_tests"), unique), new ValuedField(getField(statsTableName, "good_tests"), good), new ValuedField(getField(statsTableName, "total_tests"), total), new ValuedField(getField(statsTableName, "toolchain"), toolchain.getName()));
				}
			}
		}
	}

	private void storeCompilationStats(int user_id, Toolchain toolchain, int code, String test_id, List<CompletedTest> knownCompleted) {
		synchronized (syncer_compilation_stats) {
			final String tableName = "compilation_stats";
			try {
				JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "good_tests"), getField(tableName, "total_tests"), getField(tableName, "good_unique_tests") }, false, new ComparisionField(getField(tableName, "user_id"), user_id), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
				if (res.Value.isEmpty()) { // No stats for this user -> create stats from sratch
					createCompilationStats(user_id, toolchain, true);
					// Reselect and update
					res = this.select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "good_tests"), getField(tableName, "total_tests"), getField(tableName, "good_unique_tests") }, false, new ComparisionField(getField(tableName, "user_id"), user_id), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
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
				if (code == 0 && !test_id.equals("")) {
					good++;
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
				}
				this.update(tableName, id, new ValuedField(getField(tableName, "good_tests"), good), new ValuedField(getField(tableName, "good_unique_tests"), good_unique), new ValuedField(getField(tableName, "total_tests"), total));
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
	}

	public void updateStatsForAllUsers(Toolchain toolchain) throws DatabaseException {
		synchronized (syncer_users) {
			synchronized (syncer_compilation_stats) {
				final String tableName = "users";
				this.dropTable("compilation_stats");
				makeCompilationStatsTable();
				JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID") }, false, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));
				int index = 0;
				int total = res.Value.size();
				for (JsonValue val : res.Value) {
					int id = val.asObject().getNumber("ID").Value;
					createCompilationStats(id, toolchain, false);
					index++;
					System.out.println("Creating compilations... " + index + "/" + total);
				}
			}
		}
	}

	public void storeCompilation(List<CompletedTest> knownCompleted, String remoteAddress, Date time, String asm, int session_id, String test_id, int resultCode, String result, String full, int user_id, Toolchain toolchain, String details, int good_test, int bad_tests) throws DatabaseException {
		synchronized (syncer_compilations) {
			storeCompilationStats(user_id, toolchain, resultCode, test_id, knownCompleted);
			String[] addrParts = reverse(remoteAddress).split(":", 2);
			String address = remoteAddress;
			int port = 0;
			if (addrParts.length == 2) {
				address = reverse(addrParts[1]);
				port = Integer.parseInt(reverse(addrParts[0]));
			}

			final String tableName = "compilations";

			ValuedField[] updateData = new ValuedField[] { new ValuedField(this.getField(tableName, "address"), address), new ValuedField(this.getField(tableName, "port"), port), new ValuedField(this.getField(tableName, "asm"), asm), new ValuedField(this.getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(this.getField(tableName, "test_id"), test_id), new ValuedField(this.getField(tableName, "user_id"), user_id), new ValuedField(this.getField(tableName, "session_id"), session_id), new ValuedField(this.getField(tableName, "creation_time"), time.getTime()), new ValuedField(this.getField(tableName, "code"), resultCode), new ValuedField(this.getField(tableName, "result"), result), new ValuedField(this.getField(tableName, "full"), full), new ValuedField(this.getField(
					tableName, "bad_tests_details"), details), new ValuedField(this.getField(tableName, "bad_tests"), bad_tests), new ValuedField(this.getField(tableName, "good_tests"), good_test) };
			insert(tableName, updateData);
		}

	}

	private void makeRetestsTable() throws DatabaseException {
		this.makeTable("retests", false, KEY("ID"), NUMBER("compilation_id"), DATE("creation_time"), NUMBER("code"), BIGTEXT("result"), BIGTEXT("full"), NUMBER("good_tests"), NUMBER("bad_tests"), BIGTEXT("bad_tests_details"), NUMBER("original_code"), BIGTEXT("original_result"), BIGTEXT("original_full"));
	}

	public void resetRetestsDB() throws DatabaseException {
		this.dropTable("retests");
		makeRetestsTable();
	}

	public int getUserIDFromLogin(String login, Toolchain toolchain) throws DatabaseException {
		if (login == null) {
			return -1;
		}
		synchronized (syncer_users) {

			final String tableName = "users";
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID") }, false, new ComparisionField(this.getField(tableName, "login"), login), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()));

			int user_id = 0;
			if (res.Value.size() == 0) { // No such user, create
				if (!this.insert("users", new ValuedField(this.getField("users", "login"), login), new ValuedField(this.getField("users", "toolchain"), toolchain.getName()))) {
					return -1;
				}
				// Get the ID
				res = this.select(tableName, new TableField[] { this.getField(tableName, "ID") }, false, new ComparisionField(this.getField(tableName, "login"), login), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()));
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

	public String storeSessionKnownLogin(String address, String authToken, String login, boolean allowChangeAddress, Toolchain toolchain) throws Exception {
		long now = System.currentTimeMillis();
		final String tableName = "session";
		synchronized (syncer_sessions) {
			int user_id = getUserIDFromLogin(login, toolchain);
			if (user_id == -1) {
				this.log_expired_crypto(address, authToken, "Invalid user - looking for: " + login, toolchain);
				throw new Exception("Invalid user (looking for " + login + ")");
			}
			// Get live sessions
			ComparisionField[] cf;
			if (allowChangeAddress) {
				cf = new ComparisionField[] { new ComparisionField(this.getField(tableName, "user_id"), user_id), new ComparisionField(this.getField(tableName, "live"), 1), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()) };
			} else {
				cf = new ComparisionField[] { new ComparisionField(this.getField(tableName, "user_id"), user_id), new ComparisionField(this.getField(tableName, "live"), 1), new ComparisionField(this.getField(tableName, "address"), address), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()) };
			}
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, cf);
			if (res.Value.size() == 0) { // No such session, create new
				String newSessionToken = randomstr(32);
				while (true) { // Must not exist already

					res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, new ComparisionField(this.getField(tableName, "hash"), newSessionToken), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()));
					if (res.Value.size() == 0) {
						break;
					}
					newSessionToken = randomstr(32);
				}

				ValuedField[] updateData = new ValuedField[] { new ValuedField(this.getField(tableName, "hash"), newSessionToken), new ValuedField(this.getField(tableName, "address"), address), new ValuedField(this.getField(tableName, "live"), 1), new ValuedField(this.getField(tableName, "user_id"), user_id), new ValuedField(this.getField(tableName, "last_action"), now), new ValuedField(this.getField(tableName, "creation_time"), now), new ValuedField(this.getField(tableName, "toolchain"), toolchain.getName()) };

				if (!insert(tableName, updateData)) {
					return null;
				}
				res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "hash") }, false, new ComparisionField(this.getField(tableName, "user_id"), user_id));
				if (res.Value.size() == 0) {
					throw new Exception("Failed to update session in database");
				}
			} else {

				ValuedField[] updateData = new ValuedField[] { new ValuedField(this.getField(tableName, "last_action"), now) };
				this.update(tableName, "user_id", user_id, updateData);
			}
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("hash")) {
					String hash = obj.getString("hash").Value;
					return hash;
				}
			}
			throw new Exception("Database error?");
		}

	}

	public String storeSession(String address, String authToken, Toolchain toolchain) throws Exception {
		if (crypto == null) {
			throw new Exception("No crypto");
		}
		String dec;
		try {
			dec = crypto.decrypt(Settings.getAuthKeyFilename(), authToken);
		} catch (CryptoException e) {
			this.log_expired_crypto(address, authToken, "Parse failed - Crypto raised exception: " + e.Description, toolchain);
			throw e;
		}
		if (dec == null) {
			this.log_expired_crypto(address, authToken, "Parse failed - Crypto failed to decrypt", toolchain);
			throw new Exception("Crypto failed");
		}
		JsonValue val = JsonValue.parse(dec);
		if (val == null) {
			this.log_expired_crypto(address, authToken, "Parse failed - Not a JSON object", toolchain);
			throw new Exception("Auth data not in JSON: " + dec);
		}
		if (val.isObject()) {
			JsonObject obj = val.asObject();
			if (obj.containsString("login") && obj.containsNumber("time")) {
				long time = (obj.getNumber("time").Value & 0xffffffff);
				long now = new Date().getTime() / 1000;
				long diff = now - time;
				if (diff > 5 || diff < -5) { // Auth generated in the future or 15 seconds ago, too old
					this.log_expired_crypto(address, authToken, "Too old - " + diff + " seconds", toolchain);
					throw new Exception("Crypto auth too old (" + diff + " seconds)");
				}

				String login = obj.getString("login").Value;
				return storeSessionKnownLogin(address, authToken, login, false, toolchain);
			} else {
				this.log_expired_crypto(address, authToken, "Parse failed - missing fields", toolchain);
				throw new Exception("Invalid auth structure: missing fields");
			}
		} else {
			throw new Exception("No crypto");
		}
	}

	public int getSessionIDFromSession(String address, String session, boolean allowChangeOfAddress, Toolchain toolchain) throws DatabaseException, ChangeOfSessionAddressException {
		synchronized (syncer_sessions) {
			final String tableName = "session";
			JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "address") }, false, new ComparisionField(this.getField(tableName, "hash"), session), new ComparisionField(this.getField(tableName, "live"), 1), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()));
			if (res.Value.size() == 1) {
				refreshSession(session);
				JsonValue val = res.Value.get(0);
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("address")) {
						String addr = obj.getString("address").Value;
						if (!addr.equals(address) && !allowChangeOfAddress) {
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

	public String getLogin(String address, String sessionToken, Toolchain toolchain) throws DatabaseException, ChangeOfSessionAddressException {
		synchronized (syncer_sessions) {
			synchronized (syncer_users) {
				final String tableName = "session";
				JsonArray res = this.select(tableName, new TableField[] { this.getField(tableName, "ID"), this.getField(tableName, "address"), this.getField(tableName, "user_id"), this.getField(tableName, "address") }, false, new ComparisionField(this.getField(tableName, "hash"), sessionToken), new ComparisionField(this.getField(tableName, "live"), 1), new ComparisionField(this.getField(tableName, "toolchain"), toolchain.getName()));
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
								res = this.select(usersTableName, new TableField[] { this.getField(usersTableName, "login") }, false, new ComparisionField(this.getField(usersTableName, "ID"), user_id), new ComparisionField(this.getField(usersTableName, "toolchain"), toolchain.getName()));
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

	public List<RuntimeUserStats> getUserStats(Toolchain toolchain) {
		List<RuntimeUserStats> stats = new ArrayList<>();
		final String usersTableName = "users";
		final String sessionsTableName = "session";
		final String compilationsTableName = "compilations";
		JsonArray users = null;
		JsonArray sessions = null;
		JsonArray compilations = null;
		synchronized (syncer_users) {
			try {
				users = this.select(usersTableName, new TableField[] { getField(usersTableName, "ID"), getField(usersTableName, "login") }, false, new ComparisionField(getField(usersTableName, "toolchain"), toolchain.getName()));
			} catch (DatabaseException e1) {
				e1.printStackTrace();
			}
		}
		synchronized (syncer_sessions) {
			try {
				sessions = this.select(sessionsTableName, new TableField[] { getField(sessionsTableName, "creation_time"), getField(sessionsTableName, "last_action"), getField(sessionsTableName, "user_id") }, false, new ComparisionField(getField(sessionsTableName, "toolchain"), toolchain.getName()));
			} catch (DatabaseException e1) {
				e1.printStackTrace();
			}
		}
		synchronized (syncer_compilations) {
			try {
				compilations = this.select(compilationsTableName, new TableField[] { getField(compilationsTableName, "test_id"), getField(compilationsTableName, "user_id"), getField(compilationsTableName, "creation_time") }, false, new ComparisionField(getField(compilationsTableName, "toolchain"), toolchain.getName()));
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

	public void registerVirtualStatFile(VirtualStatFile f) {
		data.Files.registerVirtualFile(f);
	}

	public void unregisterVirtualStatFile(VirtualStatFile f) {
		data.Files.unregisterVirtualFile(f);
	}

	public static class BypassedClient {
		public final String Address;
		public final String Login;

		private BypassedClient(String addr, String login) {
			this.Address = addr;
			this.Login = login;
		}
	}

	private static class CachedBypassedClientDataCls extends CachedData<Map<String, BypassedClient>> {

		private StaticDB sdb;

		public CachedBypassedClientDataCls(StaticDB sdb) {
			super(30);
			this.sdb = sdb;
		}

		@Override
		protected Map<String, BypassedClient> update() {
			Map<String, BypassedClient> result = new HashMap<>();
			ReadVirtualFile f = sdb.loadRootFile("remapped_users.ini");
			if (f != null) {
				for (String line : f.Contents.split("\n")) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String address = parts[0].trim();
						String login = parts[1].trim();
						result.put(address, new BypassedClient(address, login));
					}
				}

			}
			return result;
		}

	};

	private final CachedBypassedClientDataCls CachedBypassedClientData;

	public BypassedClient getBypassedClientData(String remoteSocketAddress) {
		return CachedBypassedClientData.get().get(remoteSocketAddress);
	}

	public void registerToolchainUpdator(ToolchainCallback cb) {
		sdb.registerToolchainListener(cb);
	}
}