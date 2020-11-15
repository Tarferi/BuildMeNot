package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.RuntimeDB.CodedHistory;
import cz.rion.buildserver.db.RuntimeDB.CompletedTest;
import cz.rion.buildserver.db.RuntimeDB.HistoryListPart;
import cz.rion.buildserver.db.RuntimeDB.LiteStoredFeedbackData;
import cz.rion.buildserver.db.RuntimeDB.TestFeedback;
import cz.rion.buildserver.db.RuntimeDB.TestHistory;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile.VirtualFileException;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredUserDB.PermissionedUser;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.test.TestManager.RunnerLogger;
import cz.rion.buildserver.test.TestManager.TestResults;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.utils.Pair;

public class StatelessTestClient extends StatelessPresenceClient {

	private final StatelessInitData data;

	protected StatelessTestClient(StatelessInitData data) {
		super(data);
		this.data = data;
	}

	@Override
	protected boolean objectionsAgainstAuthRedirection(ProcessState state) {
		boolean others = super.objectionsAgainstAuthRedirection(state);
		if (!others) { // Others don't want redirection, we
			if (state.Request.path.startsWith("/test?cache=") && state.Request.method.equals("POST") && state.Request.data.length > 0) {
				return true;
			}
		}
		return others;
	}

	@Override
	protected String handleJSManipulation(ProcessState state, String path, String js) {
		js = super.handleJSManipulation(state, path, js);
		js = js.replace("$TOOLCHAIN$", state.Toolchain.getName());
		return js;
	}

	@Override
	protected String handleHTMLManipulation(ProcessState state, String path, String html) {
		html = super.handleHTMLManipulation(state, path, html);
		html = html.replace("$TOOLCHAIN$", state.Toolchain.getName());
		html = html.replace("$FAQ_CONTENTS$", faqCaches.get(state.Toolchain));
		return html;
	}

	private final CachedToolchainData2<String> faqCaches = new CachedToolchainDataWrapper2<>(60, new CachedToolchainDataGetter2<String>() {

		@Override
		public CachedData<String> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper2<String>(refreshIntervalInSeconds, new CachedDataGetter<String>() {

				private VirtualFile loadFile(VirtualFileManager files, String name, UserContext context) {
					List<VirtualFile> lst = files.getFile(name, context);
					if (lst.isEmpty()) {
						return null;
					}
					return lst.get(0);
				}

				private UserContext toolchainContext = new UserContext() {

					@Override
					public Toolchain getToolchain() {
						return toolchain;
					}

					@Override
					public String getLogin() {
						return "root";
					}

					@Override
					public String getAddress() {
						return "0.0.0.0";
					}

					@Override
					public boolean wantCompressedData() {
						return false;
					}

				};

				@Override
				public String update() {
					VirtualFile fo = loadFile(data.Files, "FAQs/" + toolchain.getName() + ".faq", toolchainContext);
					if (fo != null) {
						try {
							return fo.read(toolchainContext);
						} catch (VirtualFileException e) {
							e.printStackTrace();
						}
					}
					return "FAQ neni pro tento toolchain dostupne";
				}
			});
		}
	});

	private boolean canBypassTimeout(ProcessState state) {
		return state.getPermissions().allowBypassTimeout(state.Toolchain) || !Settings.getForceTimeoutOnErrors();
	}

	private JsonObject decode(HTTPRequest request) {
		try {
			JsonValue json = JsonValue.parse(new String(request.data, Settings.getDefaultCharset()));
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					return obj;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private TestResults execute_test(RuntimeDB db, TestManager tests, String testID, String code, int existingCompilationID, int builderID, BadResults badResults, Toolchain toolchain, String login, RunnerLogger logger, JsonObject idata, JsonObject returnValue, boolean allowDetails, String remoteAddr, int sessionID, int userID, List<CompletedTest> completed) {
		TestResults res = tests.run(badResults, builderID, toolchain, testID, code, login, logger);
		idata.add("success", true);
		returnValue.add("code", new JsonNumber(res.ResultCode));
		returnValue.add("result", new JsonString(res.ResultDescription));
		JsonObject full = new JsonObject();
		full.add("Log", logger.getLogs());
		full.add("Process", res.ResultDescription);
		if (allowDetails) {
			returnValue.add("good", new JsonNumber(res.GoodTests));
			returnValue.add("bad", new JsonNumber(res.BadTests));
			if (res.Details != null) {
				full.add("Details", res.Details);
				returnValue.add("details", new JsonString(res.Details));
				idata.add("details", true);
			}
		}
		try {
			db.storeCompilation(completed, remoteAddr, new Date(), code, sessionID, testID, res.ResultCode, returnValue.getJsonString(), full.getJsonString(), userID, toolchain, res.Details == null ? "" : res.Details, res.GoodTests, res.BadTests, existingCompilationID);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}
		return res;
	}

	private JsonObject execute_tests(ProcessState state, String testID, String code) {
		JsonObject idata = new JsonObject();
		idata.add("testID", testID);
		idata.add("code", code);
		idata.add("success", false);
		state.setIntention(Intention.PERFORM_TEST, idata);
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		List<CompletedTest> completed = state.Data.RuntimeDB.getCompletedTests(state.getPermissions().Login, state.Toolchain);

		Set<String> completedTestsIDs = new HashSet<>();
		for (CompletedTest t : completed) {
			completedTestsIDs.add(t.TestID);
		}

		Set<String> after = state.Data.Tests.getPriorTestIDs(state.Toolchain, testID);
		boolean priorTestsCompleted = after == null ? true : after.isEmpty();
		if (after != null) {
			priorTestsCompleted = true;
			for (String anotherTest : after) {
				if (!completedTestsIDs.contains(anotherTest)) {
					priorTestsCompleted = false;
					break;
				}
			}
		}

		BadResults badResults = null;
		try {
			badResults = state.Data.RuntimeDB.GetBadResultsForUser(state.getPermissions().getUserID(), state.Toolchain);
		} catch (DatabaseException e) {
			idata.add("reason", "Failed to get bad results history");
			idata.add("exception", e.description);
			e.printStackTrace();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Request.protocol_norm, state.Request.host)));
			return returnValue;
		}
		boolean canBypassTimeout = canBypassTimeout(state);

		if (!state.getPermissions().allowExecute(testID) || !priorTestsCompleted) {
			idata.add("reason", "No permission to execute this test");
			returnValue.add("code", new JsonNumber(55));
			returnValue.add("result", new JsonString("Hacking much?"));
			return returnValue;
		}
		long now = new Date().getTime();
		long then = badResults.AllowNext.getTime();
		long diff = then > now ? then - now : 0;
		if (diff > 10000 && !canBypassTimeout) { // 10 seconds allowance
			returnValue.add("code", new JsonNumber(54));
			returnValue.add("result", new JsonString("Hacking much?"));
			idata.add("reason", "There is a timeout still (" + (diff / 1000) + " more seconds)");
			return returnValue;
		}
		RunnerLogger logger = new RunnerLogger();

		// String testID, String code, int existingCompilationID, int builderID,
		// BadResults badResults, Toolchain toolchain, String login, RunnerLogger logger
		TestResults res = execute_test(state.Data.RuntimeDB, state.Data.Tests, testID, code, -1, state.BuilderID, badResults, state.Toolchain, state.getPermissions().Login, logger, idata, returnValue, state.getPermissions().allowDetails(testID), state.Request.remoteAddress, state.getPermissions().getSessionID(), state.getPermissions().getUserID(), completed);

		// See if user has finished this test before
		boolean newlyFinished = res.ResultCode == 0;

		for (CompletedTest test : completed) {
			if (test.Code.equals(testID)) {
				newlyFinished = false;
			}
		}

		if (newlyFinished) { // See if there are tests to be unlocked
			JsonArray newlyUnlocked = new JsonArray();
			for (GenericTest test : state.Data.Tests.getAllTests(state.Toolchain)) {
				boolean unlocked = test.getPriorTestsIDs().contains(testID);
				for (String anotherTest : test.getPriorTestsIDs()) {
					if (!anotherTest.equals(testID) && !completedTestsIDs.contains(anotherTest)) {
						unlocked = false;
						break;
					}
				}
				if (unlocked) {
					JsonObject tobj = new JsonObject();
					tobj.add("title", new JsonString(test.getTitle()));
					tobj.add("zadani", new JsonString(test.getDescription()));
					tobj.add("init", new JsonString(test.getInitialCode()));
					tobj.add("hidden", new JsonNumber(test.isHidden() ? 1 : 0));
					if (!state.getPermissions().allowExecute(test.getID())) {
						tobj.add("noexec", new JsonNumber(1));
					}
					tobj.add("id", new JsonString(test.getID()));
					newlyUnlocked.add(tobj);
				}
			}
			if (!newlyUnlocked.Value.isEmpty()) {
				returnValue.add("unlocked", newlyUnlocked);
			}
		}

		if (Settings.getForceTimeoutOnErrors()) {
			try {
				badResults.store(newlyFinished, state.Toolchain);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		if (canBypassTimeout) {
			returnValue.add("wait", new JsonNumber(0, (System.currentTimeMillis() - 10000) + ""));
		} else if (Settings.getForceTimeoutOnErrors()) {
			returnValue.add("wait", new JsonNumber(0, (badResults.AllowNext.getTime()) + ""));
		}

		return returnValue;
	}

	private JsonObject execute_collect_history_users(ProcessState state) {
		JsonObject idata = new JsonObject();
		idata.add("action", "get_history_users");
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);
		JsonObject obj = new JsonObject();
		obj.add("code", 1);

		boolean admin = state.getPermissions().allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
		if (!admin) {
			obj.add("result", "Nedostateèná úroveò oprávnìní");
			return obj;
		}
		JsonObject total = new JsonObject();
		JsonObject ar_users = new JsonObject();
		JsonArray ar_groups = new JsonArray();

		List<PermissionedUser> data = state.Data.StaticDB.getPermissionedUsers(state.Toolchain);
		Set<Integer> knownGroups = new HashSet<>();
		Map<String, Pair<JsonArray, JsonObject>> udatam = new HashMap<>();
		for (PermissionedUser entry : data) {
			int gid = entry.GroupID;
			if (!knownGroups.contains(gid)) {
				knownGroups.add(gid);
				JsonObject gdata = new JsonObject();
				gdata.add("ID", gid);
				gdata.add("Name", entry.GroupName);
				ar_groups.add(gdata);
			}
			Pair<JsonArray, JsonObject> ar = udatam.get(entry.Login);
			if (ar == null) {
				JsonObject o = new JsonObject();
				JsonArray v = new JsonArray();
				ar = new Pair<JsonArray, JsonObject>(v, o);
				o.add("Groups", v);
				o.add("Name", entry.Name);
				udatam.put(entry.Login, ar);
				ar_users.add(entry.Login, o);
			}
			ar.Key.add(entry.GroupID);
		}

		total.add("Groups", ar_groups);
		total.add("Users", ar_users);

		obj.add("code", 0);
		obj.add("result", total);
		idata.add("result", true);
		return obj;
	}

	private JsonObject execute_getHistory(ProcessState state, String testID, JsonObject input) {
		JsonObject idata = new JsonObject();
		idata.add("action", "get_history");
		idata.add("test_id", testID);
		idata.add("input", input);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		boolean admin = state.getPermissions().allowEditHistoryOfSomeoneElsesTest(state.Toolchain);

		if ((input.containsNumber("limit") && input.containsNumber("page")) && (!admin || (input.containsArray("groups") && input.containsArray("logins")))) {
			try {
				Set<String> loginsS = new HashSet<>();
				int limit = input.getNumber("limit").Value;
				int page = input.getNumber("page").Value;
				if (admin) {
					JsonArray logins = input.getArray("logins");
					JsonArray groups = input.getArray("groups");

					if (limit > 300 || limit <= 0 || page < 0) {
						obj.add("result", "Neplatný poèet výsledkù není povolen");
						return obj;
					}

					List<Integer> groupIDs = new ArrayList<>();
					for (JsonValue val : groups.Value) {
						if (val.isNumber()) {
							groupIDs.add(val.asNumber().Value);
						}
					}

					for (JsonValue val : logins.Value) {
						if (val.isString()) {
							loginsS.add(val.asString().Value);
						}
					}
					if (groupIDs.size() > 0) {
						List<String> groupLogins = state.Data.StaticDB.getLoginsByGroupIDs(state.Toolchain, groupIDs);
						loginsS.addAll(groupLogins);
					}
				} else {
					loginsS.add(state.getPermissions().Login);
				}

				HistoryListPart hist = state.Data.RuntimeDB.getHistory(state.Toolchain, state.getPermissions(), testID, !admin, admin, limit, limit * page, loginsS, false);
				JsonArray h = JsonArray.get(hist);
				JsonObject m = new JsonObject();
				m.add("data", h);
				m.add("more", hist.hasMore());
				obj.add("result", m);
				obj.add("code", 0);
				idata.add("result", true);
			} catch (DatabaseException e) {
				e.printStackTrace();
				obj.add("code", 1);
				obj.add("result", e.description);
			}
		} else { // Missing fields, notify about avilability
			obj.add("code", 0);
			obj.add("result", admin ? execute_collect_history_users(state) : new JsonObject());
		}
		return obj;
	}

	private JsonObject execute_collect_protocol(ProcessState state, int compilationID) {
		JsonObject idata = new JsonObject();
		idata.add("action", "get_protocol");
		idata.add("compilation_id", compilationID);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		boolean admin = state.getPermissions().allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
		if (admin) {
			try {
				CodedHistory res = state.Data.RuntimeDB.getSingleHistory(state.Toolchain, compilationID, true);
				String p = res.Protocol;
				JsonArray result = new JsonArray();
				if (p != null) {
					JsonValue val = JsonValue.parse(p);
					if (val != null) {
						if (val.isObject()) {
							JsonObject o = val.asObject();
							if (o.containsArray("Log")) {
								result = o.getArray("Log");
							}
						}
					}
				}
				obj.add("result", result);
				obj.add("code", 0);
				idata.add("result", true);
			} catch (DatabaseException e) {
				e.printStackTrace();
				obj.add("code", 1);
				obj.add("result", e.description);
			}
		}
		return obj;
	}

	private JsonObject execute_getFeedback(ProcessState state, int compilationID) {
		JsonObject idata = new JsonObject();
		idata.add("action", "get_feedback");
		idata.add("compilation_id", compilationID);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		try {
			UsersPermission perms = state.getPermissions();
			boolean canEditAnything = perms.allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
			CodedHistory history = state.Data.RuntimeDB.getSingleHistory(state.Toolchain, compilationID, false);
			if (history.AuthorID != perms.getUserID()) { // Editing something we don't own
				if (!perms.allowEditHistoryOfSomeoneElsesTest(state.Toolchain)) {
					obj.add("result", "Nelze editovat historii nìkoho cizího");
					return obj;
				}
			} else { // Editing our own
			}

			List<TestFeedback> feedbacks = state.Data.RuntimeDB.getFeedbacks(state.Toolchain, compilationID, perms.getUserID(), canEditAnything);
			JsonArray h = JsonArray.get(feedbacks);

			JsonObject r = new JsonObject();
			r.add("comments", h);
			r.add("data", history);

			obj.add("result", r);
			obj.add("code", 0);
			idata.add("result", true);
		} catch (DatabaseException e) {
			e.printStackTrace();
			obj.add("code", 1);
			obj.add("result", e.description);
		}
		return obj;
	}

	private JsonObject execute_saveFeedback(ProcessState state, int feedbackID, JsonValue data, boolean del) {
		JsonObject idata = new JsonObject();
		idata.add("action", "save_feedback");
		idata.add("feedback_id", feedbackID);
		idata.add("del", del);
		idata.add("data", data);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		try {
			UsersPermission perms = state.getPermissions();
			boolean canEditAnything = perms.allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
			TestFeedback existing = state.Data.RuntimeDB.getSingleFeedback(state.Toolchain, feedbackID, perms.getUserID(), canEditAnything);
			if (existing == null) {
				obj.add("result", "Záznam komentáøe s tímto ID neexistuje");
				return obj;
			}
			if (existing.AuthorID != perms.getUserID()) { // Editing something we don't own
				if (!canEditAnything) {
					obj.add("result", "Nelze editovat komentáø nìkoho cizího");
					return obj;
				}
			} else { // Editing our own
			}
			state.Data.RuntimeDB.updateFeedback(state.Toolchain, feedbackID, data, del);
			if (del) {
				obj.add("result", "OK");
			} else {
				existing = state.Data.RuntimeDB.getSingleFeedback(state.Toolchain, feedbackID, perms.getUserID(), canEditAnything);
				obj.add("result", existing);
			}
			obj.add("code", 0);
			idata.add("result", true);
		} catch (DatabaseException e) {
			e.printStackTrace();
			obj.add("code", 1);
			obj.add("result", e.description);
			return obj;
		}

		return obj;
	}

	private JsonObject execute_addFeedback(ProcessState state, int compilation_id, JsonValue data) {
		JsonObject idata = new JsonObject();
		idata.add("action", "add_feedback");
		idata.add("compilationID", compilation_id);
		idata.add("data", data);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		try {
			CodedHistory existing = state.Data.RuntimeDB.getSingleHistory(state.Toolchain, compilation_id, false);
			if (existing == null) {
				obj.add("result", "Záznam kompilace s tímto ID neexistuje");
				return obj;
			}
			UsersPermission perms = state.getPermissions();
			if (existing.AuthorID != perms.getUserID()) { // Editing something we don't own
				if (!perms.allowEditHistoryOfSomeoneElsesTest(state.Toolchain)) {
					obj.add("result", "Nelze editovat historii nìkoho cizího");
					return obj;
				}
			} else { // Editing our own
			}
			state.Data.RuntimeDB.storeFeedback(state.Toolchain, state.getPermissions().getUserID(), compilation_id, data);
			existing = state.Data.RuntimeDB.getSingleHistory(state.Toolchain, compilation_id, false);
			obj.add("result", existing);
			obj.add("code", 0);
			idata.add("result", true);
		} catch (DatabaseException e) {
			e.printStackTrace();
			obj.add("code", 1);
			obj.add("result", e.description);
			return obj;
		}
		return obj;
	}

	private JsonObject execute_poll_feedback(ProcessState state, int lastReadID, JsonArray logins, JsonArray groups) {
		JsonObject obj = new JsonObject();
		JsonObject idata = new JsonObject();
		idata.add("action", "poll");
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND_POLL, idata);
		obj.add("code", 1);
		try {
			UsersPermission perms = state.getPermissions();
			boolean admin = perms.allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
			Set<String> loginsS = new HashSet<>();
			if (admin) {
				List<Integer> groupIDs = new ArrayList<>();
				for (JsonValue val : groups.Value) {
					if (val.isNumber()) {
						groupIDs.add(val.asNumber().Value);
					}
				}
				for (JsonValue val : logins.Value) {
					if (val.isString()) {
						loginsS.add(val.asString().Value);
					}
				}
				if (groupIDs.size() > 0) {
					List<String> groupLogins = state.Data.StaticDB.getLoginsByGroupIDs(state.Toolchain, groupIDs);
					loginsS.addAll(groupLogins);
				}
			} else {
				loginsS.add(state.getPermissions().Login);
			}
			LiteStoredFeedbackData res = state.Data.RuntimeDB.getFeedbacksFor(state.Toolchain, loginsS, lastReadID, state.getPermissions().Login);
			obj.add("code", 0);
			obj.add("result", res);
			idata.add("result", true);
		} catch (DatabaseException e) {
			e.printStackTrace();
			obj.add("result", e.description);
		}
		return obj;
	}

	private JsonObject execute_retests(ProcessState state, String testID) {
		JsonObject idata = new JsonObject();
		idata.add("action", "retests");
		idata.add("test_id", testID);
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		boolean admin = state.getPermissions().allowEditHistoryOfSomeoneElsesTest(state.Toolchain);
		if (admin) {
			try {
				HistoryListPart history = state.Data.RuntimeDB.getHistory(state.Toolchain, state.getPermissions(), testID, false, false, 10000, 0, (Set<String>) null, true);
				int total = history.size();
				int current = 0;
				for (TestHistory item : history) {
					current++;
					System.out.println("Retesting " + current + "/" + total);
					List<CompletedTest> completed = state.Data.RuntimeDB.getCompletedTests(item.Login, state.Toolchain);
					BadResults badResults = null;
					try {
						badResults = state.Data.RuntimeDB.GetBadResultsForUser(state.getPermissions().getUserID(), state.Toolchain);
					} catch (DatabaseException e) {
						e.printStackTrace();
					}
					RunnerLogger logger = new RunnerLogger();
					JsonObject returnValue = new JsonObject();
					execute_test(state.Data.RuntimeDB, state.Data.Tests, item.TestID, item.Code, item.ID, state.BuilderID, badResults, state.Toolchain, item.Login, logger, idata, returnValue, false, item.Address, item.SessionID, item.UserID, completed);
				}
				obj.add("result", new JsonObject());
				obj.add("code", 0);
				idata.add("result", true);
			} catch (DatabaseException e) {
				e.printStackTrace();
				obj.add("code", 1);
				obj.add("result", e.description);
			}
		}
		return obj;
	}

	private JsonObject execute_update_stats(ProcessState state) {
		JsonObject idata = new JsonObject();
		idata.add("action", "update_stats");
		idata.add("result", false);
		state.setIntention(Intention.HISTORY_COMMAND, idata);

		JsonObject obj = new JsonObject();
		obj.add("code", 1);
		try {
			state.Data.RuntimeDB.updateStatsForAllUsers(state.Toolchain);
			obj.add("result", "ok");
			obj.add("code", 0);
			idata.add("result", true);
		} catch (DatabaseException e) {
			e.printStackTrace();
			obj.add("code", 1);
			obj.add("result", e.description);
			return obj;
		}
		return obj;
	}

	private JsonObject execute_collect(ProcessState state) {
		state.setIntention(Intention.COLLECT_TESTS, new JsonObject());
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		List<GenericTest> tsts = state.Data.Tests.getAllTests(state.Toolchain);
		tsts.sort(new Comparator<GenericTest>() {

			@Override
			public int compare(GenericTest o1, GenericTest o2) {
				String id1 = o1.getID();
				String id2 = o2.getID();

				String[] p1 = id1.split("_");
				String[] p2 = id2.split("_");

				if (p1[0].equals(p2[0])) {
					id1 = p1.length > 1 ? p1[1] : "";
					id2 = p2.length > 1 ? p2[1] : "";
					return id1.compareTo(id2);
				} else {
					return id2.compareTo(id1);
				}
			}
		});
		List<CompletedTest> completed = state.Data.RuntimeDB.getCompletedTests(state.getPermissions().Login, state.Toolchain);
		List<JsonValue> d = new ArrayList<>();
		Map<String, CompletedTest> finishedByTestID = new HashMap<>();
		for (CompletedTest test : completed) {
			finishedByTestID.put(test.TestID, test);
		}

		for (GenericTest tst : tsts) {
			if (!state.getPermissions().allowSee(tst.getID())) {
				continue;
			}
			if (tst.isSecret() && !state.getPermissions().allowSeeSecretTests(state.Toolchain)) {
				continue;
			}

			// Check if all prior tests are completed
			Set<String> after = tst.getPriorTestsIDs();
			boolean priorTestsCompleted = true;
			for (String anotherTest : after) {
				if (!finishedByTestID.containsKey(anotherTest)) {
					priorTestsCompleted = false;
					break;
				}
			}

			JsonObject tobj = new JsonObject();
			if (priorTestsCompleted) {
				tobj.add("title", new JsonString(tst.getTitle()));
				tobj.add("zadani", new JsonString(tst.getDescription()));
				tobj.add("init", new JsonString(tst.getInitialCode()));
				tobj.add("hidden", new JsonNumber(tst.isHidden() ? 1 : 0));
				if (!state.getPermissions().allowExecute(tst.getID())) {
					tobj.add("noexec", new JsonNumber(1));
				}
			}
			tobj.add("id", new JsonString(tst.getID()));
			if (finishedByTestID.containsKey(tst.getID())) {
				CompletedTest result = finishedByTestID.get(tst.getID());
				tobj.add("finished_date", new JsonString(result.CompletionDateStr));
				tobj.add("finished_code", new JsonString(result.Code));
			}
			d.add(tobj);
		}

		BadResults badResults = null;
		try {
			badResults = state.Data.RuntimeDB.GetBadResultsForUser(state.getPermissions().getUserID(), state.Toolchain);
		} catch (DatabaseException e) {
			e.printStackTrace();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Request.protocol_norm, state.Request.host)));
			return returnValue;
		}

		JsonObject res = new JsonObject();
		res.add("tests", new JsonArray(d));
		String notification = data.StaticDB.getNotification(state.Toolchain, state.getPermissions());
		if (notification != null) {
			res.add("notification", notification);
		}

		returnValue.add("code", new JsonNumber(0));
		returnValue.add("result", res.getJsonString());
		if (canBypassTimeout(state)) {
			res.add("wait", new JsonNumber(0, (new Date().getTime() - 10000) + ""));
		} else {
			res.add("wait", new JsonNumber(0, (badResults.AllowNext.getTime()) + ""));
		}
		return returnValue;
	}

	private JsonObject execute_graphs(ProcessState state) {
		state.setIntention(Intention.COLLECT_GRAPHS, new JsonObject());
		JsonValue graphs = loadGraphs(state);
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(0));
		returnValue.add("result", graphs == null ? new JsonArray(new ArrayList<JsonValue>()).getJsonString() : graphs.getJsonString());
		return returnValue;
	}

	private JsonObject execute_admin(ProcessState state, JsonObject data) {
		state.setIntention(Intention.ADMIN_COMMAND, new JsonObject());
		return handleAdminEvent(state, data);
	}

	private JsonObject execute_terms(ProcessState state, JsonObject data) {
		state.setIntention(Intention.TERM_COMMAND, new JsonObject());
		return handleTermsEvent(state, data);
	}

	private JsonObject execute_exams(ProcessState state, JsonObject data) {
		state.setIntention(Intention.EXAM_COMMAND, new JsonObject());
		return handleExamsEvent(state, data);
	}

	private JsonObject execute(ProcessState state, JsonObject input) {
		state.setIntention(Intention.UNKNOWN, input);
		if (!state.IsLoggedIn()) {
			JsonObject returnValue = new JsonObject();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Request.protocol_norm, state.Request.host)));
			return returnValue;
		}
		if (input.containsString("asm") && input.containsString("id")) {
			String code = input.getString("asm").Value;
			String id = input.getString("id").Value;
			JsonObject obj = new JsonObject();
			obj.add("code", new JsonNumber(0));
			obj.add("result", new JsonString(execute_tests(state, id, code).getJsonString()));
			return obj;
		} else if (input.containsString("action")) {
			String act = input.getString("action").Value;
			if (act.equals("COLLECT")) {
				return execute_collect(state);
			} else if (act.equals("GRAPHS")) {
				return execute_graphs(state);
			} else if (act.equals("ADMIN") && state.getPermissions().allowSeeWebAdmin(state.Toolchain)) {
				return execute_admin(state, input);
			} else if (act.equals("HANDLE_TERMS")) {
				return execute_terms(state, input);
			} else if (act.equals("HANDLE_EXAMS")) {
				return execute_exams(state, input);
			} else if (act.equals("COLLECT_HISTORY") && input.containsString("testID")) {
				return execute_getHistory(state, input.getString("testID").Value, input);
			} else if (act.equals("COLLECT_FEEDBACK") && input.containsNumber("compilationID")) {
				return execute_getFeedback(state, input.getNumber("compilationID").Value);
			} else if (act.equals("STORE_FEEDBACK") && input.containsNumber("compilationID") && input.contains("feedbackData")) {
				return execute_addFeedback(state, input.getNumber("compilationID").Value, input.get("feedbackData"));
			} else if (act.equals("EDIT_FEEDBACK") && input.containsNumber("feedbackID") && input.contains("feedbackData") && input.containsBoolean("del")) {
				return execute_saveFeedback(state, input.getNumber("feedbackID").Value, input.get("feedbackData"), input.getBoolean("del").Value);
			} else if (act.equals("COLLECT_PROTOCOL") && input.containsNumber("compilationID")) {
				return execute_collect_protocol(state, input.getNumber("compilationID").Value);
			} else if (act.equals("COLLECT_HISTORY_USERS")) {
				return execute_collect_history_users(state);
			} else if (act.equals("POLL_FEEDBACK") && input.containsNumber("lastReadID") && input.containsArray("logins") && input.containsArray("groups")) {
				return execute_poll_feedback(state, input.getNumber("lastReadID").Value, input.getArray("logins"), input.getArray("groups"));
			} else if (act.equals("RETEST") && input.containsString("testID")) {
				return execute_retests(state, input.getString("testID").Value);
			} else if (act.equals("UPDATE_STATS")) {
				return execute_update_stats(state);
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("code", new JsonNumber(1));
		obj.add("result", new JsonString("Internal error"));
		return obj;
	}

	@Override
	protected HTTPResponse handle(ProcessState state) {
		if (state.Request.path.startsWith("/exec?cache=") && state.Request.method.equals("POST") && state.Request.data.length > 0) {
			byte[] data = new byte[0];
			JsonObject req = decode(state.Request);
			if (req != null) {
				JsonObject resp;
				try {
					resp = execute(state, req);
				} catch (Exception e) {
					e.printStackTrace();
					resp = new JsonObject();
					resp.add("code", 1);
					resp.add("result", e.toString());
				}
				if (resp != null) {
					data = resp.getJsonString().getBytes(Settings.getDefaultCharset());
				}
			}
			return new HTTPResponse(state.Request.protocol, 200, "OK", data, "multipart/form-data;", state.Request.cookiesLines);
		}
		return super.handle(state);
	}

	@Override
	public void clearCache() {
		super.clearCache();
		faqCaches.clear();
	}
}
