package cz.rion.buildserver.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile.VirtualFileException;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.test.JsonTest.TestConfiguration;
import cz.rion.buildserver.test.JsonTest.TestVerificationData;
import cz.rion.buildserver.test.targets.AsmTest;
import cz.rion.buildserver.test.targets.GCCTest;
import cz.rion.buildserver.utils.Pair;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class JsonTestManager {

	private static void collectDir(File file, Collection<File> all) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				all.add(child);
				collectDir(child, all);
			}
		}
	}

	private static void fillFSTests(List<Pair<String, JsonObject>> data, String directory) {
		Collection<File> all = new ArrayList<File>();
		try {
			collectDir(new File(directory), all);
		} catch (Exception | Error e) {
		}
		for (File f : all) {
			if (f.getAbsolutePath().toLowerCase().endsWith(".json")) {
				String test;
				try {
					test = MyFS.readFile(f.getAbsolutePath());
				} catch (FileReadException e) {
					continue;
				}
				JsonValue val = JsonValue.parse(test);
				if (val != null) {
					if (val.isObject()) {
						data.add(new Pair<>(f.getAbsolutePath(), val.asObject()));
					}
				}
			}
		}
	}

	private static void fillDBTests(VirtualFileManager files, List<Pair<String, JsonObject>> data, UserContext context) {
		List<VirtualFile> fileLst = new ArrayList<>();
		files.getFiles(fileLst, context);

		for (VirtualFile file : fileLst) {
			String fname = file.Name;
			if (fname.startsWith("tests/") && fname.endsWith(".json")) {
				try {
					String contents = file.read(context);
					if (contents != null) {
						JsonValue val = JsonValue.parse(contents);
						if (val != null) {
							if (val.isObject()) {
								data.add(new Pair<>(file.Name, val.asObject()));
							}
						}
					}
				} catch (VirtualFileException e) {
					continue;
				}
			}
		}
	}

	private static GenericTest ConvertJsonToTest(String path, JsonObject obj, StaticDB sdb, VirtualFileManager files) {
		// Get category name. Find last "tests/" from the end
		String[] tmp = path.split("tests\\/");
		String category = tmp[tmp.length - 1].split("\\/")[0];
		Toolchain tc = null;
		try {
			tc = sdb.getToolchain(category, false);
		} catch (NoSuchToolchainException e) {
		}
		if (tc != null) {

			if (obj.containsString("id") && obj.containsString("description") && obj.containsString("title") && obj.containsString("type")) {

				if (obj.containsArray("tests")) {
					List<JsonValue> tests = obj.getArray("tests").Value;
					List<TestVerificationData> tvd = new ArrayList<>();
					boolean testOk = true;
					for (JsonValue tst : tests) {
						if (!tst.isObject()) {
							testOk = false;
							break;
						}
						JsonObject tsto = tst.asObject();
						if (tsto.containsString("stdin") && tsto.containsString("stdout") && tsto.containsNumber("code") && tsto.containsString("stderr") && tsto.containsNumber("timeout")) {
							String stdin = tsto.getString("stdin").Value;
							String stdout = tsto.getString("stdout").Value;
							String stderr = tsto.getString("stderr").Value;
							int timeout = tsto.getNumber("timeout").Value;
							int code = tsto.getNumber("code").Value;
							String[] arguments = new String[0];
							if (tsto.containsArray("arguments")) {
								JsonArray args = tsto.getArray("arguments").asArray();
								int argsNum = 0;
								for (JsonValue arg : args.Value) {
									if (arg.isString() || arg.isBoolean() || arg.isNumber()) {
										argsNum++;
									}
								}
								arguments = new String[argsNum];
								int argsI = 0;
								for (JsonValue arg : args.Value) {
									if (arg.isString() || arg.isBoolean() || arg.isNumber()) {
										arguments[argsI] = arg.isString() ? arg.asString().Value : arg.getJsonString();
										argsI++;
									}
								}
							}
							boolean base = false;
							if (tsto.containsNumber("base")) {
								base = tsto.getNumber("base").Value == 1;
							}
							tvd.add(new TestVerificationData(stdin, stdout, stderr, code, timeout, base, arguments));
						} else {
							testOk = false;
							break;
						}
					}
					if (!testOk) {
						return null;
					}

					Set<String> priorTests = new HashSet<>();
					if (obj.containsArray("after")) {
						for (JsonValue afterVal : obj.getArray("after").Value) {
							if (!afterVal.isString()) {
								return null;
							} else {
								priorTests.add(afterVal.asString().Value);
							}
						}
					}

					String id = obj.getString("id").Value;
					String descr = obj.getString("description").Value;
					String title = obj.getString("title").Value;
					String type = obj.getString("type").Value;

					String initial = obj.containsString("init") ? obj.getString("init").Value : "";
					boolean hidden = obj.containsNumber("hidden") ? obj.getNumber("hidden").Value == 1 : false;
					boolean secret = obj.containsNumber("secret") ? obj.getNumber("secret").Value == 1 : false;
					boolean confetty = obj.containsNumber("confetty") ? obj.getNumber("secret").Value == 1 : true;

					String builder = obj.containsString("builder") ? obj.getString("builder").Value : tc.getName();

					List<GenericTestWindow> windowData = new ArrayList<>();
					if (obj.containsObject("windows")) {
						for (Entry<String, JsonValue> entry : obj.getObject("windows").getEntries()) {
							JsonValue val = entry.getValue();
							if (val.isObject()) {
								JsonObject o = val.asObject();
								if (o.containsString("title") && o.containsString("contents") && o.containsString("label")) {
									String wid = entry.getKey();
									String wlabel = o.getString("label").Value;
									String wcontents = o.getString("contents").Value;
									String wtitle = o.getString("title").Value;
									windowData.add(new GenericTestWindow(wid, wtitle, wcontents, wlabel));
								}
							}
						}
					}

					TestConfiguration config = new TestConfiguration(tc, sdb, tvd, id, files, title, descr, initial, hidden, secret, priorTests, builder, windowData, confetty);
					if (type.equals("asm")) {
						return AsmTest.get(config, obj);
					} else if (type.equals("gcc")) {
						return GCCTest.get(config, obj);
					}
				}
			}
		}
		return null;
	}

	public static List<GenericTest> load(VirtualFileManager files, StaticDB sdb, String testDirectory, UserContext context) {
		List<GenericTest> lst = new ArrayList<>();
		List<Pair<String, JsonObject>> tests = new ArrayList<>();
		fillFSTests(tests, testDirectory);
		fillDBTests(files, tests, context);
		for (Pair<String, JsonObject> obj : tests) {
			GenericTest test = ConvertJsonToTest(obj.Key, obj.Value, sdb, files);
			if (test != null) {
				lst.add(test);
			}
		}
		return lst;
	}

}
