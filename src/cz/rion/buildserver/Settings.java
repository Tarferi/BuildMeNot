package cz.rion.buildserver;

import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class Settings {

	private final String objName;
	private final String rtName;
	private final String nasmExecutable;
	private final String golinkexecutable;
	private final String[] golinkparams;
	private final String[] nasmparams;
	private final String nasmPath;
	private final String golinkPath;
	private final boolean showUI;
	private final int httpPort;

	private Settings(String objExt, String rtExt, String nasm, String golink, String[] golinkparams, String[] nasmparams, boolean showUI, String nasmPath, String golinkPath, int httpPort) {
		this.objName = objExt;
		this.rtName = rtExt;
		this.nasmExecutable = nasm;
		this.golinkexecutable = golink;
		this.golinkparams = golinkparams;
		this.nasmparams = nasmparams;
		this.showUI = showUI;
		this.nasmPath = nasmPath;
		this.golinkPath = golinkPath;
		this.httpPort = httpPort;
	}

	private static Settings load() {
		String objName = "code.obj";
		String rtName = "code.exe";
		String nasm = "nasm.exe";
		String golink = "GoLink.exe";
		String nasmPath = "";
		String golinkPath = "";
		int httpPort = 8000;

		boolean showUI = true;
		String[] golinkparams = new String[] { "/mix", "msvcrt.dll", "kernel32.dll" };
		String[] nasmparams = new String[] { "-f", "win32" };
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
				if (key.equals("obj-ext")) {
					objName = val;
				} else if (key.equals("exec-ext")) {
					rtName = val;
				} else if (key.equals("nasm")) {
					nasm = val;
				} else if (key.equals("GoLink")) {
					golink = val;
				} else if (key.equals("GoLinkParams")) {
					if (val.length() > 0) {
						golinkparams = val.split(",");
					} else {
						golinkparams = new String[0];
					}
				} else if (key.equals("NasmParams")) {
					if (val.length() > 0) {
						nasmparams = val.split(",");
					} else {
						nasmparams = new String[0];
					}
				} else if (key.equals("UI")) {
					showUI = val.equals("1");
				} else if (key.equals("nasmPath")) {
					nasmPath = val;
				} else if (key.equals("GoLinkPath")) {
					golinkPath = val;
				} else if (key.equals("port")) {
					try {
						httpPort = Integer.parseInt(val);
					} catch (Exception e) {
					}
				}
			}
		}
		return new Settings(objName, rtName, nasm, golink, golinkparams, nasmparams, showUI, nasmPath, golinkPath, httpPort);
	}

	private static Settings instance = load();

	public static String getObjectFileName() {
		return instance.objName;
	}

	public static String getExecutableFileName() {
		return instance.rtName;
	}

	public static String getNasmExecutableName() {
		return instance.nasmExecutable;
	}

	public static String getGoLinkExecutableName() {
		return instance.golinkexecutable;
	}

	public static String[] getNasmExecutableParams() {
		return instance.nasmparams;
	}

	public static String[] getGoLinkExecutableParams() {
		return instance.golinkparams;
	}

	public static boolean showUI() {
		return instance.showUI;
	}

	public static String getNasmPath() {
		return instance.nasmPath;
	}

	public static String getGoLinkPath() {
		return instance.golinkPath;
	}

	public static int GetHTTPServerPort() {
		return instance.httpPort;
	}
}
