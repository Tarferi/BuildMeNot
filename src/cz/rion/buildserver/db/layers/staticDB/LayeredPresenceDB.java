package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class LayeredPresenceDB extends LayeredConsoleOutputDB {

	public static enum PresenceType {
		Undefined(0, "Nepøihlášeno"), Present(1, "Prezenènì"), Remote(2, "Online");

		public final int Code;
		public final boolean Visible;
		public final String Name;

		private PresenceType(int id, String name) {
			this.Code = id;
			this.Visible = id > 0;
			this.Name = name;
		}
	}

	public static class PresenceLimits {
		public final Map<PresenceType, Integer> Limits = new HashMap<>();

		public void addLimit(PresenceType type, int limit) {
			Limits.put(type, limit);
		}

		public int getLimit(PresenceType type) {
			if (Limits.containsKey(type)) {
				return Limits.get(type);
			} else {
				return 0;
			}
		}
	}

	public static class PresenceStats extends PresenceLimits {
		public final Map<PresenceType, Integer> Presents = new HashMap<>();

		public PresenceStats(PresenceLimits limits) {
			for (Entry<PresenceType, Integer> entry : limits.Limits.entrySet()) {
				addLimit(entry.getKey(), entry.getValue());
			}
		}

		public void addPresent(PresenceType type, int number) {
			int current = 0;
			if (Presents.containsKey(type)) {
				current = Presents.get(type);
			}
			current += number;
			Presents.put(type, current);
		}

		public int getPresent(PresenceType type) {
			if (Presents.containsKey(type)) {
				return Presents.get(type);
			} else {
				return 0;
			}
		}
	}

	public LayeredPresenceDB(String dbName) throws DatabaseException {
		super(dbName);
		this.makeTable("presence_slots", KEY("ID"), TEXT("name"), BIGTEXT("description"), TEXT("title"), TEXT("settings"), NUMBER("valid"), TEXT("owner_login"), TEXT("toolchain"));
		this.makeTable("presence_users", KEY("ID"), NUMBER("user_id"), NUMBER("slot_id"), NUMBER("valid"), NUMBER("type"), DATE("creation_time"));

		this.registerVirtualFile(new VirtualFile() {

			@Override
			public String read() throws DatabaseException {
				return "# Tento soubor slouží pro založení slotu rezervace cvièení. \n" + "# Každý øádek = slot.\n" + "# Formát: <toolchain>|<login>|<nazev>|max_pritomnych:max_online|<popis>\n" + "# Název poslouží pro práva:\n" + "#\t \"<toolchain>.SEE.<nazev>\" = Každý s tímto právem uvidí záznam\n" + "#\t \"<toolchain>.SIGN.<nazev>\" = Každý s tímto právem se mùže pøihlásit\n" + "#\t \"<toolchain>.ADMIN\" = Každý s tímto právem uvidí seznam pøihlášených\n# Pokud zadané jméno existuje, tak se aktualizuje. Poté je potøeba ruènì editovat popis\n# Pozn: \"login\" slouží k vymezení toho, kdo formuláø uvidí ze strany strany vyuèujících.\n# Pokud chcete nìkomu formuláø ukázat, pøidejte do do OwnerLogin (seznam oddìlený èárkami)\n# Pozn#2: Formuláøe jsou cachovány s minutovou prodlevou";
			}

			@Override
			public void write(String data) throws DatabaseException {
				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 5);
					if (parts.length == 5) {
						String toolchain = parts[0].trim();
						String login = parts[1].trim();
						String name = parts[2].trim();
						String[] limits = parts[3].trim().split(":");
						if (limits.length == 2) {
							JsonObject lim = new JsonObject();
							try {
								int pres = Integer.parseInt(limits[0]);
								int rem = Integer.parseInt(limits[1]);
								lim.add("max_" + PresenceType.Present.toString().toLowerCase(), new JsonNumber(pres));
								lim.add("max_" + PresenceType.Remote.toString().toLowerCase(), new JsonNumber(rem));
							} catch (Exception e) {
								return;
							}
							String descr = parts[4].trim();
							StaticDB sdb = (StaticDB) LayeredPresenceDB.this;
							Toolchain Toolchain = null;
							try {
								Toolchain = sdb.getToolchain(toolchain);
							} catch (NoSuchToolchainException e) {
								e.printStackTrace();
							}
							if (Toolchain != null) {
								addSlot(Toolchain, name, descr, lim.getJsonString(), login);
							}
						}
					}
				}
			}

			@Override
			public String getName() {
				return "cvièení/vytvoøení_termínu.exe";
			}

		});
	}

	public static class PresenceSlot {
		public final int ID;
		public final String Name;
		public final String Description;
		public final String Title;
		public final String OwnerLogin;
		public final PresenceLimits Limits;
		public final Toolchain Toolchain;

		private PresenceSlot(int id, String name, String description, String title, PresenceLimits limits, Toolchain toolchain, String ownerLogin) {
			this.ID = id;
			this.Name = name;
			this.Description = description;
			this.Title = title;
			this.Limits = limits;
			this.Toolchain = toolchain;
			this.OwnerLogin = ownerLogin;
		}

		public boolean canSign(UsersPermission perm) {
			return perm.can(new PermissionBranch(Toolchain.getName() + ".SIGN." + Name));
		}

		public boolean canSee(UsersPermission perm) {
			return perm.can(new PermissionBranch(Toolchain.getName() + ".SEE." + Name));
		}
	}

	public static class UserPresenceSlotView {
		public final PresenceSlot Slot;
		public final PresenceStats Stats;

		private UserPresenceSlotView(PresenceSlot slot, PresenceStats stats) {
			this.Slot = slot;
			this.Stats = stats;
		}
	}

	public static class PresenceUser {
		public final int ID;
		public final int UserID;
		public final int SlotID;
		public final long CreationTime;
		public final PresenceType Type;

		private PresenceUser(int id, int userID, int slotID, long creation, PresenceType type) {
			this.ID = id;
			this.UserID = userID;
			this.SlotID = slotID;
			this.CreationTime = creation;
			this.Type = type;
		}
	}

	public static class PresenceData {
		private final Map<String, PresenceSlot> slotsByName = new HashMap<>();
		private final Map<Integer, PresenceSlot> slotsByID = new HashMap<>();

		private final Map<Integer, List<PresentUserDetails>> presencesByUserID = new HashMap<>();
		private final Map<Integer, PresenceStats> statsBySlotID = new HashMap<>();
		private final Toolchain Toolchain;

		public List<PresenceForUser> getForUser(int userID) {
			List<PresenceForUser> lst = new ArrayList<PresenceForUser>();
			if (presencesByUserID.containsKey(userID)) {
				for (PresentUserDetails item : presencesByUserID.get(userID)) {
					lst.add(new PresenceForUser(item.UserID, item.ID, item.SlotID, item.CreationTime, item.Type));
				}
			}
			return lst;
		}

		private PresentUserDetails get(LayeredPresenceDB db, PresenceUser details) {
			LocalUser user = db.getUser(Toolchain.getName(), details.UserID);
			if (user != null) {
				return new PresentUserDetails(details.UserID, details.ID, details.SlotID, details.CreationTime, details.Type, user.FullName, user.Login);
			}
			return null;
		}

		public List<UserPresenceSlotView> getAvailableViews(UsersPermission perm) {
			List<UserPresenceSlotView> lst = new ArrayList<UserPresenceSlotView>();
			for (PresenceSlot slot : slotsByID.values()) {
				if (statsBySlotID.containsKey(slot.ID)) {
					if (slot.canSee(perm)) {
						lst.add(new UserPresenceSlotView(slot, statsBySlotID.get(slot.ID)));
					}
				}
			}
			return lst;
		}

		private PresenceData(LayeredPresenceDB db, Toolchain toolchain) throws DatabaseException {
			this.Toolchain = toolchain;
			List<PresenceSlot> slots = db.getAllSlots(toolchain);
			for (PresenceSlot slot : slots) {
				slotsByName.put(slot.Name, slot);
				slotsByID.put(slot.ID, slot);
				statsBySlotID.put(slot.ID, new PresenceStats(slot.Limits));
			}
			List<PresenceUser> users = db.getAllReservations(toolchain);
			for (PresenceUser user : users) {
				PresentUserDetails luser = get(db, user);
				if (luser != null) {
					if (presencesByUserID.containsKey(user.UserID)) {
						presencesByUserID.get(user.UserID).add(luser);
					} else {
						List<PresentUserDetails> lst = new ArrayList<>();
						lst.add(luser);
						presencesByUserID.put(user.UserID, lst);
					}
					if (statsBySlotID.containsKey(user.SlotID)) {
						statsBySlotID.get(user.SlotID).addPresent(user.Type, 1);
					}
				}
			}

		}

		public List<PresentUserDetails> getAllUsers(UsersPermission perms) {
			if (perms.can(new PermissionBranch(this.Toolchain.getName() + ".ADMIN"))) {
				List<PresentUserDetails> lst = new ArrayList<>();
				for (Entry<Integer, List<PresentUserDetails>> entry : presencesByUserID.entrySet()) {
					for (PresentUserDetails user : entry.getValue()) {
						lst.add(user);
					}
				}
				return lst;
			}
			return null;
		}
	}

	private CachedToolchainData2<PresenceData> presence = new CachedToolchainDataWrapper2<>(60 /* No need to refresh that much */, new CachedToolchainDataGetter2<PresenceData>() {

		@Override
		public CachedData<PresenceData> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper<PresenceData>(refreshIntervalInSeconds, new CachedDataGetter<PresenceData>() {

				@Override
				public PresenceData update() {
					try {
						return new PresenceData(LayeredPresenceDB.this, toolchain);
					} catch (DatabaseException e) {
						e.printStackTrace();
						return null;
					}
				}
			});
		}

	});

	private List<PresenceSlot> getAllSlots(Toolchain toolchain) throws DatabaseException {
		List<PresenceSlot> lst = new ArrayList<PresenceSlot>();

		final String tableName = "presence_slots";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "description"), getField(tableName, "owner_login"), getField(tableName, "title"), getField(tableName, "settings") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));

		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				int ID = obj.getNumber("ID").Value;
				String name = obj.getString("name").Value;
				String description = obj.getString("description").Value;
				String title = obj.getString("title").Value;
				String settings = obj.getString("settings").Value;
				String owner_login = obj.getString("owner_login").Value;
				JsonValue p = JsonValue.parse(settings);
				if (p != null) {
					if (p.isObject()) {
						JsonObject po = p.asObject();
						PresenceLimits limits = new PresenceLimits();
						for (PresenceType type : PresenceType.values()) {
							String maxName = "max_" + type.toString().toLowerCase();
							if (po.containsNumber(maxName)) {
								int limit = po.getNumber(maxName).Value;
								limits.addLimit(type, limit);
							} else {
								limits.addLimit(type, 0);
							}
						}
						lst.add(new PresenceSlot(ID, name, description, title, limits, toolchain, owner_login));
					}

				}
			}
		}
		return lst;
	}

	private List<PresenceUser> getAllReservations(Toolchain toolchain) throws DatabaseException {
		List<PresenceUser> lst = new ArrayList<PresenceUser>();

		final String tableName = "presence_users";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "user_id"), getField(tableName, "slot_id"), getField(tableName, "type"), getField(tableName, "creation_time") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "valid"), 1));

		PresenceType[] presenceTypes = PresenceType.values();
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				int ID = obj.getNumber("ID").Value;
				int user_id = obj.getNumber("user_id").Value;
				int slot_id = obj.getNumber("slot_id").Value;
				long creation = obj.getNumber("creation_time").asLong();
				int type = obj.getNumber("type").Value;
				if (type >= 0 && type < presenceTypes.length) {
					lst.add(new PresenceUser(ID, user_id, slot_id, creation, presenceTypes[type]));
				}
			}
		}
		return lst;
	}

	private PresenceData getPresence(Toolchain toolchain) throws DatabaseException {
		synchronized (presence) {
			PresenceData data = presence.get(toolchain);
			if (data == null) {
				throw new DatabaseException("No presence for " + toolchain);
			}
			return data;
		}
	}

	public List<RemoteUser> getUsersWhoCanSeeSlots(Toolchain toolchain, int slotID) {
		try {
			if (getPresence(toolchain).slotsByID.containsKey(slotID)) {
				String slotName = getPresence(toolchain).slotsByID.get(slotID).Name;
				List<RemoteUser> users = this.getUserIDsWhoCanByGroup(toolchain.getName(), new PermissionBranch(toolchain.getName() + ".SEE." + slotName));
				if (users != null) {
					return users;
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return new ArrayList<RemoteUser>();
	}

	public static class PresenceForUser {
		public final int UserID;
		public final int SlotID;
		private final int DBRowID;
		public final long ReservationTime;
		public final PresenceType Type;

		public PresenceForUser(int userID, int DBRowID, int slotID, long reservationTime, PresenceType type) {
			this.UserID = userID;
			this.SlotID = slotID;
			this.ReservationTime = reservationTime;
			this.Type = type;
			this.DBRowID = DBRowID;
		}
	}

	public static class PresentUserDetails extends PresenceUser {

		public final String Name;
		public final String Login;

		public PresentUserDetails(int userID, int DBRowID, int slotID, long reservationTime, PresenceType type, String name, String login) {
			super(DBRowID, userID, slotID, reservationTime, type);
			this.Name = name;
			this.Login = login;
		}

	}

	public List<PresenceForUser> getPresenceForUser(int userID, Toolchain toolchain) {
		try {
			return getPresence(toolchain).getForUser(userID);
		} catch (DatabaseException e) {
			e.printStackTrace();
			return new ArrayList<PresenceForUser>();
		}
	}

	public List<PresentUserDetails> getAllPresentUsers(Toolchain toolchain, UsersPermission perms) {
		try {
			return getPresence(toolchain).getAllUsers(perms);
		} catch (DatabaseException e) {
			e.printStackTrace();
			return null;
		}
	}

	public List<UserPresenceSlotView> getPresenceSlots(Toolchain toolchain, UsersPermission perm) {
		try {
			return getPresence(toolchain).getAvailableViews(perm);
		} catch (DatabaseException e) {
			e.printStackTrace();
			return new ArrayList<UserPresenceSlotView>();
		}
	}

	public boolean addUserPresence(Toolchain toolchain, int userID, int slotID, int presenceTypeID, UsersPermission perm) {
		if (presenceTypeID >= 0 && presenceTypeID < PresenceType.values().length) {
			synchronized (presence) {
				boolean hasRecord = false;
				int presenceRowID = 0;
				for (PresenceForUser pres : presence.get(toolchain).getForUser(userID)) {
					if (pres.SlotID == slotID) {
						hasRecord = true;
						presenceRowID = pres.DBRowID;
						break;
					}
				}

				if (presence.get(toolchain).slotsByID.containsKey(slotID)) {
					PresenceSlot slot = presence.get(toolchain).slotsByID.get(slotID);
					if (slot.canSign(perm)) {
						try {
							final String tableName = "presence_users";
							ValuedField vuid = new ValuedField(getField(tableName, "user_id"), userID);
							ValuedField vsid = new ValuedField(getField(tableName, "slot_id"), slotID);
							ValuedField vval = new ValuedField(getField(tableName, "valid"), 1);
							ValuedField vpid = new ValuedField(getField(tableName, "type"), presenceTypeID);
							ValuedField vct = new ValuedField(getField(tableName, "creation_time"), System.currentTimeMillis());
							ValuedField[] fields = new ValuedField[] { vuid, vsid, vpid, vct, vval };
							if (hasRecord) {
								return this.update(tableName, presenceRowID, fields);
							} else {
								return this.insert(tableName, fields);
							}
						} catch (DatabaseException e) {
							e.printStackTrace();
							return false;
						} finally {
							presence.clear();
						}
					}
				}
			}
		}
		return false;
	}

	private void addSlot(Toolchain toolchain, String name, String description, String limits, String owner_login) {
		final String tableName = "presence_slots";
		synchronized (presence) {
			try {
				String title = description;
				ValuedField[] fields = new ValuedField[] { new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "owner_login"), owner_login), new ValuedField(getField(tableName, "description"), description), new ValuedField(getField(tableName, "title"), title), new ValuedField(getField(tableName, "settings"), limits), new ValuedField(getField(tableName, "valid"), 1) };
				if (presence.get(toolchain).slotsByName.containsKey(name)) {
					int slotID = presence.get(toolchain).slotsByName.get(name).ID;
					this.update(tableName, slotID, fields);
				} else {
					this.insert(tableName, fields);
				}

			} catch (DatabaseException e) {
				e.printStackTrace();
			} finally {
				presence.clear();
			}

		}
	}
}
