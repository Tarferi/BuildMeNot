package cz.rion.buildserver.db.layers.staticDB;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.JsonTestManager;

public abstract class LayeredCodeModifiersDB extends LayeredBuildersDB {

	private static final class NasmBaseModifier implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "NasmBaseModifier";
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (test instanceof JsonTestManager.JsonTest) {
				JsonTestManager.JsonTest t = (JsonTestManager.JsonTest) test;
				String res = t.VerifyCode(errors.getBadResults(), input);
				if (res != null) {
					errors.logError(res);
					return null;
				} else {
					return t.GetFinalCode(login, input);
				}
			} else {
				errors.logError("Interni chyba. Toolchain prijal kod pro ASM v rezimu nepodporujici ASM");
				return null;
			}
		}

	}

	private static final class GCCBaseModifier implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "GCCBaseModifier";
		}

		private String getModifiedCode(ToolchainLogger errors, String input, GenericTest test, String login) {
			return input;
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			return getModifiedCode(errors, input, test, login);
		}
	}

	private static final class GCCIncludeChecker implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "GCCIncludeChecker";
		}

		final Pattern pattern = Pattern.compile("#include\\s+[\"'<]([^\"'>]+)[\"'>]", Pattern.MULTILINE);

		private boolean verifyCode(ToolchainLogger errors, String input, GenericTest test, String login) {

			Set<String> allowedImports = new HashSet<>();
			allowedImports.add("stdio");
			allowedImports.add("stdlib");

			String[] lines = input.split("\n");
			for (String line : lines) {
				line = line.trim();
				if (line.startsWith(". ")) {
					String included = line.substring(line.lastIndexOf('/') + 1, line.length());
					if (included.endsWith(".h")) {
						included = included.substring(0, included.length() - 2);
					}
					if (allowedImports.contains(included) || allowedImports.contains(included + ".h")) {
						continue;
					}
					errors.getBadResults().setNext(BadResultType.BadInstructions);
					errors.logError("Nepovolený import: " + included);
					return false;
				}
			}
			return true;
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (verifyCode(errors, input, test, login)) {
				return input;
			}
			return null;
		}
	}

	public LayeredCodeModifiersDB(String dbName) throws DatabaseException {
		super(dbName);
		this.registerModifier(new NasmBaseModifier());
		this.registerModifier(new GCCBaseModifier());
		this.registerModifier(new GCCIncludeChecker());
	}

}
