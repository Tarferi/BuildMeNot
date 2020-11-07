package cz.rion.buildserver.test.targets;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.ToolchainLogger;
import cz.rion.buildserver.db.layers.staticDB.LayeredTestDB.MallocData;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.JsonTest;
import cz.rion.buildserver.wrappers.MyExec.TestResultsExpectations;

public class GCCTest extends JsonTest {

	private final String prepend;
	private final String append;
	public final Set<String> AllowedIncludes;
	private final boolean replace;
	public final MallocOverload Malloc;

	private GCCTest(String id, StaticDB sdb, VirtualFileManager files, Toolchain toolchain, String title, String description, List<TestVerificationData> tests, String initialCode, String append, String prepend, boolean hidden, boolean secret, Set<String> allowedIncludes, boolean replace, ReplacementEntry[] replacement, MallocOverload malloc) {
		super(id, sdb, files, toolchain, title, description, initialCode, tests, hidden, secret);
		this.prepend = prepend;
		this.append = append;
		this.AllowedIncludes = allowedIncludes;
		this.replace = replace;
		this.Malloc = malloc;
	}

	private static final Pattern pattern_res = Pattern.compile("Memory Error: leaks: (\\d+), invalid frees: (\\d+), invalid mallocs: (\\d+), corruptions: (\\d+)$", Pattern.MULTILINE);

	@Override
	public String getErrorDescription(TestResultsExpectations data) {
		String parentError = super.getErrorDescription(data);
		if (parentError == null && Malloc != null) {
			final Matcher matcher = pattern_res.matcher(data.returnedSTDOUT);

			if (matcher.find()) {
				if (matcher.groupCount() == 4) {
					int leaks = Integer.parseInt(matcher.group(1));
					int invalid_frees = Integer.parseInt(matcher.group(2));
					int invalid_mallocs = Integer.parseInt(matcher.group(3));
					int corruption = Integer.parseInt(matcher.group(4));

					StringBuilder sb = new StringBuilder();
					if (leaks > 0) {
						sb.append((sb.length() > 0 ? ", " : "") + "neuvolnìná pamì " + leaks + " krát");
					}
					if (invalid_frees > 0) {
						sb.append((sb.length() > 0 ? ", " : "") + "neplatné uvolnìní " + invalid_frees + " krát");
					}
					if (invalid_mallocs > 0) {
						sb.append((sb.length() > 0 ? ", " : "") + "neplatná alokace " + invalid_mallocs + " krát");
					}
					if (corruption > 0) {
						sb.append((sb.length() > 0 ? ", " : "") + "poškození pamìti " + corruption + " krát");
					}
					return sb.toString() + ".";
				}
			}
		}
		return parentError;
	}

	public static final class MallocOverload {
		public final int TotalBlocks;
		public final int BlockSize;
		public final int BlockSizeBefore;
		public final int BlockSizeAfter;
		public final boolean ReplaceMain;

		private MallocOverload(int total, int sz, int szp, int sza, boolean repl) {
			this.TotalBlocks = total;
			this.BlockSize = sz;
			this.BlockSizeBefore = szp;
			this.BlockSizeAfter = sza;
			this.ReplaceMain = repl;
		}

		public static MallocOverload get(JsonObject testRoot) {
			if (testRoot.containsObject("malloc")) {
				JsonValue m = testRoot.getObject("malloc");
				if (m.isObject()) {
					JsonObject o = m.asObject();
					if (o.containsNumber("total_blocks") && o.containsNumber("block_size") && o.containsNumber("block_size_before") && o.containsNumber("block_size_after")) {
						int total_blocks = o.getNumber("total_blocks").Value;
						int block_size = o.getNumber("block_size").Value;
						int block_size_before = o.getNumber("block_size_before").Value;
						int block_size_after = o.getNumber("block_size_after").Value;
						boolean replaceMain = o.containsBoolean("replace_main") ? o.getBoolean("replace_main").Value : false;
						return new MallocOverload(total_blocks, block_size, block_size_before, block_size_after, replaceMain);
					}
				}
			}
			return null;
		}
	}

	public static GenericTest get(Toolchain toolchain, StaticDB sdb, VirtualFileManager files, String id, String descr, String title, String type, List<TestVerificationData> tests, JsonObject obj) {

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

		MallocOverload malloc = MallocOverload.get(obj);

		return new GCCTest(id, sdb, files, toolchain, title, description, tests, initialASM, append, prepend, hidden, secret, allowedIncludes, replace, replacement, malloc);
	}

	private static final Pattern pattern = Pattern.compile("int\\s+main\\s*\\(((\\s*)|(void))\\)", Pattern.MULTILINE);
	private static final Pattern pattern2 = Pattern.compile("int\\s+main\\s*\\(", Pattern.MULTILINE);

	public String getFinalCode(ToolchainLogger errors, StaticDB sdb, Toolchain toolchain, VirtualFileManager files, String login, String code) {
		code = prepend + code + append;
		if (replace) {
			code = code.replaceAll("\\$LOGIN\\$", login);
		}
		if (Malloc != null) {
			String random = getRandomString(10);
			MallocData mfiles = sdb.MallocFilesCache.get();
			if (files == null) {
				errors.logError("Failed to replace malloc: cannot read malloc files");
				return null;
			}

			String contentsBefore = mfiles.MallocFileBefore;
			String contentsAfter = mfiles.MallocFileAfter;
			contentsAfter = contentsAfter.replaceAll("\\%RANDOM\\%", random);
			contentsBefore = contentsBefore.replaceAll("\\%RANDOM\\%", random);
			code = contentsBefore + "\r\n" + code;
			contentsAfter = contentsAfter.replace("%MALLOC_MEM_BLOCK_SIZE%", Malloc.BlockSize + "");
			contentsAfter = contentsAfter.replace("%MALLOC_MEM_BLOCK_COUNT%", Malloc.TotalBlocks + "");
			contentsAfter = contentsAfter.replace("%MALLOC_MEM_BLOCK_COUNT_PRE%", Malloc.BlockSizeBefore + "");
			contentsAfter = contentsAfter.replace("%MALLOC_MEM_BLOCK_COUNT_POST%", Malloc.BlockSizeAfter + "");
			if (Malloc.ReplaceMain) {
				errors.logInfo("Replacing main function");
				Matcher matcher = pattern.matcher(code);
				code = matcher.replaceAll("int main(int argc, char** argv)");
				final Matcher matcher2 = pattern2.matcher(code);
				code = matcher2.replaceAll("int MALLOC_DATA_PREFIX(main)(");

				contentsAfter += "\r\n" + mfiles.MallocFile + "\r\n";
			}
			code = code + "\r\n" + contentsAfter;
			errors.logInfo("Replacing malloc", code);
		} else {
			errors.logInfo("Skipping malloc replacement because it's not part of test JSON structure");
		}
		return code;
	}

	private String getRandomString(int limit) {
		Random r = new Random(System.currentTimeMillis());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < limit; i++) {
			int n = r.nextInt();
			n = n < 0 ? -n : n;
			n = n % ('z' - 'a');
			char c = (char) (n + 'a');
			sb.append(c);
		}
		return sb.toString();
	}
}
