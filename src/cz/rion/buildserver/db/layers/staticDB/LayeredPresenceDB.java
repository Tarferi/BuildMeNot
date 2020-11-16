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
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.utils.Pair;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class LayeredPresenceDB extends LayeredExamDB {

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

	public static class FormLabels {
		private final String SignTitle;
		private final String Variant;
		private final String Capacity;
		private final String SignedUp;
		private final String SigningUp;
		private final String SelfSignedUp;
		private final String SignUp;
		private final String SignOut;
		public final JsonObject original;
		public final boolean isDefault;

		private static String query(JsonObject obj, String key, String def) {
			if (obj.containsString(key)) {
				return obj.getString(key).Value;
			}
			return def;
		}

		public JsonObject get() {
			JsonObject obj = new JsonObject();
			if (SignTitle != null) {
				obj.add("SignTitle", new JsonString(SignTitle));
			}
			if (Variant != null) {
				obj.add("Variant", new JsonString(Variant));
			}
			if (Capacity != null) {
				obj.add("Capacity", new JsonString(Capacity));
			}
			if (SignedUp != null) {
				obj.add("SignedUp", new JsonString(SignedUp));
			}
			if (SigningUp != null) {
				obj.add("SigningUp", new JsonString(SigningUp));
			}
			if (SelfSignedUp != null) {
				obj.add("SelfSignedUp", new JsonString(SelfSignedUp));
			}
			if (SignUp != null) {
				obj.add("SignUp", new JsonString(SignUp));
			}
			if (SignOut != null) {
				obj.add("SignOut", new JsonString(SignOut));
			}
			return obj;
		}

		public FormLabels(JsonObject obj) {
			this(obj, false);
		}

		private FormLabels(JsonObject obj, boolean isDef) {
			this.SignTitle = query(obj, "SignTitle", null); // Pøihlášení
			this.Variant = query(obj, "Variant", null); // Varianta
			this.Capacity = query(obj, "Capacity", null); // Kapacita
			this.SignedUp = query(obj, "SignedUp", null); // Pøihlášeno
			this.SelfSignedUp = query(obj, "SelfSignedUp", null); // Pøihlášen
			this.SigningUp = query(obj, "SigningUp", null); // Pøihlášení
			this.SignUp = query(obj, "SignUp", null); // Pøihlásit
			this.SignOut = query(obj, "SignOut", null); // Odhlásit
			this.original = obj;
			this.isDefault = isDef;
		}

		public FormLabels() {
			this(new JsonObject(), true);
		}
	}

	private final class vfCreaterTerm extends VirtualFile {

		private vfCreaterTerm(Toolchain toolchain) {
			super("cvièení/vytvoøení_termínu.exe", toolchain);
		}

		@Override
		public String read(UserContext context) {
			StringBuilder sb = new StringBuilder();
			sb.append("# Tento soubor slouží pro založení slotu rezervace cvièení. \n");
			sb.append("# Každý øádek = slot.\n");
			sb.append("# Formát: <toolchain>|<login>|<nazev>|<lst_pres>|<popis>\n");
			sb.append("# <lst_pres> je èárkami oddìlený seznam prezencí, kde každý prvek je ve formátu <pres>:<limit>\n");
			sb.append("# <pres> hodnoty jsou v souboru \"presence_types.ini\" a první zadaný typ je pro nepøíhlášení\n\n");
			sb.append("# Název poslouží pro práva:\n");
			sb.append("#\t \"<toolchain>.SEE.<nazev>\" = Každý s tímto právem uvidí záznam\n");
			sb.append("#\t \"<toolchain>.SIGN.<nazev>\" = Každý s tímto právem se mùže pøihlásit\n");
			sb.append("#\t \"<toolchain>.ADMIN\" = Každý s tímto právem uvidí seznam pøihlášených\n");
			sb.append("# Pokud zadané jméno existuje, tak se aktualizuje. Poté je potøeba ruènì editovat popis\n");
			sb.append("# Pozn: \"login\" slouží k vymezení toho, kdo formuláø uvidí ze strany strany vyuèujících.\n");
			sb.append("# Pokud chcete nìkomu formuláø ukázat, pøidejte do do OwnerLogin (seznam oddìlený èárkami)\n");
			sb.append("# Pozn#2: Formuláøe jsou cachovány s minutovou prodlevou");
			return sb.toString();
		}

		@Override
		public boolean write(UserContext context, String newName, String data) {
			try {
				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 5);
					if (parts.length == 5) {
						String toolchain = parts[0].trim();
						StaticDB sdb = (StaticDB) LayeredPresenceDB.this;
						Toolchain Toolchain = null;
						try {
							Toolchain = sdb.getToolchain(toolchain, false);
						} catch (NoSuchToolchainException e) {
							e.printStackTrace();
						}

						if (Toolchain != null) {
							String login = parts[1].trim();
							String name = parts[2].trim();
							String descr = parts[4].trim();

							PresenceData pdata = presence.get(Toolchain);
							String[] limitParts = parts[3].trim().split(",");
							JsonObject lim = new JsonObject();
							if (limitParts.length > 0) {
								for (String limitPart : limitParts) {
									String[] limits = limitPart.trim().split(":");
									if (limits.length == 2) {
										try {
											String pres = limits[0];
											int realLimit = Integer.parseInt(limits[1]);
											if (pdata.typesByCode.containsKey(pres)) {
												PresenceType type = pdata.typesByCode.get(pres);
												JsonObject typeLim = new JsonObject();
												typeLim.add("max", new JsonNumber(realLimit));
												lim.add(type.Code, typeLim);

											}
										} catch (Exception e) {
											return false;
										}
									}
								}
								JsonObject settings = new JsonObject();
								settings.add("limits", lim);
								long now = System.currentTimeMillis();
								addSlot(Toolchain, name, descr, descr, settings.getJsonString(), login, now, now, now, now);
							}
						}
					}
				}
			} finally {
				clearCache();
			}
			return true;
		}
	};

	private final class vfDuplicateTerm extends VirtualFile {

		private vfDuplicateTerm(Toolchain toolchain) {
			super("cvièení/kopie_termínu.exe", toolchain);
		}

		@Override
		public String read(UserContext context) {
			StringBuilder sb = new StringBuilder();
			sb.append("# Tento soubor slouží pro duplikaci termínù. \n");
			sb.append("# Každý øádek = duplikát.\n");
			sb.append("# Formát: <ZDROJ_ID>|<NOVY_NAZEV>|<NOVY_POPIS>|<POSUN_TERMINU_VE_DNECH>\n");
			sb.append("# <NOVY_NAZEV> poslouží pro práva, stejnì jako pøi vytvoøení termínù:\n");
			sb.append("# Pozn#: Formuláøe jsou cachovány s minutovou prodlevou");
			return sb.toString();
		}

		@Override
		public boolean write(UserContext context, String newName, String data) {
			try {
				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.startsWith("#") || line.isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 4);
					if (parts.length == 4) {
						try {
							int id = Integer.parseInt(parts[0].trim());
							String name = parts[1].trim();
							String popis = parts[2].trim();
							int posun = Integer.parseInt(parts[3].trim());
							Toolchain t = getToolchainForSlotID(id);
							if (t != null) {
								PresenceData pres = presence.get(t);
								PresenceSlot slot = pres.slotsByID.get(id);
								if (slot != null) {
									JsonObject limits = new JsonObject();
									for (Entry<PresenceType, Integer> limit : slot.Limits.Limits.entrySet()) {
										JsonObject typeLim = new JsonObject();
										typeLim.add("max", new JsonNumber(limit.getValue()));
										limits.add(limit.getKey().Code, typeLim);
									}
									JsonObject settings = new JsonObject();
									settings.add("limits", limits);
									FormLabels labels = slot.Labels;
									if (!labels.isDefault) {
										settings.add("labels", labels.original);
									}
									long offset = posun;
									offset *= 24;
									offset *= 60;
									offset *= 60;
									offset *= 1000;
									addSlot(t, name, popis, slot.Description, settings.getJsonString(), slot.OwnerLogin, slot.OdkdyZobrazit + offset, slot.DokdyZobrazit + offset, slot.OdkdyPrihlasovat + offset, slot.DokdyPrihlasovat + offset);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
							continue;
						}

					}
				}
			} finally {
				clearCache();
			}
			return true;
		}

	};

	private final class vfPresenceTypes extends VirtualFile {

		private final Toolchain toolchain;

		private vfPresenceTypes(Toolchain toolchain) {
			super("cvièení/presence_types.ini", toolchain);
			this.toolchain = toolchain;
		}

		@Override
		public String read(UserContext context) {
			StringBuilder sb = new StringBuilder();
			sb.append("# Tento soubor je urèen pro bezpeèné editování záznamù typù variant\n");
			sb.append("# Formát: <ZOBRAZIT>: <KOD_TYPU> = <NAZEV_TYPU>\n");
			sb.append("# Pozn: ZOBRAZIT je buï \"true\" nebo \"false\"\n");
			sb.append("# Pozn2: KOD_TYPU je text\n\n");
			final String tableName = "presence_types";
			try {
				for (JsonValue val : select(tableName, new TableField[] { getField(tableName, "name"), getField(tableName, "title"), getField(tableName, "show") }, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName())).Value) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsString("name") && obj.containsString("title") && obj.containsNumber("show")) {
							String name = obj.getString("name").Value;
							String title = obj.getString("title").Value;
							boolean show = obj.getNumber("show").Value == 1;
							sb.append((show ? "true" : "false") + ": " + name + " = " + title + "\n");
						}
					}
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
				return null;
			}
			return sb.toString();
		}

		@SuppressWarnings("deprecation")
		@Override
		public boolean write(UserContext context, String newName, String data) {
			try {
				Map<String, Pair<Integer, Pair<String, Boolean>>> typesByName = new HashMap<>();
				final String tableName = "presence_types";
				for (JsonValue val : select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "title"), getField(tableName, "show") }, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName())).Value) {
					if (val.isObject()) {
						JsonObject obj = val.asObject();
						if (obj.containsString("name") && obj.containsString("title") && obj.containsNumber("ID") && obj.containsNumber("show")) {
							String name = obj.getString("name").Value;
							String title = obj.getString("title").Value;
							int id = obj.getNumber("ID").Value;
							boolean show = obj.getNumber("show").Value == 1;
							typesByName.put(name, new Pair<>(id, new Pair<>(title, show)));
						}
					}
				}

				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String name = parts[0].trim();
						String title = parts[1].trim();
						parts = name.split(":", 2);
						if (parts.length == 2) {
							name = parts[1].trim();
							boolean show = parts[0].trim().equals("true");
							if (typesByName.containsKey(name)) {
								int id = typesByName.get(name).Key;
								Pair<String, Boolean> savedLabelOpt = typesByName.get(name).Value;
								if (!savedLabelOpt.Key.equals(title) || savedLabelOpt.Value != show) { // Update and remove -> no duplicates
									update(tableName, id, new ValuedField[] { new ValuedField(getField(tableName, "title"), title), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "show"), show ? 1 : 0) });
								}
								typesByName.remove(name);
							} else {
								insert(tableName, new ValuedField[] { new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "title"), title), new ValuedField(getField(tableName, "show"), show ? 1 : 0) });
							}
						}
					}
				}

				for (Entry<String, Pair<Integer, Pair<String, Boolean>>> entry : typesByName.entrySet()) {
					String nameToDelete = entry.getKey();
					final String tableName2 = "presence_slots";
					boolean skip = false;
					for (JsonValue val : select(tableName2, new TableField[] { getField(tableName2, "settings") }, true).Value) {
						if (val.isObject()) {
							JsonObject obj = val.asObject();
							if (obj.containsString("settings")) {
								String settings = obj.getString("settings").Value;
								JsonValue v = JsonValue.parse(settings);
								if (v != null) {
									if (v.isObject()) {
										JsonObject o = v.asObject();
										if (o.containsArray("variants")) {
											for (JsonValue variant : o.getArray("variants").Value) {
												if (variant.isString()) {
													String va = variant.asString().Value;
													if (va.equals(nameToDelete)) {
														skip = true;
														break;
													}
												}
											}
										}
									}
								}
							}
						}
					}
					if (!skip) {
						execute_raw("DELETE FROM presence_types WHERE name = ? AND toolchain = ?", nameToDelete, toolchain.getName());
					}
				}
				presence.clear(); // Clear
									// cache
									// after
									// saving
			} catch (DatabaseException e) {
				e.printStackTrace();
				return false;
			} finally {
				clearCache();
			}
			return true;
		}
	}

	private final DatabaseInitData dbData;;

	@Override
	public void afterInit() {
		super.afterInit();
		final Map<String, List<VirtualFile>> files = new HashMap<>();

		registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainAdded(Toolchain toolchain) {
				synchronized (files) {
					List<VirtualFile> lst = files.get(toolchain.getName());
					if (lst == null) {
						lst = new ArrayList<>();
						lst.add(new vfCreaterTerm(toolchain));
						lst.add(new vfDuplicateTerm(toolchain));
						lst.add(new vfPresenceTypes(toolchain));
						for (VirtualFile vs : lst) {
							dbData.Files.registerVirtualFile(vs);
						}
						files.put(toolchain.getName(), lst);
					}
				}

			}

			@Override
			public void toolchainRemoved(Toolchain toolchain) {
				synchronized (files) {
					List<VirtualFile> lst = files.get(toolchain.getName());
					if (lst != null) {
						for (VirtualFile vf : lst) {
							dbData.Files.unregisterVirtualFile(vf);
						}
						files.remove(toolchain.getName());
					}
				}
			}

		});

	}

	public LayeredPresenceDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.makeTable("presence_types", false, KEY("ID"), TEXT("name"), TEXT("title"), NUMBER("show"), TEXT("toolchain"));
		this.makeTable("presence_slots", false, KEY("ID"), TEXT("name"), BIGTEXT("description"), TEXT("title"), BIGTEXT("settings"), NUMBER("valid"), TEXT("owner_login"), TEXT("toolchain"), DATE("odkdy_zobrazit"), DATE("odkdy_nezobrazit"), DATE("odkdy_prihlasovani"), DATE("dokdy_prihlasovani"));
		this.makeTable("presence_users", false, KEY("ID"), NUMBER("user_id"), NUMBER("slot_id"), NUMBER("valid"), TEXT("type"), DATE("creation_time"), TEXT("toolchain"));
		this.dbData = dbData;
	}

	public static class PresenceSlot {
		public final int ID;
		public final String Name;
		public final String Description;
		public final String Title;
		public final String OwnerLogin;
		public final PresenceLimits Limits;
		public final Toolchain Toolchain;
		public final long OdkdyZobrazit;
		public final long DokdyZobrazit;
		public final long OdkdyPrihlasovat;
		public final long DokdyPrihlasovat;
		public final PresenceType DefaultType;
		public final FormLabels Labels;

		private PresenceSlot(int id, String name, String description, String title, PresenceLimits limits, PresenceType undef, Toolchain toolchain, String ownerLogin, long odkdyZobrazit, long dokdyZobrazit, long odkdyPrihlasovat, long dokdyPrihlasovat, FormLabels labels) {
			this.ID = id;
			this.Name = name;
			this.Description = description;
			this.Title = title;
			this.Limits = limits;
			this.Toolchain = toolchain;
			this.OwnerLogin = ownerLogin;
			this.OdkdyZobrazit = odkdyZobrazit;
			this.DokdyZobrazit = dokdyZobrazit;
			this.OdkdyPrihlasovat = odkdyPrihlasovat;
			this.DokdyPrihlasovat = dokdyPrihlasovat;
			this.DefaultType = undef;
			this.Labels = labels;
		}

		public boolean canSign(UsersPermission perm) {
			return perm.can(new PermissionBranch(Toolchain, Toolchain.getName() + ".SIGN." + Name));
		}

		public boolean canSee(UsersPermission perm) {
			return perm.can(new PermissionBranch(Toolchain, Toolchain.getName() + ".SEE." + Name));
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

	public static class PresenceType {
		public final boolean Show;
		public final String Name;
		public final String Code;

		private PresenceType(boolean show, String code, String name) {
			this.Show = show;
			this.Name = name;
			this.Code = code;
		}

		@Override
		public boolean equals(Object anothertype) {
			if (anothertype instanceof PresenceType) {
				return Code.equals(((PresenceType) anothertype).Code);
			}
			return super.equals(anothertype);
		}
	}

	public static class PresenceData {
		private final Map<String, PresenceSlot> slotsByName = new HashMap<>();
		private final Map<Integer, PresenceSlot> slotsByID = new HashMap<>();

		private final List<PresenceType> presenceTypes = new ArrayList<>();
		private final Map<String, PresenceType> typesByCode = new HashMap<>();

		private final Map<Integer, List<PresentUserDetails>> presencesByUserID = new HashMap<>();
		private final Map<Integer, PresenceStats> statsBySlotID = new HashMap<>();
		private final Toolchain Toolchain;

		public List<PresenceType> getPresenceTypes() {
			return presenceTypes;
		}

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
			LocalUser user = db.getUser(Toolchain, details.UserID);
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
			presenceTypes.addAll(db.getPresenceTypes(toolchain));
			for (PresenceType type : presenceTypes) {
				typesByCode.put(type.Code, type);
			}

			List<PresenceSlot> slots = db.getAllSlots(toolchain, typesByCode);
			for (PresenceSlot slot : slots) {
				slotsByName.put(slot.Name, slot);
				slotsByID.put(slot.ID, slot);
				statsBySlotID.put(slot.ID, new PresenceStats(slot.Limits));
			}
			List<PresenceUser> users = db.getAllReservations(toolchain, typesByCode);
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
			if (perms.can(new PermissionBranch(Toolchain, this.Toolchain.getName() + ".ADMIN"))) {
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

	private List<PresenceSlot> getAllSlots(Toolchain toolchain, Map<String, PresenceType> presenceTypes) throws DatabaseException {
		List<PresenceSlot> lst = new ArrayList<PresenceSlot>();
		final String tableName = "presence_slots";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "description"), getField(tableName, "owner_login"), getField(tableName, "title"), getField(tableName, "settings"), getField(tableName, "odkdy_zobrazit"), getField(tableName, "odkdy_nezobrazit"), getField(tableName, "odkdy_prihlasovani"), getField(tableName, "dokdy_prihlasovani"), };
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
				long odkdy_zobrazit = obj.getNumber("odkdy_zobrazit").asLong();
				long odkdy_nezobrazit = obj.getNumber("odkdy_nezobrazit").asLong();
				long odkdy_prihlasovani = obj.getNumber("odkdy_prihlasovani").asLong();
				long dokdy_prihlasovani = obj.getNumber("dokdy_prihlasovani").asLong();
				JsonValue p = JsonValue.parse(settings);
				if (p != null) {
					if (p.isObject()) {
						JsonObject ppo = p.asObject();
						PresenceLimits limits = new PresenceLimits();
						PresenceType first = null;
						if (ppo.containsObject("limits")) {
							JsonObject po = ppo.getObject("limits");
							for (Entry<String, JsonValue> entry : po.getEntries()) {
								String typeCode = entry.getKey();
								JsonValue typeVal = entry.getValue();
								if (typeVal.isObject()) {
									if (typeVal.asObject().containsNumber("max")) {
										int typeMax = typeVal.asObject().getNumber("max").Value;
										if (presenceTypes.containsKey(typeCode)) {
											if (first == null) {
												first = presenceTypes.get(typeCode);
											}
											limits.addLimit(presenceTypes.get(typeCode), typeMax);
										}
									}
								}
							}
							FormLabels labels;
							if (ppo.containsObject("labels")) {
								labels = new FormLabels(ppo.getObject("labels"));
							} else {
								labels = new FormLabels();
							}
							lst.add(new PresenceSlot(ID, name, description, title, limits, first, toolchain, owner_login, odkdy_zobrazit, odkdy_nezobrazit, odkdy_prihlasovani, dokdy_prihlasovani, labels));
						}

					}
				}
			}
		}
		return lst;
	}

	private List<PresenceUser> getAllReservations(Toolchain toolchain, Map<String, PresenceType> presenceTypes) throws DatabaseException {
		List<PresenceUser> lst = new ArrayList<PresenceUser>();

		final String tableName = "presence_users";
		TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "user_id"), getField(tableName, "slot_id"), getField(tableName, "type"), getField(tableName, "creation_time") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "valid"), 1), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()));

		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				int ID = obj.getNumber("ID").Value;
				int user_id = obj.getNumber("user_id").Value;
				int slot_id = obj.getNumber("slot_id").Value;
				long creation = obj.getNumber("creation_time").asLong();
				String type = obj.getString("type").Value;
				if (presenceTypes.containsKey(type)) {
					lst.add(new PresenceUser(ID, user_id, slot_id, creation, presenceTypes.get(type)));
				}
			}
		}
		return lst;
	}

	private List<PresenceType> getPresenceTypes(Toolchain toolchain) throws DatabaseException {
		List<PresenceType> lst = new ArrayList<PresenceType>();

		final String tableName = "presence_types";
		for (JsonValue val : select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "title"), getField(tableName, "show") }, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName())).Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("name") && obj.containsString("title") && obj.containsNumber("ID") && obj.containsNumber("show")) {
					String name = obj.getString("name").Value;
					String title = obj.getString("title").Value;
					boolean show = obj.getNumber("show").Value == 1;
					lst.add(new PresenceType(show, name, title));
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
				List<RemoteUser> users = this.getUserIDsWhoCanByGroup(toolchain, new PermissionBranch(toolchain, toolchain.getName() + ".SEE." + slotName));
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

	public List<PresenceType> getAllPresenceTypes(Toolchain toolchain) {
		try {
			return getPresence(toolchain).presenceTypes;
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

	public boolean addUserPresence(Toolchain toolchain, int userID, int slotID, String presenceTypeCode, UsersPermission perm) {
		synchronized (presence) {
			PresenceData pdata = presence.get(toolchain);
			if (pdata.typesByCode.containsKey(presenceTypeCode)) {
				boolean hasRecord = false;
				int presenceRowID = 0;
				for (PresenceForUser pres : presence.get(toolchain).getForUser(userID)) {
					if (pres.SlotID == slotID) {
						hasRecord = true;
						presenceRowID = pres.DBRowID;
						break;
					}
				}
				if (pdata.slotsByID.containsKey(slotID)) {
					PresenceSlot slot = presence.get(toolchain).slotsByID.get(slotID);
					if (slot.canSign(perm)) {
						try {
							final String tableName = "presence_users";
							ValuedField vuid = new ValuedField(getField(tableName, "user_id"), userID);
							ValuedField vtlc = new ValuedField(getField(tableName, "toolchain"), toolchain.getName());
							ValuedField vsid = new ValuedField(getField(tableName, "slot_id"), slotID);
							ValuedField vval = new ValuedField(getField(tableName, "valid"), 1);
							ValuedField vpid = new ValuedField(getField(tableName, "type"), presenceTypeCode);
							ValuedField vct = new ValuedField(getField(tableName, "creation_time"), System.currentTimeMillis());
							ValuedField[] fields = new ValuedField[] { vuid, vtlc, vsid, vpid, vct, vval };
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

	private void addSlot(Toolchain toolchain, String name, String title, String description, String limits, String owner_login, long odkdy_zobrazit, long odkdy_nezobrazit, long odkdy_prihlasovani, long dokdy_prihlasovani) {
		final String tableName = "presence_slots";
		synchronized (presence) {
			try {
				ValuedField[] fields = new ValuedField[] { new ValuedField(getField(tableName, "toolchain"), toolchain.getName()), new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "owner_login"), owner_login), new ValuedField(getField(tableName, "description"), description), new ValuedField(getField(tableName, "title"), title), new ValuedField(getField(tableName, "settings"), limits), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "odkdy_zobrazit"), odkdy_zobrazit), new ValuedField(getField(tableName, "odkdy_nezobrazit"), odkdy_nezobrazit), new ValuedField(getField(tableName, "odkdy_prihlasovani"), odkdy_prihlasovani), new ValuedField(getField(tableName, "dokdy_prihlasovani"), dokdy_prihlasovani) };
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

	private Toolchain getToolchainForSlotID(int slotID) {
		final String tableName = "presence_slots";
		try {
			JsonArray res = this.select(tableName, new TableField[] { getField(tableName, "toolchain") }, true, new ComparisionField(getField(tableName, "ID"), slotID));
			for (JsonValue val : res.Value) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("toolchain")) {
						String toolchain = obj.getString("toolchain").Value;
						if (this instanceof StaticDB) {
							StaticDB sdb = (StaticDB) this;
							try {
								return sdb.getToolchain(toolchain, false);
							} catch (NoSuchToolchainException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void clearCache() {
		super.clearCache();
		if (presence != null) {
			presence.clear();
		}
	}

}
