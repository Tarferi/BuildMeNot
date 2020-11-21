package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;
import cz.rion.buildserver.utils.Pair;

public abstract class LayeredProjectDB extends LayeredExamDB {

	private final DatabaseInitData dbData;
	private static final int TYPE_FILE = 1;
	private static final int TYPE_SOLUTION = 2;

	public LayeredProjectDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		// this.dropTable("proj_projects");
		// this.dropTable("proj_projects_sol");
		// this.dropTable("proj_files");
		// this.dropTable("proj_comments");

		this.makeTable("proj_projects", false, KEY("ID"), TEXT("name"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("proj_projects_sol", false, KEY("ID"), NUMBER("project_id"), TEXT("login"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("proj_files", false, KEY("ID"), NUMBER("solution_id"), TEXT("name"), BIGTEXT("contents"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("proj_comments", false, KEY("ID"), TEXT("login"), DATE("creation_time"), DATE("last_update"), NUMBER("type"), NUMBER("target_id"), BIGTEXT("contents"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.dbData = dbData;
	}

	@Override
	public void afterInit() {
		super.afterInit();
		final Map<String, ProjectVFile> toolchainFiles = new HashMap<>();
		this.registerToolchainListener(new ToolchainCallback() {

			@Override
			public void toolchainRemoved(Toolchain t) {
				synchronized (toolchainFiles) {
					if (toolchainFiles.containsKey(t.getName())) {
						ProjectVFile f = toolchainFiles.remove(t.getName());
						dbData.Files.unregisterVirtualFile(f);
					}
				}
			}

			@Override
			public void toolchainAdded(Toolchain t) {
				synchronized (toolchainFiles) {
					if (!toolchainFiles.containsKey(t.getName())) {
						ProjectVFile f = new ProjectVFile(t);
						toolchainFiles.put(t.getName(), f);
						dbData.Files.registerVirtualFile(f);
					}
				}
			}
		});
	}

	private final class ProjectVFile extends VirtualFile {

		public ProjectVFile(Toolchain toolchain) {
			super("projects.ini", toolchain);
		}

		@Override
		public String read(UserContext context) throws VirtualFileException {
			StringBuilder sb = new StringBuilder();
			sb.append("# Každý øádek = název projektu\n# Pro pøejmenování se musí sáhnout do databáze!\n\n");
			for (Project project : getExistingProjects(Toolchain)) {
				sb.append(project.Name + "\n");
			}
			return sb.toString();
		}

		@Override
		public boolean write(UserContext context, String newName, String value) throws VirtualFileException {
			Map<String, Integer> existing = new HashMap<>();
			try {
				for (Project proj : loadProjects(Toolchain)) {
					existing.put(proj.Name, proj.ID);
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
				throw new VirtualFileException(e.description);
			}
			Set<String> newProjects = new HashSet<>();
			for (String line : value.split("\n")) {
				if (line.startsWith("#") || line.trim().isEmpty()) {
					continue;
				}
				String proj = line.trim();
				if (existing.containsKey(proj)) {
					existing.remove(proj);
				} else {
					newProjects.add(proj);
				}
			}
			boolean ok = true;
			for (String newProj : newProjects) {
				try {
					ok &= createProject(Toolchain, newProj);
				} catch (DatabaseException e) {
					e.printStackTrace();
					throw new VirtualFileException(e.description);
				}
			}
			for (Entry<String, Integer> entry : existing.entrySet()) {
				try {
					ok &= deleteProject(Toolchain, entry.getValue());
				} catch (DatabaseException e) {
					e.printStackTrace();
					throw new VirtualFileException(e.description);
				}
			}
			return ok;
		}
	}

	public static final class ProjectCommentSelection {
		public final int begin;
		public final int end;
		public final int mark;

		public ProjectCommentSelection(int begin, int end, int mark) {
			this.begin = begin;
			this.end = end;
			this.mark = mark;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("begin", begin);
			obj.add("end", end);
			obj.add("mark", mark);
			return obj;
		}

		private static ProjectCommentSelection get(JsonValue val) {
			if (val.isString()) {
				val = JsonValue.parse(val.asString().Value);
				if (val == null) {
					return null;
				}
			}
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("begin") && obj.containsNumber("end") && obj.containsNumber("mark")) {
					int begin = obj.getNumber("begin").Value;
					int end = obj.getNumber("end").Value;
					int mark = obj.getNumber("mark").Value;
					return new ProjectCommentSelection(begin, end, mark);
				}
			}
			return null;
		}
	}

	public static final class ProjectCommentSelectionList {
		private final List<ProjectCommentSelection> Selections;

		private ProjectCommentSelectionList() {
			Selections = new ArrayList<>();
		}

		public JsonValue get() {
			JsonArray arr = new JsonArray();
			for (ProjectCommentSelection sel : Selections) {
				arr.add(sel.get());
			}
			return arr;
		}

		private static ProjectCommentSelectionList get(JsonValue val) {
			if (val.isString()) {
				val = JsonValue.parse(val.asString().Value);
				if (val == null) {
					return null;
				}
			}
			if (val.isArray()) {
				JsonArray arr = val.asArray();
				ProjectCommentSelectionList res = new ProjectCommentSelectionList();
				for (JsonValue v : arr.Value) {
					ProjectCommentSelection cfg = ProjectCommentSelection.get(v);
					if (cfg == null) {
						return null;
					} else {
						res.Selections.add(cfg);
					}
				}
				return res;
			}
			return null;
		}
	}

	public static final class ProjectComment {
		public final int ID;
		public final int TargetID;
		public final int Type;
		public final String Login;
		public final Long CreationTime;
		public final Long LastUpdateTime;
		public final String Contents;
		public final ProjectCommentSelectionList Selections;
		private final LayeredProjectDB db;
		private final Toolchain toolchain;

		private ProjectComment(LayeredProjectDB db, Toolchain toolchain, int ID, int targetID, int type, String login, long creation, long last, String contents, ProjectCommentSelectionList sel) {
			this.ID = ID;
			this.Login = login;
			this.CreationTime = creation;
			this.LastUpdateTime = last;
			this.Contents = contents;
			this.TargetID = targetID;
			this.Type = type;
			this.Selections = sel;
			this.db = db;
			this.toolchain = toolchain;
		}

		private static ProjectComment get(LayeredProjectDB db, Toolchain toolchain, JsonValue val) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsNumber("creation_time") && obj.containsNumber("last_update") && obj.containsString("login") && obj.containsNumber("type") && obj.containsNumber("target_id") && obj.containsString("contents") && obj.contains("config")) {
					int id = obj.getNumber("ID").Value;
					int target_id = obj.getNumber("target_id").Value;
					int type = obj.getNumber("type").Value;

					long creation = obj.getNumber("creation_time").asLong();
					long last = obj.getNumber("last_update").asLong();
					String login = obj.getString("login").Value;
					String contents = obj.getString("contents").Value;
					ProjectCommentSelectionList sel = ProjectCommentSelectionList.get(obj.get("config"));
					if (sel != null) {
						return new ProjectComment(db, toolchain, id, target_id, type, login, creation, last, contents, sel);
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("ID", ID);
			obj.add("creation_time", CreationTime);
			obj.add("last_update", LastUpdateTime);
			obj.add("login", Login);
			obj.add("contents", Contents);
			obj.add("config", Selections.get());
			obj.add("type", Type);
			obj.add("target_id", TargetID);
			return obj;
		}

		public boolean saveEdited(List<Pair<Integer, Pair<Integer, Integer>>> selections, String text) {
			return db.editComment(this, toolchain, selections, text);
		}
	}

	private static List<Pair<String, String>> toPairs(JsonObject obj) {
		List<Pair<String, String>> lst = new ArrayList<>();
		for (Entry<String, JsonValue> entry : obj.getEntries()) {
			if (entry.getValue().isString()) {
				lst.add(new Pair<>(entry.getKey(), entry.getValue().asString().Value));
			}
		}
		return lst;
	}

	private static List<String> toList(JsonArray array) {
		List<String> lst = new ArrayList<>();
		for (JsonValue val : array.Value) {
			if (val.isString()) {
				lst.add(val.asString().Value);
			}
		}
		return lst;
	}

	private static JsonObject fromPairs(List<Pair<String, String>> pairs) {
		JsonObject obj = new JsonObject();
		for (Pair<String, String> pair : pairs) {
			obj.add(pair.Key, pair.Value);
		}
		return obj;
	}

	private static JsonArray fromList(List<String> lst) {
		JsonArray ar = new JsonArray();
		for (String str : lst) {
			ar.add(str);
		}
		return ar;
	}

	public static final class ProjectConfig {

		public final String Title;
		public final String Description;
		public final List<Pair<String, String>> Files;
		public final String Prefix;
		public final List<Pair<String, String>> EditorFiles;
		public final List<String> CommentFiles;
		public final List<String> VisibleFiles;

		private ProjectConfig(String title, String description, List<Pair<String, String>> files, List<Pair<String, String>> editor_files, List<String> comment_files, List<String> visible_files, String prefix) {
			this.Title = title;
			this.Description = description;
			this.Files = files;
			this.EditorFiles = editor_files;
			this.CommentFiles = comment_files;
			this.VisibleFiles = visible_files;
			this.Prefix = prefix;
		}

		private static ProjectConfig get(JsonValue val) {
			if (val != null) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("title") && obj.containsString("description")) {
						String title = obj.getString("title").Value;
						String description = obj.getString("description").Value;
						List<Pair<String, String>> files = obj.containsObject("files") ? toPairs(obj.getObject("files")) : new ArrayList<>();
						List<Pair<String, String>> editor_files = obj.containsObject("editor_files") ? toPairs(obj.getObject("editor_files")) : new ArrayList<>();
						List<String> comment_files = obj.containsArray("comment_files") ? toList(obj.getArray("comment_files")) : new ArrayList<>();
						List<String> visible_files = obj.containsArray("visible_files") ? toList(obj.getArray("visible_files")) : new ArrayList<>();
						String prefix = obj.containsString("prefix") ? obj.getString("prefix").Value : "";
						return new ProjectConfig(title, description, files, editor_files, comment_files, visible_files, prefix);
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			return get(false);
		}

		public static ProjectConfig createNew() {
			return new ProjectConfig("Název projektu", "Popis projektu", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "");
		}

		public JsonValue get(boolean reduced) {
			JsonObject obj = new JsonObject();
			obj.add("title", Title);
			obj.add("description", Description);
			if (reduced) {
				JsonObject allowedFiles = new JsonObject();
				JsonObject allowedEditorFiles = new JsonObject();
				JsonArray allowedCommentFiles = new JsonArray();
				JsonObject pairedFiles = fromPairs(Files);
				JsonObject pairedEditorFiles = fromPairs(EditorFiles);
				Set<String> visibleFiles = new HashSet<>();
				for (String vf : this.VisibleFiles) {
					visibleFiles.add(vf);
					if (pairedFiles.contains(vf)) {
						allowedFiles.add(vf, pairedFiles.get(vf));
					}
					if (pairedEditorFiles.contains(vf)) {
						allowedEditorFiles.add(vf, pairedEditorFiles.get(vf));
					}
				}
				for (JsonValue commentedFile : fromList(CommentFiles).Value) {
					if (commentedFile.isString()) {
						String cf = commentedFile.asString().Value;
						if (visibleFiles.contains(cf)) {
							allowedCommentFiles.add(cf);
						}
					}
				}
				obj.add("files", allowedFiles);
				obj.add("editor_files", allowedEditorFiles);
				obj.add("comment_files", allowedCommentFiles);
			} else {
				obj.add("files", fromPairs(Files));
				obj.add("editor_files", fromPairs(EditorFiles));
				obj.add("comment_files", fromList(CommentFiles));
				obj.add("visible_files", fromList(VisibleFiles));
				obj.add("prefix", Prefix);
			}
			return obj;
		}

	}

	public static final class Project {
		public final int ID;
		public final String Name;
		public final ProjectConfig Config;
		private final LayeredProjectDB db;
		private final Toolchain toolchain;

		private Project(LayeredProjectDB db, Toolchain toolchain, int id, String name, ProjectConfig config) {
			this.toolchain = toolchain;
			this.db = db;
			this.ID = id;
			this.Name = name;
			this.Config = config;
		}

		private static Project get(LayeredProjectDB db, Toolchain toolchain, JsonValue val) {
			if (val != null) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("config")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						JsonValue configVal = JsonValue.parse(obj.getString("config").Value);
						ProjectConfig config = ProjectConfig.get(configVal);
						if (config != null) {
							return new Project(db, toolchain, id, name, config);
						}
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			return get(false);
		}

		public boolean update(String title, String description, List<Pair<String, String>> files, List<Pair<String, String>> editor_files, List<String> comment_files, List<String> visible_files, String prefix) {
			try {
				return db.updateProject(new Project(db, toolchain, ID, Name, new ProjectConfig(title, description, files, editor_files, comment_files, visible_files, prefix)));
			} catch (DatabaseException e) {
				e.printStackTrace();
				return false;
			}
		}

		public JsonValue get(boolean reduced) {
			JsonObject obj = new JsonObject();
			obj.add("ID", ID);
			obj.add("name", Name);
			obj.add("config", Config.get(reduced));
			return obj;
		}
	}

	public static final class ProjectSolutionConfig {

		public final String CreatorLogin;

		public ProjectSolutionConfig(String creatorLogin) {
			this.CreatorLogin = creatorLogin;
		}

		private static ProjectSolutionConfig get(JsonValue val) {
			if (val != null) {
				if (val.isString()) {
					val = JsonValue.parse(val.asString().Value);
					if (val == null) {
						return null;
					}
				}

				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("creator")) {
						String creator = obj.getString("creator").Value;
						return new ProjectSolutionConfig(creator);
					}
				}
			}
			return null;
		}

		public JsonObject get() {
			return get(false);
		}

		public JsonObject get(boolean reduced) {
			JsonObject obj = new JsonObject();
			obj.add("creator", CreatorLogin);
			return obj;
		}
	}

	public static final class ProjectFileConfigSelection {
		public final int selectionBegin;
		public final int selectionEnd;

		private ProjectFileConfigSelection(int begin, int end) {
			this.selectionBegin = begin;
			this.selectionEnd = end;
		}

		public static ProjectFileConfigSelection get(JsonValue val) {
			if (val.isObject()) {
				JsonObject o = val.asObject();
				if (o.containsNumber("begin") && o.containsNumber("end")) {
					int begin = o.getNumber("begin").Value;
					int end = o.getNumber("end").Value;
					return new ProjectFileConfigSelection(begin, end);
				}
			}
			return null;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("begin", selectionBegin);
			obj.add("end", selectionEnd);
			return obj;
		}
	}

	public static final class ProjectFileConfigComment {
		public final Map<String, ProjectFileConfigSelection> Selections;
		public final String Login;
		public final long Date;
		public final String Text;
		public final int ID;

		private ProjectFileConfigComment(String login, long date, String text, Map<String, ProjectFileConfigSelection> selections, int id) {
			this.Login = login;
			this.Date = date;
			this.Text = text;
			this.Selections = selections;
			this.ID = id;
		}

		private static ProjectFileConfigComment get(JsonValue v) {
			if (v.isObject()) {
				JsonObject obj = v.asObject();
				Map<String, ProjectFileConfigSelection> selections = new HashMap<>();
				if (obj.containsObject("selections")) {
					for (Entry<String, JsonValue> entry : obj.getObject("selections").getEntries()) {
						String mark = entry.getKey();
						ProjectFileConfigSelection sel = ProjectFileConfigSelection.get(entry.getValue());
						if (sel != null) {
							selections.put(mark, sel);
						}
					}
				}
				if (obj.containsNumber("date") && obj.containsString("login") && obj.containsString("text") && obj.containsNumber("id")) {
					long date = obj.getNumber("date").asLong();
					String login = obj.getString("login").Value;
					String text = obj.getString("text").Value;
					int id = obj.getNumber("id").Value;
					return new ProjectFileConfigComment(login, date, text, selections, id);
				}
			}
			return null;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("date", Date);
			obj.add("login", Login);
			obj.add("text", Text);
			obj.add("id", ID);

			if (!Selections.isEmpty()) {
				JsonObject sobj = new JsonObject();
				for (Entry<String, ProjectFileConfigSelection> entry : Selections.entrySet()) {
					sobj.add(entry.getKey(), entry.getValue().get());
				}
				obj.add("selections", sobj);
			}
			return obj;
		}
	}

	public static final class ProjectFileConfig {

		public final String Login;

		private final LayeredProjectDB db;
		private final Toolchain toolchain;

		private ProjectFileConfig(LayeredProjectDB db, Toolchain toolchain, String login) {
			this.Login = login;
			this.db = db;
			this.toolchain = toolchain;
		}

		private static ProjectFileConfig get(LayeredProjectDB db, Toolchain toolchain, JsonValue val) {
			if (val != null) {
				if (val.isString()) {
					val = JsonValue.parse(val.asString().Value);
				}
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsString("login")) {
						String login = obj.getString("login").Value;
						ProjectFileConfig config = new ProjectFileConfig(db, toolchain, login);
						return config;
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("login", Login);
			return obj;
		}

		public boolean addComment(int project_id, int file_id, String login, List<Pair<Integer, Pair<Integer, Integer>>> selections, String text) {
			return db.addComment(toolchain, file_id, project_id, login, selections, text);
		}
	}

	public static final class ProjectSolutionFile {
		public final int ID;
		public final String Name;
		public final String Contents;
		public final ProjectFileConfig Config;
		public final Project Project;

		public ProjectSolutionFile(int id, String name, String contents, ProjectFileConfig config, Project project) {
			this.ID = id;
			this.Name = name;
			this.Contents = contents;
			this.Config = config;
			this.Project = project;
		}

		public static ProjectSolutionFile get(LayeredProjectDB db, Toolchain toolchain, JsonValue val, Project project) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsString("contents") && obj.containsString("name") && obj.contains("config")) {
					int id = obj.getNumber("ID").Value;
					String contents = obj.getString("contents").Value;
					String name = obj.getString("name").Value;
					ProjectFileConfig config = ProjectFileConfig.get(db, toolchain, obj.get("config"));
					if (config != null) {
						return new ProjectSolutionFile(id, name, contents, config, project);
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("ID", ID);
			obj.add("name", Name);
			obj.add("contents", Contents);
			obj.add("config", Config.get());
			return obj;
		}

	}

	public static final class ProjectSolution {
		public final Project Project;
		public final int ID;
		public final String Login;
		public final ProjectSolutionConfig Config;

		private JsonValue listFiles = null;

		private ProjectSolution(Project project, int id, String login, ProjectSolutionConfig config) {
			this.Project = project;
			this.ID = id;
			this.Login = login;
			this.Config = config;
		}

		private static ProjectSolution get(JsonValue val, Map<Integer, Project> projects) {
			if (val != null) {
				if (val.isObject()) {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID") && obj.containsNumber("project_id") && obj.containsString("login") && obj.contains("config")) {
						int id = obj.getNumber("ID").Value;
						int project_id = obj.getNumber("project_id").Value;
						String login = obj.getString("login").Value;
						ProjectSolutionConfig config = ProjectSolutionConfig.get(obj.get("config"));
						Project project = projects.get(project_id);
						if (config != null && project != null) {
							return new ProjectSolution(project, id, login, config);
						}
					}
				}
			}
			return null;
		}

		public JsonValue get() {
			return get(false);
		}

		public JsonValue get(boolean reduced) {
			JsonObject obj = new JsonObject();
			obj.add("ID", ID);
			obj.add("project_id", Project.ID);
			obj.add("login", Login);
			obj.add("config", Config.get(reduced));
			return obj;
		}

		public JsonValue getFileList() {
			return getFileList(false);
		}

		public JsonValue getFileList(boolean onlyOwn) {
			if (listFiles == null) {
				JsonArray ar = new JsonArray();
				try {
					List<Pair<String, Integer>> availableFiles = Project.db.loadFilesForSolution(Project.toolchain, ID);
					Set<String> allowed = new HashSet<>();
					if (onlyOwn) {
						for (String vis : Project.Config.VisibleFiles) {
							allowed.add(vis);
						}
					}

					for (Pair<String, Integer> str : availableFiles) {
						JsonObject obj = new JsonObject();
						if (!onlyOwn || allowed.contains(str.Key)) {
							obj.add("name", str.Key);
							obj.add("id", str.Value);
							ar.add(obj);
						}
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}

				listFiles = ar;
			}
			return listFiles;
		}
	}

	private final class ProjectData {
		private final Toolchain toolchain;
		public final Map<Integer, Project> ProjectsById = new HashMap<>();
		public final Map<Integer, Map<String, ProjectSolution>> SolutionsByProjectIdLogin = new HashMap<>();
		public final Map<Integer, List<ProjectSolutionFile>> ProjectFilesBySolutionId = new HashMap<>();
		public final Map<Integer, ProjectSolutionFile> ProjectFilesByFileId = new HashMap<>();
		public final Map<Integer, List<ProjectComment>> CommentsByFileId = new HashMap<>();

		private ProjectData(Toolchain toolchain) {
			this.toolchain = toolchain;
			try {
				for (Project proj : loadProjects(this.toolchain)) {
					ProjectsById.put(proj.ID, proj);
				}
				for (ProjectSolution sol : loadProjectSolutions(this.toolchain, ProjectsById)) {
					Map<String, ProjectSolution> sols = SolutionsByProjectIdLogin.get(sol.Project.ID);
					if (sols == null) {
						sols = new HashMap<>();
						SolutionsByProjectIdLogin.put(sol.Project.ID, sols);
					}
					sols.put(sol.Login, sol);
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
			}
		}

		private ProjectSolutionFile getFileForID(int projectID, int fileID) {
			ProjectSolutionFile f = ProjectFilesByFileId.get(fileID);
			Project project = ProjectsById.get(projectID);
			if (f == null && project != null) {
				try {
					f = loadFile(toolchain, project, fileID);
					if (f != null) {
						ProjectFilesByFileId.put(f.ID, f);
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
			return f;
		}

		private List<ProjectSolutionFile> getFilesForSolution(int projectID, int solutionID) {
			List<ProjectSolutionFile> lst = ProjectFilesBySolutionId.get(solutionID);
			Project project = ProjectsById.get(projectID);
			if (lst != null) {
				return lst;
			}
			if (project != null) {
				try {
					lst = loadSolutionFiles(toolchain, project, solutionID);
					ProjectFilesBySolutionId.put(solutionID, lst);
					return lst;
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
			return new ArrayList<>();
		}

		private List<ProjectComment> getCommentsForFile(int fileID) {
			List<ProjectComment> res = CommentsByFileId.get(fileID);
			if (res == null) {
				try {
					res = loadCommentsForFile(toolchain, fileID);
					CommentsByFileId.put(fileID, res);
				} catch (DatabaseException e) {
					e.printStackTrace();
					res = new ArrayList<>();
				}
			}
			return res;
		}
	}

	private final CachedToolchainData2<ProjectData> cache = new CachedToolchainDataWrapper2<>(1, new CachedToolchainDataGetter2<ProjectData>() {

		@Override
		public CachedData<ProjectData> createData(int refreshIntervalInSeconds, Toolchain toolchain) {
			return new CachedData<ProjectData>(refreshIntervalInSeconds) {

				@Override
				public ProjectData update() {
					return new ProjectData(toolchain);
				}
			};
		}
	});

	@Override
	public void clearCache() {
		super.clearCache();
		this.cache.clear();
	}

	private boolean addComment(Toolchain toolchain, int file_id, int project_id, String login, List<Pair<Integer, Pair<Integer, Integer>>> sels, String text) {
		synchronized (cache) {
			try {
				ProjectData data = cache.get(toolchain);
				ProjectSolutionFile file = data.getFileForID(project_id, file_id);
				if (file != null) { // Cached update
					ProjectCommentSelectionList selections = new ProjectCommentSelectionList();
					for (Pair<Integer, Pair<Integer, Integer>> sel : sels) {
						int mark = sel.Key;
						int begin = sel.Value.Key;
						int end = sel.Value.Value;
						selections.Selections.add(new ProjectCommentSelection(begin, end, mark));
					}
					return createComment(toolchain, file_id, login, selections, text);
				}
			} catch (DatabaseException e) {
				e.printStackTrace();
			} finally {
				cache.clear();
			}
		}
		return false;
	}

	private boolean editComment(ProjectComment comment, Toolchain toolchain, List<Pair<Integer, Pair<Integer, Integer>>> sels, String text) {
		synchronized (cache) {
			try {
				ProjectCommentSelectionList selections = new ProjectCommentSelectionList();
				for (Pair<Integer, Pair<Integer, Integer>> sel : sels) {
					int mark = sel.Key;
					int begin = sel.Value.Key;
					int end = sel.Value.Value;
					selections.Selections.add(new ProjectCommentSelection(begin, end, mark));
				}
				return editComment(toolchain, comment, selections, text);
			} catch (DatabaseException e) {
				e.printStackTrace();
			} finally {
				cache.clear();
			}
		}
		return false;
	}

	private boolean editComment(Toolchain toolchain, ProjectComment comment, ProjectCommentSelectionList sels, String text) throws DatabaseException {
		final String tableName = "proj_comments";
		TableField f_update = getField(tableName, "last_update");
		TableField f_contents = getField(tableName, "contents");
		TableField f_config = getField(tableName, "config");
		TableField f_valid = getField(tableName, "valid");
		int newValid = text.trim().isEmpty() ? 0 : 1;
		long now = System.currentTimeMillis();
		String config = sels.get().getJsonString();
		return this.update(tableName, comment.ID, new ValuedField(f_update, now), new ValuedField(f_contents, text), new ValuedField(f_config, config), new ValuedField(f_valid, newValid));
	}

	private boolean createComment(Toolchain toolchain, int file_id, String login, ProjectCommentSelectionList sels, String text) throws DatabaseException {
		final String tableName = "proj_comments";
		TableField f_login = getField(tableName, "login");
		TableField f_creation = getField(tableName, "creation_time");
		TableField f_update = getField(tableName, "last_update");
		TableField f_type = getField(tableName, "type");
		TableField f_target = getField(tableName, "target_id");
		TableField f_contents = getField(tableName, "contents");
		TableField f_config = getField(tableName, "config");
		TableField f_toolchain = getField(tableName, "toolchain");
		TableField f_valid = getField(tableName, "valid");
		long now = System.currentTimeMillis();
		String config = sels.get().getJsonString();
		return this.insert(tableName, new ValuedField(f_login, login), new ValuedField(f_target, file_id), new ValuedField(f_creation, now), new ValuedField(f_update, now), new ValuedField(f_type, TYPE_FILE), new ValuedField(f_contents, text), new ValuedField(f_config, config), new ValuedField(f_toolchain, toolchain.getName()), new ValuedField(f_valid, 1));
	}

	public ProjectSolutionFile loadFile(Toolchain toolchain, Project project, int fileID) throws DatabaseException {
		final String tableName = "proj_files";
		final TableField[] fields = new TableField[] { getField(tableName, "name"), getField(tableName, "ID"), getField(tableName, "contents"), getField(tableName, "config") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "ID"), fileID), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			return ProjectSolutionFile.get(this, toolchain, val, project);
		}
		return null;
	}

	public List<Pair<String, Integer>> loadFilesForSolution(Toolchain toolchain, int solutionID) throws DatabaseException {
		List<Pair<String, Integer>> result = new ArrayList<>();
		final String tableName = "proj_files";
		final TableField[] fields = new TableField[] { getField(tableName, "name"), getField(tableName, "ID") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "solution_id"), solutionID), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("name") && obj.containsNumber("ID")) {
					String name = obj.getString("name").Value;
					int id = obj.getNumber("ID").Value;
					result.add(new Pair<>(name, id));
				}
			}
		}
		return result;
	}

	private boolean updateProject(Project project) throws DatabaseException {
		try {
			synchronized (cache) {
				final String tableName = "proj_projects";
				TableField f_name = getField(tableName, "name");
				TableField f_config = getField(tableName, "config");
				return this.update(tableName, project.ID, new ValuedField(f_name, project.Name), new ValuedField(f_config, project.Config.get().getJsonString()));
			}
		} finally {
			cache.clear();
		}
	}

	private List<Project> loadProjects(Toolchain toolchain) throws DatabaseException {
		List<Project> lst = new ArrayList<>();
		final String tableName = "proj_projects";
		final TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "config") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			Project proj = Project.get(this, toolchain, val);
			if (proj != null) {
				lst.add(proj);
			}
		}
		return lst;
	}

	private List<ProjectSolution> loadProjectSolutions(Toolchain toolchain, Map<Integer, Project> mapping) throws DatabaseException {
		List<ProjectSolution> lst = new ArrayList<>();
		final String tableName = "proj_projects_sol";
		final TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "project_id"), getField(tableName, "login"), getField(tableName, "config") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			ProjectSolution sol = ProjectSolution.get(val, mapping);
			if (sol != null) {
				lst.add(sol);
			}
		}
		return lst;
	}

	private List<ProjectSolutionFile> loadSolutionFiles(Toolchain toolchain, Project project, int solutionID) throws DatabaseException {
		List<ProjectSolutionFile> lst = new ArrayList<>();
		final String tableName = "proj_files";
		final TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "contents"), getField(tableName, "config") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			ProjectSolutionFile sol = ProjectSolutionFile.get(this, toolchain, val, project);
			if (sol != null) {
				lst.add(sol);
			}
		}
		return lst;
	}

	private List<ProjectComment> loadCommentsFor(Toolchain toolchain, int type, int targetID) throws DatabaseException {
		List<ProjectComment> lst = new ArrayList<>();
		final String tableName = "proj_comments";
		final TableField[] fields = new TableField[] { getField(tableName, "ID"), getField(tableName, "login"), getField(tableName, "creation_time"), getField(tableName, "last_update"), getField(tableName, "type"), getField(tableName, "contents"), getField(tableName, "config"), getField(tableName, "target_id") };
		JsonArray res = this.select(tableName, fields, true, new ComparisionField(getField(tableName, "target_id"), targetID), new ComparisionField(getField(tableName, "type"), type), new ComparisionField(getField(tableName, "toolchain"), toolchain.getName()), new ComparisionField(getField(tableName, "valid"), 1));
		for (JsonValue val : res.Value) {
			ProjectComment sol = ProjectComment.get(this, toolchain, val);
			if (sol != null) {
				lst.add(sol);
			}
		}
		return lst;
	}

	private List<ProjectComment> loadCommentsForSolution(Toolchain toolchain, int solutionID) throws DatabaseException {
		return loadCommentsFor(toolchain, TYPE_SOLUTION, solutionID);
	}

	private List<ProjectComment> loadCommentsForFile(Toolchain toolchain, int fileID) throws DatabaseException {
		return loadCommentsFor(toolchain, TYPE_FILE, fileID);
	}

	public boolean createProject(Toolchain toolchain, String name) throws DatabaseException {
		try {
			synchronized (cache) {
				final String tableName = "proj_projects";
				TableField f_name = getField(tableName, "name");
				TableField f_valid = getField(tableName, "valid");
				TableField f_toolchain = getField(tableName, "toolchain");
				TableField f_config = getField(tableName, "config");
				String config = ProjectConfig.createNew().get().getJsonString();
				return this.insert(tableName, new ValuedField(f_name, name), new ValuedField(f_valid, 1), new ValuedField(f_toolchain, toolchain.getName()), new ValuedField(f_config, config));
			}
		} finally {
			clearCache();
		}
	}

	public boolean deleteProject(Toolchain toolchain, int project_id) throws DatabaseException {
		try {
			final String tableName = "proj_projects";
			return this.update(tableName, project_id, new ValuedField(getField(tableName, "valid"), 0));
		} finally {
			clearCache();
		}
	}

	public List<ProjectSolutionFile> getFilesForSolution(Toolchain toolchain, int project_id, int solution_id) {
		synchronized (cache) {
			ProjectData data = cache.get(toolchain);
			return data.getFilesForSolution(project_id, solution_id);
		}
	}

	public ProjectSolutionFile getFile(Toolchain toolchain, int project_id, int file_id) {
		synchronized (cache) {
			ProjectData data = cache.get(toolchain);
			return data.getFileForID(project_id, file_id);
		}
	}

	public List<Project> getExistingProjects(Toolchain toolchain) {
		List<Project> lst = new ArrayList<>();
		lst.addAll(cache.get(toolchain).ProjectsById.values());
		return lst;
	}

	public List<ProjectSolution> getSolutionsFor(int projectID, Toolchain toolchain) {
		synchronized (cache) {
			ProjectData data = cache.get(toolchain);
			List<ProjectSolution> lst = new ArrayList<>();
			Map<String, ProjectSolution> vdata = data.SolutionsByProjectIdLogin.get(projectID);
			if (vdata != null) {
				lst.addAll(vdata.values());
			}
			return lst;
		}
	}

	public Project getProjectByID(Toolchain toolchain, int project_id) {
		synchronized (cache) {
			return cache.get(toolchain).ProjectsById.get(project_id);
		}
	}

	public List<ProjectComment> getCommentsForFile(Toolchain toolchain, int fileID) {
		synchronized (cache) {
			return cache.get(toolchain).getCommentsForFile(fileID);
		}
	}

	private ProjectSolution getNewProjectSolution(Toolchain toolchain, Project project, String login, ProjectSolutionConfig config) throws DatabaseException {
		final String tableName = "proj_projects_sol";
		final TableField f_id = getField(tableName, "ID");
		final TableField f_pid = getField(tableName, "project_id");
		final TableField f_login = getField(tableName, "login");
		final TableField f_config = getField(tableName, "config");
		final TableField f_valid = getField(tableName, "valid");
		final TableField f_toolchain = getField(tableName, "toolchain");

		final ValuedField[] cmpV = new ValuedField[] { new ValuedField(f_pid, project.ID), new ValuedField(f_login, login), new ValuedField(f_config, config.get().getJsonString()), new ValuedField(f_valid, 1), new ValuedField(f_toolchain, toolchain.getName()) };
		final ComparisionField[] cmpF = new ComparisionField[] { new ComparisionField(f_pid, project.ID), new ComparisionField(f_login, login), new ComparisionField(f_valid, 1), new ComparisionField(f_toolchain, toolchain.getName()) };

		JsonArray res = this.select(tableName, new TableField[] { f_id }, false, cmpF);
		if (res.Value.isEmpty()) {
			this.insert(tableName, cmpV);
			res = this.select(tableName, new TableField[] { f_id }, false, cmpF);
		}
		int id = -1;
		if (res.Value.isEmpty()) {
			throw new DatabaseException("Failed to create new solution");
		} else {
			id = res.Value.get(0).asObject().getNumber("ID").Value;
		}
		return new ProjectSolution(project, id, login, config);
	}

	private boolean createOrUpdateFile(Toolchain toolchain, ProjectSolution sol, String uploaderLogin, String fileName, String contents) throws DatabaseException {
		final String tableName = "proj_files";
		final TableField f_id = getField(tableName, "ID");
		final TableField f_sid = getField(tableName, "solution_id");
		final TableField f_name = getField(tableName, "name");
		final TableField f_contents = getField(tableName, "contents");
		final TableField f_config = getField(tableName, "config");
		final TableField f_valid = getField(tableName, "valid");
		final TableField f_toolchain = getField(tableName, "toolchain");

		ProjectFileConfig config = new ProjectFileConfig(this, toolchain, sol.Login);

		final ValuedField[] cmpV = new ValuedField[] { new ValuedField(f_sid, sol.ID), new ValuedField(f_name, fileName), new ValuedField(f_contents, contents), new ValuedField(f_config, config.get().getJsonString()), new ValuedField(f_valid, 1), new ValuedField(f_toolchain, toolchain.getName()) };

		final ComparisionField[] cmpF = new ComparisionField[] { new ComparisionField(f_sid, sol.ID), new ComparisionField(f_name, fileName), new ComparisionField(f_valid, 1), new ComparisionField(f_toolchain, toolchain.getName()) };

		JsonArray res = this.select(tableName, new TableField[] { f_id }, false, cmpF);
		if (res.Value.isEmpty()) {
			return this.insert(tableName, cmpV);
		} else {
			int id = res.Value.get(0).asObject().getNumber("ID").Value;
			return this.update(tableName, id, new ValuedField(f_contents, contents));
		}
	}

	public boolean updateFiles(Toolchain toolchain, int project_id, String uploaderLogin, List<Pair<String, List<Pair<String, String>>>> files) {
		synchronized (cache) {
			try {
				ProjectData data = cache.get(toolchain);
				Project p = data.ProjectsById.get(project_id);
				if (p != null) {
					Map<String, ProjectSolution> pdata = data.SolutionsByProjectIdLogin.get(p.ID);
					if (pdata == null) {
						pdata = new HashMap<>();
						data.SolutionsByProjectIdLogin.put(p.ID, pdata);
					}
					for (Pair<String, List<Pair<String, String>>> files1 : files) {
						String login = files1.Key;
						ProjectSolution sol = pdata.get(login);
						if (sol == null) { // Create solution
							ProjectSolutionConfig config = new ProjectSolutionConfig(uploaderLogin);
							sol = getNewProjectSolution(toolchain, p, login, config);
							pdata.put(sol.Login, sol);
						}

						for (Pair<String, String> files2 : files1.Value) {
							String fileName = files2.Key;
							String contents = files2.Value;

							if (!createOrUpdateFile(toolchain, sol, uploaderLogin, fileName, contents)) {
								return false;
							}
						}
					}
					return true;
				}
				return false;
			} catch (DatabaseException e) {
				e.printStackTrace();
				return false;
			} finally {
				cache.clear();
			}
		}
	}

}
