package cz.rion.buildserver.http.stateless;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.PermissionManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedGenericData;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;

public class StatelessPermissionClient extends AbstractStatelessClient {

	private final StatelessInitData data;

	protected StatelessPermissionClient(StatelessInitData data) {
		super(data);
		this.data = data;
	}

	private static final class PermissionContext {
		private CachedGenericData<String, UsersPermission> usersPermissionByLogin;
		private final PermissionManager permanager;

		private PermissionContext(final Toolchain toolchain, final RuntimeDB rdb, StaticDB sdb, PermissionManager manager) {
			this.permanager = manager;
			this.usersPermissionByLogin = new CachedGenericData<String, UsersPermission>(60) {

				@Override
				protected CachedData<UsersPermission> createData(int refreshIntervalInSeconds, final String login) {
					return new CachedData<UsersPermission>(refreshIntervalInSeconds) {

						@Override
						protected UsersPermission update() {
							return permanager.getPermissionForLogin(toolchain, login, 0, rdb);
						}
					};
				}

			};
		}
	}

	private CachedToolchainData2<PermissionContext> contexts = new CachedToolchainDataWrapper2<>(30, new CachedToolchainDataGetter2<PermissionContext>() {

		@Override
		public CachedData<PermissionContext> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper2<PermissionContext>(refreshIntervalInSeconds, new CachedDataGetter<PermissionContext>() {

				@Override
				public PermissionContext update() {
					return new PermissionContext(toolchain, data.RuntimeDB, data.StaticDB, data.PermissionManager);
				}
			});
		}
	});

	protected boolean liteCan(Toolchain tc, String login, PermissionBranch branch) {
		UsersPermission perms = contexts.get(tc).usersPermissionByLogin.get(login);
		return perms.can(branch);
	}

	protected void loadPermissions(ProcessState state, int sessionID, String login) {
		UsersPermission perms = contexts.get(state.Toolchain).usersPermissionByLogin.get(login);
		if (perms != null) {
			state.setLoggedIn(perms.getSessionedInstance(sessionID));
		}
	}

	@Override
	public void clearCache() {
		super.clearCache();
		contexts.clear();
	}

}
