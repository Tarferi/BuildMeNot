package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class StatelessFileProviderClient extends StatelessAdminClient {

	private final StatelessInitData data;

	protected StatelessFileProviderClient(StatelessInitData data) {
		super(data);
		this.data = data;
	}

	protected String handleJSManipulation(ProcessState state, String path, String content) {
		String repl = "";
		if (state.getPermissions().allowSeeWebAdmin()) {
			repl = readFileOrDBFile("admin.js");
		}
		content = content.replace("$INJECT_ADMIN$", repl);
		return content;
	}

	protected String handleHTMLManipulation(ProcessState state, String path, String content) {
		return content;
	}

	protected String handleCSSManipulation(ProcessState state, String path, String content) {
		String repl = "";
		if (state.getPermissions().allowSeeWebAdmin()) {
			repl = readFileOrDBFile("admin.css");
		}
		content = content.replace("$INJECT_ADMIN$", repl);
		return content;
	}

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

		public FileMappingManager(StaticDB sdb, Toolchain toolchain) {
			List<FileMapping> lst = new ArrayList<>();
			try {
				FileInfo fo = sdb.loadFile("file_mapping.ini", true);
				if (fo != null) {
					for (String line : fo.Contents.split("\n")) {
						line = line.trim();
						line = line.replaceAll("\\$TOOLCHAIN\\$", toolchain.getName().toLowerCase());
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

	private CachedToolchainData2<FileMappingManager> fileMappings = new CachedToolchainDataWrapper2<>(60, new CachedToolchainDataGetter2<FileMappingManager>() {

		@Override
		public CachedData<FileMappingManager> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper<>(refreshIntervalInSeconds, new CachedDataGetter<FileMappingManager>() {

				@Override
				public FileMappingManager update() {
					return new FileMappingManager(data.StaticDB, toolchain);
				}

			});
		}
	});

	private String readFileOrDBFile(String endPoint) {
		try {
			String fileContents = MyFS.readFile("./web/" + endPoint);
			return fileContents;
		} catch (FileReadException e) {
			FileInfo dbf = data.StaticDB.loadFile("web/" + endPoint, true);
			if (dbf != null) {
				return dbf.Contents;
			}
		}
		return null;
	}

	@Override
	protected HTTPResponse handle(ProcessState state) {
		if (state.IsLoggedIn()) {

			if (state.Request.path.startsWith("/") && state.Request.method.equals("GET")) {
				int returnCode = 200;
				String type = "multipart/form-data;";
				String returnCodeDescription = "OK";

				byte[] data = ("\"" + state.Request.path + "\" neumim!").getBytes(Settings.getDefaultCharset());

				String endPoint = state.Request.path.substring(1);
				if (endPoint.equals("")) {
					endPoint = "index.html";
				}
				FileMappingManager mapping = fileMappings.get(state.Toolchain);
				String allowed = mapping.getRemappedEndpoint(endPoint, state.Request.host);
				if (allowed != null) {
					String contents = readFileOrDBFile(allowed);
					if (contents != null) {
						data = contents.getBytes(Settings.getDefaultCharset());
						state.setIntention(Intention.GET_RESOURCE);
						if (allowed.endsWith(".html")) {
							type = "text/html; charset=UTF-8";
						} else if (allowed.endsWith(".js")) {
							type = "text/js; charset=UTF-8";
						} else if (allowed.endsWith(".css")) {
							type = "text/css";
						}
						if (allowed.endsWith(".js")) {
							contents = this.handleJSManipulation(state, endPoint, contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						} else if (allowed.endsWith(".css")) {
							contents = this.handleCSSManipulation(state, endPoint, contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						} else if (allowed.endsWith(".html")) {
							contents = this.handleHTMLManipulation(state, endPoint, contents);
							data = contents.getBytes(Settings.getDefaultCharset());
						}
					} else {
						returnCode = 404;
						returnCodeDescription = "Not Found";
						data = ("Nemuzu precist: " + endPoint).getBytes(Settings.getDefaultCharset());
					}
				} else {
					state.setIntention(Intention.GET_INVALID_RESOURCE);
				}
				return new HTTPResponse(state.Request.protocol, returnCode, returnCodeDescription, data, type, state.Request.cookiesLines);
			}
		}
		return super.handle(state);
	}

	@Override
	public void clearCache() {
		super.clearCache();
		fileMappings.clear();
	}
}
