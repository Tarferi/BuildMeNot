package cz.rion.buildserver.http.stateless;

import java.util.HashMap;
import java.util.Map;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
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
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;

public class AbstractStatelessClient {

	private final StatelessInitData Data;

	protected static final class StatelessInitData {
		public final RuntimeDB RuntimeDB;
		public final StaticDB StaticDB;
		public final TestManager Tests;
		public final PermissionManager PermissionManager;

		protected StatelessInitData(RuntimeDB db, StaticDB sdb, TestManager tests) {
			this.RuntimeDB = db;
			this.StaticDB = sdb;
			this.Tests = tests;
			this.PermissionManager = new PermissionManager(sdb, Settings.GetDefaultUsername());
		}
	}

	public static enum Intention {
		UNKNOWN, GET_RESOURCE, GET_INVALID_RESOURCE, PERFORM_TEST, COLLECT_TESTS, COLLECT_GRAPHS, ADMIN_COMMAND, TERM_COMMAND
	}

	protected static final class ProcessState {
		public final StatelessInitData Data;
		public final Toolchain Toolchain;
		public final HTTPRequest Request;
		private final UsersPermission defaultPerms;
		private UsersPermission perms = null;
		private Intention intention = Intention.UNKNOWN;
		public final int BuilderID;

		public void setIntention(Intention intention) {
			this.intention = intention;
		}

		private ProcessState(StatelessInitData data, HTTPRequest request, Toolchain toolchain, UsersPermission defaultPerms, int BuilderID) {
			this.Data = data;
			this.Request = request;
			this.Toolchain = toolchain;
			this.defaultPerms = defaultPerms;
			this.BuilderID = BuilderID;
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

	public HTTPResponse getResponse(HTTPRequest request) {
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

		String toolchainStr = Data.StaticDB.getToolchainMapping(request.host);
		if (toolchainStr != null) {
			Toolchain t = null;
			try {
				t = Data.StaticDB.getToolchain(toolchainStr);
			} catch (NoSuchToolchainException e) {
			}
			if (t != null) {
				ProcessState state = new ProcessState(Data, request, t, cachedDefaultPermissions.get(t), 0);
				HTTPResponse response = handle(state);
				log(state);
				return response;
			}
		}
		return new HTTPResponse(request.protocol, 200, "OK", "No such toolchain is known", "text/html", request.cookiesLines);
	}

	private void log(ProcessState state) {

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
