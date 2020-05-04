package cz.rion.buildserver.db;

import cz.rion.buildserver.json.JsonValue.JsonArray;

public abstract class VirtualStatFile {

	private String prefix = "";
	private String suffix = "";

	public void setPrefixAndSuffix(String prefix, String suffix) {
		this.prefix = prefix;
		this.suffix = suffix;
	}

	public String getPrefix() {
		return prefix;
	}

	public String getSuffix() {
		return suffix;
	}

	public abstract String getName();

	public abstract JsonArray getData();

	public abstract String getQueryString();
}