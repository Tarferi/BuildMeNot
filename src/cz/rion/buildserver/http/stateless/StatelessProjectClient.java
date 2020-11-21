package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredProjectDB.Project;
import cz.rion.buildserver.db.layers.staticDB.LayeredProjectDB.ProjectComment;
import cz.rion.buildserver.db.layers.staticDB.LayeredProjectDB.ProjectSolution;
import cz.rion.buildserver.db.layers.staticDB.LayeredProjectDB.ProjectSolutionFile;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.json.JsonValue.JsonBoolean;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.Pair;
import cz.rion.buildserver.utils.ToolchainedPermissionCache;

public class StatelessProjectClient extends StatelessGraphProviderClient {

	private static final Object syncer = new Object();

	private static Map<String, PermissionBranch> reqPerms = new HashMap<>();

	private static final ToolchainedPermissionCache pbMap = new ToolchainedPermissionCache("WEB.PROJECTS.SEE");

	protected StatelessProjectClient(StatelessInitData data) {
		super(data);
	}

	private JsonValue collectProjectData(int project_id, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonObject obj_result = new JsonObject();
		JsonArray arr_solutions = new JsonArray();
		for (ProjectSolution solution : staticDB.getSolutionsFor(project_id, toolchain)) {
			arr_solutions.add(solution.get());
		}
		obj_result.add("projects", arr_solutions);
		return obj_result;
	}

	private JsonValue collectData(boolean onlyOwnFiles, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonObject obj_result = new JsonObject();
		JsonArray arr_projects = new JsonArray();
		for (Project proj : staticDB.getExistingProjects(toolchain)) {
			arr_projects.add(proj.get(onlyOwnFiles));
		}
		obj_result.add("projects", arr_projects);
		return obj_result;
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

	private List<String> toList(JsonArray array) {
		List<String> lst = new ArrayList<>();
		for (JsonValue val : array.Value) {
			if (val.isString()) {
				lst.add(val.asString().Value);
			}
		}
		return lst;
	}

	private List<Pair<String, List<Pair<String, String>>>> toPairsOfPairs(JsonObject obj) {
		List<Pair<String, List<Pair<String, String>>>> lst = new ArrayList<>();
		for (Entry<String, JsonValue> entry : obj.getEntries()) {
			String key = entry.getKey();
			List<Pair<String, String>> inner = new ArrayList<>();
			if (entry.getValue().isObject()) {
				JsonObject obj2 = entry.getValue().asObject();
				for (Entry<String, JsonValue> entry2 : obj2.getEntries()) {
					if (entry2.getValue().isString()) {
						inner.add(new Pair<>(entry2.getKey(), entry2.getValue().asString().Value));
					}
				}
			}
			lst.add(new Pair<>(key, inner));
		}
		return lst;
	}

	private List<Pair<Integer, Pair<Integer, Integer>>> toPairsOfIntervals(JsonArray arr) {
		List<Pair<Integer, Pair<Integer, Integer>>> lst = new ArrayList<>();
		for (JsonValue val : arr.Value) {
			if (val.isArray()) {
				JsonArray a = val.asArray();
				if (a.Value.size() == 3) {
					JsonValue v1 = a.Value.get(0);
					JsonValue v2 = a.Value.get(1);
					JsonValue v3 = a.Value.get(2);
					if (v1.isNumber() && v2.isNumber() && v3.isNumber()) {
						int x1 = v1.asNumber().Value;
						int x2 = v2.asNumber().Value;
						int x3 = v3.asNumber().Value;
						lst.add(new Pair<>(x1, new Pair<>(x2, x3)));
					}

				}
			}
		}
		return lst;
	}

	protected JsonObject handleProjectsEvent(ProcessState state, JsonObject obj) {
		JsonObject idata = new JsonObject();

		synchronized (syncer) {
			String tc = state.Toolchain.getName();
			PermissionBranch requiredPermissions;
			if (!reqPerms.containsKey(tc)) {
				requiredPermissions = new PermissionBranch(state.Toolchain, tc + ".PROJECTS");
				reqPerms.put(tc, requiredPermissions);
			} else {
				requiredPermissions = reqPerms.get(tc);
			}

			JsonObject result = new JsonObject();
			result.add("code", new JsonNumber(1));
			result.add("result", new JsonString("Invalid project command"));

			boolean canAdmin = state.getPermissions().can(requiredPermissions);
			boolean canUser = state.getPermissions().can(pbMap.toBranch(state.Toolchain));

			idata.add("toolchain", tc);
			idata.add("canAdmin", canAdmin);
			idata.add("canUser", canUser);
			idata.add("success", false);
			idata.add("reason", "unknown");
			if (obj.containsString("project_data")) {
				String project_data = obj.getString("project_data").Value;
				idata.add("action", project_data);
				if ((canAdmin || canUser) && project_data.equals("getData")) {
					idata.add("success", true);
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectData(!canAdmin, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString()));
				} else if (canAdmin && project_data.equals("getProjectSolutions") && obj.containsNumber("project_id")) {
					int project_id = obj.getNumber("project_id").Value;
					idata.add("action", project_data);
					idata.add("project_id", project_id);
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectProjectData(project_id, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString()));
				} else if (canAdmin && project_data.equals("saveProject") && obj.containsNumber("project_id") && obj.containsString("title") && obj.containsString("description") && obj.containsObject("files") && obj.containsObject("editor_files") && obj.containsArray("comment_files") && obj.containsArray("visible_files") && obj.containsString("prefix")) {
					int project_id = obj.getNumber("project_id").Value;
					String title = obj.getString("title").Value;
					String prefix = obj.getString("prefix").Value;
					String description = obj.getString("description").Value;
					List<Pair<String, String>> files = toPairs(obj.getObject("files"));
					List<Pair<String, String>> editor_files = toPairs(obj.getObject("editor_files"));
					List<String> comment_files = toList(obj.getArray("comment_files"));
					List<String> visible_files = toList(obj.getArray("visible_files"));
					idata.add("action", project_data);
					idata.add("project_id", project_id);
					idata.add("title", title);
					idata.add("description", description);
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(saveProject(project_id, title, description, files, editor_files, comment_files, visible_files, prefix, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString()));
				} else if ((canAdmin || canUser) && project_data.equals("loadFiles") && obj.containsNumber("project_id")) {
					int project_id = obj.getNumber("project_id").Value;
					idata.add("action", project_data);
					idata.add("project_id", project_id);
					result.add("code", new JsonNumber(0));
					result.add("result", loadFiles(!canAdmin, project_id, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString());
				} else if (canAdmin && project_data.equals("updateFiles") && obj.containsNumber("project_id") && obj.containsObject("files")) {
					int project_id = obj.getNumber("project_id").Value;
					List<Pair<String, List<Pair<String, String>>>> files = toPairsOfPairs(obj.getObject("files"));
					idata.add("action", project_data);
					idata.add("project_id", project_id);
					result.add("code", new JsonNumber(0));
					result.add("result", updateFiles(project_id, files, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString());
				} else if ((canAdmin || canUser) && project_data.equals("loadFile") && obj.containsNumber("project_id") && obj.containsNumber("file_id")) {
					int project_id = obj.getNumber("project_id").Value;
					int file_id = obj.getNumber("file_id").Value;
					idata.add("action", project_data);
					idata.add("project_id", project_id);
					result.add("code", new JsonNumber(0));
					result.add("result", loadFile(!canAdmin, project_id, file_id, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString());
				} else if ((canAdmin || canUser) && project_data.equals("add_feedback") && obj.containsNumber("project_id") && obj.containsNumber("file_id") && obj.containsArray("selections") && obj.containsString("text")) {
					int project_id = obj.getNumber("project_id").Value;
					int file_id = obj.getNumber("file_id").Value;
					String text = obj.getString("text").Value;
					List<Pair<Integer, Pair<Integer, Integer>>> selections = toPairsOfIntervals(obj.getArray("selections"));
					idata.add("action", project_data);
					idata.add("project_id", file_id);
					result.add("code", new JsonNumber(0));
					result.add("result", addComment(!canAdmin, project_id, file_id, selections, text, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString());
				} else if ((canAdmin || canUser) && project_data.equals("edit_feedback") && obj.containsNumber("project_id") && obj.containsNumber("file_id") && obj.containsNumber("comment_id") && obj.containsArray("selections") && obj.containsString("text")) {
					int project_id = obj.getNumber("project_id").Value;
					int file_id = obj.getNumber("file_id").Value;
					int comment_id = obj.getNumber("comment_id").Value;
					String text = obj.getString("text").Value;
					List<Pair<Integer, Pair<Integer, Integer>>> selections = toPairsOfIntervals(obj.getArray("selections"));
					idata.add("action", project_data);
					idata.add("project_id", file_id);
					result.add("code", new JsonNumber(0));
					result.add("result", editComment(!canAdmin, project_id, file_id, comment_id, selections, text, state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString());
				}
			}
			state.setIntention(Intention.PROJECT_COMMAND, idata);
			return result;
		}
	}

	private JsonValue editComment(boolean onlyOwn, int project_id, int file_id, int comment_id, List<Pair<Integer, Pair<Integer, Integer>>> selections, String text, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonObject obj = new JsonObject();
		ProjectSolutionFile f = staticDB.getFile(toolchain, project_id, file_id);
		if (f != null) {
			if (onlyOwn && !f.Config.Login.equals(permissions.Login)) {
				obj.add("err", "No such file exists");
			} else {
				for (ProjectComment comment : staticDB.getCommentsForFile(toolchain, file_id)) {
					if (comment.ID == comment_id && permissions.Login.equals(comment.Login)) {
						if (comment.saveEdited(selections, text)) {
							obj.add("success", true);
						} else {
							obj.add("err", "Failed to edit comment");
						}
						return obj;
					}
				}
			}
			obj.add("err", "Failed to edit comment");
		} else {
			obj.add("err", "No such comment exists");
		}
		return obj;
	}

	private JsonValue addComment(boolean onlyOwn, int project_id, int file_id, List<Pair<Integer, Pair<Integer, Integer>>> selections, String text, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonObject obj = new JsonObject();
		ProjectSolutionFile f = staticDB.getFile(toolchain, project_id, file_id);
		if (f != null) {
			if (onlyOwn && !f.Config.Login.equals(permissions.Login)) {
				obj.add("err", "No such file exists");
			} else if (f.Config.addComment(project_id, file_id, permissions.Login, selections, text)) {
				obj.add("success", true);
			} else {
				obj.add("err", "Failed to save comment");
			}
		} else {
			obj.add("err", "No such file exists");
		}
		return obj;
	}

	private JsonArray fromCommentList(List<ProjectComment> list) {
		JsonArray arr = new JsonArray();
		for (ProjectComment item : list) {
			arr.add(item.get());
		}
		return arr;
	}

	private JsonValue loadFile(boolean onlyOwn, int project_id, int file_id, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonObject obj = new JsonObject();
		ProjectSolutionFile f = staticDB.getFile(toolchain, project_id, file_id);
		if (f != null) {
			if (onlyOwn && !f.Config.Login.equals(permissions.Login)) {
				obj.add("err", "No such file exists");
			} else {
				if (onlyOwn) {
					boolean allowed = false;
					for (String visibleFile : f.Project.Config.VisibleFiles) {
						if (visibleFile.equals(f.Name)) {
							allowed = true;
						}
					}
					if (!allowed) {
						obj.add("err", "No such file exists");
						return obj;
					}
				}

				obj.add("data", f.get());
				obj.add("comments", fromCommentList(staticDB.getCommentsForFile(toolchain, file_id)));
			}
		} else {
			obj.add("err", "No such file exists");
		}
		return obj;
	}

	private JsonValue updateFiles(int project_id, List<Pair<String, List<Pair<String, String>>>> files, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		String str = null;
		staticDB.updateFiles(toolchain, project_id, permissions.Login, files);
		return str == null ? new JsonBoolean(true) : new JsonString(str);
	}

	private JsonValue loadFiles(boolean onlyOwn, int project_id, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		JsonArray arr = new JsonArray();
		for (ProjectSolution sol : staticDB.getSolutionsFor(project_id, toolchain)) {
			if (!onlyOwn || sol.Login.equals(permissions.Login)) {
				JsonValue solutionData = sol.get(onlyOwn);
				JsonValue solFiles = sol.getFileList(onlyOwn);
				JsonObject obj = new JsonObject();
				obj.add("data", solutionData);
				obj.add("files", solFiles);
				arr.add(obj);
			}
		}
		return arr;
	}

	private JsonValue saveProject(int project_id, String title, String description, List<Pair<String, String>> files, List<Pair<String, String>> editor_files, List<String> comment_files, List<String> visible_files, String prefix, StaticDB staticDB, Toolchain toolchain, UsersPermission permissions) {
		String err = null;

		Project proj = staticDB.getProjectByID(toolchain, project_id);
		if (proj == null) {
			err = "Test s uvedeným identifikátorem nebyl nalezen";
		} else {
			if (!proj.update(title, description, files, editor_files, comment_files, visible_files, prefix)) {
				err = "Nepodaøilo se uložit zmìny";
			}
		}

		return err == null ? new JsonBoolean(true) : new JsonString(err);
	}

}
