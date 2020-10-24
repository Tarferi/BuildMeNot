package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.ui.events.FileLoadedEvent.FileInfo;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredImportDB extends LayeredVirtualFilesDB {

	private final Object syncer = new Object();

	private Map<String, ImportMetaFile> loadedVirtualFiles = new HashMap<>();

	static interface WritableData {
		void write(String data);
	}

	private class ImportMetaFile implements VirtualFile {

		private final String name;
		private String value = "";
		private final Object syncer;
		private final boolean realFile;
		private final LayeredFilesDB fdb;
		private DatabaseFile realFileObj = null;
		private WritableData onReady;

		public ImportMetaFile(Object syncer, String name, boolean addRealFile, LayeredFilesDB fdb) {
			this.name = name;
			this.syncer = syncer;
			this.realFile = addRealFile;
			this.fdb = fdb;
			if (realFile) {
				loadedVirtualFiles.put(name, this);
			}
		}

		private boolean ensureFile(List<DatabaseFile> files) {
			if (realFile) {
				if (realFileObj != null) {
					return true;
				} else {
					for (DatabaseFile file : files) {
						if (file.FileName.equals(name)) {
							realFileObj = file;
							if (onReady != null) {
								onReady.write(read());
								onReady = null;
							}
							return true;
						}
					}
					try {
						fdb.createFile(name, "", true);
					} catch (DatabaseException e) {
						e.printStackTrace();
					}
					return false;
				}
			} else {
				if (onReady != null) {
					onReady.write(read());
					onReady = null;
				}
				return true;
			}
		}

		public void writeWhenReady(WritableData onReady) {
			if (realFile && realFileObj == null) {
				this.onReady = onReady;
			} else {
				onReady.write(read());
			}
		}

		@Override
		public String read() {
			synchronized (syncer) {
				if (realFile) {
					if (realFileObj == null) {
						return "Failed to read file";
					}
					try {
						return JsonValue.getPrettyJsonString(fdb.getFile(realFileObj.ID, true).Contents);
					} catch (DatabaseException e) {
						e.printStackTrace();
						return "Failed to read file";
					}
				} else {
					return value;
				}
			}
		}

		@Override
		public void write(String data) {
			synchronized (syncer) {
				if (realFile) {
					if (realFileObj != null) {
						fdb.storeFile(realFileObj, realFileObj.FileName, data);
					}
				} else {
					value = data;
				}
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
			this.name = name;
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
		private final ImportMetaFile perms;
		private final ImportMetaFile defaultSettingsFile;

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
			public final String name;

			private InternalVariant(int id, String name) {
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

		private class InternalTeamMember {
			public final String login;
			public final String name;

			private InternalTeamMember(String login, String name) {
				this.login = login;
				this.name = name;
			}
		}

		private class InternalTeam {

			public final InternalTeamMember[] MemberLoginsAndNames;

			private InternalTeam(int teamID, InternalTeamMember[] memberLogins) {
				this.MemberLoginsAndNames = memberLogins;
			}
		}

		private class InternalCourse {

			public final List<InternalResultUser> results = new ArrayList<>();

			public InternalCourse(String courseName, String courseYear, String studentsGroupPrefix, String teacherGroupPrefix, String regexVarNameFrom, String regexVarNameTo, Map<String, InternalPasswdUser> pwd, String groupsJSN) {
				List<InternalTeacher> teachers = new ArrayList<>();
				List<InternalEnrollment> enrollemnts = new ArrayList<>();
				Map<Integer, InternalVariant> variants = new HashMap<>();

				Map<String, InternalTeam> teams = null;

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
						if (arr.Value.size() == 3 || arr.Value.size() == 4) {
							JsonValue jvariants = arr.Value.get(0);
							JsonValue jenrollments = arr.Value.get(1);
							JsonValue jteachers = arr.Value.get(2);

							if (arr.Value.size() == 4) { // Load teams
								JsonValue jteams = arr.Value.get(3);
								if (jteams.isObject()) {
									JsonObject jteamso = jteams.asObject();
									if (jteamso.containsArray("data")) {
										JsonArray ateams = jteamso.getArray("data");
										teams = new HashMap<>();
										for (JsonValue ateam : ateams.Value) {
											if (ateam.isObject()) {
												JsonObject oteam = ateam.asObject();
												if (oteam.containsNumber("id")) {
													int teamID = oteam.getNumber("id").Value;

													if (oteam.containsString("leader_login") && oteam.containsString("leader_name")) {

														String leader = oteam.getString("leader_login").Value;
														String leader_name = oteam.getString("leader_name").Value;
														InternalTeamMember[] members = new InternalTeamMember[] { new InternalTeamMember(leader, leader_name) };
														if (oteam.containsArray("members")) {
															JsonArray jmembers = oteam.getArray("members");
															InternalTeamMember[] newMembers = new InternalTeamMember[1 + jmembers.Value.size()];
															newMembers[0] = members[0];
															members = newMembers;
															int memberID = 1;
															for (JsonValue jmember : jmembers.Value) {
																if (jmember.isObject()) {
																	JsonObject omember = jmember.asObject();
																	if (omember.containsString("login")) {
																		String login = omember.getString("login").Value;
																		String name = omember.getString("name").Value;
																		newMembers[memberID] = new InternalTeamMember(login, name);
																	} else {
																		ok = false;
																	}
																} else {
																	ok = false;
																}
																memberID++;
															}
															if (ok) {
																for (InternalTeamMember member : members) {
																	teams.put(member.login, new InternalTeam(teamID, members));
																}
															}
														}
													} // Teams without leader are empty
												} else {
													ok = false;
												}
											} else {
												ok = false;
											}
										}
									} else {
										ok = false;
									}

								} else {
									ok = false;
								}
							}

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
									variants.put(0, new InternalVariant(0, "Registered"));

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
											if (teams != null) {
												if (teams.containsKey(login)) {
													InternalTeam team = teams.get(login);
													for (InternalTeamMember member : team.MemberLoginsAndNames) {
														enrollemnts.add(new InternalEnrollment(var_id, member.login, member.name));
													}
												}
											}
											enrollemnts.add(new InternalEnrollment(var_id, login, name));
										} else if (obj.containsString("name") && obj.containsString("email")) {
											String name = obj.getString("name").Value;
											String email = obj.getString("email").Value;
											String login = email.split("@")[0];
											if (teams != null) {
												if (teams.containsKey(login)) {
													InternalTeam team = teams.get(login);
													for (InternalTeamMember member : team.MemberLoginsAndNames) {
														enrollemnts.add(new InternalEnrollment(0, member.login, member.name));
													}
												}
											}
											enrollemnts.add(new InternalEnrollment(0, login, name));
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

		private class InternalDefaultPermission {
			public final String GroupName;
			public final String Permission;

			private InternalDefaultPermission(String name, String perm) {
				this.GroupName = name;
				this.Permission = perm;
			}
		}

		public ImportMetaFunctioningFile(Object syncer, String name, ImportMetaFile users, ImportMetaFile groups, ImportMetaFile defaultPermissionsFile, ImportMetaFile defaultSettingsFile, LayeredFilesDB fdb) {
			super(syncer, name, false, fdb);
			this.users = users;
			this.groups = groups;
			this.perms = defaultPermissionsFile;
			this.defaultSettingsFile = defaultSettingsFile;
			this.syncer = syncer;
			writeEmpty();
		}

		private void superWrite(String data) {
			super.write(data);
			;
		}

		public void writeEmpty() {
			synchronized (syncer) {
				super.write(defaultSettingsFile.read());
				defaultSettingsFile.writeWhenReady(new WritableData() {

					@Override
					public void write(String data) {
						superWrite(data);
					}

				});
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
					String toolchain = map.get("Toolchain".toLowerCase());

					Toolchain Toolchain = null;
					StaticDB sdb = (StaticDB) LayeredImportDB.this;
					try {
						Toolchain = sdb.getToolchain(toolchain);
					} catch (NoSuchToolchainException e) {
					}
					if (Toolchain != null) {
						StringBuilder processLog = new StringBuilder();
						List<InternalResultUser> loadedUsers = process(data, CourseName, CourseYear, RegexGroupSearch, RegexGroupReplace, StudentsGroupPrefix, TeachersGroupPrefix, processLog);
						List<InternalDefaultPermission> defPerms = this.loadDefaultPermissions();
						if (loadedUsers == null || defPerms == null) {
							super.write("# Import failed\n\n\n" + processLog + "\n\n========= Original Data ===========" + data);
						} else {
							super.write(replaceUsers(loadedUsers, Toolchain, Settings.GetDefaultGroup(), defPerms) + "\n\n\n ====== Protocol =======\n" + processLog.toString());
						}
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

		private List<InternalDefaultPermission> loadDefaultPermissions() {
			List<InternalDefaultPermission> lst = new ArrayList<>();
			String permData = this.perms.read();
			JsonValue val = JsonValue.parse(permData);
			if (val != null) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					for (Entry<String, JsonValue> entry : obj.getEntries()) {
						boolean ok = false;
						String group = entry.getKey();
						if (entry.getValue().isArray()) {
							JsonArray pa = entry.getValue().asArray();
							for (JsonValue p : pa.Value) {
								if (p.isString()) {
									String perm = p.asString().Value;
									lst.add(new InternalDefaultPermission(group, perm));
									ok = true;
								}
							}
						}
						if (!ok) {
							return null;
						}
					}
					return lst;
				}
			}
			return null;
		}
	}

	private void addVirtualImporterForToolchain(String toolchain) {
		ImportMetaFile UsersFile = new ImportMetaFile(syncer, "importy/" + toolchain + "/Users.db", true, this);
		ImportMetaFile GroupsFile = new ImportMetaFile(syncer, "importy/" + toolchain + "/Groups.db", true, this);
		ImportMetaFile DefaultPermissionsFile = new ImportMetaFile(syncer, "importy/" + toolchain + "/DefaultPermissions.db", true, this);
		ImportMetaFile DefaultSettings = new ImportMetaFile(syncer, "importy/" + toolchain + "/Configuration.db", true, this);
		ImportMetaFunctioningFile ImportFile = new ImportMetaFunctioningFile(syncer, "importy/" + toolchain + "/Import.exe", UsersFile, GroupsFile, DefaultPermissionsFile, DefaultSettings, this);
		super.registerVirtualFile(ImportFile);

	}

	private boolean toolchainsAdded = false;

	public LayeredImportDB(DatabaseInitData fileName) throws DatabaseException {
		super(fileName);
	}

	private void handleInitToolchains() {
		if (!toolchainsAdded) {
			toolchainsAdded = true;
			StaticDB sdb = (StaticDB) this;
			for (Toolchain tc : sdb.getAllToolchains()) {
				addVirtualImporterForToolchain(tc.getName());
			}
		}
	}

	@Override
	public List<DatabaseFile> getFiles() {
		handleInitToolchains();
		synchronized (syncer) {
			List<DatabaseFile> files = super.getFiles();
			for (ImportMetaFile myFile : loadedVirtualFiles.values()) {
				myFile.ensureFile(files);
			}
			return files;
		}
	}

	@Override
	public FileInfo loadFile(String name, boolean decodeBigString) {
		FileInfo fo = super.loadFile(name, decodeBigString);
		if (fo != null) {
			if (loadedVirtualFiles.containsKey(fo.FileName)) {
				return new FileInfo(fo.ID, fo.FileName, JsonValue.getPrettyJsonString(fo.Contents));
			}
		}
		return fo;
	}

	@Override
	public FileInfo getFile(int fileID, boolean decodeBigString) throws DatabaseException {
		FileInfo fo = super.getFile(fileID, decodeBigString);
		if (fo != null) {
			if (loadedVirtualFiles.containsKey(fo.FileName)) {
				return new FileInfo(fo.ID, fo.FileName, JsonValue.getPrettyJsonString(fo.Contents));
			}
		}
		return fo;
	}

	public abstract boolean clearUsers(Toolchain toolchain);

	public abstract Integer getRootPermissionGroup(Toolchain toolchain, String name);

	public abstract boolean createUser(Toolchain toolchain, String login, String origin, String fullName, List<String> permissionGroups, int rootPermissionGroupID);

	public abstract boolean addPermission(Toolchain toolchain, String group, String permission);

	private String replaceUsers(List<InternalResultUser> users, Toolchain toolchain, String rootPermissionGroupName, List<ImportMetaFunctioningFile.InternalDefaultPermission> defPerms) {
		if (!clearUsers(toolchain)) {
			return "Failed to purge user database";
		}
		Integer rootGroup = getRootPermissionGroup(toolchain, rootPermissionGroupName);
		if (rootGroup == null) {
			return "Failed to create root permission group";
		}

		for (InternalResultUser user : users) {
			if (!createUser(toolchain, user.login, user.origin, user.name, user.groups, rootGroup)) {
				return "Failed to create user(s)";
			}
		}

		for (ImportMetaFunctioningFile.InternalDefaultPermission perm : defPerms) {
			if (!this.addPermission(toolchain, perm.GroupName, perm.Permission)) {
				return "Failed to add permissions for group \"" + perm.GroupName + "\"";
			}
		}
		return "Users imported and loaded";
	}
}
