package cz.rion.buildserver.db.layers.staticDB;

import java.util.Set;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.test.targets.AsmTest;
import cz.rion.buildserver.test.targets.GCCTest;

public abstract class LayeredCodeModifiersDB extends LayeredBuildersDB {

	private static final class NasmBaseModifier implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "NasmBaseModifier";
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (test instanceof AsmTest) {
				AsmTest t = (AsmTest) test;
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

		@Override
		public String getDescription() {
			StringBuilder sb = new StringBuilder();
			sb.append("Ovìøí, že kód neobsahuje zakázané instrukce a pøipojí ke kódu prepend a append èást\n");
			sb.append("Použitelné pouze pro testy typu \"asm\" s builderem \"NASM\"");
			return sb.toString();
		}

	}

	private static final class GCCBaseModifier implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "GCCBaseModifier";
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (test instanceof GCCTest) {
				GCCTest t = (GCCTest) test;
				String res = t.VerifyCode(errors.getBadResults(), input);
				if (res != null) {
					errors.logError(res);
					return null;
				} else {
					String newCode = t.getFinalCode(errors, test.getStaticDB(), test.getToolchain(), test.getFiles(), login, input);
					if (newCode == null) {
						errors.logError("Failed to handle replacements of GCC file contents", input);
					}
					return newCode;
				}
			} else {
				errors.logError("Interni chyba. Toolchain prijal kod pro GCC v rezimu nepodporujici GCC");
				return null;
			}
		}

		@Override
		public String getDescription() {
			StringBuilder sb = new StringBuilder();
			sb.append("Pøipojí ke kódu prepend a append èást. Dále pøipojuje malloc kód a nahrazuje main, je-li povolen\n");
			sb.append("Použitelné pouze pro testy typu \"gcc\" s builderem \"GCC\"");
			return sb.toString();
		}
	}

	private static final class GCCIncludeChecker implements LayeredBuildersDB.ToolInputModifier {

		@Override
		public String getName() {
			return "GCCIncludeChecker";
		}

		private boolean verifyCode(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (test instanceof GCCTest) {
				GCCTest t = (GCCTest) test;
				Set<String> allowedIncludes = t.AllowedIncludes;
				if (allowedIncludes != null) {
					String[] lines = input.split("\n");
					for (String line : lines) {
						line = line.trim();
						if (line.startsWith(". ")) {
							String included = line.substring(line.lastIndexOf('/') + 1, line.length());
							if (included.endsWith(".h")) {
								included = included.substring(0, included.length() - 2);
							}
							if (allowedIncludes.contains(included) || allowedIncludes.contains(included + ".h")) {
								continue;
							}
							errors.getBadResults().setNext(BadResultType.BadInstructions);
							errors.logError("Nepovolený import: " + included);
							return false;
						}
					}
				}
			} else {
				errors.logError("Interni chyba. Toolchain prijal kod pro GCC v rezimu nepodporujici GCC");
				return false;
			}
			return true;
		}

		@Override
		public String getDescription() {
			StringBuilder sb = new StringBuilder();
			sb.append("Ovìøuje, že není v uživatelském kódu nepoovolený hlavièkový soubor, je-li u testu uveden seznam povolených hlavièkových souborù.\n");
			sb.append("Použitelné pouze pro testy typu \"gcc\" s builderem \"GCC\"");
			return sb.toString();
		}

		@Override
		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login) {
			if (verifyCode(errors, input, test, login)) {
				return input;
			}
			return null;
		}
	}

	public LayeredCodeModifiersDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.registerModifier(new NasmBaseModifier());
		this.registerModifier(new GCCBaseModifier());
		this.registerModifier(new GCCIncludeChecker());
	}

}
