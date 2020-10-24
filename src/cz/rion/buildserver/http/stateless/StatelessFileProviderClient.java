package cz.rion.buildserver.http.stateless;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.permissions.PermissionBranch;
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
	private static final class JWTCrypto {

		private static String jwt_header = null;

		private static final String base64(String data) {
			return Base64.getEncoder().encodeToString(data.getBytes(Settings.getDefaultCharset())).replaceAll("=", "");
		}

		private static String encode(byte[] bytes) {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}

		private static String hmacSha256(String data, String secret) {
			try {
				byte[] hash = secret.getBytes(Settings.getDefaultCharset());
				Mac sha256Hmac = Mac.getInstance("HmacSHA256");
				SecretKeySpec secretKey = new SecretKeySpec(hash, "HmacSHA256");
				sha256Hmac.init(secretKey);
				byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(Settings.getDefaultCharset()));
				return encode(signedBytes);
			} catch (InvalidKeyException | NoSuchAlgorithmException ex) {
				return null;
			}
		}

		private static String EncodeHMAC(String data) {
			if (jwt_header == null) {
				JsonObject obj = new JsonObject();
				obj.add("alg", "HS256");
				obj.add("typ", "JWT");
				jwt_header = obj.getJsonString();
			}
			String bheader = base64(jwt_header);
			String bdata = base64(data);
			String signature = hmacSha256(bheader + "." + bdata, Settings.getJWTSecret());
			if (signature == null) {
				return null;
			}
			String jwtToken = bheader + "." + bdata + "." + signature;
			return jwtToken;
		}

		private static final PermissionBranch pb = new PermissionBranch("MEET.ADMIN");

		private static String getJWT(ProcessState state) {
			JsonObject obj = new JsonObject();
			UsersPermission perms = state.getPermissions();
			JsonObject context = new JsonObject();
			// context.add("avatar", "https://robohash.org/john-doe");
			JsonObject user = new JsonObject();
			user.add("name", perms.getFullName());
			user.add("email", perms.getEmail());
			context.add("user", user);

			obj.add("name", perms.getFullName());
			obj.add("context", context);
			obj.add("aud", Settings.getJWTApp());
			obj.add("iss", Settings.getJWTApp());
			obj.add("sub", "meet.jitsi");
			obj.add("room", "TEST");
			obj.add("moderator", perms.can(pb));
			return EncodeHMAC(obj.getJsonString());
		}

	}

	private final StatelessInitData data;

	protected StatelessFileProviderClient(StatelessInitData data) {
		super(data);
		this.data = data;
	}

	private String handleReplacements(String content, UsersPermission perms) {

		final String regex = "\\$INJECT\\(([a-zA-Z0-9\\.]+,){0,1} *([a-zA-Z0-9_\\.\\/]+)\\)\\$";

		final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		final Matcher matcher = pattern.matcher(content);

		while (matcher.find()) {
			final MatchResult matchResult = matcher.toMatchResult();
			String name = null;
			if (matchResult.groupCount() == 1) {
				name = matchResult.group(0);
			} else if (matchResult.groupCount() == 2) {
				name = matchResult.group(2);
				String permsD = matchResult.group(1);
				if (permsD != null) {
					if (!perms.can(new PermissionBranch(permsD))) {
						name = null;
					}
				}
			} else {
				continue;
			}
			String replacement = "";
			if (name != null) {
				replacement = readFileOrDBFile(name);
				if (replacement == null) {
					replacement = "";
				} else {
					replacement = handleReplacements(replacement, perms);
				}
			}
			content = content.substring(0, matchResult.start()) + replacement + content.substring(matchResult.end());
			matcher.reset(content);
		}

		return content;
	}

	protected String handleJSManipulation(ProcessState state, String path, String content) {
		content = handleReplacements(content, state.getPermissions());

		{
			int index = content.indexOf("$INJECT_JWT$");
			if (index != -1) {
				String jwt = null;
				try {
					jwt = JWTCrypto.getJWT(state);
				} catch (Throwable t) {
					t.printStackTrace();
				}
				String repl = jwt == null ? "alert(\"Nepodarilo se ziskat prihlasovaci udaje\");return false;" : jwt;
				content = content.replace("$INJECT_JWT$", repl);
			}
		}

		return content;
	}

	protected String handleHTMLManipulation(ProcessState state, String path, String content) {
		return content;
	}

	protected String handleCSSManipulation(ProcessState state, String path, String content) {
		content = handleReplacements(content, state.getPermissions());
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
				String type = "text/html;";
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
