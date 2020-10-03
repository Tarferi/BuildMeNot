package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.RuntimeDB.CompletedTest;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.TestManager.TestResults;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;

public class StatelessTestClient extends StatelessTermClient {

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

	private static int fromHex(char c) throws HTTPClientException {
		if (c >= '0' && c <= '9') {
			return c - '0';
		} else if (c >= 'A' && c <= 'F') {
			return 10 + c - 'A';
		} else if (c >= 'a' && c <= 'f') {
			return 10 + c - 'a';
		} else {
			throw new HTTPClientException("Invalid data item: " + c);
		}
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

				@Override
				public String update() {
					FileInfo fo = data.StaticDB.loadFile("FAQs/" + toolchain.getName() + ".faq", true);
					if (fo == null) {
						return "FAQ neni pro tento toolchain dostupne";
					}
					return fo.Contents;
				}
			});
		}
	});

	private static String decode(byte[] data) throws HTTPClientException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < data.length / 2; i++) {
			char c1 = (char) data[i * 2];
			char c2 = (char) data[(i * 2) + 1];
			int x = (fromHex(c1) << 4) | fromHex(c2);
			char c = (char) x;
			sb.append(c);
		}
		return sb.toString();
	}

	private static char toHex(int c) throws HTTPClientException {
		if (c >= 0 && c <= 9) {
			return (char) (c + '0');
		} else if (c >= 10 && c <= 15) {
			return (char) ((c - 10) + 'a');
		} else {
			throw new HTTPClientException("Invlaid by to encode");
		}
	}

	private static byte[] encode(String data) throws HTTPClientException {
		byte[] bdata = data.getBytes(Settings.getDefaultCharset());
		byte[] result = new byte[bdata.length * 2];

		for (int i = 0; i < bdata.length; i++) {
			int c = bdata[i] & 0xff;
			char c1 = toHex((byte) (c >> 4));
			char c2 = toHex((byte) (c & 0b1111));
			result[i * 2] = (byte) c1;
			result[(i * 2) + 1] = (byte) c2;
		}
		return result;
	}

	private boolean canBypassTimeout(ProcessState state) {
		return state.getPermissions().allowBypassTimeout() || !Settings.getForceTimeoutOnErrors();
	}

	private JsonObject decode(HTTPRequest request) {
		byte[] data = request.data;
		if (data[0] == 'q' && data[1] == '=') {
			byte[] newData = new byte[data.length - 2];
			System.arraycopy(data, 2, newData, 0, data.length - 2);
			data = newData;
		}
		if (data.length % 2 == 0) {
			String jsn;
			try {
				jsn = decode(data);
			} catch (HTTPClientException e) {
				e.printStackTrace();
				return null;
			}
			JsonValue json = JsonValue.parse(jsn);
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					return obj;
				}
			}
		}
		return null;
	}

	private JsonObject execute_tests(ProcessState state, String testID, String code) {
		state.setIntention(Intention.PERFORM_TEST);
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		List<CompletedTest> completed = state.Data.RuntimeDB.getCompletedTests(state.getPermissions().Login, state.Toolchain.getName());
		BadResults badResults = null;
		try {
			badResults = state.Data.RuntimeDB.GetBadResultsForUser(state.getPermissions().getUserID());
		} catch (DatabaseException e) {
			e.printStackTrace();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Toolchain.getName(), "")));
			return returnValue;
		}
		boolean canBypassTimeout = canBypassTimeout(state);

		if (!state.getPermissions().allowExecute(testID)) {
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
			return returnValue;
		}

		TestResults res = state.Data.Tests.run(badResults, state.BuilderID, state.Toolchain, testID, code, state.getPermissions().Login);

		returnValue.add("code", new JsonNumber(res.ResultCode));
		returnValue.add("result", new JsonString(res.ResultDescription));

		if (state.getPermissions().allowDetails(testID)) {
			returnValue.add("good", new JsonNumber(res.GoodTests));
			returnValue.add("bad", new JsonNumber(res.BadTests));
			if (res.Details != null) {
				returnValue.add("details", new JsonString(res.Details));
			}
		}

		try {
			state.Data.RuntimeDB.storeCompilation(completed, state.Request.remoteAddress, new Date(), code, state.getPermissions().getSessionID(), testID, res.ResultCode, returnValue.getJsonString(), res.ResultDescription, state.getPermissions().getUserID(), state.Toolchain.getName(), res.Details == null ? "" : res.Details, res.GoodTests, res.BadTests);
		} catch (DatabaseException e1) {
			e1.printStackTrace();
		}

		// See if user has finished this test before
		boolean newlyFinished = res.ResultCode == 0;
		for (CompletedTest test : completed) {
			if (test.Code.equals(testID)) {
				newlyFinished = false;
			}
		}

		if (Settings.getForceTimeoutOnErrors()) {
			try {
				badResults.store(newlyFinished);
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}
		if (canBypassTimeout) {
			returnValue.add("wait", new JsonNumber(0, (new Date().getTime() - 10000) + ""));
		} else if (Settings.getForceTimeoutOnErrors()) {
			returnValue.add("wait", new JsonNumber(0, (badResults.AllowNext.getTime()) + ""));
		}

		return returnValue;
	}

	private JsonObject execute_collect(ProcessState state) {
		state.setIntention(Intention.COLLECT_TESTS);
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		List<GenericTest> tsts = state.Data.Tests.getAllTests(state.Toolchain.getName());
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
		List<CompletedTest> completed = state.Data.RuntimeDB.getCompletedTests(state.getPermissions().Login, state.Toolchain.getName());
		List<JsonValue> d = new ArrayList<>();
		Map<String, CompletedTest> finishedByTestID = new HashMap<>();
		for (CompletedTest test : completed) {
			finishedByTestID.put(test.TestID, test);
		}

		for (GenericTest tst : tsts) {
			if (!state.getPermissions().allowSee(tst.getID())) {
				continue;
			}
			if (tst.isSecret() && !state.getPermissions().allowSeeSecretTests()) {
				continue;
			}
			JsonObject tobj = new JsonObject();
			tobj.add("title", new JsonString(tst.getTitle()));
			tobj.add("zadani", new JsonString(tst.getDescription()));
			tobj.add("init", new JsonString(tst.getSubmittedCode()));
			tobj.add("id", new JsonString(tst.getID()));
			if (!state.getPermissions().allowExecute(tst.getID())) {
				tobj.add("noexec", new JsonNumber(1));
			}
			tobj.add("hidden", new JsonNumber(tst.isHidden() ? 1 : 0));
			if (finishedByTestID.containsKey(tst.getID())) {
				CompletedTest result = finishedByTestID.get(tst.getID());
				tobj.add("finished_date", new JsonString(result.CompletionDateStr));
				tobj.add("finished_code", new JsonString(result.Code));
			}
			d.add(tobj);
		}

		BadResults badResults = null;
		try {
			badResults = state.Data.RuntimeDB.GetBadResultsForUser(state.getPermissions().getUserID());
		} catch (DatabaseException e) {
			e.printStackTrace();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Toolchain.getName(), "")));
			return returnValue;
		}

		returnValue.add("code", new JsonNumber(0));
		returnValue.add("tests", new JsonArray(d));
		if (canBypassTimeout(state)) {
			returnValue.add("wait", new JsonNumber(0, (new Date().getTime() - 10000) + ""));
		} else {
			returnValue.add("wait", new JsonNumber(0, (badResults.AllowNext.getTime()) + ""));
		}
		return returnValue;
	}

	private JsonObject execute_graphs(ProcessState state) {
		state.setIntention(Intention.COLLECT_GRAPHS);
		JsonValue graphs = loadGraphs(state);
		JsonObject returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(0));
		returnValue.add("data", graphs == null ? new JsonArray(new ArrayList<JsonValue>()) : graphs);
		return returnValue;
	}

	private JsonObject execute_admin(ProcessState state, JsonObject data) {
		state.setIntention(Intention.ADMIN_COMMAND);
		return handleAdminEvent(state, data);
	}

	private JsonObject execute_terms(ProcessState state, JsonObject data) {
		state.setIntention(Intention.TERM_COMMAND);
		return handleTermsEvent(state, data);
	}

	private JsonObject execute(ProcessState state, JsonObject input) {
		if (!state.IsLoggedIn()) {
			JsonObject returnValue = new JsonObject();
			returnValue.add("code", new JsonNumber(53));
			returnValue.add("result", new JsonString("Not logged in"));
			returnValue.add("authUrl", new JsonString(Settings.getAuthURL(state.Toolchain.getName(), "")));
			return returnValue;
		}

		if (input.containsString("asm") && input.containsString("id")) {
			String code = input.getString("asm").Value;
			String id = input.getString("id").Value;
			return execute_tests(state, id, code);
		} else if (input.containsString("action")) {
			String act = input.getString("action").Value;
			if (act.equals("COLLECT")) {
				return execute_collect(state);
			} else if (act.equals("GRAPHS")) {
				return execute_graphs(state);
			} else if (act.equals("ADMIN") && state.getPermissions().allowSeeWebAdmin()) {
				return execute_admin(state, input);
			} else if (act.equals("HANDLE_TERMS")) {
				return execute_terms(state, input);
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("code", new JsonNumber(1));
		obj.add("result", new JsonString("Internal error"));
		return obj;
	}

	private byte[] encode(JsonObject obj) {
		try {
			return encode(obj.getJsonString());
		} catch (HTTPClientException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected HTTPResponse handle(ProcessState state) {
		if (state.Request.path.startsWith("/test?cache=") && state.Request.method.equals("POST") && state.Request.data.length > 0) {
			byte[] data = new byte[0];
			JsonObject req = decode(state.Request);
			if (req != null) {
				JsonObject resp = execute(state, req);
				if (resp != null) {
					byte[] rdata = encode(resp);
					if (rdata != null) {
						data = rdata;
					}
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
