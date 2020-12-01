package cz.rion.buildserver.test.targets;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.JsonTest;
import cz.rion.buildserver.utils.Pair;

public class AsmTest extends JsonTest {

	private final String prepend;
	private final String append;
	private final String[] allowedInstructions;
	public final boolean replace;
	private final ReplacementEntry[] replacement;

	public AsmTest(TestConfiguration config, String prepend, String append, String[] allowedInstructions, boolean replace, ReplacementEntry[] replacement) {
		super(config);
		this.prepend = prepend;
		this.append = append;
		this.allowedInstructions = allowedInstructions;
		this.replace = replace;
		this.replacement = replacement;
	}

	public static AsmTest get(TestConfiguration config, JsonObject obj) {
		String[] allowedInstructions = null;

		if (obj.containsArray("instructions")) {
			JsonArray allowedInstr = obj.getArray("instructions");
			allowedInstructions = new String[allowedInstr.Value.size()];
			int index = 0;
			for (JsonValue val : allowedInstr.Value) {
				allowedInstructions[index] = val.asString().Value;
				index++;
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

		String prepend = obj.containsString("prepend") ? obj.getString("prepend").Value : "";
		String append = obj.containsString("append") ? obj.getString("append").Value : "";
		boolean replace = obj.containsNumber("replace") ? obj.getNumber("replace").Value == 1 : false;

		return new AsmTest(config, prepend, append, allowedInstructions, replace, replacement);
	}

	private static final Pattern pattern = Pattern.compile("\\%include +\"([^\\\"]+)\"", Pattern.MULTILINE);

	@Override
	public String VerifyCode(BadResults badResults, String asm) {
		String scode = super.VerifyCode(badResults, asm);
		if (scode == null) {
			if (allowedInstructions != null) {
				boolean codeSection = true;
				String[] asmLine = asm.split("\n");
				for (String line : asmLine) {
					String lt = line.trim().toLowerCase();
					if (lt.contains(";")) {
						if (lt.trim().equals(";")) {
							continue;
						}
						lt = lt.split(";")[0].trim();
					}
					if (lt.contains(":")) {
						if (lt.endsWith(":")) {
							continue;
						}
						String[] d = lt.split(":");
						lt = d[d.length - 1].trim(); // Last
					}
					if (!lt.isEmpty()) {
						if (lt.startsWith("section")) {
							codeSection = lt.contains(".text");
						} else if (codeSection) {
							if (lt.trim().startsWith("%")) {
								if (!lt.trim().startsWith("%include")) {
									return null;
								}
							} else {
								String fail = getInvalidInstruction(lt, allowedInstructions);
								if (fail != null) {
									badResults.setNext(BadResultType.BadInstructions);
									return fail;
								}
							}
						}
					}
				}
			}

			if (prepend.contains("_main:") || prepend.contains("CMAIN") || append.contains("_main:") || append.contains("CMAIN")) {
				if (asm.contains("_main:") || asm.contains("CMAIN")) {
					return "Nesmí být definován vstupní bod programu";
				}
			}
			return null;
		} else {
			return scode;
		}
	}

	private static final String getInvalidInstruction(String lt, String[] allowedInstructions) {
		boolean validStart = false;
		for (String instruction : allowedInstructions) {
			if (lt.startsWith(instruction.toLowerCase())) { // Could allow "MOVX" if "MOV" is allowed
				String next = lt.substring(instruction.length());
				if (next.length() == 0) {
					validStart = true;
					break;
				} else {
					char c = next.charAt(0);
					if (c == ' ' || c == '\r' || c == '\n' || c == '\t') {
						if (instruction.toLowerCase().startsWith("rep")) {
							String inv = getInvalidInstruction(next.trim(), allowedInstructions);
							if (inv != null) {
								return inv;
							}
						}
						validStart = true;
						break;
					}
				}
			}
		}
		if (!validStart) {
			return "Nepovolená instrukce: " + lt.trim().split(" ")[0].toUpperCase();
		}
		return null;
	}

	public String GetFinalCode(String login, String asm) {
		asm = prepend + asm + append;
		if (replace) {
			asm = asm.replaceAll("\\$LOGIN\\$", login);
		}
		asm = asm.replaceAll("(?i)cextern", "cextern");
		asm = asm.replaceAll("(?i)extern", "extern");
		if (replacement != null) {
			for (ReplacementEntry rep : replacement) {
				asm = asm.replaceAll(rep.source, rep.replacement);
			}
		}
		if (asm.toLowerCase().contains("extern")) {
			return null;
		}
		asm = prepend + "\r\n" + asm + "\r\n" + append;

		final Matcher matcher = pattern.matcher(asm);

		// The substituted value will be contained in the result variable
		String path = new File("./nasm/").getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\\\\";
		asm = matcher.replaceAll("%include \"" + path + "$1\"");

		return asm;
	}
}
