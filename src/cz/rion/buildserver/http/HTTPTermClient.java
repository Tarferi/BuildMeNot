package cz.rion.buildserver.http;

import java.util.List;

import cz.rion.buildserver.db.RuntimeDB;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredPresenceDB.PresenceForUser;
import cz.rion.buildserver.db.layers.staticDB.LayeredPresenceDB.PresenceType;
import cz.rion.buildserver.db.layers.staticDB.LayeredPresenceDB.PresentUserDetails;
import cz.rion.buildserver.db.layers.staticDB.LayeredPresenceDB.UserPresenceSlotView;
import cz.rion.buildserver.db.layers.staticDB.LayeredUserDB.RemoteUser;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;

public class HTTPTermClient extends HTTPParserClient {

	private final StaticDB sdb;

	protected HTTPTermClient(CompatibleSocketClient client, int BuilderID, RuntimeDB rdb, StaticDB sdb) {
		super(client, BuilderID, rdb, sdb);
		this.sdb = sdb;
	}

	private JsonObject collectSlots(Toolchain toolchain, UsersPermission perms) {
		JsonObject result = new JsonObject();
		long now = System.currentTimeMillis();
		List<UserPresenceSlotView> availableSlots = sdb.getPresenceSlots(toolchain, perms);
		List<PresentUserDetails> admin = sdb.getAllPresentUsers(toolchain, perms);
		long nextEvent = 0;
		if (admin != null) { // Data available (has Admin)
			JsonArray adm = new JsonArray();
			for (PresentUserDetails data : admin) {
				JsonObject obj = new JsonObject();
				obj.add("UserID", new JsonNumber(data.UserID));
				obj.add("Login", new JsonString(data.Login));
				obj.add("Name", new JsonString(data.Name));
				obj.add("SlotID", new JsonNumber(data.SlotID));
				obj.add("Time", new JsonNumber(0, data.CreationTime + ""));
				obj.add("Type", new JsonNumber(data.Type.Code));
				obj.add("TypeName", new JsonString(data.Type.Name));
				adm.add(obj);
			}
			result.add("Admin", adm);

			JsonObject admAll = new JsonObject();
			for (UserPresenceSlotView slot : availableSlots) {
				int slotID = slot.Slot.ID;
				JsonArray admAllX = new JsonArray();
				for (RemoteUser user : sdb.getUsersWhoCanSeeSlots(toolchain, slotID)) {
					JsonObject obj = new JsonObject();
					if (!user.Group.toLowerCase().contains("teacher")) {
						obj.add("Login", new JsonString(user.Login));
						obj.add("Name", new JsonString(user.FullName));
						obj.add("Type", new JsonNumber(PresenceType.Undefined.Code));
						obj.add("Time", new JsonNumber(0));
						obj.add("SlotID", new JsonNumber(slotID));
						obj.add("TypeName", new JsonString(PresenceType.Undefined.Name));
						admAllX.add(obj);
					}
				}
				admAll.add(slot.Slot.Name, admAllX);
			}

			result.add("AdminAll", admAll);
		}
		JsonArray available = new JsonArray();

		for (UserPresenceSlotView slot : availableSlots) {
			boolean add = admin != null;
			if (!add) { // Not admin, check date constrains
				if ((nextEvent == 0 || slot.Slot.OdkdyPrihlasovat < nextEvent) && slot.Slot.OdkdyPrihlasovat > now) {
					nextEvent = slot.Slot.OdkdyPrihlasovat;
				}
				if ((nextEvent == 0 || slot.Slot.DokdyPrihlasovat < nextEvent) && slot.Slot.DokdyPrihlasovat > now) {
					nextEvent = slot.Slot.DokdyPrihlasovat;
				}
				if ((nextEvent == 0 || slot.Slot.OdkdyZobrazit < nextEvent) && slot.Slot.OdkdyZobrazit > now) {
					nextEvent = slot.Slot.OdkdyZobrazit;
				}
				if ((nextEvent == 0 || slot.Slot.DokdyZobrazit < nextEvent) && slot.Slot.DokdyZobrazit > now) {
					nextEvent = slot.Slot.DokdyZobrazit;
				}

				if (slot.Slot.OdkdyZobrazit < now && slot.Slot.DokdyZobrazit > now) {
					add = true;
				}
			}
			if (add) {
				JsonObject obj = new JsonObject();
				obj.add("ID", new JsonNumber(slot.Slot.ID));
				obj.add("Name", new JsonString(slot.Slot.Name));
				obj.add("Description", new JsonString(slot.Slot.Description));
				obj.add("Title", new JsonString(slot.Slot.Title));
				obj.add("OwnerLogin", new JsonString(slot.Slot.OwnerLogin));
				obj.add("Available", new JsonNumber(slot.Slot.OdkdyPrihlasovat < now && slot.Slot.DokdyPrihlasovat > now ? 1 : 0));
				JsonObject presences = new JsonObject();
				for (PresenceType type : PresenceType.values()) {
					if (type.Visible) {
						JsonObject data = new JsonObject();
						data.add("Code", new JsonNumber(type.Code));
						data.add("Name", new JsonString(type.Name));
						data.add("Limit", new JsonNumber(slot.Stats.getLimit(type)));
						data.add("Value", new JsonNumber(slot.Stats.getPresent(type)));
						presences.add(type.toString().toLowerCase(), data);
					}
				}
				obj.add("Stats", presences);
				available.add(obj);
			}
		}

		JsonArray my = new JsonArray();
		for (PresenceForUser data : sdb.getPresenceForUser(perms.getStaticUserID(), toolchain)) {
			JsonObject obj = new JsonObject();
			obj.add("SlotID", new JsonNumber(data.SlotID));
			obj.add("Time", new JsonNumber(0, data.ReservationTime + ""));
			obj.add("Type", new JsonNumber(data.Type.Code));
			obj.add("TypeName", new JsonString(data.Type.Name));
			my.add(obj);
		}
		if (nextEvent != 0 && nextEvent > now) {
			result.add("Now", new JsonNumber(0, now + ""));
			result.add("NextEvent", new JsonNumber(0, nextEvent + ""));

		}
		result.add("Available", available);
		result.add("MyData", my);
		return result;
	}

	protected void handleTermsEvent(JsonObject obj, Toolchain toolchain, JsonObject result, UsersPermission perms) {
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid admin command"));

		if (obj.containsString("term_data")) {
			String term_data = obj.getString("term_data").Value;
			if (term_data.equals("getTerms")) {
				result.add("code", new JsonNumber(0));
				result.add("result", new JsonString(collectSlots(toolchain, perms).getJsonString()));
			} else if (term_data.equals("subscribe") && obj.containsNumber("slotID") && obj.containsNumber("variantID")) {
				int slotID = obj.getNumber("slotID").Value;
				int variantID = obj.getNumber("variantID").Value;
				if (sdb.addUserPresence(toolchain, perms.getStaticUserID(), slotID, variantID, perms)) {
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectSlots(toolchain, perms).getJsonString()));
				} else {
					result.add("code", new JsonNumber(1));
					result.add("result", new JsonString("Nepodaøilo se pøihlásit na daný termín"));
				}
			}
		}
	}
}
