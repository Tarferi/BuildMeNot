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
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.GeneratedQuestionEvaluation;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.Question;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.QuestionGroup;
import cz.rion.buildserver.db.layers.staticDB.LayeredExamDB.QuestionType;
import cz.rion.buildserver.db.layers.staticDB.LayeredPermissionDB.UsersPermission;
import cz.rion.buildserver.db.layers.staticDB.LayeredUserDB.LocalUser;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonNumber;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.permissions.PermissionBranch;
import cz.rion.buildserver.utils.ToolchainedPermissionCache;

public class StatelessExamClient extends StatelessProjectClient {

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
				LocalUser user = sdb.getUser(state.Toolchain, gen.Login);
				if (user == null) { // Should never happen
					continue;
				}
				obj.add("FullName", user.FullName);
				if (isExamRunning(gen)) {
					obj.add("StartedAt", gen.Config.Response.getNumber("Started"));
				}
				if (gen.Config.EvaluationPublished) {
					obj.add("Evaluated", true);
					obj.add("EvaluatedAt", gen.Config.EvaluationPublishedTime);
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

	private Generated getGenerated(List<Generated> gens, int generatedID) {
		for (Generated gen : gens) {
			if (gen.ID == generatedID) {
				return gen;
			}
		}
		return null;
	}

	private Generated getExam(List<Generated> gens, int examID) {
		for (Generated gen : gens) {
			if (examID == gen.Exam.ID || (examID == -1 && isExamRunning(gen))) {
				return gen;
			}
		}
		for (Generated gen : gens) {
			if (examID == -1 && !isExamRunning(gen) && !isExamFinished(gen)) {
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

	private JsonObject getGenerated(ProcessState state, Generated gen, boolean includeQuestionsByDefault, boolean includeUnpublishedEvaluation) {
		boolean includeQuestions = includeQuestionsByDefault;
		boolean includeEvaluation = gen.Config.EvaluationPublished || includeUnpublishedEvaluation;
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
			obj.add("EvaluationAvailable", gen.Config.EvaluationPublished);
			if (includeEvaluation) {
				obj.add("EvaluatedAt", gen.Config.EvaluationPublishedTime);
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
				if (includeEvaluation) {
					qobj.add("EvaluationData", question.Evaluation.get());
				}
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
				return getGenerated(state, gen, includeQuestionsByDefault, true);
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
				return getGenerated(state, gen, false, false);
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

	private static boolean isInt(String str) {
		try {
			Integer.parseInt(str);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

	private JsonValue saveEvaluation(ProcessState state, int generatedID, JsonArray evals, boolean publish) {
		int code = 1;
		boolean error = false;
		String description = "No such exam";
		Generated gen = getGenerated(state.Data.StaticDB.getExamsData(state.Toolchain).Generated, generatedID);
		if (gen != null) {
			int totalQuestionsCount = gen.Config.GeneratedQuestions.length;
			int totalEvaluatedCount = 0;

			for (JsonValue eval : evals.Value) {
				if (eval.isObject()) {
					JsonObject o = eval.asObject();
					if (o.containsString("eval") && o.containsString("comment") && o.containsNumber("ID")) {
						String pointStr = o.getString("eval").Value.trim();
						String comment = o.getString("comment").Value;
						int ID = o.getNumber("ID").Value;
						if (pointStr.isEmpty()) {
							continue;
						}
						if (pointStr.indexOf('.') >= 0) {
							String pre = pointStr.split("\\.")[0];
							String post = pointStr.split("\\.")[1];
							if (!isInt(pre) || !isInt(post)) {
								code = 1;
								description = "Invalid evaluation for question " + ID;
								error = true;
								break;
							} else {
								int postI = Integer.parseInt(post);
								int preI = Integer.parseInt(pre);
								pointStr = postI == 0 ? preI + "" : preI + "," + postI;
							}
						} else {
							if (!isInt(pointStr)) {
								code = 1;
								description = "Invalid evaluation for question " + ID;
								error = true;
								break;
							} else {
								pointStr = Integer.parseInt(pointStr) + "";
							}
						}

						GeneratedQuestionEvaluation data = gen.Config.getEvaluation(ID);
						if (data == null) {
							code = 1;
							description = "Evaluating question (" + ID + ") that is not a part of generated exam";
							error = true;
							break;
						}
						GeneratedQuestionEvaluation newData = data.getUpdatedInstance(state.getPermissions().Login, pointStr, comment);
						if (!gen.Config.update(ID, newData)) {
							code = 1;
							description = "Evaluating question (" + ID + ") that is not a part of generated exam";
							error = true;
							break;
						}
						totalEvaluatedCount++;
					}
				}
			}

			if (!error) {
				description = "Saved";
			}
			if (!error && publish && totalQuestionsCount != totalEvaluatedCount) {
				code = 0;
				description = "Saved, but not published. Missing " + (totalQuestionsCount - totalEvaluatedCount) + " evaluated questions";
				publish = false;
			}
			if (!error) {
				gen = gen.getUpdatedInstance(publish);
				if (!state.Data.StaticDB.updateGenerated(state.Toolchain, gen)) {
					code = 1;
					description = "Database error";
				}
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("code", code);
		obj.add("details", description);
		return obj;
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

	private static final ToolchainedPermissionCache pbMap = new ToolchainedPermissionCache("WEB.EXAMS.SEE");

	private final Object syncer = new Object();

	protected JsonObject handleExamsEvent(ProcessState state, JsonObject obj) {
		JsonObject idata = new JsonObject();

		synchronized (syncer) {
			String tc = state.Toolchain.getName();
			PermissionBranch requiredPermissions;
			if (!reqPerms.containsKey(tc)) {
				requiredPermissions = new PermissionBranch(state.Toolchain, tc + ".EXAMS");
				reqPerms.put(tc, requiredPermissions);
			} else {
				requiredPermissions = reqPerms.get(tc);
			}

			JsonObject result = new JsonObject();
			result.add("code", new JsonNumber(1));
			result.add("result", new JsonString("Invalid exam command"));

			boolean canAdmin = state.getPermissions().can(requiredPermissions);
			boolean canUser = state.getPermissions().can(pbMap.toBranch(state.Toolchain));

			idata.add("toolchain", tc);
			idata.add("canAdmin", canAdmin);
			idata.add("canUser", canUser);
			idata.add("success", false);
			idata.add("reason", "unknown");
			if (obj.containsString("exam_data")) {
				String exam_data = obj.getString("exam_data").Value;
				idata.add("action", exam_data);
				if (canAdmin && exam_data.equals("getData")) {
					idata.add("success", true);
					result.add("code", new JsonNumber(0));
					result.add("result", new JsonString(collectData(state.Data.StaticDB, state.Toolchain, state.getPermissions()).getJsonString()));
				} else if (canAdmin && exam_data.equals("create_exam") && obj.containsString("name")) {
					idata.add("name", obj.getString("name").Value);
					if (state.Data.StaticDB.addExam(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
						result.add("code", 0);
						result.add("result", "created");
						idata.add("success", true);
					} else {
						result.add("code", 1);
						result.add("result", "Exam could not be created");
					}
				} else if (canAdmin && exam_data.equals("create_group") && obj.containsString("name")) {
					idata.add("name", obj.getString("name").Value);
					if (state.Data.StaticDB.addQuestionGroup(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
						result.add("code", 0);
						result.add("result", "created");
						idata.add("success", true);
					} else {
						result.add("code", 1);
						result.add("result", "Exam could not be created");
					}
				} else if (canAdmin && exam_data.equals("create_question") && obj.containsString("name")) {
					idata.add("name", obj.getString("name").Value);
					if (state.Data.StaticDB.addQuestion(state.Toolchain, obj.getString("name").Value, new JsonObject())) {
						result.add("code", 0);
						result.add("result", "created");
						idata.add("success", true);
					} else {
						result.add("code", 1);
						result.add("result", "Exam could not be created");
					}
				} else if (canAdmin && exam_data.equals("edit_exam") && obj.containsObject("data")) {
					JsonObject data = obj.getObject("data");
					if (data.containsNumber("ID") && data.containsString("name") && data.containsObject("config")) {
						idata.add("ID", data.getNumber("ID").Value);
						idata.add("name", data.getString("name").Value);
						idata.add("config", data.getObject("config"));
						if (state.Data.StaticDB.updateExam(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
							result.add("code", 0);
							result.add("result", "created");
							idata.add("success", true);
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
						idata.add("ID", data.getNumber("ID").Value);
						idata.add("name", data.getString("name").Value);
						idata.add("config", data.getObject("config"));
						if (state.Data.StaticDB.updateQuestionGroup(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
							result.add("code", 0);
							result.add("result", "created");
							idata.add("success", true);
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
						idata.add("ID", data.getNumber("ID").Value);
						idata.add("name", data.getString("name").Value);
						idata.add("config", data.getObject("config"));
						if (state.Data.StaticDB.updateQuestion(state.Toolchain, data.getNumber("ID").Value, data.getString("name").Value, data.getObject("config"))) {
							result.add("code", 0);
							result.add("result", "created");
							idata.add("success", true);
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
						idata.add("ID", data.getNumber("ID").Value);
						if (state.Data.StaticDB.deleteExam(state.Toolchain, data.getNumber("ID").Value)) {
							result.add("code", 0);
							result.add("result", "deleted");
							idata.add("success", true);
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
						idata.add("ID", data.getNumber("ID").Value);
						if (state.Data.StaticDB.deleteQuestionGroup(state.Toolchain, data.getNumber("ID").Value)) {
							result.add("code", 0);
							result.add("result", "deleted");
							idata.add("success", true);
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
						idata.add("ID", data.getNumber("ID").Value);
						if (state.Data.StaticDB.deleteQuestion(state.Toolchain, data.getNumber("ID").Value)) {
							result.add("code", 0);
							result.add("result", "deleted");
							idata.add("success", true);
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
					idata.add("success", true);
				} else if (canUser && exam_data.equals("begin_exam") && obj.containsNumber("ID")) {
					if (startExam(state, obj.getNumber("ID").Value)) {
						idata.add("ID", obj.getNumber("ID").Value);
						result.add("code", 0);
						result.add("result", new JsonString(getExamForMe(state, obj.getNumber("ID").Value).getJsonString()));
						idata.add("success", true);
					}
				} else if (canUser && exam_data.equals("finish_exam") && obj.containsNumber("ID") && obj.containsArray("Data")) {
					JsonArray data = obj.getArray("Data");
					idata.add("ID", obj.getNumber("ID").Value);
					idata.add("data", data);
					if (finishExam(state, obj.getNumber("ID").Value, data)) {
						result.add("code", 0);
						result.add("result", new JsonString(getExamForMe(state, obj.getNumber("ID").Value).getJsonString()));
						idata.add("success", true);
					}
				} else if (canAdmin && exam_data.equals("get_results") && obj.containsNumber("ID")) {
					idata.add("ID", obj.getNumber("ID").Value);
					result.add("code", 0);
					result.add("result", new JsonString(getResultsFor(state, obj.getNumber("ID").Value).getJsonString()));
					idata.add("success", true);
				} else if (canAdmin && exam_data.equals("get_result_by_id") && obj.containsNumber("ID")) {
					idata.add("ID", obj.getNumber("ID").Value);
					result.add("code", 0);
					result.add("result", new JsonString(getGenerated(state, obj.getNumber("ID").Value, true).getJsonString()));
					idata.add("success", true);
				} else if (canAdmin && exam_data.equals("eval_results") && obj.containsObject("data") && obj.containsNumber("ID")) {
					int id = obj.getNumber("ID").Value; // ID of generated entry
					JsonObject data = obj.getObject("data");
					idata.add("ID", obj.getNumber("ID").Value);
					idata.add("data", data);
					if (data.containsBoolean("save") && data.containsArray("evals")) {
						boolean publish = data.getBoolean("save").Value;
						JsonArray evals = data.getArray("evals");
						result.add("code", 0);
						result.add("result", new JsonString(saveEvaluation(state, id, evals, publish).getJsonString()));
						idata.add("success", true);
					}
				}
			}
			state.setIntention(Intention.EXAM_COMMAND, idata);
			return result;
		}
	}
}
