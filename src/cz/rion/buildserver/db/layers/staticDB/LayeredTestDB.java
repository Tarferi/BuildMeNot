package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager.ReadVirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermissionGroup;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;

public abstract class LayeredTestDB extends LayeredCodeModifiersDB {

	private final DatabaseInitData dbData;

	@Override
	public void afterInit() {
		final Map<String, TestConfigFile> registeredFiles = new HashMap<>();
		super.afterInit();
		this.registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainAdded(Toolchain t) {
				synchronized (registeredFiles) {
					if (!registeredFiles.containsKey(t.getName())) {
						TestConfigFile f = new TestConfigFile(t);
						registeredFiles.put(t.getName(), f);
						dbData.Files.registerVirtualFile(f);
					}
				}
			}

			@Override
			public void toolchainRemoved(Toolchain t) {
				synchronized (registeredFiles) {
					if (registeredFiles.containsKey(t.getName())) {
						TestConfigFile f = registeredFiles.remove(t.getName());
						dbData.Files.unregisterVirtualFile(f);
					}
				}
			}

		});
	}

	public LayeredTestDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.makeTable("tests_notifications", false, KEY("ID"), TEXT("GroupName"), TEXT("Notification"), NUMBER("valid"), TEXT("toolchain"));
		this.dbData = dbData;
	}

	public static class MallocData {

		public static class MallocDataContents {
			private final String[] parts;

			private MallocDataContents(String contents) {
				this.parts = contents.split("\\%RANDOM\\%");
			}

			public final String get(String random) {
				if (parts.length == 0) {
					return "";
				} else if (parts.length == 1) {
					return parts[0];
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append(this.parts[0]);
					for (int i = 1; i < parts.length; i++) {
						sb.append(random + parts[i]);
					}
					return sb.toString();
				}
			}
		}

		private static final String mallocFileBeforeName = "tests/includes/malloc_before_code.c";
		private static final String mallocFileAfterName = "tests/includes/malloc_after_code.c";
		private static final String mallocMainFileName = "tests/includes/malloc_main.c";
		public final MallocDataContents MallocFileBefore;
		public final MallocDataContents MallocFileAfter;
		public final MallocDataContents MallocFile;

		private MallocData(String before, String after, String malloc) {
			this.MallocFileBefore = new MallocDataContents(before);
			this.MallocFileAfter = new MallocDataContents(after);
			this.MallocFile = new MallocDataContents(malloc);
		}

		private static MallocData get(StaticDB db) {
			ReadVirtualFile before = db.loadRootFile(mallocFileBeforeName);
			ReadVirtualFile after = db.loadRootFile(mallocFileAfterName);
			ReadVirtualFile malloc = db.loadRootFile(mallocMainFileName);
			if (before != null && after != null && malloc != null) {
				if (before.Contents != null && after.Contents != null && malloc.Contents != null) {
					return new MallocData(before.Contents, after.Contents, malloc.Contents);
				}
			}
			return null;
		}
	}

	public final CachedData<MallocData> MallocFilesCache = new CachedDataWrapper<MallocData>(30 * 60, new CachedDataGetter<MallocData>() {

		@Override
		public MallocData update() {
			return MallocData.get((StaticDB) LayeredTestDB.this);
		}
	});

	@Override
	public void clearCache() {
		super.clearCache();
		MallocFilesCache.clear();
		cache.clear();
	}

	private final class TestConfigFile extends VirtualFile {

		private static final String path = "tests/notifications.ini";

		public TestConfigFile(Toolchain toolchain) {
			super(path, toolchain);
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			synchronized (cache) {
				NotificationsData data = cache.get(context.getToolchain());
				JsonObject obj = new JsonObject();
				for (Entry<String, Notification> entry : data.notificationsByGroupName.entrySet()) {
					Notification item = entry.getValue();
					obj.add(item.GroupName, item.Notification);
				}
				return JsonValue.getPrettyJsonString(obj);
			}
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			synchronized (cache) {
				try {
					JsonValue val = JsonValue.parse(value);
					if (val != null) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							NotificationsData data = cache.get(context.getToolchain());
							Map<String, Notification> existingGroups = new HashMap<>();
							for (Entry<String, Notification> notf : data.notificationsByGroupName.entrySet()) {
								existingGroups.put(notf.getKey(), notf.getValue());
							}
							for (Entry<String, JsonValue> entry : obj.getEntries()) {
								String group = entry.getKey();
								if (entry.getValue().isString()) {
									String notification = entry.getValue().asString().Value;
									if (!notification.trim().isEmpty()) {

										boolean exists = false;
										int id = -1;
										if (existingGroups.containsKey(group)) {
											Notification g = existingGroups.get(group);
											existingGroups.remove(group);
											if (g instanceof StoredNotification) {
												exists = true;
												id = ((StoredNotification) g).ID;
											}
										}

										if (exists) { // Exists -> update
											if (!updateNotification(id, group, notification)) {
												return false;
											}
										} else { // Doesn't exist -> create
											if (!insertNotification(context.getToolchain(), group, notification)) {
												return false;
											}
										}
									}
								}
							}
							for (Entry<String, Notification> entry : existingGroups.entrySet()) {
								Notification n = entry.getValue();
								if (n instanceof StoredNotification) {
									int id = ((StoredNotification) n).ID;
									if (!removeNotification(id)) {
										return false;
									}
								}
							}
							return true;
						}
					}
					return false;
				} finally {
					cache.clear();
				}
			}
		}

	}

	private static class Notification {
		public final String GroupName;
		public final String Notification;

		private Notification(String gname, String txt) {
			this.GroupName = gname;
			this.Notification = txt;
		}
	}

	private static class StoredNotification extends Notification {
		public final int ID;

		private StoredNotification(int ID, String gname, String txt) {
			super(gname, txt);
			this.ID = ID;
		}
	}

	private static final class NotificationsData {
		private final Map<String, Notification> notificationsByGroupName = new HashMap<>();

		private NotificationsData(List<String> groups, List<StoredNotification> stored) {
			for (String g : groups) {
				notificationsByGroupName.put(g, new Notification(g, ""));
			}
			for (StoredNotification not : stored) {
				notificationsByGroupName.put(not.GroupName, not);
			}
		}
	}

	private final CachedToolchainData2<NotificationsData> cache = new CachedToolchainDataWrapper2<NotificationsData>(600, new CachedToolchainDataGetter2<NotificationsData>() {

		@Override
		public CachedData<NotificationsData> createData(int refreshIntervalInSeconds, Toolchain toolchain) {
			return new CachedData<NotificationsData>(refreshIntervalInSeconds) {

				@Override
				protected NotificationsData update() {
					return getNew(toolchain);
				}

			};
		}

	});

	private List<String> getGroupNames(Toolchain tc) {
		final String tableName = "groups";
		List<String> groups = new ArrayList<>();
		try {
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "name") }, true, new ComparisionField(getField(tableName, "toolchain"), tc.getName()));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("name")) {
						String name = obj.getString("name").Value;
						groups.add(name);
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return groups;
	}

	private NotificationsData getNew(Toolchain tc) {
		final String tableName = "tests_notifications";
		List<String> groups = getGroupNames(tc);
		List<StoredNotification> stored = new ArrayList<>();

		try {

			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "GroupName"), getField(tableName, "Notification") }, false, new ComparisionField(getField(tableName, "toolchain"), tc.getName()), new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("GroupName") && obj.containsString("Notification")) {
						int id = obj.getNumber("ID").Value;
						String gname = obj.getString("GroupName").Value;
						String not = obj.getString("Notification").Value;
						stored.add(new StoredNotification(id, gname, not));
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new NotificationsData(groups, stored);
	}

	private boolean insertNotification(Toolchain tc, String groupName, String notification) {
		final String tableName = "tests_notifications";
		try {
			return this.insert(tableName, new ValuedField(getField(tableName, "GroupName"), groupName), new ValuedField(getField(tableName, "Notification"), notification), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "toolchain"), tc.getName()));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean updateNotification(int id, String groupName, String notification) {
		final String tableName = "tests_notifications";
		try {
			return this.update(tableName, id, new ValuedField(getField(tableName, "GroupName"), groupName), new ValuedField(getField(tableName, "Notification"), notification));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean removeNotification(int id) {
		final String tableName = "tests_notifications";
		try {
			return this.update(tableName, id, new ValuedField(getField(tableName, "valid"), 0));
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	public String getNotification(Toolchain tc, UsersPermission perms) {
		synchronized (cache) {
			NotificationsData data = cache.get(tc);
			UsersPermissionGroup group = perms.getPrimaryGroup();
			if (group != null) {
				Notification n = data.notificationsByGroupName.get(group.Name);
				if (n != null) {
					if (n instanceof StoredNotification) {
						return n.Notification;
					}
				}
			}
			return null;
		}
	}
}
