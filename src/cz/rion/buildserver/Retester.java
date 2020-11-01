package cz.rion.buildserver;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.SQLiteDB.ComparisionField;
import cz.rion.buildserver.db.SQLiteDB.TableField;
import cz.rion.buildserver.db.SQLiteDB.TableJoin;
import cz.rion.buildserver.db.SQLiteDB.ValuedField;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.test.TestManager.TestResults;

public class Retester {

	private final TestManager tests;

	private static class TestResult {
		public final int Code;
		public final String Result;

		private TestResult(int Code, String Result) {
			this.Code = Code;
			this.Result = Result;
		}
	}

	private static final class PastTestResult extends TestResult {
		public final String ASM;
		public final String TestID;
		public final String ToolChain;
		public final String Login;
		public final int RowID;
		public final String Full;
		public final int UserID;

		private PastTestResult(String ASM, String TestID, String toolChain, int RowID, int Code, String Result, String full, String Login, int UserID) {
			super(Code, Result);
			this.RowID = RowID;
			this.ASM = ASM;
			this.ToolChain = toolChain;
			this.TestID = TestID;
			this.Login = Login;
			this.Full = full;
			this.UserID = UserID;
		}
	}

	private static class NewTestResult extends TestResult {
		public final PastTestResult Past;
		public final String Full;
		public final int Good;
		public final String Details;
		public final int Bad;

		private NewTestResult(PastTestResult Past, int Code, String Result, String full, String Details, int good, int bad) {
			super(Code, Result);
			this.Past = Past;
			this.Full = full;
			this.Good = good;
			this.Details = Details;
			this.Bad = bad;
		}
	}

	private static final class StoredNewTestResult extends NewTestResult {
		public final int ID;
		public final int BackupCode;
		public final String BackupFull;
		public final String BackupResult;

		private StoredNewTestResult(int id, PastTestResult Past, int Code, String Result, String full, String Details, int good, int bad, int BackupCode, String BackupFull, String BackupResult) {
			super(Past, Code, Result, full, Details, good, bad);
			this.ID = id;
			this.BackupCode = BackupCode;
			this.BackupFull = BackupFull;
			this.BackupResult = BackupResult;
		}
	}

	private final RuntimeDB db;
	private final StaticDB sdb;

	public Retester() throws DatabaseException {
		VirtualFileManager files = new VirtualFileManager();
		this.sdb = new StaticDB(new DatabaseInitData("static.sqlite", files));
		this.db = new RuntimeDB(new DatabaseInitData("data.sqlite", files), sdb);
		tests = new TestManager(files, sdb, "./web/tests");
	}

	public void restoreData() throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();

		final String tableName = "compilations";
		for (StoredNewTestResult test : data) {
			ValuedField[] fieldData = new ValuedField[] { new ValuedField(db.getField(tableName, "good_tests"), 0), new ValuedField(db.getField(tableName, "bad_tests"), 0), new ValuedField(db.getField(tableName, "result"), test.BackupResult), new ValuedField(db.getField(tableName, "full"), test.BackupFull), new ValuedField(db.getField(tableName, "code"), test.BackupCode) };
			this.db.update(tableName, test.Past.RowID, fieldData);
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	public void backupData() throws DatabaseException {
		backupData(null);
	}

	public void backupData(String test_id) throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();
		final String tableName = "retests";
		for (StoredNewTestResult test : data) {
			if (test_id == null ? true : test.Past.TestID.equals(test_id)) {
				ValuedField[] fieldData = new ValuedField[] { new ValuedField(db.getField(tableName, "original_result"), test.Past.Result), new ValuedField(db.getField(tableName, "original_full"), test.Past.Full), new ValuedField(db.getField(tableName, "original_code"), test.Past.Code) };
				this.db.update(tableName, test.ID, fieldData);
			}
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	public void updateData(String test_id) throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();
		final String tableName = "compilations";
		for (StoredNewTestResult test : data) {
			if (test.Past.TestID.equals(test_id)) {
				ValuedField[] fieldData = new ValuedField[] { new ValuedField(db.getField(tableName, "good_tests"), test.Good), new ValuedField(db.getField(tableName, "bad_tests"), test.Bad), new ValuedField(db.getField(tableName, "bad_tests_details"), test.Details), new ValuedField(db.getField(tableName, "result"), test.Result), new ValuedField(db.getField(tableName, "full"), test.Full), new ValuedField(db.getField(tableName, "code"), test.Code) };
				this.db.update(tableName, test.Past.RowID, fieldData);
			}
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	private int getretestIDForCompilation(int compilation_id) throws DatabaseException {
		final String tableName = "retests";
		JsonArray res = db.select(tableName, new TableField[] { db.getField(tableName, "ID") }, false, new ComparisionField(db.getField(tableName, "compilation_id"), compilation_id));
		if (!res.Value.isEmpty()) {
			return res.Value.get(0).asObject().getNumber("ID").Value;
		}
		return -1;
	}

	public void runTests(String test_id) throws DatabaseException, NoSuchToolchainException {
		List<PastTestResult> data = load();
		int index = 0;
		int total = data.size();
		final String tableName = "retests";
		for (PastTestResult test : data) {
			if (test.TestID.equals(test_id)) {
				NewTestResult ntest = retest(test);
				int existingID = getretestIDForCompilation(ntest.Past.RowID);
				if (existingID == -1) { // New
					ValuedField[] updateData = new ValuedField[] { new ValuedField(db.getField(tableName, "compilation_id"), ntest.Past.RowID), new ValuedField(db.getField(tableName, "code"), ntest.Code), new ValuedField(db.getField(tableName, "result"), ntest.Result), new ValuedField(db.getField(tableName, "full"), ntest.Full), new ValuedField(db.getField(tableName, "creation_time"), new Date().getTime()), new ValuedField(db.getField(tableName, "good_tests"), ntest.Good), new ValuedField(db.getField(tableName, "bad_tests"), ntest.Bad), new ValuedField(db.getField(tableName, "bad_tests_details"), ntest.Details) };
					db.insert(tableName, updateData);
				} else { // Updates
					ValuedField[] updateData = new ValuedField[] { new ValuedField(db.getField(tableName, "code"), ntest.Code), new ValuedField(db.getField(tableName, "result"), ntest.Result), new ValuedField(db.getField(tableName, "full"), ntest.Full), new ValuedField(db.getField(tableName, "creation_time"), new Date().getTime()), new ValuedField(db.getField(tableName, "good_tests"), ntest.Good), new ValuedField(db.getField(tableName, "bad_tests"), ntest.Bad), new ValuedField(db.getField(tableName, "bad_tests_details"), ntest.Details) };
					this.db.update("retests", existingID, updateData);
				}
			}
			System.out.println("Done " + index + "/" + total);
			index++;
		}
	}

	public void runTests(boolean onlyBad) throws DatabaseException, NoSuchToolchainException {
		List<PastTestResult> data = load();
		db.resetRetestsDB();
		int index = 0;
		int total = data.size();
		final String tableName = "retests";
		for (PastTestResult test : data) {
			if (!onlyBad || test.Code != 0) {
				NewTestResult ntest = retest(test);
				ValuedField[] updateData = new ValuedField[] { new ValuedField(db.getField(tableName, "compilation_id"), ntest.Past.RowID), new ValuedField(db.getField(tableName, "code"), ntest.Code), new ValuedField(db.getField(tableName, "result"), ntest.Result), new ValuedField(db.getField(tableName, "full"), ntest.Full), new ValuedField(db.getField(tableName, "creation_time"), new Date().getTime()), new ValuedField(db.getField(tableName, "good_tests"), ntest.Good), new ValuedField(db.getField(tableName, "bad_tests"), ntest.Bad), new ValuedField(db.getField(tableName, "bad_tests_details"), ntest.Details) };
				db.insert(tableName, updateData);
			}
			System.out.println("Done " + index + "/" + total);
			index++;
		}
	}

	private List<StoredNewTestResult> loadNew() throws DatabaseException {
		List<StoredNewTestResult> lst = new ArrayList<>();
		Map<Integer, PastTestResult> rs = new HashMap<>();
		for (PastTestResult test : load()) {
			rs.put(test.RowID, test);
		}

		final String tableName = "retests";
		TableField[] selectFields = new TableField[] { db.getField(tableName, "ID"), db.getField(tableName, "compilation_id"), db.getField(tableName, "code"), db.getField(tableName, "result"), db.getField(tableName, "full"), db.getField(tableName, "good_tests"), db.getField(tableName, "bad_tests"), db.getField(tableName, "bad_tests_details"), db.getField(tableName, "original_code"), db.getField(tableName, "original_full"), db.getField(tableName, "original_result") };
		JsonArray res = db.select(tableName, selectFields, true);
		for (JsonValue val : res.Value) {
			JsonObject obj = val.asObject();
			int id = obj.getNumber("ID").Value;
			int compilation_id = obj.getNumber("compilation_id").Value;

			int code = obj.getNumber("code").Value;
			String result = obj.getString("result").Value;
			String full = obj.getString("full").Value;

			int good_tests = obj.getNumber("good_tests").Value;
			int bad_tests = obj.getNumber("bad_tests").Value;
			String bad_tests_details = obj.getString("bad_tests_details").Value;

			String originalFull = obj.getString("original_full").Value;
			String original_result = obj.getString("original_result").Value;
			int original_code = obj.getNumber("original_code").Value;

			if (rs.containsKey(compilation_id)) {
				PastTestResult past = rs.get(compilation_id);
				lst.add(new StoredNewTestResult(id, past, code, result, full, bad_tests_details, good_tests, bad_tests, original_code, originalFull, original_result));
			}
		}
		return lst;
	}

	private List<PastTestResult> load() throws DatabaseException {
		List<PastTestResult> lst = new ArrayList<>();

		final String tableName1 = "compilations";
		final String tableName2 = "users";

		TableField[] selectFields = new TableField[] { db.getField(tableName1, "ID"), db.getField(tableName1, "asm"), db.getField(tableName1, "toolchain"), db.getField(tableName1, "test_id"), db.getField(tableName1, "user_id"), db.getField(tableName1, "full"), db.getField(tableName1, "code"), db.getField(tableName1, "result"), db.getField(tableName2, "login"), };
		TableJoin[] joins = new TableJoin[] { new TableJoin(db.getField(tableName1, "user_id"), db.getField(tableName2, "ID")) };
		JsonArray res = db.select(tableName1, selectFields, new ComparisionField[0], joins, true);
		for (JsonValue val : res.Value) {
			JsonObject obj = val.asObject();
			int id = obj.getNumber("ID").Value;
			String asm = obj.getString("asm").Value;
			String toolchain = obj.getString("toolchain").Value;
			String test_id = obj.getString("test_id").Value;
			String full = obj.getString("full").Value;
			String login = obj.getString("login").Value;
			int code = obj.getNumber("code").Value;
			int user_id = obj.getNumber("user_id").Value;
			String result = obj.getString("result").Value;
			PastTestResult test = new PastTestResult(asm, toolchain, test_id, id, code, result, full, login, user_id);
			if (test.Login != null) {
				if (!test.Login.isEmpty()) {
					if (test.TestID != null) {
						if (!test.TestID.isEmpty()) {
							if (test.ASM != null) {
								lst.add(test);
							}
						}
					}
				}
			}
		}
		return lst;
	}

	private NewTestResult retest(PastTestResult test) throws NoSuchToolchainException {
		TestResults res = tests.run(db.new BadResults(-1, test.UserID, new Date()), test.RowID, sdb.getToolchain(test.ToolChain, false), test.TestID, test.ASM, test.Login);
		int code = res.ResultCode;
		String result = res.ResultDescription;
		int good = res.GoodTests;
		int bad = res.BadTests;
		String details = "[]";
		if (res.Details != null) {
			details = res.Details;
		}
		return new NewTestResult(test, code, result, "", details, good, bad); // TODO: "res" should be passed as json structure
	}

	public void redo() throws DatabaseException, NoSuchToolchainException {
		List<StoredNewTestResult> nw = loadNew();
		final String tableName = "compilations";

		final TableField f_result = db.getField(tableName, "result");
		final TableField f_full = db.getField(tableName, "full");
		final TableField f_bad_tests_details = db.getField(tableName, "bad_tests_details");
		final TableField f_bad_tests = db.getField(tableName, "bad_tests");
		final TableField f_good_tests = db.getField(tableName, "good_tests");
		int total = nw.size();
		int done = 0;

		for (StoredNewTestResult item : nw) {
			done++;
			if (item.Past.Code != 0 && item.Code == 0) {
				NewTestResult rt = retest(item.Past);
				db.update(tableName, item.Past.RowID, new ValuedField(f_result, rt.Result), new ValuedField(f_full, rt.Full), new ValuedField(f_bad_tests_details, rt.Details), new ValuedField(f_bad_tests, rt.Bad), new ValuedField(f_good_tests, rt.Good));
			}
			System.out.println("Done: " + done + "/" + total);
		}
	}

}
