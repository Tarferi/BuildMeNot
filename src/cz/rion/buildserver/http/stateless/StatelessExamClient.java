package cz.rion.buildserver.http.stateless;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.Exam;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.ExamCache;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.Generated;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.GeneratedQuestionConfig;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.Question;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.QuestionGroup;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.QuestionType;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.permissions.PermissionBranch;

public class StatelessExamClient extends StatelessGraphProviderClient {

	protected StatelessExamClient(StatelessInitData data) {
		super(data);
	}

	private boolean isExamRunning(Generated gen) {
		if (gen.Config.Response.containsNumber("Started")) {
			long started = gen.Config.Response.getNumber("Started").asLong();
			long examTime = gen.Exam.Config.ExamTime.longValue();
			long now = System.currentTimeMillis();
			return now - started < examTime;
		}
		return false;
	}

	private boolean isExamFinished(Generated gen) {
		if (gen.Config.Response.containsBoolean("Answered")) {
			if (gen.Config.Response.getBoolean("Answered").Value) {
				return true;
			}
		}
		if (gen.Config.Response.containsNumber("Started")) {
			long started = gen.Config.Response.getNumber("Started").asLong();
			long examTime = gen.Exam.Config.ExamTime.longValue();
			long now = System.currentTimeMillis();
			return now - started > examTime;
		}
		return false;
	}

	private boolean startExam(ProcessState state, int examID) {
		List<Generated> gens = state.Data.StaticDB.getGeneratedExam(state.Toolchain, state.getPermissions().Login);
		for (Generated gen : gens) {
			if (gen.Exam.ID == examID) {
				if (!isExamRunning(gen)) {
					gen.Config.Response.add("Started", System.currentTimeMillis());
					return state.Data.StaticDB.updateGenerated(state.Toolchain, gen);
				}
				return true;
			}
		}
		return false;
	}

	private JsonValue getResultsFor(ProcessState state, int examID) {
		StaticDB sdb = state.Data.StaticDB;
		ExamCache cache = sdb.getExamsData(state.Toolchain);
		JsonArray ar = new JsonArray();
		if (cache.GeneratedByExamID.containsKey(examID)) {
			for (Generated gen : cache.GeneratedByExamID.get(examID)) {
				JsonObject obj = new JsonObject();
				obj.add("ID", gen.ID);
				obj.add("Login", gen.Login);
				obj.add("FullName", sdb.getUser(state.Toolchain.getName(), gen.Login).FullName);
				if (isExamRunning(gen)) {
					obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
				}
				if (gen.Config.Response.containsBoolean("Answered")) {
					if (gen.Config.Response.getBoolean("Answered").Value) {
						obj.add("Finished", true);
						obj.add("FinishedAt", gen.Config.Response.getNumber("AnswerTime"));
						obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
					}
				}
				if (!obj.contains("Finished") && isExamFinished(gen)) {
					obj.add("Timeouted", true);
					obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
					long started = gen.Config.Response.getNumber("Started").asLong();
					long examTime = gen.Exam.Config.ExamTime.longValue();
					obj.add("TimeoutedAt", new JsonNumber(0, "" + (started + examTime)));
				}
				obj.add("Details", gen.Config.Response);
				ar.add(obj);
			}
		}
		return ar;
	}

	private Generated getExam(List<Generated> gens, int examID) {
		for (Generated gen : gens) {
			if (examID == gen.Exam.ID || (examID == -1 && isExamRunning(gen))) {
				return gen;
			}
		}
		for (Generated gen : gens) {
			if (examID == -1 && !isExamRunning(gen)) {
				return gen;
			}
		}
		return null;
	}

	private JsonObject getGenerated(ProcessState state, Generated gen, boolean includeQuestionsByDefault) {
		boolean includeQuestions = includeQuestionsByDefault;
		JsonObject obj = new JsonObject();
		obj.add("Available", true);
		obj.add("ExamID", gen.Exam.ID);
		obj.add("ExamName", gen.Exam.Name);
		obj.add("TotalQuestions", gen.Config.GeneratedQuestions.length);
		obj.add("ExamTime", gen.Exam.Config.ExamTime);

		int totalPoints = 0;
		for (GeneratedQuestionConfig question : gen.Config.GeneratedQuestions) {
			totalPoints += question.Group.Config.evaluation;
		}

		obj.add("ExamTotalPoints", totalPoints);

		if (isExamFinished(gen)) {
			obj.add("ReadOnly", true);
			obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
			if (gen.Config.Response.containsNumber("AnswerTime") && gen.Config.Response.containsArray("Answer")) {
				obj.add("AnswerTime", gen.Config.Response.getNumber("AnswerTime"));
				obj.add("Answers", gen.Config.Response.getArray("Answer"));
			}
			includeQuestions = true;
		} else if (isExamRunning(gen)) {
			obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
			includeQuestions = true;
		}

		if (includeQuestions) {
			JsonArray qarr = new JsonArray();
			for (GeneratedQuestionConfig question : gen.Config.GeneratedQuestions) {
				JsonObject qobj = new JsonObject();
				qobj.add("ID", question.Question.ID);
				qobj.add("Name", question.Question.Name);
				qobj.add("Evaluation", question.Group.Config.evaluation);
				qobj.add("Config", question.Question.Config.get());
				qobj.add("Permutations", question.OptionsPermutation);
				qarr.add(qobj);
			}
			obj.add("Questions", qarr);
		}
		return obj;
	}

	private JsonObject getGenerated(ProcessState state, int generatedID, boolean includeQuestionsByDefault) {
		StaticDB sdb = state.Data.StaticDB;
		for (Generated gen : sdb.getExamsData(state.Toolchain).Generated) {
			if (gen.ID == generatedID) {
				return getGenerated(state, gen, includeQuestionsByDefault);
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("Available", false);
		return obj;
	}

	private JsonObject getExamForMe(ProcessState state, int examID) {
		StaticDB sdb = state.Data.StaticDB;
		List<Generated> lst = sdb.getGeneratedExam(state.Toolchain, state.getPermissions().Login);
		if (lst != null) {
			Generated gen = getExam(lst, examID);
			if (gen != null) {
				return getGenerated(state, gen, false);
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("Available", false);
		return obj;
	}

	private boolean finishExam(ProcessState state, int examID, JsonArray data) {
		List<Generated> gens = state.Data.StaticDB.getGeneratedExam(state.Toolchain, state.getPermissions().Login);
		for (Generated gen : gens) {
			if (gen.Exam.ID == examID) {
				if (isExamRunning(gen) && !isExamFinished(gen)) {
					gen.Config.Response.add("Answered", true);
					gen.Config.Response.add("Answer", data);
					gen.Config.Response.add("AnswerTime", System.currentTimeMillis());
					return state.Data.StaticDB.updateGenerated(state.Toolchain, gen);
				}
				return true;
			}
		}
		return false;
	}

	private JsonArray collectExams(StaticDB sdb, Toolchain toolchain) {
		JsonArray arr = new JsonArray();
		for (Exam exam : sdb.getExamsData(toolchain).Exams) {
			JsonObject obj = new JsonObject();
			obj.add("ID", exam.ID);
			obj.add("name", exam.Name);
			obj.add("config", exam.Config.get());
			arr.add(obj);
		}
		return arr;
	}

	private JsonValue collectQuestions(StaticDB sdb, Toolchain toolchain) {
		JsonArray arr = new JsonArray();
		for (Question question : sdb.getExamsData(toolchain).Questions) {
			JsonObject obj = new JsonObject();
			obj.add("ID", question.ID);
			obj.add("name", question.Name);
			obj.add("config", question.Config.get());
			arr.add(obj);
		}
		return arr;
	}

	private JsonValue collectGroups(StaticDB sdb, Toolchain toolchain) {
		JsonArray arr = new JsonArray();
		for (QuestionGroup group : sdb.getExamsData(toolchain).QuestionGroups) {
			JsonObject obj = new JsonObject();
			obj.add("ID", group.ID);
			obj.add("name", group.Name);
			obj.add("config", group.Config.get());
			arr.add(obj);
		}
		return arr;
	}

	private JsonArray collectQuestionTypes(StaticDB sdb, Toolchain toolchain) {
		JsonArray arr = new JsonArray();
		for (QuestionType opt : LayeredExamDB.QuestionType.values()) {
			JsonObject obj = new JsonObject();
			obj.add("ID", opt.ID);
			obj.add("name", opt.Name);
			arr.add(obj);
		}
		return arr;
	}

	private JsonObject collectData(StaticDB sdb, Toolchain toolchain, UsersPermission usersPermission) {
		JsonObject obj = new JsonObject();
		obj.add("exams", collectExams(sdb, toolchain));
		obj.add("groups", collectGroups(sdb, toolchain));
		obj.add("questions", collectQuestions(sdb, toolchain));
		obj.add("question_types", collectQuestionTypes(sdb, toolchain));
		return obj;
	}

	private static Map<String, PermissionBranch> reqPerms = new HashMap<>();

	private static final PermissionBranch pbSee = new PermissionBranch("WEB.EXAMS.SEE");

	protected JsonObject handleExamsEvent(ProcessState state, JsonObject obj) {
		String tc = state.Toolchain.getName();
		PermissionBranch requiredPermissions;
		if (!reqPerms.containsKey(tc)) {
			requiredPermissions = new PermissionBranch(tc + ".EXAMS");
			reqPerms.put(tc, requiredPermissions);
		} else {
			requiredPermissions = reqPerms.get(tc);
		}

		JsonObject result = new JsonObject();
		result.add("code", new JsonNumber(1));
		result.add("result", new JsonString("Invalid exam command"));

		boolean canAdmin = state.getPermissions().can(requiredPermissions);
		boolean canUser = state.getPermissions().can(pbSee);

		if (obj.containsString("exam_data")) {
			String exam_data = obj.getString("exam_data").Value;
			if (canAdmin && exam_data.equals("getData")) {
				result.add("code", new JsonNumber(0));
				result.add("result", new JsonString(collectData(state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString()));
			} else if (canAdmin && exam_data.equals("create_exam") && obj.containsString("name")) {
				if (state.Data.StaticDB.addExam(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
					result.add("code", 0);
					result.add("result", "created");
				} else {
					result.add("code", 1);
					result.add("result", "Exam could not be created");
				}
			} else if (canAdmin && exam_data.equals("create_group") && obj.containsString("name")) {
				if (state.Data.StaticDB.addQuestionGroup(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
					result.add("code", 0);
					result.add("result", "created");
				} else {
					result.add("code", 1);
					result.add("result", "Exam could not be created");
				}
			} else if (canAdmin && exam_data.equals("create_question") && obj.containsString("name")) {
				if (state.Data.StaticDB.addQuestion(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
					result.add("code", 0);
					result.add("result", "created");
				} else {
					result.add("code", 1);
					result.add("result", "Exam could not be created");
				}
			} else if (canAdmin && exam_data.equals("edit_exam") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID") && data.containsString("name") && data.containsObject("config")) {
					if (state.Data.StaticDB.updateExam(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
						result.add("code", 0);
						result.add("result", "created");
					} else {
						result.add("code", 1);
						result.add("result", "Question group could not be created");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Question group could not be created");
				}
			} else if (canAdmin && exam_data.equals("edit_group") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID") && data.containsString("name") && data.containsObject("config")) {
					if (state.Data.StaticDB.updateQuestionGroup(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
						result.add("code", 0);
						result.add("result", "created");
					} else {
						result.add("code", 1);
						result.add("result", "Exam could not be created");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Exam could not be created");
				}
			} else if (canAdmin && exam_data.equals("edit_question") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID") && data.containsString("name") && data.containsObject("config")) {
					if (state.Data.StaticDB.updateQuestion(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
						result.add("code", 0);
						result.add("result", "created");
					} else {
						result.add("code", 1);
						result.add("result", "Question could not be created");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Question could not be created");
				}
			} else if (canAdmin && exam_data.equals("del_exam") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID")) {
					if (state.Data.StaticDB.deleteExam(state.Toolchain, data.getNumber("ID").Value)) {
						result.add("code", 0);
						result.add("result", "deleted");
					} else {
						result.add("code", 1);
						result.add("result", "Exam could not be deleted");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Exam could not be deleted");
				}
			} else if (canAdmin && exam_data.equals("del_group") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID")) {
					if (state.Data.StaticDB.deleteQuestionGroup(state.Toolchain, data.getNumber("ID").Value)) {
						result.add("code", 0);
						result.add("result", "deleted");
					} else {
						result.add("code", 1);
						result.add("result", "Question group could not be deleted");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Question group could not be deleted");
				}
			} else if (canAdmin && exam_data.equals("del_question") && obj.containsObject("data")) {
				JsonObject data = obj.getObject("data");
				if (data.containsNumber("ID")) {
					if (state.Data.StaticDB.deleteQuestion(state.Toolchain, data.getNumber("ID").Value)) {
						result.add("code", 0);
						result.add("result", "deleted");
					} else {
						result.add("code", 1);
						result.add("result", "Question could not be deleted");
					}
				} else {
					result.add("code", 1);
					result.add("result", "Question could not be deleted");
				}
			} else if (canUser && exam_data.equals("get_exam")) {
				result.add("code", 0);
				result.add("result", new JsonString(getExamForMe(state, -1).getJsonString()));
			} else if (canUser && exam_data.equals("begin_exam") && obj.containsNumber("ID")) {
				if (startExam(state, obj.getNumber("ID").Value)) {
					result.add("code", 0);
					result.add("result", new JsonString(getExamForMe(state, obj.getNumber("ID").Value).getJsonString()));
				}
			} else if (canUser && exam_data.equals("finish_exam") && obj.containsNumber("ID") && obj.containsArray("Data")) {
				JsonArray data = obj.getArray("Data");
				if (finishExam(state, obj.getNumber("ID").Value, data)) {
					result.add("code", 0);
					result.add("result", new JsonString(getExamForMe(state, obj.getNumber("ID").Value).getJsonString()));
				}
			} else if (canAdmin && exam_data.equals("get_results") && obj.containsNumber("ID")) {
				result.add("code", 0);
				result.add("result", new JsonString(getResultsFor(state, obj.getNumber("ID").Value).getJsonString()));
			} else if (canAdmin && exam_data.equals("get_result_by_id") && obj.containsNumber("ID")) {
				result.add("code", 0);
				result.add("result", new JsonString(getGenerated(state, obj.getNumber("ID").Value, true).getJsonString()));
			}
		}
		return result;
	}
}
