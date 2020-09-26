package cz.rion.buildserver.http;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData;
import cz.rion.buildserver.utils.CachedToolchainDataGetter;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class HTTPFileProviderClient extends HTTPAdminClient {

	private final StaticDB sdb;
	private String endPoint;
	private Toolchain toolchain;

	private static class FileMapping {
		private final String Input;
		private final String Output;
		private final String Hostname;

		private FileMapping(String input, String output, String hostname) {
			this.Input = input;
			this.Output = output;
			this.Hostname = hostname;
		}

		public String getRemappedEndpoint(String endpoint, String host) {
			if (Hostname != null) {
				if (Hostname.equalsIgnoreCase(host) && Input.equalsIgnoreCase(endpoint)) {
					return Output;
				}
			}
			return null;
		}
	}

	private static class FileMappingManager {

		public final FileMapping[] Allowed;

		private String getRemappedEndpoint(String endpoint, String host) {
			for (FileMapping allowed : Allowed) {
				String remap = allowed.getRemappedEndpoint(endpoint, host);
				if (remap != null) {
					return remap;
				}
			}
			return null;
		}

		public FileMappingManager(StaticDB sdb, String toolchain) {
			List<FileMapping> lst = new ArrayList<>();
			try {
				FileInfo fo = sdb.loadFile("file_mapping.ini", true);
				if (fo != null) {
					for (String line : fo.Contents.split("\n")) {
						line = line.trim();
						line = line.replaceAll("\\$TOOLCHAIN\\$", toolchain.toLowerCase());
						if (line.isEmpty() || line.startsWith("#")) {
							continue;
						}
						String[] parts = line.split("=>", 2);
						if (parts.length == 2) {
							String from = parts[0].trim();
							String to = parts[1].trim();
							int lastFS = from.lastIndexOf("/");
							String hostName = "";
							if (lastFS != -1) { // Hostname present
								hostName = from.substring(0, lastFS).trim();
								from = from.substring(lastFS + 1).trim();
							}
							lst.add(new FileMapping(from, to, hostName));
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				this.Allowed = new FileMapping[lst.size()];
				for (int i = 0; i < this.Allowed.length; i++) {
					this.Allowed[i] = lst.get(i);
				}
			}
		}

	}

	@Override
	protected void ToolChainKnown(Toolchain toolchain) {
		super.ToolChainKnown(toolchain);
		this.toolchain = toolchain;
	}

	private CachedToolchainData<FileMappingManager> fileMappings = new CachedToolchainDataWrapper<>(60, new CachedToolchainDataGetter<FileMappingManager>() {

		@Override
		public CachedData<FileMappingManager> createData(int refreshIntervalInSeconds, final String toolchain) {
			return new CachedDataWrapper<>(refreshIntervalInSeconds, new CachedDataGetter<FileMappingManager>() {

				@Override
				public FileMappingManager update() {
					return new FileMappingManager(sdb, toolchain);
				}

			});
		}
	});

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
			FileMappingManager mapping = fileMappings.get(toolchain.getName());
			String allowed = mapping.getRemappedEndpoint(endPoint, request.host);
			if (allowed != null) {
				String contents = readFileOrDBFile(allowed);
				if (contents != null) {
					data = contents.getBytes(Settings.getDefaultCharset());
					this.setIntention(HTTPClientIntentType.GET_RESOURCE);
					if (allowed.endsWith(".html")) {
						type = "text/html; charset=UTF-8";
						this.setIntention(HTTPClientIntentType.GET_HTML);
					} else if (allowed.endsWith(".js")) {
						type = "text/js; charset=UTF-8";
					} else if (allowed.endsWith(".css")) {
						type = "text/css";
					}
					if (allowed.endsWith(".js")) {
						contents = this.handleJSManipulation(request.host, endPoint, contents);
						data = contents.getBytes(Settings.getDefaultCharset());
					} else if (allowed.endsWith(".css")) {
						contents = this.handleCSSManipulation(contents);
						data = contents.getBytes(Settings.getDefaultCharset());
					} else if (allowed.endsWith(".html")) {
						contents = this.handleHTMLManipulation(request.host, request.path, contents);
						data = contents.getBytes(Settings.getDefaultCharset());
					}
				} else {
					returnCode = 404;
					returnCodeDescription = "Not Found";
					data = ("Nemuzu precist: " + endPoint).getBytes(Settings.getDefaultCharset());
				}
			} else {
				this.setIntention(HTTPClientIntentType.HACK);
			}
			return new HTTPResponse(request.protocol, returnCode, returnCodeDescription, data, type, request.cookiesLines);
		}
		return super.handle(request);
	}
}
