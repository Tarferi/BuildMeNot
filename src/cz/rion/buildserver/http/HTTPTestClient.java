package cz.rion.buildserver.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.RuntimeDB.CompletedTest;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.utils.CachedData;

public class HTTPTestClient extends HTTPGraphProviderClient {

	private final CompatibleSocketClient client;
	private JsonObject returnValue = null;
	private boolean testsPassed = false;
	private final RuntimeDB db;
	private final StaticDB sdb;
	private String test_id;
	private Toolchain toolchain;
	private String asm;
	private final TestManager tests;
	private List<CompletedTest> completed = new ArrayList<>();
	private BadResults badResults = null;
	private boolean wantsToRedirect = false;

	public String getTestID() {
		return test_id;
	}

	public String getASM() {
		return asm;
	}

	@Override
	public Toolchain getToolchain() {
		return toolchain;
	}

	@Override
	public boolean objectionsAgainstRedirection(HTTPRequest request) {
		boolean others = super.objectionsAgainstRedirection(request);
		if (!others) { // Others don't want redirection, we
			if (request.path.startsWith("/test?cache=") && request.method.equals("POST") && request.data.length > 0) {
				wantsToRedirect = true;
				return true;
			}
		}
		return false;
	}

	public JsonObject getReturnValue() {
		return returnValue;
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
	protected String handleJSManipulation(String host, String path, String js) {
		js = super.handleJSManipulation(host, path, js);
		String toolchain = this.toolchain == null ? "alert(\"Nepodarilo se nacist toolchain\");" : this.toolchain.getName();
		js = js.replace("$TOOLCHAIN$", toolchain);
		return js;
	}

	@Override
	protected String handleHTMLManipulation(String host, String path, String html) {
		html = super.handleHTMLManipulation(host, path, html);
		String toolchain = this.toolchain == null ? "alert(\"Nepodarilo se nacist toolchain\");" : this.toolchain.getName();
		html = html.replace("$TOOLCHAIN$", toolchain);
		html = html.replace("$FAQ_CONTENTS$", getFAQ(toolchain));
		return html;
	}

	private final Map<String, CachedData<String>> faqCaches = new HashMap<>();

	private String getFAQ(final String toolchain) {
		String key = "FAQs/" + toolchain + ".faq";
		if (!faqCaches.containsKey(key)) {
			faqCaches.put(key, new CachedData<String>(10) {

				@Override
				protected String update() {
					FileInfo fo = sdb.loadFile("FAQs/" + toolchain + ".faq", true);
					if (fo == null) {
						return "FAQ neni pro tento toolchain dostupne";
					}
					return fo.Contents;
				}
			});
		}
		return faqCaches.get(key).get();
	}

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

	protected HTTPTestClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb, TestManager tests) {
		super(client, BuilderID, rdb, sdb);
		this.client = client;
		this.tests = tests;
		this.db = rdb;
		this.sdb = sdb;
	}

	@Override
	protected HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		String toolchain = sdb.getToolchainMapping(request.host);
		try {
			this.toolchain = sdb.getToolchain(toolchain == null ? "" : toolchain);
		} catch (NoSuchToolchainException e) {
			return new HTTPResponse(request.protocol, 200, "OK", ("Invalid toolchain: " + toolchain).getBytes(), "text/html; charset=UTF-8", request.cookiesLines);
		}

		if (request.path.startsWith("/test?cache=") && request.method.equals("POST") && request.data.length > 0) {
			byte[] data = handleTest(request.data, wantsToRedirect, request.authData);
			return new HTTPResponse(request.protocol, 200, "OK", data, "multipart/form-data;", request.cookiesLines);
		}
		return super.handle(request);
	}

	private byte[] handleTest(byte[] data, boolean authenticationRequired, String authData) throws HTTPClientException {
		if (data[0] == 'q' && data[1] == '=') {
			byte[] newData = new byte[data.length - 2];
			System.arraycopy(data, 2, newData, 0, data.length - 2);
			data = newData;
		}
		this.setIntention(HTTPClientIntentType.PERFORM_TEST);
		returnValue = new JsonObject();
		returnValue.add("code", new JsonNumber(1));
		returnValue.add("result", new JsonString("Internal error"));

		if (data.length % 2 == 0) {
			String jsn = decode(data);
			JsonValue json = JsonValue.parse(jsn);
			if (json != null) {
				if (json.isObject()) {
					JsonObject obj = json.asObject();
					boolean authenticated = true;
					if (authenticationRequired) {
						authenticated = false;
						if (authData != null) { // Without auth data, invalid client
							returnValue.add("code", new JsonNumber(53));
							returnValue.add("result", new JsonString("Not logged in"));
						}
					}

					if (authenticated) {
						completed = db.getCompletedTests(getPermissions().Login, toolchain.getName());
						try {
							badResults = db.GetBadResultsForUser(getPermissions().UserID);
						} catch (DatabaseException e) {
							e.printStackTrace();
							returnValue.add("code", new JsonNumber(53));
							returnValue.add("result", new JsonString("Not logged in"));
						}
						boolean canBypassTimeout = getPermissions().allowBypassTimeout() || !Settings.getForceTimeoutOnErrors();
						if (badResults != null) {
							if (obj.containsString("asm") && obj.containsString("id")) {

								test_id = obj.getString("id").Value;
								asm = obj.getString("asm").Value;

								if (!getPermissions().allowExecute(toolchain.getName(), test_id)) {
									returnValue.add("code", new JsonNumber(55));
									returnValue.add("result", new JsonString("Hacking much?"));
								} else {
									long now = new Date().getTime();
									long then = badResults.AllowNext.getTime();
									long diff = then > now ? then - now : 0;
									if (diff > 10000 && !canBypassTimeout) { // 10 seconds allowance
										returnValue.add("code", new JsonNumber(54));
										returnValue.add("result", new JsonString("Hacking much?"));
									} else {
										returnValue = tests.run(badResults, BuilderID, toolchain, test_id, asm, getPermissions().Login);
										testsPassed = returnValue.containsNumber("code") ? returnValue.getNumber("code").Value == 0 : false;
									}

									// See if user has finished this test before
									boolean newlyFinished = testsPassed;
									for (CompletedTest test : completed) {
										if (test.Code.equals(test_id)) {
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
								}
							} else if (obj.containsString("action")) {
								String act = obj.getString("action").Value;
								if (act.equals("COLLECT")) {
									this.setIntention(HTTPClientIntentType.COLLECT_TESTS);
									List<GenericTest> tsts = tests.getAllTests(toolchain.getName());
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

									List<JsonValue> d = new ArrayList<>();
									Map<String, CompletedTest> finishedByTestID = new HashMap<>();
									for (CompletedTest test : completed) {
										finishedByTestID.put(test.TestID, test);
									}

									for (GenericTest tst : tsts) {
										if (!getPermissions().allowSee(toolchain.getName(), tst.getID())) {
											continue;
										}
										if (tst.isSecret() && !getPermissions().allowSeeSecretTests()) {
											continue;
										}
										JsonObject tobj = new JsonObject();
										tobj.add("title", new JsonString(tst.getTitle()));
										tobj.add("zadani", new JsonString(tst.getDescription()));
										tobj.add("init", new JsonString(tst.getSubmittedCode()));
										tobj.add("id", new JsonString(tst.getID()));
										if (!getPermissions().allowExecute(toolchain.getName(), tst.getID())) {
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

									returnValue.add("code", new JsonNumber(0));
									returnValue.add("tests", new JsonArray(d));
									if (canBypassTimeout) {
										returnValue.add("wait", new JsonNumber(0, (new Date().getTime() - 10000) + ""));
									} else {
										returnValue.add("wait", new JsonNumber(0, (badResults.AllowNext.getTime()) + ""));
									}
								} else if (act.equals("GRAPHS")) {
									JsonValue graphs = this.loadGraphs(toolchain.getName());
									returnValue.add("code", new JsonNumber(0));
									returnValue.add("data", graphs == null ? new JsonArray(new ArrayList<JsonValue>()) : graphs);
									this.setIntention(HTTPClientIntentType.COLLECT_TESTS);
								} else if (act.equals("ADMIN") && getPermissions().allowSeeWebAdmin()) {
									handleAdminEvent(obj, returnValue);
									this.setIntention(HTTPClientIntentType.COLLECT_TESTS);
								}
							}
						}
					} else { // Not authenticated
						returnValue.add("code", new JsonNumber(53));
						returnValue.add("result", new JsonString("Not logged in"));
						returnValue.add("authUrl", new JsonString(Settings.getAuthURL()));
					}
				}
			}
		}
		String resultJson;
		if (returnValue.containsObject("details") && !this.getPermissions().allowDetails(toolchain.getName(), test_id)) {
			JsonValue details = returnValue.get("details");
			returnValue.remove("details");
			resultJson = returnValue.getJsonString(); // So that the "details" are not sent
			returnValue.add("details", details);
		} else {
			resultJson = returnValue.getJsonString();
		}
		return encode(resultJson);
	}

	public boolean haveTestsPassed() {
		return testsPassed;
	}

	private String getReducedResult() {
		if (returnValue != null) {
			JsonObject nobj = new JsonObject();
			nobj.add("code", returnValue.asObject().getNumber("code"));
			nobj.add("result", returnValue.asObject().getString("result"));
			nobj.add("test_id", new JsonString(test_id));
			return nobj.getJsonString();
		} else {
			return "{\"code\":1, \"result\":\"compilation failure\"}";
		}
	}

	@Override
	public void run() throws SwitchClientException, DatabaseException, IOException {
		super.run(); // After calling this, client is closed
		if (this.getIntention() == HTTPClientIntentType.PERFORM_TEST) {
			int code = returnValue.asObject().getNumber("code").Value;
			String result = returnValue.asObject().getString("result").Value;
			String details = returnValue.asObject().contains("details") ? returnValue.asObject().get("details").getJsonString() : "[]";
			int good_tests = returnValue.asObject().containsNumber("good") ? returnValue.asObject().getNumber("good").Value : 0;
			int bad_tests = returnValue.asObject().containsNumber("bad") ? returnValue.asObject().getNumber("bad").Value : 0;
			db.storeCompilation(completed, client.getRemoteSocketAddress().toString(), new Date(), asm, getPermissions().getSessionID(), test_id, code, result, getReducedResult(), getPermissions().UserID, toolchain.getName(), details, good_tests, bad_tests);
		}
	}
}
