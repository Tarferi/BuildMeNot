package cz.rion.buildserver;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.TestManager;

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
		public final String Login;
		public final int RowID;
		public final String Full;

		private PastTestResult(String ASM, String TestID, int RowID, int Code, String Result, String full, String Login) {
			super(Code, Result);
			this.RowID = RowID;
			this.ASM = ASM;
			this.TestID = TestID;
			this.Login = Login;
			this.Full = full;
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

	private RuntimeDB db;

	public Retester() throws DatabaseException {
		StaticDB sdb = new StaticDB("static.sqlite");
		this.db = new RuntimeDB("data.sqlite", sdb);
		tests = new TestManager(sdb, "./web/tests");
	}

	public void restoreData() throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();
		for (StoredNewTestResult test : data) {
			this.db.execute("UPDATE compilations SET good_tests = ?, bad_tests = ?, result = '?', full = '?', code = ? WHERE ID = ?", 0, 0, test.BackupResult, test.BackupFull, test.BackupCode, test.Past.RowID);
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	public void backupData() throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();
		for (StoredNewTestResult test : data) {
			this.db.execute("UPDATE retests SET original_result = '?', original_full = '?', original_code = ? WHERE ID = ?", test.Past.Result, test.Past.Full, test.Past.Code, test.ID);
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	public void updateData() throws DatabaseException {
		List<StoredNewTestResult> data = loadNew();
		int index = 0;
		int total = data.size();
		this.db.execute("UPDATE compilations SET good_tests = ?, bad_tests = ?, bad_tests_details = '?'", 0, 0, "[]");
		for (StoredNewTestResult test : data) {
			this.db.execute("UPDATE compilations SET good_tests = ?, bad_tests = ?, bad_tests_details = '?', result = '?', full = '?', code = ? WHERE ID = ?", test.Good, test.Bad, test.Details, test.Result, test.Full, test.Code, test.Past.RowID);
			index++;
			System.out.println("Updating " + index + "/" + total);
		}
	}

	private int getretestIDForCompilation(int compilation_id) throws DatabaseException {
		JsonArray res = db.select("SELECT * FROM retests WHERE compilation_id = ?", compilation_id).getJSON();
		if (res != null) {
			if (!res.Value.isEmpty()) {
				return res.Value.get(0).asObject().getNumber("ID").Value;
			}
		}
		return -1;
	}

	public void runTests(String test_id) throws DatabaseException {
		List<PastTestResult> data = load();
		int index = 0;
		int total = data.size();
		for (PastTestResult test : data) {
			if (test.TestID.equals(test_id)) {
				NewTestResult ntest = retest(test);
				int existingID = getretestIDForCompilation(ntest.Past.RowID);
				if (existingID == -1) { // New
					db.execute("INSERT INTO retests (compilation_id, code, result, full, creation_time, good_tests, bad_tests, bad_tests_details) VALUES (?, ?, '?', '?', ?, ?, ?, ?)", ntest.Past.RowID, ntest.Code, ntest.Result, ntest.Full, new Date().getTime(), ntest.Good, ntest.Bad, ntest.Details);
				} else { // Update
					db.execute("UPDATE retests SET code = ?, result = '?', full = '?', creation_time = ?, good_tests = ?, bad_tests = ?, bad_tests_details = '?' WHERE ID = ?", ntest.Code, ntest.Result, ntest.Full, new Date().getTime(), ntest.Good, ntest.Bad, ntest.Details, existingID);
				}
			}
			System.out.println("Done " + index + "/" + total);
			index++;
		}
	}

	public void runTests() throws DatabaseException {
		List<PastTestResult> data = load();
		db.resetRetestsDB();
		int index = 0;
		int total = data.size();
		for (PastTestResult test : data) {
			NewTestResult ntest = retest(test);
			db.execute("INSERT INTO retests (compilation_id, code, result, full, creation_time, good_tests, bad_tests, bad_tests_details) VALUES (?, ?, '?', '?', ?, ?, ?, ?)", ntest.Past.RowID, ntest.Code, ntest.Result, ntest.Full, new Date().getTime(), ntest.Good, ntest.Bad, ntest.Details);
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

		JsonArray res = db.select("SELECT retests.ID, retests.compilation_id, retests.code, retests.result, retests.full, retests.good_tests, retests.bad_tests, retests.bad_tests_details, retests.original_code, retests.original_full, retests.original_result FROM retests").getJSON();
		if (res != null) {
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
		}
		return lst;
	}

	private List<PastTestResult> load() throws DatabaseException {
		List<PastTestResult> lst = new ArrayList<>();
		JsonArray res = db.select("SELECT compilations.ID, compilations.asm, compilations.test_id, compilations.full, compilations.code, compilations.result, users.login FROM compilations, users WHERE compilations.user_id = users.id").getJSON();
		if (res != null) {
			for (JsonValue val : res.Value) {
				JsonObject obj = val.asObject();
				int id = obj.getNumber("ID").Value;
				String asm = obj.getString("asm").Value;
				String test_id = obj.getString("test_id").Value;
				String full = obj.getString("full").Value;
				String login = obj.getString("login").Value;
				int code = obj.getNumber("code").Value;
				String result = obj.getString("result").Value;
				PastTestResult test = new PastTestResult(asm, test_id, id, code, result, full, login);
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
		}
		return lst;
	}

	private NewTestResult retest(PastTestResult test) {
		JsonObject res = tests.run(test.RowID, test.TestID, test.ASM, test.Login);
		int code = res.getNumber("code").Value;
		String result = res.getString("result").Value;
		int good = res.getNumber("good").Value;
		int bad = res.getNumber("bad").Value;
		String details = "[]";
		if (res.contains("details")) {
			details = res.get("details").getJsonString();
		}
		return new NewTestResult(test, code, result, res.getJsonString(), details, good, bad);
	}

}
