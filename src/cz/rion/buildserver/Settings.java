package cz.rion.buildserver;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.utils.Base64;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class Settings {

	private final SettingsValue objName = new SettingsValue(SettingsCategory.NASM, "objFile", "N�zev v�stupn�ho objektov�ho souboru p�elo�en�ho pomoc� NASM", ValueType.STRING, "code.obj");
	private final SettingsValue rtName = new SettingsValue(SettingsCategory.NASM, "execFile", "N�zev v�stupn�ho spustiteln�ho souboru p�elo�en�ho pomoc� NASM", ValueType.STRING, "code.exe");
	private final SettingsValue nasmExecutable = new SettingsValue(SettingsCategory.NASM, "nasm", "N�zev souboru p�eklada�e nasm", ValueType.STRING, "nasm.exe");
	private final SettingsValue gccExecutable = new SettingsValue(SettingsCategory.GCC, "GCCName", "N�zev p�eklada�e GCC", ValueType.STRING, "gcc.exe");
	private final SettingsValue gccPath = new SettingsValue(SettingsCategory.GCC, "GCCPath", "Cesta k p�eklada�i GCC", ValueType.STRING, "./gcc/");
	private final SettingsValue GCCFinalExecutable = new SettingsValue(SettingsCategory.GCC, "GCCFinalExecutable", "N�zev v�sledn�ho souboru p�elo�en�ho pomoc� GCC", ValueType.STRING, "run.exe");
	private final SettingsValue gccExecParams = new SettingsValue(SettingsCategory.GCC, "GCCExecParams", "Dodate�n� argumenty pro p�eklada� GCC", ValueType.STRING_ARRAY, new String[] {});
	private final SettingsValue golinkexecutable = new SettingsValue(SettingsCategory.NASM, "GoLink", "N�zev souboru p�eklada�e GoLink", ValueType.STRING, "GoLink.exe");
	private final SettingsValue golinkparams = new SettingsValue(SettingsCategory.NASM, "GoLinkParams", "Dodate�n� argumenty pro GoLink", ValueType.STRING_ARRAY, new String[] { "/mix", "msvcrt.dll", "kernel32.dll" });
	private final SettingsValue nasmparams = new SettingsValue(SettingsCategory.NASM, "NasmParams", "Dodate�n� argumenty pro NASM", ValueType.STRING_ARRAY, new String[] { "-f", "win32" });
	private final SettingsValue nasmExecParams = new SettingsValue(SettingsCategory.NASM, "ASMExecParams", "Dodate�n� argumenty pro sput�n� v�sledn�ho programu", ValueType.STRING_ARRAY, new String[] {});
	private final SettingsValue nasmPath = new SettingsValue(SettingsCategory.NASM, "nasmPath", "Cesta k p�eklada�i NASM", ValueType.STRING, "");
	private final SettingsValue golinkPath = new SettingsValue(SettingsCategory.NASM, "GoLinkPath", "Cesta k p�eklada�i GoLink", ValueType.STRING, "");
	private final SettingsValue showUI = new SettingsValue(SettingsCategory.UI, "UI", "Zobrazit UI", ValueType.BOOLEAN, 1);
	private final SettingsValue httpPort = new SettingsValue(SettingsCategory.Server, "port", "Port pro HTTP server", ValueType.INTEGER, 8000);
	private final SettingsValue httpsPort = new SettingsValue(SettingsCategory.Server, "portHTTPS", "Port pro HTTPS server", ValueType.INTEGER, 8443);
	private final SettingsValue passcode = new SettingsValue(SettingsCategory.Server, "passcode", "Lok�ln� heslo pro vzd�len� ovl�d�n�", ValueType.STRING, "abc");
	private final SettingsValue buildThreads = new SettingsValue(SettingsCategory.Server, "buildThreads", "Po�et obslu�n�ch vl�ken", ValueType.INTEGER, 8);
	private final SettingsValue authURL = new SettingsValue(SettingsCategory.Server, "authURL", "Adresa autentiza�n�ho endpointu", ValueType.STRING, null);
	private final SettingsValue main_db = new SettingsValue(SettingsCategory.Server, "main_db", "N�zev souboru b�hov� datab�ze", ValueType.STRING, "data.sqlite");
	private final SettingsValue static_db = new SettingsValue(SettingsCategory.Server, "static_db", "N�zev souboru statick� datab�ze", ValueType.STRING, "static.sqlite");
	private final SettingsValue cookieName = new SettingsValue(SettingsCategory.Server, "cookieName", "Suffix cookie pro ukl�d�n� sezen�", ValueType.STRING, "ISUSession");
	private final SettingsValue authKeyFilename = new SettingsValue(SettingsCategory.Server, "authKeyFilename", "N�zev souboru obsahuj�c� kl�� pro vzd�lenou autentizaci", ValueType.STRING, "enc.key");
	private final SettingsValue noExecPath = new SettingsValue(SettingsCategory.Server, "noExecPath", "Nep�id�vat �plnou cestu k p�elo�en�mu programu pro jeho spu�t�n�", ValueType.BOOLEAN, 0);
	private final SettingsValue onlyUI = new SettingsValue(SettingsCategory.UI, "onlyUI", "Spustit pouze vzd�len� ovl�d�n�", ValueType.BOOLEAN, 0);
	private final SettingsValue onlyUITarget = new SettingsValue(SettingsCategory.UI, "onlyUITarget", "Adresa vzd�len�ho server pro vzd�len� ovl�d�n�", ValueType.STRING, "127.0.0.1");
	private final SettingsValue onlyUITargetPort = new SettingsValue(SettingsCategory.UI, "onlyUITargetPort", "V�choz� port serveru pro vzd�len� ovl�d�n�", ValueType.INTEGER, 8000);
	private final SettingsValue onlyUITargetPasscode = new SettingsValue(SettingsCategory.UI, "onlyUITargetPasscode", "Heslo pro server pro vz�len� ovl�d�n�", ValueType.STRING, "abc");
	private final SettingsValue RemoteUserDatabaseURL = new SettingsValue(SettingsCategory.Server, "RemoteUserDatabaseURL", "Adresa lok�ln�ho serveru pro vzd�len� ovl�d�n�", ValueType.STRING, null);
	private final SettingsValue DefaultUsername = new SettingsValue(SettingsCategory.Server, "DefaultUsername", "V�choz� login", ValueType.STRING, "Anonymous");
	private final SettingsValue DefaultGroup = new SettingsValue(SettingsCategory.Server, "DefaultGroup", "N�zev v�choz� skupiny", ValueType.STRING, "Default");
	private final SettingsValue InitGroupsAndUsers = new SettingsValue(SettingsCategory.Server, "InitGroupsAndUsers", "Inicializovat skupiny (nevyu��vat)", ValueType.BOOLEAN, 0);
	private final SettingsValue nasmTimeout = new SettingsValue(SettingsCategory.NASM, "NasmTimeout", "Timeout pro nasm", ValueType.INTEGER, 5000);
	private final SettingsValue linkTimeout = new SettingsValue(SettingsCategory.NASM, "LinkerTimeout", "Timeout pro linker", ValueType.INTEGER, 5000);
	private final SettingsValue UseSettingsBuilders = new SettingsValue(SettingsCategory.Server, "UseSettingsBuilders", "Pou��vat buildery pro IZP a ISU z nastaven�. Pokud nen� nastaveno, jsou zavedeny wrappery nad datab�z�", ValueType.BOOLEAN, 0);

	private final SettingsValue forceTimeoutOnErrors = new SettingsValue(SettingsCategory.Server, "ForceTimeoutOnErrors", "Timeout p�i odevzd�n� �patn�ho k�du", ValueType.BOOLEAN, 1);

	private final SettingsValue fontSize = new SettingsValue(SettingsCategory.UI, "FontSize", "Velikost fontu pro vzd�len� u�ivatelsk� rozhran�", ValueType.INTEGER, 17);
	private final SettingsValue remoteAuthAPIEndpoint = new SettingsValue(SettingsCategory.Server, "RemoteAuthAPIEndpoint", "URL pro vzd�lenou komunikaci s autentiza�n� slu�bou", ValueType.STRING, "");

	private final SettingsValue SSLRequiresRemotePartX = new SettingsValue(SettingsCategory.Server, "SSLRequiresRemotePart", "Spr�va SSL vy�aduje vzd�lenou slu�bu", ValueType.BOOLEAN, 0);
	private final SettingsValue SSLEmail = new SettingsValue(SettingsCategory.Server, "SSLEmail", "E-mail pro SSL certifik�ty", ValueType.STRING, "");
	private final SettingsValue forceSSL = new SettingsValue(SettingsCategory.Server, "SSLForced", "Vynucovat HTTPS", ValueType.BOOLEAN, 0);

	private final SettingsValue JWTApp = new SettingsValue(SettingsCategory.Server, "JWTApp", "N�zev aplikace pro generov�n� JWT token�", ValueType.STRING, "");
	private final SettingsValue JWTSecret = new SettingsValue(SettingsCategory.Server, "JWTSecret", "Sd�len� tajemstv� pro generov�n� JWT token�", ValueType.STRING, "");

	private final SettingsValue CompressJS = new SettingsValue(SettingsCategory.Server, "CompressJS", "Komprimovat webov� dokumenty", ValueType.BOOLEAN, 0);
	private final SettingsValue RootToolchain = new SettingsValue(SettingsCategory.Server, "RootToolchain", "N�zev ko�enov�ho toolchainu", ValueType.STRING, "root");
	private final SettingsValue RootUser = new SettingsValue(SettingsCategory.Server, "RootUser", "Login ko�enov�ho u�ivatele", ValueType.STRING, "");

	private final SettingsValue JSRedirect = new SettingsValue(SettingsCategory.Server, "JSRedirect", "Pro p�esm�rov�n� vyu��vat Javascript", ValueType.BOOLEAN, 1);

	private List<SettingsValue> settings;

	private Settings() {
		load();
	}

	private enum ValueType {
		STRING, INTEGER, STRING_ARRAY, BOOLEAN
	}

	private enum SettingsCategory {
		NASM("Nastaven� p�eklada�e NASM"), GCC("Nastaven� p�eklada�e GCC"), Server("Nastaven� serveru"), UI("Nastaven� u�ivatelsk�ho rozhran�");

		private final String Description;
		private final String Name;

		private SettingsCategory(String descr) {
			this.Description = descr;
			this.Name = toString();
		}
	}

	private class SettingsValue {
		private final String name;
		private final ValueType type;
		private final Object defaultValue;
		private Object value;
		private final String description;
		private final SettingsCategory category;

		@Override
		public String toString() {
			return "Settings \"" + name + "\"";
		}

		private SettingsValue(SettingsCategory category, String name, String description, ValueType type, Object defaultValue) {
			this.name = name;
			this.type = type;
			this.description = description;
			this.defaultValue = defaultValue;
			this.category = category;
			this.value = defaultValue;
			if (settings == null) {
				settings = new ArrayList<SettingsValue>();
			}
			settings.add(this);
		}

		public String asString() {
			return (String) value;
		}

		public int asInt() {
			return (int) value;
		}

		public String[] asStringArray() {
			return (String[]) value;
		}

		public boolean asBoolean() {
			return (int) value == 1;
		}

		public boolean is(String name) {
			return this.name.equals(name);
		}

		public void set(String value) {
			this.value = parseValue(value, this.defaultValue);
		}

		private String formatValue(Object value) {
			if (type == ValueType.STRING) {
				return asString();
			} else if (type == ValueType.INTEGER || type == ValueType.BOOLEAN) {
				try {
					return asInt() + "";
				} catch (Exception e) {
				}
			} else if (type == ValueType.STRING_ARRAY) {
				String[] data = asStringArray();
				StringBuilder sb = new StringBuilder();
				if (data.length >= 1) {
					sb.append(data[0]);
				}
				for (int i = 1; i < data.length; i++) {
					sb.append(",");
					sb.append(data[i]);
				}
				return sb.toString();
			}
			return "";
		}

		private Object parseValue(String value, Object defaultValue) {
			if (type == ValueType.STRING) {
				return value;
			} else if (type == ValueType.INTEGER || type == ValueType.BOOLEAN) {
				try {
					return Integer.parseInt(value);
				} catch (Exception e) {
				}
			} else if (type == ValueType.STRING_ARRAY) {
				if (value.length() > 0) {
					return value.split(",");
				} else {
					return new String[0];
				}
			}
			return defaultValue;
		}
	}

	public static void reload() {
		instance.load();
	}

	public static JsonObject get() {
		JsonObject obj = new JsonObject();
		for (SettingsValue val : instance.settings) {
			JsonArray arr;
			if (obj.containsArray(val.category.Name)) {
				arr = obj.getArray(val.category.Name);
			} else {
				arr = new JsonArray();
				obj.add(val.category.Name, arr);
			}
			JsonObject s = new JsonObject();
			s.add("Name", val.name);
			s.add("Description", val.description);
			s.add("Default", val.formatValue(val.defaultValue));
			s.add("Value", val.formatValue(val.value));
			arr.add(s);
		}
		return obj;
	}

	public static boolean set(JsonObject obj) {
		Map<SettingsValue, String> toSet = new HashMap<>();
		Map<String, SettingsCategory> cats = new HashMap<>();
		Map<String, SettingsValue> sets = new HashMap<>();
		for (SettingsCategory cat : SettingsCategory.values()) {
			cats.put(cat.Name, cat);
		}
		for (SettingsValue val : instance.settings) {
			sets.put(val.name, val);
		}

		for (Entry<String, JsonValue> entry : obj.getEntries()) {
			String cat = entry.getKey();
			if (!cats.containsKey(cat)) {
				return false;
			}
			if (!entry.getValue().isArray()) {
				return false;
			}
			for (JsonValue val : entry.getValue().asArray().Value) {
				if (!val.isObject()) {
					return false;
				}
				JsonObject o = val.asObject();
				if (!o.containsString("Name") || !o.containsString("Value")) {
					return false;
				}
				String name = o.getString("Name").Value;
				String value = o.getString("Value").Value;
				if (!sets.containsKey(name)) {
					return false;
				}
				SettingsValue set = sets.get(name);
				Object v = set.parseValue(value, null);
				if (v == null) {
					return false;
				}
				if (toSet.containsKey(set)) {
					return false;
				}
				toSet.put(set, value);
			}
		}

		for (Entry<SettingsValue, String> entry : toSet.entrySet()) {
			SettingsValue set = entry.getKey();
			String value = entry.getValue();
			set.set(value);
		}

		instance.save();
		return true;
	}

	private final Object fileSyncer = new Object();

	private void save() {
		synchronized (fileSyncer) {
			StringBuilder newLines = new StringBuilder();

			String set;
			try {
				set = MyFS.readFile("settings.ini");
			} catch (FileReadException e) {
				throw new RuntimeException(e);
			}
			String[] lines = set.split("\n");
			for (String line : lines) {
				String[] l = line.trim().split(":", 2);
				if (l.length == 2) {
					String key = l[0].trim();
					String val = l[1].trim();
					for (SettingsValue setting : settings) {
						if (setting.is(key)) {
							val = setting.formatValue(setting.value);
							break;
						}
					}
					newLines.append(key + ": " + val);
					newLines.append("\r\n");
				} else {
					newLines.append(line);
					newLines.append("\r\n");
				}
			}
			try {
				MyFS.writeFile("settings.ini", newLines.toString().trim());
			} catch (FileWriteException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void load() {
		synchronized (fileSyncer) {
			String set;
			try {
				set = MyFS.readFile("settings.ini");
			} catch (FileReadException e) {
				throw new RuntimeException(e);
			}
			String[] lines = set.split("\n");
			for (String line : lines) {
				String[] l = line.trim().split(":", 2);
				if (l.length == 2) {
					String key = l[0].trim();
					String val = l[1].trim();
					for (SettingsValue setting : settings) {
						if (setting.is(key)) {
							setting.set(val);
							break;
						}
					}
				}
			}
		}
	}

	private static Settings instance = new Settings();

	public static String getObjectFileName() {
		return instance.objName.asString();
	}

	public static String getExecutableFileName() {
		return instance.rtName.asString();
	}

	public static String getNasmExecutableName() {
		return instance.nasmExecutable.asString();
	}

	public static String getGoLinkExecutableName() {
		return instance.golinkexecutable.asString();
	}

	public static String[] getNasmExecutableParams() {
		return instance.nasmparams.asStringArray();
	}

	public static String[] getGoLinkExecutableParams() {
		return instance.golinkparams.asStringArray();
	}

	public static String[] getAsmExecutableRunnerParams() {
		return instance.nasmExecParams.asStringArray();
	}

	public static String[] getGccExecutableRunnerParams() {
		return instance.gccExecParams.asStringArray();
	}

	public static boolean showUI() {
		return instance.showUI.asBoolean();
	}

	public static String getNasmPath() {
		return instance.nasmPath.asString();
	}

	public static String getGoLinkPath() {
		return instance.golinkPath.asString();
	}

	public static int GetHTTPServerPort() {
		return instance.httpPort.asInt();
	}

	public static int GetHTTPSServerPort() {
		return instance.httpsPort.asInt();
	}

	public static String getPasscode() {
		return instance.passcode.asString();
	}

	public static int getBuildersCount() {
		return instance.buildThreads.asInt();
	}

	public static boolean isAuth() {
		return instance.authURL.asString() != null;
	}

	public static String getAuthURL(String protocol, String host) {
		if (Settings.ForceSSL()) {
			protocol = "https";
		} else {
			protocol = protocol.split("/")[0].toLowerCase().trim();
		}
		char c = Settings.DoJsRedirect() ? 'J' : 'P';
		@SuppressWarnings("deprecation")
		String ret = Base64.encode((c + protocol + "://" + host).getBytes(Settings.getDefaultCharset()));
		return instance.authURL.asString() + "?return=" + ret.replaceAll("=", "");
	}

	public static String getMainDB() {
		return instance.main_db.asString();
	}

	public static String getStaticDB() {
		return instance.static_db.asString();
	}

	public static String getCookieName(Toolchain toolchain) {
		return toolchain.getName() + "_" + instance.cookieName.asString();
	}

	public static String getAuthKeyFilename() {
		return instance.authKeyFilename.asString();
	}

	public static boolean hasNoExecPath() {
		return instance.noExecPath.asBoolean();
	}

	public static Charset getDefaultCharset() {
		return Charset.forName("UTF-8");
	}

	public static boolean RunOnlyUI() {
		return instance.onlyUI.asBoolean();
	}

	public static String GetOnlyUIAddress() {
		return instance.onlyUITarget.asString();
	}

	public static int GetOnlyUIPort() {
		return instance.onlyUITargetPort.asInt();
	}

	public static String GetOnlyUIPasscode() {
		return instance.onlyUITargetPasscode.asString();
	}

	public static boolean UsersRemoteUserDB() {
		return instance.RemoteUserDatabaseURL.asString() != null;
	}

	public static String GetRemoteUserDB() {
		return instance.RemoteUserDatabaseURL.asString();
	}

	public static String GetDefaultUsername() {
		return instance.DefaultUsername.asString();
	}

	public static String GetDefaultGroup() {
		return instance.DefaultGroup.asString();
	}

	public static boolean GetInitGroupsAndUsers() {
		return instance.InitGroupsAndUsers.asBoolean();
	}

	public static int getNasmTimeout() {
		return instance.nasmTimeout.asInt();
	}

	public static String getGCCPath() {
		return instance.gccPath.asString();
	}

	public static String getGCCExecutable() {
		return instance.gccExecutable.asString();
	}

	public static int getLinkerTimeout() {
		return instance.linkTimeout.asInt();
	}

	public static int getFontSize() {
		return instance.fontSize.asInt();
	}

	public static String getRemoteAuthAPIEndpoint() {
		return instance.remoteAuthAPIEndpoint.asString();
	}

	public static boolean getForceTimeoutOnErrors() {
		return instance.forceTimeoutOnErrors.asBoolean();
	}

	public static String getGCCFilalExecutable() {
		return instance.GCCFinalExecutable.asString();
	}

	public static boolean SSLRequiresRemotePart() {
		return instance.SSLRequiresRemotePartX.asBoolean();
	}

	public static String getSSLEmail() {
		return instance.SSLEmail.asString();
	}

	public static boolean ForceSSL() {
		return instance.forceSSL.asBoolean();
	}

	public static String getJWTApp() {
		return instance.JWTApp.asString();
	}

	public static String getJWTSecret() {
		return instance.JWTSecret.asString();
	}

	public static boolean DoJSCompression() {
		return instance.CompressJS.asBoolean();
	}

	public static String getRootToolchain() {
		return instance.RootToolchain.asString();
	}

	public static String getRootUser() {
		return instance.RootUser.asString();
	}

	public static boolean DoJsRedirect() {
		return instance.JSRedirect.asBoolean();
	}

	public static boolean GetUseSettingsBuilders() {
		return instance.UseSettingsBuilders.asBoolean();
	}
}
