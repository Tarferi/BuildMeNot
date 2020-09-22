package cz.rion.buildserver.http;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class HTTPFileProviderClient extends HTTPAdminClient {

	private final StaticDB sdb;
	private String endPoint;

	protected HTTPFileProviderClient(CompatibleSocketClient client, int BuilderID, RuntimeDB db, StaticDB sdb) {
		super(client, BuilderID, db, sdb);
		this.sdb = sdb;
	}

	protected String handleCSSManipulation(String css) {
		String repl = "";
		if (getPermissions().allowSeeWebAdmin()) {
			repl = readFileOrDBFile("admin.css");
		}
		css = css.replace("$INJECT_ADMIN$", repl);
		return css;
	}

	@Override
	protected String handleJSManipulation(String host, String path, String js) {
		js = super.handleJSManipulation(host, path, js);
		String repl = "";
		if (getPermissions().allowSeeWebAdmin()) {
			repl = readFileOrDBFile("admin.js");
		}
		js = js.replace("$INJECT_ADMIN$", repl);
		return js;
	}

	private String readFileOrDBFile(String endPoint) {
		try {
			String fileContents = MyFS.readFile("./web/" + endPoint);
			return fileContents;
		} catch (FileReadException e) {
			FileInfo dbf = sdb.loadFile("web/" + endPoint, true);
			if (dbf != null) {
				return dbf.Contents;
			}
		}
		return null;
	}

	public String getDownloadedDocumentPath() {
		return endPoint;
	}

	protected HTTPResponse handle(HTTPRequest request) throws HTTPClientException {
		if (request.path.startsWith("/") && request.method.equals("GET")) {
			int returnCode = 200;
			String type = "multipart/form-data;";
			String returnCodeDescription = "OK";

			byte[] data = ("\"" + request.path + "\" neumim!").getBytes(Settings.getDefaultCharset());

			endPoint = request.path.substring(1);
			if (endPoint.equals("")) {
				endPoint = "index.html";
			}
			String[] allowed = new String[] { "index.html", "index.css", "index.js" };
			boolean isAllowed = false;
			for (String allow : allowed) {
				if (allow.equals(endPoint)) {
					String contents = readFileOrDBFile(endPoint);
					if (contents != null) {
						data = contents.getBytes(Settings.getDefaultCharset());
						this.setIntention(HTTPClientIntentType.GET_RESOURCE);
						if (allow.endsWith(".html")) {
							type = "text/html; charset=UTF-8";
							this.setIntention(HTTPClientIntentType.GET_HTML);
						} else if (allow.endsWith(".js")) {
							type = "text/js; charset=UTF-8";
						} else if (allow.endsWith(".css")) {
							type = "text/css";
						}
						if (allow.equals("index.js")) {
							contents = this.handleJSManipulation(request.host, endPoint, contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						} else if (allow.equals("index.css")) {
							contents = this.handleCSSManipulation(contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						} else if (allow.equals("index.html")) {
							contents = this.handleHTMLManipulation(request.host, request.path, contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						}
					} else {
						returnCode = 404;
						returnCodeDescription = "Not Found";
						data = ("Nemuzu precist: " + endPoint).getBytes(Settings.getDefaultCharset());

					}
					isAllowed = true;
					break;
				}
			}
			if (!isAllowed) {
				this.setIntention(HTTPClientIntentType.HACK);
			}
			return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type, request.cookiesLines);
		}
		return super.handle(request);
	}
}
