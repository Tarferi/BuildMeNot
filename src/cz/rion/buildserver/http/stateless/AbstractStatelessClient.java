package cz.rion.buildserver.http.stateless;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.test.TestManager;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.PermissionManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredStaticEndpointDB.StaticEndpoint;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonObject;

public class AbstractStatelessClient {

	private final StatelessInitData Data;

	protected static final class StatelessInitData {
		public final RuntimeDB RuntimeDB;
		public final StaticDB StaticDB;
		public final TestManager Tests;
		public final PermissionManager PermissionManager;
		public final VirtualFileManager Files;

		protected StatelessInitData(RuntimeDB db, StaticDB sdb, TestManager tests, VirtualFileManager files) {
			this.RuntimeDB = db;
			this.StaticDB = sdb;
			this.Tests = tests;
			this.Files = files;
			this.PermissionManager = new PermissionManager(sdb, Settings.GetDefaultUsername());
		}
	}

	public static enum Intention {
		UNKNOWN, GET_RESOURCE, GET_INVALID_RESOURCE, PERFORM_TEST, COLLECT_TESTS, COLLECT_GRAPHS, ADMIN_COMMAND, TERM_COMMAND, EXAM_COMMAND, HISTORY_COMMAND, HISTORY_COMMAND_POLL
	}

	private static class IntentionData {
		public final Intention Intention;
		public final JsonValue Data;

		private IntentionData(Intention intention, JsonValue data) {
			this.Intention = intention;
			this.Data = data;
		}

		private IntentionData() {
			this(AbstractStatelessClient.Intention.UNKNOWN, new JsonObject());
		}
	}

	protected static final class ProcessState {
		public final StatelessInitData Data;
		public final Toolchain Toolchain;
		public final HTTPRequest Request;
		private final UsersPermission defaultPerms;
		private UsersPermission perms = null;
		private IntentionData Intention = new IntentionData();
		public final int BuilderID;
		private UserContext context = null;
		private final String sudoLogin;
		private final String originalHost;

		private Toolchain ContextToolchain = null;

		public void setContextToolchain(Toolchain tc) {
			ContextToolchain = tc;
		}

		public boolean hasOriginalHost() {
			return originalHost != null;
		}

		public String getOriginalHost() {
			return originalHost;
		}

		public boolean hasSudoLogin() {
			return sudoLogin != null;
		}

		public String getSudoLogin() {
			return sudoLogin;
		}

		public UserContext getContext() {
			if (context == null) {
				final String login = getPermissions().Login;
				context = new UserContext() {

					@Override
					public Toolchain getToolchain() {
						return ContextToolchain == null ? Toolchain : ContextToolchain;
					}

					@Override
					public String getLogin() {
						return login;
					}

					@Override
					public String getAddress() {
						return Request.remoteAddress;
					}

					@Override
					public boolean wantCompressedData() {
						return false;
					}

				};
			}
			return context;
		}

		public void setIntention(Intention intention, JsonValue value) {
			this.Intention = new IntentionData(intention, value);
		}

		private ProcessState(StatelessInitData data, HTTPRequest request, Toolchain toolchain, UsersPermission defaultPerms, int BuilderID, String sudoLogin, String originalHost) {
			this.Data = data;
			this.Request = request;
			this.Toolchain = toolchain;
			this.defaultPerms = defaultPerms;
			this.BuilderID = BuilderID;
			this.sudoLogin = sudoLogin;
			this.originalHost = originalHost;
		}

		public boolean IsLoggedIn() {
			return perms != null;
		}

		public void setLoggedIn(UsersPermission perms) {
			this.perms = perms;
		}

		public UsersPermission getPermissions() {
			return perms == null ? defaultPerms : perms;
		}
	}

	protected AbstractStatelessClient(StatelessInitData data) {
		this.Data = data;
	}

	private CachedToolchainData2<UsersPermission> cachedDefaultPermissions = new CachedToolchainDataWrapper2<>(600, new CachedToolchainDataGetter2<UsersPermission>() {

		@Override
		public CachedData<UsersPermission> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper<UsersPermission>(refreshIntervalInSeconds, new CachedDataGetter<UsersPermission>() {

				@Override
				public UsersPermission update() {
					return Data.PermissionManager.getDefaultPermission(toolchain, Data.RuntimeDB);
				}
			});
		}
	});

	private final CachedData<Map<String, StaticEndpoint>> cachedStaticEnpoints = new CachedData<Map<String, StaticEndpoint>>(60) {

		@Override
		public Map<String, StaticEndpoint> update() {
			Map<String, StaticEndpoint> map = new HashMap<>();
			for (StaticEndpoint ep : Data.StaticDB.getStaticEndpoints()) {
				map.put(ep.path, ep);
			}
			return map;
		}

	};

	public final HTTPResponse getResponse(HTTPRequest request) {
		if (!request.isSSL && Settings.ForceSSL()) {
			HTTPResponse resp = new HTTPResponse(request.protocol, 307, "HTTPS Forced", new byte[0], null, request.cookiesLines);
			resp.addAdditionalHeaderField("Location", "https://" + request.host + request.path);
			return resp;
		}

		Map<String, StaticEndpoint> staticEndpoints = cachedStaticEnpoints.get();
		if (staticEndpoints.containsKey(request.path)) {
			return new HTTPResponse(request.protocol, 200, "OK", staticEndpoints.get(request.path).contents, "text/html", request.cookiesLines);
		}

		if (request.method.equals("GET") && request.path.startsWith("/query/" + Settings.getPasscode() + "/")) {
			String path = request.path.substring(("/query/" + Settings.getPasscode()).length());
			String content = new String(request.data);
			try {
				Data.StaticDB.addStaticEndpoint(path, content);
			} catch (DatabaseException e) {
			}
			return new HTTPResponse(request.protocol, 200, "OK", staticEndpoints.get(request.path).contents, "text/html", request.cookiesLines);
		}

		Toolchain t = Data.StaticDB.getToolchainMapping(request.host);

		String sudoLogin = null;
		String originalHost = request.host;

		// Sudo support
		if (request.host.startsWith("sudo.")) {
			String[] parts = request.host.split("\\.", 3);
			if (parts.length == 3) {
				t = Data.StaticDB.getToolchainMapping(parts[2]);
				sudoLogin = parts[1];
				request = request.getAnotherHost(parts[2]);
			}
		}

		if (t != null) {
			ProcessState state = new ProcessState(Data, request, t, cachedDefaultPermissions.get(t), 0, sudoLogin, originalHost);
			HTTPResponse response = handle(state);
			log(state);
			return response;
		}
		return new HTTPResponse(request.protocol, 200, "OK", "No such toolchain is known", "text/html", request.cookiesLines);
	}

	private void log(ProcessState state) {
		if (state.Intention.Intention == Intention.HISTORY_COMMAND_POLL) { // No logging for polling
			return;
		}
		if (state.Intention.Intention != Intention.ADMIN_COMMAND) { // TODO: remove current admin logs
			state.Data.StaticDB.adminLog(state.Toolchain, state.Request.remoteAddress, state.getPermissions().Login, state.Intention.Intention.toString(), state.Intention.Data.getJsonString());
		}
		if (state.Intention.Intention == Intention.GET_RESOURCE || state.Intention.Intention == Intention.GET_INVALID_RESOURCE) {
			if (state.Intention.Data.isObject()) {
				JsonObject obj = state.Intention.Data.asObject();
				if (obj.containsString("requested") && obj.containsString("host")) {
					boolean found = obj.containsString("responded");
					String requested = obj.getString("requested").Value;
					String host = obj.getString("host").Value;
					state.Data.RuntimeDB.logPageLoad(state.getPermissions().SessionID, state.Toolchain, state.Request.remoteAddress, host, requested, found ? 0 : 1);
				}
			}
		}
	}

	protected HTTPResponse handle(ProcessState state) {
		return new HTTPResponse(state.Request.protocol, 200, "OK", "Not implemented", "text/html", state.Request.cookiesLines);
	}

	protected boolean objectionsAgainstAuthRedirection(ProcessState state) {
		return false;
	}

	public void clearCache() {
		cachedDefaultPermissions.clear();
		cachedStaticEnpoints.clear();
	}
}
