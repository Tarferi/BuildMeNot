package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredImportDB extends LayeredVirtualFilesDB {

	private final Object syncer = new Object();

	private static class ImportMetaFile implements VirtualFile {

		private final String name;
		private String value = "";
		private final Object syncer;

		public ImportMetaFile(Object syncer, String name) {
			this.name = name;
			this.syncer = syncer;

			String[] cname = name.split("\\/");
			String aname = cname[cname.length - 1];
			try {
				write(MyFS.readFile(aname + ".txt"));
			} catch (FileReadException e) {
			}
		}

		@Override
		public String read() {
			synchronized (syncer) {
				return value;
			}
		}

		@Override
		public void write(String data) {
			synchronized (syncer) {
				value = data;
			}
		}

		@Override
		public String getName() {
			return name;
		}

	}

	private class InternalResultUser {
		public final String login;
		public final String origin;
		public final List<String> groups = new ArrayList<>();
		public String name;

		private InternalResultUser(String login, String origin, String name) {
			this.login = login;
			this.origin = origin;
			this.groups.add("Everyone");
			this.name = name;
		}

		public void updateName(String name) {
			// this.name = name;
		}

		public void addGroup(String group) {
			if (!this.groups.contains(group)) {
				this.groups.add(group);
			}
		}
	}

	private class ImportMetaFunctioningFile extends ImportMetaFile {

		private final ImportMetaFile users;
		private final ImportMetaFile groups;
		private final Object syncer;

		private class InternalPasswdUser {
			public final String login;
			public final String name;
			public final String group;

			public InternalPasswdUser(String login, String name, String group) {
				this.login = login;
				this.name = name;
				this.group = group;
			}
		}

		private class InternalVariant {
			public final int ID;
			public final String name;

			private InternalVariant(int id, String name) {
				this.ID = id;
				this.name = name;
			}
		}

		private class InternalEnrollment {
			public final int VariantID;
			public final String login;
			public final String name;

			private InternalEnrollment(int varID, String login, String name) {
				this.VariantID = varID;
				this.login = login;
				this.name = name;
			}
		}

		private class InternalTeacher {
			public final String login;
			public final String name;

			private InternalTeacher(String login, String name) {
				this.login = login;
				this.name = name;
			}
		}

		private class InternalCourse {

			public final List<InternalResultUser> results = new ArrayList<>();

			public InternalCourse(String courseName, String courseYear, String studentsGroupPrefix, String teacherGroupPrefix, String regexVarNameFrom, String regexVarNameTo, Map<String, InternalPasswdUser> pwd, String groupsJSN) {
				List<InternalTeacher> teachers = new ArrayList<>();
				List<InternalEnrollment> enrollemnts = new ArrayList<>();
				Map<Integer, InternalVariant> variants = new HashMap<>();

				Map<String, InternalResultUser> result = new HashMap<>();

				// Add all currently known
				for (Entry<String, InternalPasswdUser> pw : pwd.entrySet()) {
					result.put(pw.getKey(), new InternalResultUser(pw.getValue().login, pw.getValue().group, pw.getValue().name));
				}
				boolean ok = true;

				JsonValue val = JsonValue.parse(groupsJSN);
				if (val == null) {
					try {
						MyFS.writeFile("test.txt", groupsJSN);
					} catch (FileWriteException e) {
						e.printStackTrace();
					}
					ok = false;
				} else {
					if (val.isArray()) {
						JsonArray arr = val.asArray();
						if (arr.Value.size() == 3) {
							JsonValue jvariants = arr.Value.get(0);
							JsonValue jenrollments = arr.Value.get(1);
							JsonValue jteachers = arr.Value.get(2);
							if (jvariants.isObject() && jenrollments.isObject() && jteachers.isObject()) {
								JsonObject avariants = jvariants.asObject();
								JsonObject aenrollments = jenrollments.asObject();
								JsonObject ateachers = jteachers.asObject();
								if (avariants.containsArray("data") && aenrollments.containsArray("data") && ateachers.containsArray("data")) {

									final Pattern pattern = Pattern.compile(regexVarNameFrom, Pattern.MULTILINE);
									for (JsonValue var : avariants.getArray("data").Value) {
										if (!var.isObject()) {
											ok = false;
										}
										JsonObject obj = var.asObject();
										if (obj.containsString("title") && obj.containsNumber("id")) {
											String title = obj.getString("title").Value;
											final Matcher matcher = pattern.matcher(title);
											title = matcher.replaceAll(regexVarNameTo);
											int id = obj.getNumber("id").Value;
											variants.put(id, new InternalVariant(id, title));
										} else {
											ok = false;
										}
									}

									for (JsonValue var : aenrollments.getArray("data").Value) {
										if (!var.isObject()) {
											ok = false;
										}
										JsonObject obj = var.asObject();
										if (obj.containsString("name") && obj.containsNumber("var_id") && obj.containsString("email")) {
											int var_id = obj.getNumber("var_id").Value;
											String name = obj.getString("name").Value;
											String email = obj.getString("email").Value;
											String login = email.split("@")[0];
											enrollemnts.add(new InternalEnrollment(var_id, login, name));
										} else {
											ok = false;
										}
									}

									for (JsonValue var : ateachers.getArray("data").Value) {
										if (!var.isObject()) {
											ok = false;
										}
										JsonObject obj = var.asObject();
										if (obj.containsString("name") && obj.containsString("email")) {
											String name = obj.getString("name").Value;
											String email = obj.getString("email").Value;
											String login = email.split("@")[0];
											teachers.add(new InternalTeacher(login, name));
										} else {
											ok = false;
										}
									}
								}
							}
						}
					}
				}
				// Update names
				for (InternalTeacher teacher : teachers) {
					if (result.containsKey(teacher.login)) {
						InternalResultUser r = result.get(teacher.login);
						r.updateName(teacher.name);
						r.addGroup(courseName + "." + courseYear + "." + teacherGroupPrefix);
					} else {
						ok = false;
					}
				}
				for (InternalEnrollment student : enrollemnts) {
					if (result.containsKey(student.login)) {
						InternalResultUser r = result.get(student.login);
						r.updateName(student.name);
						if (variants.containsKey(student.VariantID)) {
							r.addGroup(courseName + "." + courseYear + "." + studentsGroupPrefix + variants.get(student.VariantID).name);
						} else {
							ok = false;
						}
					} else {
						ok = false;
					}
				}

				if (ok) {
					for (Entry<String, InternalResultUser> res : result.entrySet()) {
						results.add(res.getValue());
					}
				}
			}
		}

		public ImportMetaFunctioningFile(Object syncer, String name, ImportMetaFile users, ImportMetaFile groups) {
			super(syncer, name);
			this.users = users;
			this.groups = groups;
			this.syncer = syncer;
			writeEmpty();
		}

		public void writeEmpty() {
			synchronized (syncer) {
				StringBuilder sb = new StringBuilder();
				sb.append("# Import novych uzivatelu. Tohle smaze vsechny existujici uzivatele!\n");
				sb.append("# Pred importem vypln nasledujici udaje\n\n");
				sb.append("# Regularni vyraz, kterym se aplikuje na nazvy variant\n");
				sb.append("RegexGroupSearch: tpl ([\\S]+) (\\d+):(\\d+) *- *(\\d+):(\\d+), lab. (\\S+), .+\n");
				sb.append("# Regularni vyraz, kterym se provede nahrazeni vyberu predchozim vyrazem\n");
				sb.append("RegexGroupReplace: $6_$2:$3\n");
				sb.append("# Skolni rok (stane se soucasti retezce skupin\n");
				sb.append("CourseYear: 2020\n");
				sb.append("# Nazev predmetu (stane se soucasti retezce skupin\n");
				sb.append("CourseName: IZP\n");
				sb.append("# Predpona studentskeho retezce skupin\n");
				sb.append("StudentsGroupPrefix: students.\n");
				sb.append("# Predpona uceitelskeho retezce skupin\n");
				sb.append("TeachersGroupPrefix: teachers\n");
				sb.append("# Toolchain pro uzivatele\n");
				sb.append("Toolchain: IZP\n");
				sb.append("\n");
				sb.append("\n");
				sb.append("# Jakmile je vse OK, odkomunetuj nasledujici radek. Pokud chce zacit znovu, uloz prazdny soubor\n");
				sb.append("#confirm\n");
				super.write(sb.toString());
			}
		}

		@Override
		public void write(String data) {
			synchronized (syncer) {
				String[] lines = data.split("\n");
				Map<String, String> map = new HashMap<>();
				int lineNum = 0;
				boolean hasConfirm = false;
				if (data.trim().isEmpty()) {
					writeEmpty();
					return;
				}
				for (String line : lines) {
					String[] parts = line.trim().split(":", 2);
					if (line.equals("confirm")) {
						hasConfirm = true;
					} else if (parts.length != 2 && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
						super.write("Unexpected line: " + lineNum);
						return;
					} else if (parts.length == 2) {
						map.put(parts[0].toLowerCase(), parts[1].trim());
					}
					lineNum++;
				}
				if (!hasConfirm) {
					super.write(data);
				} else if (map.containsKey("RegexGroupSearch".toLowerCase()) && map.containsKey("RegexGroupReplace".toLowerCase()) && map.containsKey("CourseName".toLowerCase()) && map.containsKey("CourseYear".toLowerCase()) && map.containsKey("StudentsGroupPrefix".toLowerCase()) && map.containsKey("TeachersGroupPrefix".toLowerCase()) && map.containsKey("Toolchain".toLowerCase())) {
					String RegexGroupSearch = map.get("RegexGroupSearch".toLowerCase());
					String RegexGroupReplace = map.get("RegexGroupReplace".toLowerCase());
					String CourseName = map.get("CourseName".toLowerCase());
					String CourseYear = map.get("CourseYear".toLowerCase());
					String StudentsGroupPrefix = map.get("StudentsGroupPrefix".toLowerCase());
					String TeachersGroupPrefix = map.get("TeachersGroupPrefix".toLowerCase());
					String Toolchain = map.get("Toolchain".toLowerCase());

					StringBuilder processLog = new StringBuilder();
					List<InternalResultUser> loadedUsers = process(data, CourseName, CourseYear, RegexGroupSearch, RegexGroupReplace, StudentsGroupPrefix, TeachersGroupPrefix, processLog);
					if (loadedUsers == null) {
						super.write("# Import failed\n\n\n" + processLog + "\n\n========= Original Data ===========" + data);
					} else {
						super.write(replaceUsers(loadedUsers, Toolchain) + "\n\n\n ====== Protocol =======\n" + processLog.toString());
					}
				} else {
					super.write("# Missing some mandatory fields. Save as empty file to reset\n" + data);
				}
			}
		}

		private List<InternalResultUser> process(String originalData, String courseName, String courseYear, String regexGroupSearch, String regexGroupReplace, String studentsGroupPrefix, String teachersGroupPrefix, StringBuilder sb) {
			Map<String, InternalPasswdUser> users = getPasswdUsers();
			InternalCourse course = new InternalCourse(courseName, courseYear, studentsGroupPrefix, teachersGroupPrefix, regexGroupSearch, regexGroupReplace, users, this.users.read());
			if (course.results.isEmpty()) {
				return null;
			} else {
				sb.append("Loaded " + course.results.size() + " users\n\n");
				for (InternalResultUser user : course.results) {
					sb.append("\t" + user.login + ": " + user.name + " (" + user.origin + "):\n");
					for (String group : user.groups) {
						sb.append("\t\t" + group + "\n");
					}
				}
				sb.append("\n\n\n\n ========= Original Data ============\n\n" + originalData);
				return course.results;
			}
		}

		private Map<String, InternalPasswdUser> getPasswdUsers() {
			Map<String, InternalPasswdUser> ret = new HashMap<>();
			String[] usersStr = groups.read().split("\n");
			for (String userStr : usersStr) {
				String[] parts = userStr.trim().split(":");
				if (parts.length >= 6) {
					String login = parts[0];
					String[] nameParts = parts[4].split(",");
					if (nameParts.length >= 2) {
						String name = nameParts[0];
						String group = nameParts[1];
						ret.put(login, new InternalPasswdUser(login, name, group));
					}
				}
			}
			return ret;
		}
	}

	public LayeredImportDB(String fileName) throws DatabaseException {
		super(fileName);
		ImportMetaFile UsersFile = new ImportMetaFile(syncer, "importy/Users.db");
		ImportMetaFile GroupsFile = new ImportMetaFile(syncer, "importy/Groups.db");
		ImportMetaFunctioningFile ImportFile = new ImportMetaFunctioningFile(syncer, "importy/Import.exe", UsersFile, GroupsFile);
		super.registerVirtualFile(UsersFile);
		super.registerVirtualFile(GroupsFile);
		super.registerVirtualFile(ImportFile);
	}

	public abstract boolean clearUsers(String toolchain);

	public abstract boolean createUser(String toolchain, String login, String origin, String fullName, List<String> permissionGroups);

	private String replaceUsers(List<InternalResultUser> users, String toolchain) {
		if (!clearUsers(toolchain)) {
			return "Failed to purge user database";
		}
		for (InternalResultUser user : users) {
			if (!createUser(toolchain, user.login, user.origin, user.name, user.groups)) {
				return "Failed to create user(s)";
			}
		}
		return "Users imported and loaded";
	}
}
