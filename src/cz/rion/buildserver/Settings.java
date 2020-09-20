package cz.rion.buildserver;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class Settings {

	private final SettingsValue objName = new SettingsValue("objFile", ValueType.STRING, "code.obj");
	private final SettingsValue rtName = new SettingsValue("execFile", ValueType.STRING, "code.exe");
	private final SettingsValue nasmExecutable = new SettingsValue("nasm", ValueType.STRING, "nasm.exe");
	private final SettingsValue gccExecutable = new SettingsValue("GCCName", ValueType.STRING, "gcc.exe");
	private final SettingsValue gccPath = new SettingsValue("GCCPath", ValueType.STRING, "./gcc/");
	private final SettingsValue golinkexecutable = new SettingsValue("GoLink", ValueType.STRING, "GoLink.exe");
	private final SettingsValue golinkparams = new SettingsValue("GoLinkParams", ValueType.STRING_ARRAY, new String[] { "/mix", "msvcrt.dll", "kernel32.dll" });
	private final SettingsValue nasmparams = new SettingsValue("NasmParams", ValueType.STRING_ARRAY, new String[] { "-f", "win32" });
	private final SettingsValue execParams = new SettingsValue("ExecParams", ValueType.STRING_ARRAY, new String[] {});
	private final SettingsValue nasmPath = new SettingsValue("nasmPath", ValueType.STRING, "");
	private final SettingsValue golinkPath = new SettingsValue("GoLinkPath", ValueType.STRING, "");
	private final SettingsValue showUI = new SettingsValue("UI", ValueType.BOOLEAN, 1);
	private final SettingsValue httpPort = new SettingsValue("port", ValueType.INTEGER, 8000);
	private final SettingsValue passcode = new SettingsValue("passcode", ValueType.STRING, "abc");
	private final SettingsValue buildThreads = new SettingsValue("buildThreads", ValueType.INTEGER, 8);
	private final SettingsValue authURL = new SettingsValue("authURL", ValueType.STRING, null);
	private final SettingsValue main_db = new SettingsValue("main_db", ValueType.STRING, "data.sqlite");
	private final SettingsValue static_db = new SettingsValue("main_db", ValueType.STRING, "static.sqlite");
	private final SettingsValue cookieName = new SettingsValue("cookieName", ValueType.STRING, "ISUSession");
	private final SettingsValue authKeyFilename = new SettingsValue("authKeyFilename", ValueType.STRING, "enc.key");
	private final SettingsValue noExecPath = new SettingsValue("noExecPath", ValueType.BOOLEAN, 0);
	private final SettingsValue onlyUI = new SettingsValue("onlyUI", ValueType.BOOLEAN, 0);
	private final SettingsValue onlyUITarget = new SettingsValue("onlyUITarget", ValueType.STRING, "127.0.0.1");
	private final SettingsValue onlyUITargetPort = new SettingsValue("onlyUITargetPort", ValueType.INTEGER, 8000);
	private final SettingsValue onlyUITargetPasscode = new SettingsValue("onlyUITargetPasscode", ValueType.STRING, "abc");
	private final SettingsValue RemoteUserDatabaseURL = new SettingsValue("RemoteUserDatabaseURL", ValueType.STRING, null);
	private final SettingsValue DefaultUsername = new SettingsValue("DefaultUsername", ValueType.STRING, "Anonymous");
	private final SettingsValue DefaultGroup = new SettingsValue("DefaultGroup", ValueType.STRING, "Default");
	private final SettingsValue InitGroupsAndUsers = new SettingsValue("InitGroupsAndUsers", ValueType.BOOLEAN, 0);
	private final SettingsValue nasmTimeout = new SettingsValue("NasmTimeout", ValueType.INTEGER, 5000);
	private final SettingsValue linkTimeout = new SettingsValue("LinkerTimeout", ValueType.INTEGER, 5000);

	private final SettingsValue forceTimeoutOnErrors = new SettingsValue("ForceTimeoutOnErrors", ValueType.BOOLEAN, 1);

	private final SettingsValue fontSize = new SettingsValue("FontSize", ValueType.INTEGER, 17);
	private final SettingsValue remoteAuthAPIEndpoint = new SettingsValue("RemoteAuthAPIEndpoint", ValueType.STRING, "");

	private List<SettingsValue> settings;

	private Settings() {
		load();
	}

	private enum ValueType {
		STRING, INTEGER, STRING_ARRAY, BOOLEAN
	}

	private class SettingsValue {
		private final String name;
		private final ValueType type;
		private final Object defaultValue;
		private Object value;

		private SettingsValue(String name, ValueType type, Object defaultValue) {
			this.name = name;
			this.type = type;
			this.defaultValue = defaultValue;
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
			this.value = parseValue(value);
		}

		private Object parseValue(String value) {
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

	private void load() {

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

	public static String[] getGExecutableParams() {
		return instance.execParams.asStringArray();
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

	public static String getPasscode() {
		return instance.passcode.asString();
	}

	public static int getBuildersCount() {
		return instance.buildThreads.asInt();
	}

	public static boolean isAuth() {
		return instance.authURL.asString() != null;
	}

	public static String getAuthURL() {
		return instance.authURL.asString();
	}

	public static String getMainDB() {
		return instance.main_db.asString();
	}

	public static String getStaticDB() {
		return instance.static_db.asString();
	}

	public static Object getCookieName() {
		return instance.cookieName.asString();
	}

	public static String getAuthKeyFilename() {
		return instance.authKeyFilename.asString();
	}

	public static boolean hasNoExecPath() {
		return instance.noExecPath.asBoolean();
	}

	public static Charset getDefaultCharset() {
		return Charset.forName("windows-1250");
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
}
