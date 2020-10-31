package cz.rion.buildserver.db;

import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class VirtualStatFile extends VirtualFile {

	public VirtualStatFile(String name, Toolchain toolchain) {
		super("database/RuntimeDB/stats/virtual/" + name + ".view", toolchain);
	}

	public abstract JsonArray getData();

	public abstract String getQueryString();

	@Override
	public boolean write(UserContext context, String newName, String newContents) {
		return false;
	}

	@Override
	public String read(UserContext context) {
		JsonObject robj = new JsonObject();
		robj.add("SQL", new JsonString(getQueryString()));
		robj.add("freeSQL", new JsonString(getQueryString()));
		robj.add("code", new JsonNumber(0));
		robj.add("result", getData());
		return robj.getJsonString();
	}

}