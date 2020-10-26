package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;

public abstract class LayeredExamDB extends LayeredStaticEndpointDB {

	public static enum QuestionType {
		Optioned(0, "Výbìr"), OptionedWithCustom(1, "Výbìr vèetnì vlastní možnosti"), PlainSingleLine(2, "Jeden øádek"), PlainMultiLine(3, "Více øádkù");

		public final int ID;
		public final String Name;
		public static final int Total = QuestionType.values().length;

		private QuestionType(int ID, String name) {
			this.ID = ID;
			this.Name = name;
		}
	}

	public LayeredExamDB(DatabaseInitData dbData) throws DatabaseException {
		super(dbData);
		this.makeTable("ex_terms", KEY("ID"), TEXT("name"), BIGTEXT("config"), BIGTEXT("participants"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("ex_question_groups", KEY("ID"), TEXT("name"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("ex_questions", KEY("ID"), TEXT("name"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.makeTable("ex_generated", KEY("ID"), NUMBER("exam_id"), TEXT("login"), BIGTEXT("config"), NUMBER("valid"), TEXT("toolchain"));
		this.registerVirtualFile(generateExamsVF);
	}

	private final VirtualFile generateExamsVF = new VirtualFile() {

		private String lastcontents = "";

		private void setDefaultContents() {
			StringBuilder sb = new StringBuilder();
			sb.append("# Generovani zadani\n\n");
			sb.append("# Nasledujici radky je treba odkomentovat a doplnit spravne hodnoty\n\n");
			sb.append("#toolchain:<nazev_toolchainu>\n");
			sb.append("#exam_id:<id_exam>\n");
			sb.append("#generate");
			lastcontents = sb.toString();
		}

		@Override
		public String read() throws DatabaseException {
			if (lastcontents.trim().isEmpty()) {
				setDefaultContents();
			}
			return lastcontents;
		}

		@Override
		public void write(String data) throws DatabaseException {
			if (data.trim().isEmpty()) {
				setDefaultContents();
			} else {
				String exam_id = null;
				String toolchain = null;
				boolean generate = false;
				for (String line : data.split("\n")) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					if (line.startsWith("exam_id:")) {
						exam_id = line.split(":", 2)[1].trim();
					} else if (line.startsWith("toolchain:")) {
						toolchain = line.split(":", 2)[1].trim();
					} else if (line.equals("generate")) {
						generate = true;
					}
				}
				if (generate && exam_id != null && toolchain != null) {
					int ei = 0;
					try {
						ei = Integer.parseInt(exam_id);
					} catch (Exception e) {
						lastcontents = "# Invalid exam_id: " + exam_id + "\n\n" + data;
						return;
					}

					StaticDB sdb = (StaticDB) LayeredExamDB.this;
					Toolchain tc = null;
					try {
						tc = sdb.getToolchain(toolchain);
					} catch (NoSuchToolchainException e) {
						lastcontents = "# No such toolchain: " + toolchain + "\n\n" + data;
						return;
					}
					if (tc == null) {
						lastcontents = "# No such toolchain: " + toolchain + "\n\n" + data;
						return;
					}
					StringBuilder log = new StringBuilder();
					if (sdb.generateExams(tc, ei, log)) {
						lastcontents = "# Generating finished OK\n\n" + data;
					} else {
						lastcontents = "# Generating failed: \n" + log.toString() + "\n\n" + data;
					}
				}
			}
		}

		@Override
		public String getName() {
			return "exams/generate.exe";
		}

	};

	private abstract static class JsonConfig {

		protected static int[] getIA(JsonObject obj, String name) {
			if (obj.containsArray(name)) {
				JsonArray ar = obj.getArray(name);
				int i = 0;
				int[] res = new int[ar.Value.size()];
				for (JsonValue val : ar.Value) {
					if (val.isNumber()) {
						int v = val.asNumber().Value;
						res[i] = v;
						i++;
					}
				}
				if (i != res.length) {
					int[] nres = new int[i];
					System.arraycopy(res, 0, nres, 0, nres.length);
					res = nres;
				}
				return res;
			}
			return new int[0];
		}

		protected static String[] getSA(JsonObject obj, String name) {
			if (obj.containsArray(name)) {
				JsonArray ar = obj.getArray(name);
				int i = 0;
				String[] res = new String[ar.Value.size()];
				for (JsonValue val : ar.Value) {
					if (val.isString()) {
						String v = val.asString().Value;
						res[i] = v;
						i++;
					}
				}
				if (i != res.length) {
					String[] nres = new String[i];
					System.arraycopy(res, 0, nres, 0, nres.length);
					res = nres;
				}
				return res;
			}
			return new String[0];
		}

		protected static Long getL(JsonObject obj, String name) {
			if (obj.containsNumber(name)) {
				return obj.getNumber(name).asLong();
			} else {
				return null;
			}
		}

		protected static void get(JsonObject obj, String name, int[] value) {
			JsonArray ar = new JsonArray();
			for (int val : value) {
				ar.add(val);
			}
			obj.add(name, ar);
		}

		protected static void get(JsonObject obj, String name, String[] value) {
			JsonArray ar = new JsonArray();
			for (String val : value) {
				ar.add(val);
			}
			obj.add(name, ar);
		}

		protected static String getS(JsonObject obj, String name) {
			if (obj.containsString(name)) {
				return obj.getString(name).Value;
			}
			return null;
		}

		public static Integer getI(JsonObject obj, String name) {
			if (obj.containsNumber(name)) {
				return obj.getNumber(name).Value;
			}
			return null;
		}

		protected static void get(JsonObject obj, String name, Long value) {
			if (value != null) {
				obj.add(name, (long) value);
			}
		}

		protected static void get(JsonObject obj, String name, String value) {
			if (value != null) {
				obj.add(name, value);
			}
		}

		protected static void get(JsonObject obj, String name, boolean value) {
			obj.add(name, value);
		}

		protected void get(JsonObject obj, String name, int value) {
			obj.add(name, value);
		}

		public abstract JsonValue get();
	}

	public static final class ExamConfig extends JsonConfig {

		public final Long VisibleSince;
		public final Long VisibleUntil;
		public final Long TurnableSince;
		public final Long TurnableUntil;
		public final Long ExamTime;
		public final QuestionGroup[] groups;

		private ExamConfig(Long visSince, Long visUntil, Long turnableSince, Long turnableUntil, Long examTime, QuestionGroup[] groups) {
			this.VisibleSince = visSince;
			this.VisibleUntil = visUntil;
			this.TurnableSince = turnableSince;
			this.TurnableUntil = turnableUntil;
			this.ExamTime = examTime;
			this.groups = groups;
		}

		private static int[] get(QuestionGroup[] q) {
			int[] r = new int[q.length];
			for (int i = 0; i < r.length; i++) {
				r[i] = q[i].ID;
			}
			return r;
		}

		private static QuestionGroup[] get(int[] q, Map<Integer, QuestionGroup> qgmap) {
			QuestionGroup[] qa = new QuestionGroup[q.length];
			int i = 0;
			for (int qi : q) {
				if (qgmap.containsKey(qi)) {
					qa[i] = qgmap.get(qi);
					i++;
				}
			}
			if (i != qa.length) {
				QuestionGroup[] nqa = new QuestionGroup[i];
				System.arraycopy(qa, 0, nqa, 0, nqa.length);
				qa = nqa;
			}
			return qa;
		}

		private static ExamConfig get(JsonValue val, Map<Integer, QuestionGroup> qgmap) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				Long visSince = getL(obj, "visible_since");
				Long visUntil = getL(obj, "visible_until");
				Long turnSince = getL(obj, "turn_since");
				Long turnUntil = getL(obj, "turn_until");
				Long examtime = getL(obj, "exam_time");
				int[] groups = getIA(obj, "groups");
				return new ExamConfig(visSince, visUntil, turnSince, turnUntil, examtime, get(groups, qgmap));
			}
			return null;
		}

		@Override
		public JsonValue get() {
			JsonObject obj = new JsonObject();
			get(obj, "visible_since", VisibleSince);
			get(obj, "visible_until", VisibleUntil);
			get(obj, "turn_since", TurnableSince);
			get(obj, "turn_until", TurnableUntil);
			get(obj, "exam_time", ExamTime);
			get(obj, "groups", get(groups));
			return obj;
		}
	}

	public static final class Exam {
		public final int ID;
		public final String Name;
		public final ExamConfig Config;
		public final Toolchain Toolchain;
		public final String[] Participants;

		private Exam(int ID, String name, ExamConfig config, String[] participants, Toolchain toolchain) {
			this.ID = ID;
			this.Name = name;
			this.Config = config;
			this.Toolchain = toolchain;
			this.Participants = participants;
		}
	}

	public static final class QuestionGroupConfig extends JsonConfig {
		public final Question[] questions;
		public final Integer evaluation;

		private QuestionGroupConfig(Question[] questions, Integer evaluation) {
			this.questions = questions;
			this.evaluation = evaluation == null ? 0 : evaluation;
		}

		private static int[] get(Question[] q) {
			int[] r = new int[q.length];
			for (int i = 0; i < r.length; i++) {
				r[i] = q[i].ID;
			}
			return r;
		}

		private static Question[] get(int[] q, Map<Integer, Question> qmap) {
			Question[] qa = new Question[q.length];
			int i = 0;
			for (int qi : q) {
				if (qmap.containsKey(qi)) {
					qa[i] = qmap.get(qi);
					i++;
				}
			}
			if (i != qa.length) {
				Question[] nqa = new Question[i];
				System.arraycopy(qa, 0, nqa, 0, nqa.length);
				qa = nqa;
			}
			return qa;
		}

		private static QuestionGroupConfig get(JsonValue val, Map<Integer, Question> qmap) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				int[] q = getIA(obj, "questions");
				Integer eval = getI(obj, "evaluation");
				return new QuestionGroupConfig(get(q, qmap), eval);
			}
			return null;
		}

		@Override
		public JsonValue get() {
			JsonObject obj = new JsonObject();
			get(obj, "questions", get(questions));
			get(obj, "evaluation", evaluation);
			return obj;
		}
	}

	public static final class QuestionGroup {
		public final int ID;
		public final String Name;
		public final QuestionGroupConfig Config;
		public final Toolchain Toolchain;

		private QuestionGroup(int ID, String name, QuestionGroupConfig config, Toolchain toolchain) {
			this.ID = ID;
			this.Name = name;
			this.Config = config;
			this.Toolchain = toolchain;
		}
	}

	public static final class QuestionConfig extends JsonConfig {
		public final String Description;
		public final QuestionType Type;
		public final String Options[];
		public final boolean AllowMultiple;

		private QuestionConfig(String description, QuestionType type, String[] options, boolean allowMultiple) {
			this.Description = description;
			this.Type = type;
			this.Options = options;
			this.AllowMultiple = allowMultiple;
		}

		private static QuestionConfig get(JsonValue val) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				String descr = getS(obj, "description");
				Integer type = getI(obj, "type");
				QuestionType Type;
				if (type == null) {
					Type = QuestionType.PlainSingleLine;
				} else if (type > QuestionType.Total) {
					Type = QuestionType.PlainSingleLine;
				} else {
					Type = QuestionType.values()[type];
				}
				boolean multiple = false;
				if (obj.containsBoolean("multiple")) {
					multiple = obj.getBoolean("multiple").Value;
				}
				String[] opts = getSA(obj, "options");
				return new QuestionConfig(descr, Type, opts, multiple);
			}
			return null;
		}

		@Override
		public JsonValue get() {
			JsonObject obj = new JsonObject();
			get(obj, "description", Description);
			get(obj, "type", Type.ID);
			get(obj, "options", Options);
			get(obj, "multiple", AllowMultiple);
			return obj;
		}
	}

	public static final class Question {
		public final int ID;
		public final String Name;
		public final QuestionConfig Config;
		public final Toolchain Toolchain;

		private Question(int ID, String name, QuestionConfig config, Toolchain toolchain) {
			this.ID = ID;
			this.Name = name;
			this.Config = config;
			this.Toolchain = toolchain;
		}
	}

	public static final class GeneratedQuestionEvaluation extends JsonConfig {

		public final String EvaluatorLogin;
		public final Long EvaluatedAt;
		public final String Points;
		public final String Comment;

		private GeneratedQuestionEvaluation(String evaluatorLogin, Long evaluatedAt, String points, String comment) {
			this.EvaluatedAt = evaluatedAt;
			this.EvaluatorLogin = evaluatorLogin;
			this.Points = points;
			this.Comment = comment;
		}

		public static GeneratedQuestionEvaluation get(JsonObject evalStr) {
			String login = evalStr.containsString("login") ? evalStr.getString("login").Value : "";
			long evalAt = evalStr.containsNumber("time") ? evalStr.getNumber("time").Value : 0;
			String points = evalStr.containsString("points") ? evalStr.getString("points").Value : "0";
			String comment = evalStr.containsString("comment") ? evalStr.getString("comment").Value : "";
			return new GeneratedQuestionEvaluation(login, evalAt, points, comment);
		}

		@Override
		public JsonValue get() {
			JsonObject obj = new JsonObject();
			obj.add("login", EvaluatorLogin);
			obj.add("time", EvaluatedAt);
			obj.add("points", Points);
			obj.add("comment", Comment);
			return obj;
		}

		public GeneratedQuestionEvaluation getUpdatedInstance(String login, String points, String comment) {
			return new GeneratedQuestionEvaluation(login, System.currentTimeMillis(), points, comment);
		}

	}

	public static final class GeneratedQuestionConfig extends JsonConfig {
		public final Question Question;
		public final QuestionGroup Group;
		public final String OptionsPermutation;
		public final GeneratedQuestionEvaluation Evaluation;

		private GeneratedQuestionConfig(Question question, QuestionGroup questionGroup, String permutation, GeneratedQuestionEvaluation evaluation) {
			this.Question = question;
			this.Group = questionGroup;
			this.OptionsPermutation = permutation;
			this.Evaluation = evaluation;
		}

		public static GeneratedQuestionConfig get(JsonValue val, Map<Integer, Question> qmap, Map<Integer, QuestionGroup> qgmap) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				int qi = getI(obj, "question");
				int qgi = getI(obj, "group");
				String permutation = getS(obj, "permutation");
				if (qmap.containsKey(qi) && qgmap.containsKey(qgi)) {
					Question q = qmap.get(qi);
					QuestionGroup qg = qgmap.get(qgi);

					JsonObject evalStr = val.asObject().containsObject("evaluation") ? val.asObject().getObject("evaluation") : new JsonObject();
					GeneratedQuestionEvaluation eval = GeneratedQuestionEvaluation.get(evalStr);

					return new GeneratedQuestionConfig(q, qg, permutation, eval);
				}
			}
			return null;
		}

		@Override
		public JsonValue get() {
			return get(true);
		}

		private JsonValue get(boolean addEvaluation) {
			JsonObject obj = new JsonObject();
			get(obj, "question", Question.ID);
			get(obj, "group", Group.ID);
			get(obj, "permutation", OptionsPermutation);
			if (addEvaluation) {
				obj.add("evaluation", Evaluation.get());
			}
			return obj;
		}

		public GeneratedQuestionConfig getUpdatedInstance(GeneratedQuestionEvaluation newData) {
			return new GeneratedQuestionConfig(Question, Group, OptionsPermutation, newData);
		}
	}

	public static final class GeneratedConfig extends JsonConfig {
		public final GeneratedQuestionConfig[] GeneratedQuestions;
		public final JsonObject Response;
		public final boolean EvaluationPublished;
		public final Long EvaluationPublishedTime;

		private GeneratedConfig(GeneratedQuestionConfig[] generatedQuestions, JsonObject response, boolean evaluationPublished, Long evaluationPublishedTime) {
			this.GeneratedQuestions = generatedQuestions;
			this.Response = response;
			this.EvaluationPublished = evaluationPublished;
			this.EvaluationPublishedTime = evaluationPublishedTime;
		}

		private static GeneratedConfig get(JsonValue val, Map<Integer, Question> qmap, Map<Integer, QuestionGroup> qgmap) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsArray("questions") && obj.containsObject("responses")) {
					GeneratedQuestionConfig[] qc = new GeneratedQuestionConfig[obj.getArray("questions").Value.size()];
					int i = 0;
					for (JsonValue v : obj.getArray("questions").Value) {
						GeneratedQuestionConfig gc = GeneratedQuestionConfig.get(v, qmap, qgmap);
						if (gc == null) {
							return null;
						}
						qc[i] = gc;
						i++;
					}
					JsonObject resp = obj.getObject("responses");
					boolean publish = obj.containsBoolean("publish") ? obj.getBoolean("publish").Value : false;
					long pubTime = obj.containsNumber("publishedAt") ? obj.getNumber("publishedAt").asLong() : 0;
					return new GeneratedConfig(qc, resp, publish, pubTime);
				}
			}
			return null;
		}

		@Override
		public JsonValue get() {
			return get(true);
		}

		private JsonValue get(boolean addEvaluation) {
			JsonObject obj = new JsonObject();
			JsonArray arr = new JsonArray();
			for (GeneratedQuestionConfig conf : GeneratedQuestions) {
				arr.add(conf.get(addEvaluation));
			}
			obj.add("questions", arr);
			obj.add("responses", Response);
			if (addEvaluation) {
				obj.add("publish", EvaluationPublished);
				obj.add("publishedAt", EvaluationPublishedTime);
			}
			return obj;
		}

		public GeneratedQuestionEvaluation getEvaluation(int ID) {
			for (int i = 0; i < GeneratedQuestions.length; i++) {
				GeneratedQuestionConfig q = GeneratedQuestions[i];
				if (q.Question.ID == ID) {
					return q.Evaluation;
				}
			}
			return null;
		}

		public boolean update(int ID, GeneratedQuestionEvaluation newData) {
			for (int i = 0; i < GeneratedQuestions.length; i++) {
				GeneratedQuestionConfig q = GeneratedQuestions[i];
				if (q.Question.ID == ID) {
					q = q.getUpdatedInstance(newData);
					GeneratedQuestions[i] = q;
					return true;
				}
			}
			return false;
		}

		public GeneratedConfig getUpdateInstance(boolean publish) {
			return new GeneratedConfig(GeneratedQuestions, Response, publish, System.currentTimeMillis());
		}
	}

	public static final class Generated {
		public final int ID;
		public final String Login;
		public final Exam Exam;
		public final GeneratedConfig Config;

		public Generated(int id, Exam exam, String login, GeneratedConfig config) {
			this.ID = id;
			this.Login = login;
			this.Exam = exam;
			this.Config = config;
		}

		public Generated getUpdatedInstance(boolean publish) {
			return new Generated(ID, Exam, Login, Config.getUpdateInstance(publish));
		}

	}

	public final class ExamCache {
		private final LayeredExamDB db;
		private final Toolchain tc;

		public final Map<Integer, Question> QuestionsByID = new HashMap<>();
		public final Map<Integer, QuestionGroup> QuestionGroupsByID = new HashMap<>();
		public final Map<Integer, Exam> ExamsByID = new HashMap<>();
		public final Map<String, List<Generated>> GeneratedByUserLogin = new HashMap<>();
		public final Map<Integer, List<Generated>> GeneratedByExamID = new HashMap<>();

		public final List<Question> Questions;
		public final List<QuestionGroup> QuestionGroups;
		public final List<Exam> Exams;
		public final List<Generated> Generated;

		public ExamCache(Toolchain tc) {
			db = LayeredExamDB.this;
			this.tc = tc;
			this.Questions = db.getQuestions(tc);
			for (Question question : Questions) {
				QuestionsByID.put(question.ID, question);
			}
			this.QuestionGroups = db.getQuestionGroups(tc, QuestionsByID);
			for (QuestionGroup questionGroup : QuestionGroups) {
				QuestionGroupsByID.put(questionGroup.ID, questionGroup);
			}
			this.Exams = db.getTerms(tc, QuestionGroupsByID);
			for (Exam exam : Exams) {
				ExamsByID.put(exam.ID, exam);
			}
			this.Generated = db.getGenerated(tc, ExamsByID, QuestionsByID, QuestionGroupsByID);
			for (Generated gen : this.Generated) {
				List<Generated> lstByExamID = null;
				if (!GeneratedByExamID.containsKey(gen.Exam.ID)) {
					lstByExamID = new ArrayList<>();
					GeneratedByExamID.put(gen.Exam.ID, lstByExamID);
				} else {
					lstByExamID = GeneratedByExamID.get(gen.Exam.ID);
				}
				lstByExamID.add(gen);

				List<Generated> lstByUserLogin = null;
				if (!GeneratedByUserLogin.containsKey(gen.Login)) {
					lstByUserLogin = new ArrayList<>();
					GeneratedByUserLogin.put(gen.Login, lstByUserLogin);
				} else {
					lstByUserLogin = GeneratedByUserLogin.get(gen.Login);
				}
				lstByUserLogin.add(gen);
			}
		}
	}

	private final CachedToolchainData2<ExamCache> cache = new CachedToolchainDataWrapper2<ExamCache>(300, new CachedToolchainDataGetter2<ExamCache>() {

		@Override
		public CachedData<ExamCache> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper2<ExamCache>(refreshIntervalInSeconds, new CachedDataGetter<ExamCache>() {

				@Override
				public ExamCache update() {
					return new ExamCache(toolchain);
				}
			});
		}
	});

	private List<Question> getQuestions(Toolchain tc) {
		List<Question> lst = new ArrayList<>();
		final String tableName = "ex_questions";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "config") }, true, new ComparisionField(getField(tableName, "toolchain"), tc.getName()), new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue v : res.Value) {
				if (v.isObject()) {
					JsonObject obj = v.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("config")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String config = obj.getString("config").Value;
						JsonValue cv = JsonValue.parse(config);
						if (cv != null) {
							QuestionConfig qc = QuestionConfig.get(cv);
							if (qc != null) {
								Question q = new Question(id, name, qc, tc);
								lst.add(q);
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;
	}

	public ExamCache getExamsData(Toolchain toolchain) {
		return cache.get(toolchain);
	}

	public List<Generated> getGeneratedExam(Toolchain toolchain, String login) {
		return cache.get(toolchain).GeneratedByUserLogin.get(login);
	}

	private List<QuestionGroup> getQuestionGroups(Toolchain tc, Map<Integer, Question> qmap) {
		List<QuestionGroup> lst = new ArrayList<>();
		final String tableName = "ex_question_groups";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "config") }, true, new ComparisionField(getField(tableName, "toolchain"), tc.getName()), new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue v : res.Value) {
				if (v.isObject()) {
					JsonObject obj = v.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("config")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String config = obj.getString("config").Value;
						JsonValue cv = JsonValue.parse(config);
						if (cv != null) {
							QuestionGroupConfig qc = QuestionGroupConfig.get(cv, qmap);
							if (qc != null) {
								QuestionGroup qg = new QuestionGroup(id, name, qc, tc);
								lst.add(qg);
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;
	}

	private List<Exam> getTerms(Toolchain tc, Map<Integer, QuestionGroup> qgmap) {
		List<Exam> lst = new ArrayList<>();
		final String tableName = "ex_terms";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "name"), getField(tableName, "config"), getField(tableName, "participants") }, true, new ComparisionField(getField(tableName, "toolchain"), tc.getName()), new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue v : res.Value) {
				if (v.isObject()) {
					JsonObject obj = v.asObject();
					if (obj.containsNumber("ID") && obj.containsString("name") && obj.containsString("config") && obj.containsString("participants")) {
						int id = obj.getNumber("ID").Value;
						String name = obj.getString("name").Value;
						String config = obj.getString("config").Value;
						String[] pcp = obj.getString("participants").Value.split("\n");
						JsonValue cv = JsonValue.parse(config);
						String[] participants = new String[pcp.length];
						int pi = 0;
						for (String pcps : pcp) {
							pcps = pcps.trim();
							if (!pcps.isEmpty()) {
								participants[pi] = pcps;
								pi++;
							}
						}
						if (pi != participants.length) {
							String[] np = new String[pi];
							System.arraycopy(participants, 0, np, 0, np.length);
							participants = np;
						}

						if (cv != null) {
							ExamConfig qc = ExamConfig.get(cv, qgmap);
							if (qc != null) {
								Exam qg = new Exam(id, name, qc, participants, tc);
								lst.add(qg);
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;
	}

	private List<Generated> getGenerated(Toolchain tc, Map<Integer, Exam> emap, Map<Integer, Question> qmap, Map<Integer, QuestionGroup> qgmap) {
		List<Generated> lst = new ArrayList<>();
		final String tableName = "ex_generated";
		try {
			JsonArray res = select(tableName, new TableField[] { getField(tableName, "ID"), getField(tableName, "exam_id"), getField(tableName, "login"), getField(tableName, "config") }, true, new ComparisionField(getField(tableName, "toolchain"), tc.getName()), new ComparisionField(getField(tableName, "valid"), 1));
			for (JsonValue v : res.Value) {
				if (v.isObject()) {
					JsonObject obj = v.asObject();
					if (obj.containsNumber("ID") && obj.containsNumber("exam_id") && obj.containsString("login") && obj.containsString("config")) {
						int id = obj.getNumber("ID").Value;
						int exam_id = obj.getNumber("exam_id").Value;
						String login = obj.getString("login").Value;
						String config = obj.getString("config").Value;
						JsonValue confval = JsonValue.parse(config);
						if (confval != null && emap.containsKey(exam_id)) {
							GeneratedConfig gc = GeneratedConfig.get(confval, qmap, qgmap);
							Exam ex = emap.get(exam_id);
							if (gc != null) {
								Generated gen = new Generated(id, ex, login, gc);
								lst.add(gen);
							}
						}
					}
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return lst;
	}

	private boolean clearGeneratedExams(Toolchain toolchain, int exam_id) {
		try {
			return this.execute_raw("DELETE FROM ex_generated WHERE toolchain=? AND exam_id=?", toolchain.getName(), exam_id);
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	private class RandomizedPeriodicSet<T> {
		private final T[] data;

		private List<T> permutation;
		private final Random random;

		public RandomizedPeriodicSet(T[] data) {
			this.data = data;
			this.random = new Random(System.currentTimeMillis());
			permutation = new ArrayList<T>();
			this.regenerate();
		}

		private void regenerate() {
			for (T d : data) {
				permutation.add(d);
			}
			Collections.shuffle(permutation, random);
		}

		public T get() {
			if (permutation.isEmpty()) {
				regenerate();
			}
			return permutation.remove(0);
		}
	}

	private String randomizedOrder(int length, Random rnd) {
		if (length == 1) {
			return "0";
		}
		List<Integer> lst = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			lst.add(i);
		}
		Collections.shuffle(lst, rnd);
		StringBuilder sb = new StringBuilder();
		sb.append(lst.remove(0));
		while (!lst.isEmpty()) {
			sb.append("-" + lst.remove(0));
		}
		return sb.toString();
	}

	protected boolean generateExams(Toolchain toolchain, int exam_id, StringBuilder log) {
		ExamCache data = getExamsData(toolchain);
		if (data.ExamsByID.containsKey(exam_id)) {
			Exam exam = data.ExamsByID.get(exam_id);
			Random rnd = new Random(System.currentTimeMillis());
			if (clearGeneratedExams(toolchain, exam_id)) {

				// Init
				Map<String, List<GeneratedQuestionConfig>> generated = new HashMap<>();
				for (String login : exam.Participants) {
					generated.put(login, new ArrayList<>());
				}

				// Questiongroup by question group pick one question
				for (QuestionGroup group : exam.Config.groups) {

					if (group.Config.questions.length == 0) {
						log.append("# Group " + group.ID + " question array is empty");
						return false;
					}
					RandomizedPeriodicSet<Question> questions = new RandomizedPeriodicSet<>(group.Config.questions);
					for (String login : exam.Participants) {
						Question question = questions.get();
						String permutation = "";
						if (question.Config.Type == QuestionType.Optioned || question.Config.Type == QuestionType.OptionedWithCustom) {
							String[] options = question.Config.Options;
							if (options == null) {
								log.append("# Question " + question.ID + ": no options available\n");
								return false;
							} else if (options.length == 0) {
								log.append("# Question " + question.ID + ": no options available\n");
								return false;
							} else {
								permutation = randomizedOrder(options.length, rnd);
							}
						}
						generated.get(login).add(new GeneratedQuestionConfig(question, group, permutation, GeneratedQuestionEvaluation.get(new JsonObject())));
					}
				}

				// Store
				for (Entry<String, List<GeneratedQuestionConfig>> entry : generated.entrySet()) {
					String login = entry.getKey();
					GeneratedQuestionConfig[] cfg = new GeneratedQuestionConfig[entry.getValue().size()];
					for (int i = 0; i < cfg.length; i++) {
						cfg[i] = entry.getValue().get(i);
					}
					Long x = (long) 0;
					GeneratedConfig config = new GeneratedConfig(cfg, new JsonObject(), false, x);
					if (!addGenerated(login, exam, config)) {
						log.append("# Failed to generate entry for " + login + "\n");
						return false;
					}
				}
				cache.clear();
				return true;
			} else {
				log.append("# Failed to delete existing data\n");
			}
		}
		return false;
	}

	public boolean addGenerated(String login, Exam exam, GeneratedConfig config) {
		final String tableName = "ex_generated";
		try {
			if (this.insert(tableName, new ValuedField(getField(tableName, "exam_id"), exam.ID), new ValuedField(getField(tableName, "login"), login), new ValuedField(getField(tableName, "config"), config.get().getJsonString()), new ValuedField(getField(tableName, "toolchain"), exam.Toolchain.getName()), new ValuedField(getField(tableName, "valid"), 1))) {
				cache.clear();
				return true;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		cache.clear();
		return false;
	}

	public boolean addQuestion(Toolchain toolchain, String name, JsonValue config) {
		final String tableName = "ex_questions";
		try {
			QuestionConfig qc = QuestionConfig.get(config);
			if (qc != null) {
				if (this.insert(tableName, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "config"), qc.get().getJsonString()), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()))) {
					cache.clear();
					return true;
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		cache.clear();
		return false;
	}

	public boolean addQuestionGroup(Toolchain toolchain, String name, JsonValue config) {
		ExamCache data = getExamsData(toolchain);
		final String tableName = "ex_question_groups";
		try {
			QuestionGroupConfig qc = QuestionGroupConfig.get(config, data.QuestionsByID);
			if (qc != null) {
				if (this.insert(tableName, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "config"), qc.get().getJsonString()), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()))) {
					cache.clear();
					return true;
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		cache.clear();
		return false;
	}

	public boolean addExam(Toolchain toolchain, String name, JsonValue config) {
		ExamCache data = getExamsData(toolchain);
		final String tableName = "ex_terms";
		try {
			ExamConfig qc = ExamConfig.get(config, data.QuestionGroupsByID);
			if (qc != null) {
				if (this.insert(tableName, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "participants"), ""), new ValuedField(getField(tableName, "config"), qc.get().getJsonString()), new ValuedField(getField(tableName, "valid"), 1), new ValuedField(getField(tableName, "toolchain"), toolchain.getName()))) {
					cache.clear();
					return true;
				}
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		cache.clear();
		return false;
	}

	public boolean updateQuestion(Toolchain toolchain, int id, String name, JsonValue config) {
		ExamCache data = getExamsData(toolchain);
		if (data.QuestionsByID.containsKey(id)) {
			QuestionConfig qc = QuestionConfig.get(config);
			if (qc != null) {
				final String tableName = "ex_questions";
				try {
					if (this.update(tableName, id, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "config"), qc.get().getJsonString()))) {
						cache.clear();
						return true;
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
		}
		cache.clear();
		return false;
	}

	public boolean updateGenerated(Toolchain toolchain, Generated gen) {
		final String tableName = "ex_generated";
		try {
			if (this.update(tableName, gen.ID, new ValuedField(getField(tableName, "config"), gen.Config.get().getJsonString()))) {
				cache.clear();
				return true;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		cache.clear();
		return false;
	}

	public boolean updateQuestionGroup(Toolchain toolchain, int id, String name, JsonValue config) {
		ExamCache data = getExamsData(toolchain);
		if (data.QuestionGroupsByID.containsKey(id)) {
			QuestionGroupConfig qc = QuestionGroupConfig.get(config, data.QuestionsByID);
			if (qc != null) {
				final String tableName = "ex_question_groups";
				try {
					if (this.update(tableName, id, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "config"), qc.get().getJsonString()))) {
						cache.clear();
						return true;
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
		}
		cache.clear();
		return false;
	}

	public boolean updateExam(Toolchain toolchain, int id, String name, JsonValue config/* , String[] participants */) {
		ExamCache data = getExamsData(toolchain);
		if (data.ExamsByID.containsKey(id)) {
			ExamConfig qc = ExamConfig.get(config, data.QuestionGroupsByID);
			if (qc != null) {
				final String tableName = "ex_terms";
				/*
				 * StringBuilder sbp = new StringBuilder(); if (participants.length == 0) {
				 * 
				 * } else if (participants.length == 1) { sbp.append(participants[0]); } else {
				 * sbp.append(participants[0]); for (int i = 1; i < participants.length; i++) {
				 * sbp.append("\n" + participants[i]); } }
				 */
				try {
					if (this.update(tableName, id, new ValuedField(getField(tableName, "name"), name), new ValuedField(getField(tableName, "config"), qc.get().getJsonString())/* , new ValuedField(getField(tableName, "participants"), sbp.toString()) */)) {
						cache.clear();
						return true;
					}
				} catch (DatabaseException e) {
					e.printStackTrace();
				}
			}
		}
		cache.clear();
		return false;
	}

	public boolean deleteExam(Toolchain toolchain, int id) {
		final String tableName = "ex_terms";
		try {
			if (this.update(tableName, id, new ValuedField(getField(tableName, "valid"), 0))) {
				cache.clear();
				return true;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean deleteQuestionGroup(Toolchain toolchain, int id) {
		final String tableName = "ex_question_groups";
		try {
			if (this.update(tableName, id, new ValuedField(getField(tableName, "valid"), 0))) {
				cache.clear();
				return true;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean deleteQuestion(Toolchain toolchain, int id) {
		final String tableName = "ex_questions";
		try {
			if (this.update(tableName, id, new ValuedField(getField(tableName, "valid"), 0))) {
				cache.clear();
				return true;
			}
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return false;
	}
}
