package cz.rion.buildserver.test.targets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.JsonTest;

public class GCCTest extends JsonTest {

	private final String prepend;
	private final String append;
	public final Set<String> AllowedIncludes;
	private final boolean replace;

	private GCCTest(String id, Toolchain toolchain, String title, String description, List<TestVerificationData> tests, String initialCode, String append, String prepend, boolean hidden, boolean secret, Set<String> allowedIncludes, boolean replace, ReplacementEntry[] replacement) {
		super(id, toolchain, title, description, initialCode, tests, hidden, secret);
		this.prepend = prepend;
		this.append = append;
		this.AllowedIncludes = allowedIncludes;
		this.replace = replace;
	}

	public static GenericTest get(Toolchain toolchain, String id, String descr, String title, String type, List<TestVerificationData> tests, JsonObject obj) {

		Set<String> allowedIncludes = null;

		if (obj.containsArray("includes")) {
			allowedIncludes = new HashSet<>();
			JsonArray allowedInstr = obj.getArray("includes");
			for (JsonValue val : allowedInstr.Value) {
				allowedIncludes.add(val.asString().Value);
			}
		}

		ReplacementEntry[] replacement = null;
		if (obj.containsArray("replacement")) {
			JsonArray replacements = obj.getArray("replacement");
			replacement = new ReplacementEntry[replacements.Value.size()];
			int index = 0;
			for (JsonValue val : replacements.Value) {
				String source = val.asObject().getString("from").Value;
				String to = val.asObject().getString("to").Value;
				replacement[index] = new ReplacementEntry(source, to);
				index++;
			}
		}

		String description = obj.getString("description").Value;
		String prepend = obj.containsString("prepend") ? obj.getString("prepend").Value : "";
		String append = obj.containsString("append") ? obj.getString("append").Value : "";

		String initialASM = obj.containsString("init") ? obj.getString("init").Value : "";
		boolean hidden = obj.containsNumber("hidden") ? obj.getNumber("hidden").Value == 1 : false;
		boolean secret = obj.containsNumber("secret") ? obj.getNumber("secret").Value == 1 : false;
		boolean replace = obj.containsNumber("replace") ? obj.getNumber("replace").Value == 1 : false;

		return new GCCTest(id, toolchain, title, description, tests, initialASM, append, prepend, hidden, secret, allowedIncludes, replace, replacement);
	}

	public String getFinalCode(String login, String code) {
		code = prepend + code + append;
		if (replace) {
			code = code.replaceAll("\\$LOGIN\\$", login);
		}
		return code;
	}
}
