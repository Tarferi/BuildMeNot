package cz.rion.buildserver.db.layers.staticDB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.util.Set;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.db.DatabaseInitData;
import cz.rion.buildserver.db.RuntimeDB.BadResultType;
import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.exceptions.CommandLineExecutionException;
import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.FileWriteException;
import cz.rion.buildserver.exceptions.NoSuchToolException;
import cz.rion.buildserver.exceptions.NoSuchToolchainException;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.test.GenericTest;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyExec;
import cz.rion.buildserver.wrappers.MyExec.MyExecResult;
import cz.rion.buildserver.wrappers.MyFS;

public abstract class LayeredBuildersDB extends LayeredSettingsDB {

	private static final boolean SERIALIZE_JSON = true;

	public static class ToolExecutionResult {
		public final String stdout;
		public final String stderr;
		public final int returnCode;
		boolean timeoutReached;
		private final int expectedReturnCode;

		private ToolExecutionResult(String stdout, String stderr, int returnCode, int expectedReturnCode, boolean timeoutReached) {
			this.stdout = stdout;
			this.stderr = stderr;
			this.returnCode = returnCode;
			this.timeoutReached = timeoutReached;
			this.expectedReturnCode = expectedReturnCode;
		}

		boolean wasOK() {
			return !timeoutReached && expectedReturnCode == returnCode;
		}
	}

	private final Map<String, ToolInputModifier> modifiers = new HashMap<>();

	protected void registerModifier(ToolInputModifier mod) throws DatabaseException {
		if (modifiers.containsKey(mod.getName())) {
			throw new DatabaseException("Duplicate modifier: " + mod.getName());
		}
		modifiers.put(mod.getName(), mod);
	}

	public static class ExecutionResult {
		public final ToolExecutionResult[] SubExecutions;
		private final boolean hadError;

		public ExecutionResult(ToolExecutionResult[] se) {
			this(se, false);
		}

		public ExecutionResult(ToolExecutionResult[] se, boolean hadError) {
			this.SubExecutions = se;
			this.hadError = hadError;
		}

		public boolean wasOK() {
			for (ToolExecutionResult subExecution : SubExecutions) {
				if (!subExecution.wasOK()) {
					return false;
				}
			}
			return SubExecutions.length > 0 && !hadError;
		}
	}

	public static interface ToolInputModifier {

		public String getModified(ToolchainLogger errors, String input, GenericTest test, String login);

		public String getName();
	}

	public final class Tool {

		private final String toolPath;
		private final String toolExecutable;
		private final String toolName;
		private final String[] toolParams;
		private final ToolInputModifier[] modifiers;
		private final String failIfExists;
		private final String failIfDoesntExist;
		private final int expectedResult;
		private final int timeout;
		private final String expectedOutputFile;
		private final String expectedInputFile;
		private final boolean provideStdin;
		private final ToolInputModifier[] stdoutOutputHandler;
		private final ToolInputModifier[] stdErrOutputputHandler;

		public ToolExecutionResult run(ToolchainLogger errors, GenericTest test, String workingDirectory, String lastKnownCode, String stdin, String login) {
			if (this.toolExecutable.isEmpty()) {
				return new ToolExecutionResult("", "", expectedResult, expectedResult, false);
			}
			lastKnownCode = handleCodeManipulation(errors, test, lastKnownCode, login);

			// Execute
			// First create the directory if it doesn't exist yet
			if (this.requiresCode()) {
				try {
					MyFS.writeFile(workingDirectory + "/" + this.expectedInputFile, lastKnownCode);
				} catch (FileWriteException e) {
					errors.getBadResults().setNext(BadResultType.Good);
					errors.logError("Could not store file " + this.expectedInputFile);
					return new ToolExecutionResult("", "", -1, this.expectedResult, false);
				}
			}

			try {
				MyExecResult exec = MyExec.execute(workingDirectory, this.provideStdin ? stdin : "", this.toolPath + "/" + this.toolExecutable, handleParams(this.toolParams, workingDirectory), this.timeout);
				return new ToolExecutionResult(exec.stdout, exec.stderr, exec.returnCode, this.expectedResult, exec.Timeout);
			} catch (CommandLineExecutionException e) {
				errors.getBadResults().setNext(BadResultType.Uncompillable);
				errors.logError(e.description);
			}
			return new ToolExecutionResult("", "", -1, this.expectedResult, false);
		}

		public String[] handleParams(String[] toolParams, String workingDirectory) {
			String[] newParams = new String[toolParams.length];
			for (int i = 0; i < newParams.length; i++) {
				newParams[i] = toolParams[i].replace("$CWD$", workingDirectory);
			}
			return newParams;
		}

		private String handleCodeManipulation(ToolchainLogger errors, GenericTest test, String previousCode, String login) {
			if (previousCode != null) {
				for (ToolInputModifier modifier : this.modifiers) {
					if (previousCode != null) {
						previousCode = modifier.getModified(errors, previousCode, test, login);
					}
				}
			}
			return previousCode;
		}

		public boolean requiresCode() {
			return this.expectedInputFile != null && !"".equals(expectedInputFile);
		}

		public boolean hasSTDOUTHandler() {
			return stdoutOutputHandler.length > 0;
		}

		public boolean hasSTDErrHandler() {
			return stdErrOutputputHandler.length > 0;
		}

		public String getOutputCode(ToolchainLogger errors, GenericTest test, String workingDirectory, String previousCode, String login) {
			if (providesCode()) {
				if (this.toolExecutable.isEmpty()) { // Nothing was executed, alter on the fly
					return handleCodeManipulation(errors, test, previousCode, login);
				} else { // Something executed, read from file
					try {
						return MyFS.readFile(workingDirectory + "/" + expectedOutputFile);
					} catch (FileReadException e) {
						return null;
					}
				}
			}
			return null;
		}

		/**
		 * 
		 * @return Whether or not this file produces output.
		 */
		public boolean providesCode() {
			return this.expectedOutputFile != null && !"".equals(expectedOutputFile);
		}

		/**
		 * 
		 * @param builderPath               Path of the builder
		 * @param builderName               Name of the builder
		 * @param failIfThisFileExists      Fail if this file exists. Empty to disable
		 * @param failIfThisFileDoesntExist Failed if this file doesn't exist. Empty to
		 *                                  disable
		 * @param expectedReturnCode        Expected return code
		 * @param builderParams             Parameters to provide to this builder
		 * @param timeout                   Builder timeout
		 * @param builderExecutableName     Builder executable name. If empty, nothing
		 *                                  is executed, but code is still modified.
		 *                                  This can be used to alter code internally
		 * @param codeModifiers             If expected input file is not empty, a code
		 *                                  is provided and that will be passed to
		 *                                  modifiers and stored to expected input file
		 * @param expectedOutputFile        If this builder provides output for the next
		 *                                  builder in chain, this will be read as next
		 *                                  code to provide. First empty expected output
		 *                                  file disables all next
		 * @param expectedInputFile         Provides input file as output for modifiers
		 *                                  and input for builder itself. If empty, no
		 *                                  operations in modifiers happen
		 * @param provideStdin              If set to "1", stdin is passed to the
		 *                                  builder
		 * @param stdoutOutputHandler       If not empty, this handler acts the same way
		 *                                  as {@link #modifiers}
		 * 
		 * @throws DatabaseException
		 */
		private Tool(String builderPath, String builderName, String failIfThisFileExists, String failIfThisFileDoesntExist, int expectedReturnCode, String builderParams, int timeout, String builderExecutableName, String codeModifiers, String expectedOutputFile, String expectedInputFile, int provideStdin, String stdoutOutputHandler, String sderrOutputHandler) throws DatabaseException {
			this.toolPath = builderPath;
			this.toolName = builderName;
			this.failIfExists = failIfThisFileExists;
			this.failIfDoesntExist = failIfThisFileDoesntExist;
			this.expectedResult = expectedReturnCode;
			this.toolParams = unserializeParams(builderParams);
			this.timeout = timeout;
			this.toolExecutable = builderExecutableName;
			this.expectedOutputFile = expectedOutputFile;
			this.expectedInputFile = expectedInputFile;
			this.provideStdin = provideStdin == 1;

			String[] stdoutOutputHandlers = unserializeParams(stdoutOutputHandler);
			this.stdoutOutputHandler = new ToolInputModifier[stdoutOutputHandlers.length];
			for (int i = 0; i < this.stdoutOutputHandler.length; i++) {
				this.stdoutOutputHandler[i] = getModifier(stdoutOutputHandlers[i]);
			}

			String[] sderrOutputHandlers = unserializeParams(sderrOutputHandler);
			this.stdErrOutputputHandler = new ToolInputModifier[sderrOutputHandlers.length];
			for (int i = 0; i < this.stdErrOutputputHandler.length; i++) {
				this.stdErrOutputputHandler[i] = getModifier(sderrOutputHandlers[i]);
			}

			String[] decodedModifiers = unserializeParams(codeModifiers);
			this.modifiers = new ToolInputModifier[decodedModifiers.length];
			for (int i = 0; i < this.modifiers.length; i++) {
				this.modifiers[i] = getModifier(decodedModifiers[i]);
			}
		}

		public String handleStdout(ToolchainLogger errors, GenericTest test, String previousCode, String login, ToolExecutionResult result) {
			String stdout = result.stdout;
			for (ToolInputModifier modifier : this.stdoutOutputHandler) {
				if (stdout != null) {
					stdout = modifier.getModified(errors, stdout, test, login);
				}
			}
			return stdout;
		}

		public String handleStderr(ToolchainLogger errors, GenericTest test, String previousCode, String login, ToolExecutionResult result) {
			String stderr = result.stderr;
			for (ToolInputModifier modifier : this.stdErrOutputputHandler) {
				if (stderr != null) {
					stderr = modifier.getModified(errors, stderr, test, login);
				}
			}
			return stderr;
		}
	}

	public interface ToolchainLogger {

		public void logError(String error);

		public BadResults getBadResults();

	}

	public final class Toolchain {
		private final Tool[] tools;
		private final String name;
		private final String pathPrefix;
		public final String[] runnerParams;

		public ExecutionResult run(ToolchainLogger errors, GenericTest test, String workingDirectory, String inputString, String stdin, String login) {
			MyFS.deleteFileSilent(workingDirectory);
			boolean codeKnown = true;

			ToolExecutionResult[] lst = new ToolExecutionResult[tools.length];
			int lastUpdateOfOutput = -1;
			for (int i = 0; i < tools.length; i++) {
				if (tools[i].requiresCode()) { // Requests output of previous tool
					if (i > 0 && lastUpdateOfOutput != i - 1) { // Not first tool, read previous code as new
						inputString = tools[i - 1].getOutputCode(errors, test, workingDirectory, inputString, login);
						lastUpdateOfOutput = i - 1;
					}
				}

				lst[i] = tools[i].run(errors, test, workingDirectory, inputString, stdin, login);
				String lstStdout = lst[i].stdout;
				if (tools[i].hasSTDOUTHandler()) {
					lstStdout = tools[i].handleStdout(errors, test, inputString, login, lst[i]);
				}
				String lstStderr = lst[i].stderr;
				if (tools[i].hasSTDErrHandler()) {
					lstStderr = tools[i].handleStderr(errors, test, inputString, login, lst[i]);
				}

				if (!lst[i].wasOK() || lstStdout == null || lstStderr == null) { // Was not OK, delete
					ToolExecutionResult[] ret = new ToolExecutionResult[i + 1];
					System.arraycopy(lst, 0, ret, 0, i + 1);
					if (lstStdout != null && lstStderr != null) { // Could only happen in stdout handler which sets error upon failure
						errors.getBadResults().setNext(BadResultType.Uncompillable);
						errors.logError("Nepodaøilo se pøeložit kód");
					}
					return new ExecutionResult(ret, true);
				} else if (tools[i].providesCode() && codeKnown) { // was ok, provides code and it is known
					inputString = tools[i].getOutputCode(errors, test, workingDirectory, inputString, login);
					if (inputString == null) {
						ToolExecutionResult[] ret = new ToolExecutionResult[i + 1];
						System.arraycopy(lst, 0, ret, 0, i + 1);
						return new ExecutionResult(ret, true);
					}
					lastUpdateOfOutput = i;
				} else {
					codeKnown = false;
				}
			}
			return new ExecutionResult(lst);
		}

		public Toolchain(String name, String prefix, Tool[] tools, String runnerParams) throws DatabaseException {
			this.tools = tools;
			this.name = name;
			this.pathPrefix = prefix;
			this.runnerParams = unserializeParams(runnerParams);
		}

		public String getLastOutputFileName() {
			String lastOutput = "";
			for (Tool t : tools) {
				if (t.providesCode()) {
					lastOutput = t.expectedOutputFile;
				}
			}
			return lastOutput;
		}

		public String getName() {
			return name;
		}

		@Override
		public boolean equals(Object another) {
			if (another instanceof Toolchain) {
				return name.equals(((Toolchain) another).name);
			}
			return super.equals(another);
		}
	}

	private final ToolInputModifier getModifier(String name) throws DatabaseException {
		if (modifiers.containsKey(name)) {
			return modifiers.get(name);
		}
		throw new DatabaseException("Invalid modifier: " + name);
	}

	private static final String[] unserializeParams(String params) throws DatabaseException {
		if (SERIALIZE_JSON) {
			JsonValue val = JsonValue.parse(params);
			if (val.isArray()) {
				JsonArray arr = val.asArray();
				String[] data = new String[arr.Value.size()];
				for (int i = 0; i < data.length; i++) {
					JsonValue v = arr.Value.get(i);
					if (!v.isString()) {
						throw new DatabaseException("Invalid param string: " + params);
					} else {
						data[i] = v.asString().Value;
					}
				}
				return data;
			}

			throw new DatabaseException("Invalid param string: " + params);
		} else {
			try {
				int paramsLengthIndex = params.indexOf('!');
				int totalParams = Integer.parseInt(params.substring(0, paramsLengthIndex));
				String[] paramsArr = new String[totalParams];
				params = params.substring(paramsLengthIndex + 1);
				for (int i = 0; i < paramsArr.length; i++) {
					int nextIndex = params.indexOf('!');
					int nextParamLength = Integer.parseInt(params.substring(0, nextIndex));
					params = params.substring(nextIndex + 1);
					paramsArr[i] = params.substring(0, nextParamLength);
					params = params.substring(nextParamLength);
				}
				return paramsArr;
			} catch (Exception e) {
				throw new DatabaseException("Invalid param string: " + params);
			}
		}
	}

	private static final String serializeParams(String[] params) {
		if (SERIALIZE_JSON) {
			JsonArray arr = new JsonArray();
			for (String param : params) {
				arr.add(new JsonString(param));
			}
			return arr.getJsonString();
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append(params.length + "!");
			for (String param : params) {
				sb.append(param.length() + "!");
				sb.append(param);
			}
			return sb.toString();
		}
	}

	private static final class ToolLink {
		public final String builder;
		public final int nextID;

		private ToolLink(String builder, int nextId) {
			this.builder = builder;
			this.nextID = nextId;
		}
	}

	public Toolchain getToolchain(String name) throws NoSuchToolchainException {
		synchronized (toolchains) {
			if (toolchains.containsKey(name)) {
				return toolchains.get(name);
			} else {
				try {
					loadToolchains();
				} catch (NoSuchToolException | DatabaseException e) {
					throw new NoSuchToolchainException("No such toolchain: " + name, e);
				}
				if (toolchains.containsKey(name)) {
					return toolchains.get(name);
				}
			}
		}
		throw new NoSuchToolchainException(name);
	}

	private Map<String, Toolchain> toolchains = new HashMap<>();

	public Toolchain[] getAllToolchains() {
		Toolchain[] tc = new Toolchain[toolchains.entrySet().size()];
		int i = 0;
		for (Entry<String, Toolchain> entry : toolchains.entrySet()) {
			tc[i] = entry.getValue();
			i++;
		}
		return tc;
	}

	public LayeredBuildersDB(DatabaseInitData dbName) throws DatabaseException {
		super(dbName);
		this.dropTable("builders");
		this.dropTable("tools");
		this.dropTable("toolchain");

		this.makeTable("builders", KEY("ID"), TEXT("name"), TEXT("builder_path"), TEXT("builder_executable"), TEXT("params"), TEXT("fail_if_file_exists"), TEXT("fail_if_file_doesnt_exist"), NUMBER("expected_result"), NUMBER("valid"), NUMBER("timeout"), TEXT("expected_outputFile"), TEXT("expected_inputFile"), TEXT("modifiers"), NUMBER("provide_stdin"), TEXT("stdout_output_handler"), TEXT("stderr_output_handler"));
		this.makeTable("tools", KEY("ID"), TEXT("builder"), NUMBER("next"), NUMBER("valid"));
		this.makeTable("toolchain", KEY("ID"), TEXT("name"), NUMBER("first_tool"), TEXT("target_path_prefix"), TEXT("runner_params"), NUMBER("valid"));
		initDefaultToolchains();
	}

	private Map<String, Tool> getTools() throws DatabaseException {
		Map<String, Tool> result = new HashMap<>();
		final String tableName = "builders";

		final TableField fname = this.getField(tableName, "name");
		final TableField fbuilder_path = this.getField(tableName, "builder_path");
		final TableField fparams = this.getField(tableName, "params");
		final TableField fex = this.getField(tableName, "fail_if_file_exists");
		final TableField fnex = this.getField(tableName, "fail_if_file_doesnt_exist");
		final TableField fres = this.getField(tableName, "expected_result");
		final TableField ftimeout = this.getField(tableName, "timeout");
		final TableField fexec = this.getField(tableName, "builder_executable");
		final TableField fexpectedOutputFile = this.getField(tableName, "expected_outputFile");
		final TableField fexpectedInputFile = this.getField(tableName, "expected_inputFile");
		final TableField fmod = this.getField(tableName, "modifiers");
		final TableField fprovide_stdin = this.getField(tableName, "provide_stdin");
		final TableField fstdout_output_handler = this.getField(tableName, "stdout_output_handler");
		final TableField fstderr_output_handler = this.getField(tableName, "stderr_output_handler");

		final TableField fvalid = this.getField(tableName, "valid");

		JsonArray res = this.select(tableName, new TableField[] { fname, fbuilder_path, fparams, fex, fnex, fres, ftimeout, fexec, fmod, fexpectedOutputFile, fexpectedInputFile, fprovide_stdin, fstdout_output_handler, fstderr_output_handler }, false, new ComparisionField(fvalid, 1));

		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("name") && obj.containsString("builder_path") && obj.containsString("params") && obj.containsString("fail_if_file_exists") && obj.containsString("fail_if_file_doesnt_exist") && obj.containsNumber("expected_result") && obj.containsNumber("timeout") && obj.containsString("builder_executable") && obj.containsString("expected_outputFile") && obj.containsString("modifiers") && obj.containsString("expected_inputFile") && obj.containsNumber("provide_stdin")) {
					String name = obj.getString("name").Value;
					String builder_path = obj.getString("builder_path").Value;
					String params = obj.getString("params").Value;
					String fail_if_file_exists = obj.getString("fail_if_file_exists").Value;
					String fail_if_file_doesnt_exist = obj.getString("fail_if_file_doesnt_exist").Value;
					int expected_result = obj.getNumber("expected_result").Value;
					int timeout = obj.getNumber("timeout").Value;
					String builder_executable = obj.getString("builder_executable").Value;
					String expected_outputFile = obj.getString("expected_outputFile").Value;
					String modifiers = obj.getString("modifiers").Value;
					String expected_inputFile = obj.getString("expected_inputFile").Value;
					int provide_stdin = obj.getNumber("provide_stdin").Value;
					String stdout_output_handler = obj.getString("stdout_output_handler").Value;
					String stderr_output_handler = obj.getString("stderr_output_handler").Value;
					result.put(name, new Tool(builder_path, name, fail_if_file_exists, fail_if_file_doesnt_exist, expected_result, params, timeout, builder_executable, modifiers, expected_outputFile, expected_inputFile, provide_stdin, stdout_output_handler, stderr_output_handler));
				}
			}
		}
		return result;
	}

	private Map<Integer, ToolLink> getLinks() throws DatabaseException {
		Map<Integer, ToolLink> result = new HashMap<>();
		final String tableName = "tools";

		final TableField fid = this.getField(tableName, "ID");
		final TableField fbuilder = this.getField(tableName, "builder");
		final TableField fnext = this.getField(tableName, "next");
		final TableField fvalid = this.getField(tableName, "valid");

		JsonArray res = this.select(tableName, new TableField[] { fid, fbuilder, fnext }, false, new ComparisionField(fvalid, 1));

		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsString("builder") && obj.containsNumber("next")) {
					String builder = obj.getString("builder").Value;
					int ID = obj.getNumber("ID").Value;
					int nextID = obj.getNumber("next").Value;
					result.put(ID, new ToolLink(builder, nextID));
				}
			}
		}
		return result;
	}

	private static final class LinkedTool {
		public final Tool Tool;
		public final int nextID;

		private LinkedTool(Tool tool, int nextId) {
			this.Tool = tool;
			this.nextID = nextId;
		}
	}

	private LinkedTool getToolByID(Map<String, Tool> tools, Map<Integer, ToolLink> links, int tid) throws NoSuchToolException {
		if (links.containsKey(tid)) {
			ToolLink link = links.get(tid);
			for (Entry<String, Tool> tool : tools.entrySet()) {
				if (tool.getValue().toolName.equals(link.builder)) {
					return new LinkedTool(tool.getValue(), link.nextID);
				}
			}
		}
		throw new NoSuchToolException(tid);
	}

	private Tool[] getToolsByFirstID(Map<String, Tool> tools, Map<Integer, ToolLink> links, int pid) throws NoSuchToolException {
		List<Tool> tls = new ArrayList<>();
		while (pid != -1) {
			LinkedTool link = getToolByID(tools, links, pid);
			tls.add(link.Tool);
			pid = link.nextID;
		}
		Tool[] result = new Tool[tls.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = tls.get(i);
		}
		return result;
	}

	private Map<String, Toolchain> getToolchains() throws NoSuchToolException, DatabaseException {
		Map<String, Toolchain> result = new HashMap<>();
		Map<Integer, ToolLink> links = getLinks();
		Map<String, Tool> tools = getTools();

		final String tableName = "toolchain";

		final TableField fname = this.getField(tableName, "name");
		final TableField ffirst = this.getField(tableName, "first_tool");
		final TableField fprefix = this.getField(tableName, "target_path_prefix");
		final TableField frunner_params = this.getField(tableName, "runner_params");
		final TableField fvalid = this.getField(tableName, "valid");

		JsonArray res = this.select(tableName, new TableField[] { fname, ffirst, fprefix, frunner_params }, false, new ComparisionField(fvalid, 1));

		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("name") && obj.containsNumber("first_tool") && obj.containsString("target_path_prefix")) {
					String name = obj.getString("name").Value;
					String target_path_prefix = obj.getString("target_path_prefix").Value;
					int first_tool = obj.getNumber("first_tool").Value;
					Tool[] ntools = getToolsByFirstID(tools, links, first_tool);
					String runner_params = obj.getString("runner_params").Value;
					result.put(name, new Toolchain(name, target_path_prefix, ntools, runner_params));
				}
			}
		}
		return result;
	}

	private boolean builderExists(String name) throws DatabaseException {
		final String tableName = "builders";
		final TableField fvalid = this.getField(tableName, "valid");
		final TableField fname = this.getField(tableName, "name");
		JsonArray res = this.select(tableName, new TableField[] { fname }, false, new ComparisionField(fname, name), new ComparisionField(fvalid, 1));
		if (res.Value.size() != 1) {
			return false;
		}
		return true;
	}

	private int getFirstToolInToolchain(String name) throws DatabaseException, NoSuchToolchainException {
		final String tableName = "toolchain";
		final TableField fname = this.getField(tableName, "name");
		final TableField fvalid = this.getField(tableName, "valid");
		final TableField ffirst = this.getField(tableName, "first_tool");
		JsonArray res = this.select(tableName, new TableField[] { ffirst }, false, new ComparisionField(fname, name), new ComparisionField(fvalid, 1));
		if (res.Value.size() == 1) {
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("first_tool")) {
					return obj.getNumber("first_tool").Value;
				}
			}
		}
		throw new NoSuchToolchainException(name);
	}

	private class ToolInfo {
		public final String BuilderName;
		public final int ToolID;
		public final int NextToolID;

		private ToolInfo(String builderName, int toolID, int nextToolID) {
			this.BuilderName = builderName;
			this.ToolID = toolID;
			this.NextToolID = nextToolID;
		}
	}

	private ToolInfo getBuilderForTool(int toolID) throws DatabaseException, NoSuchToolException {
		final String tableName = "tools";
		final TableField fid = this.getField(tableName, "ID");
		final TableField fbuilder = this.getField(tableName, "builder");
		final TableField fnext = this.getField(tableName, "next");
		final TableField fvalid = this.getField(tableName, "valid");
		JsonArray res = this.select(tableName, new TableField[] { fbuilder, fnext }, false, new ComparisionField(fid, toolID), new ComparisionField(fvalid, 1));
		if (res.Value.size() == 1) {
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("builder") && obj.containsNumber("next")) {
					int nextToolID = obj.getNumber("next").Value;
					String builder = obj.getString("builder").Value;
					return new ToolInfo(builder, toolID, nextToolID);
				}
			}
		}
		throw new NoSuchToolException(toolID);
	}

	private boolean toolchainExists(String name, String... builderNames) throws DatabaseException {
		try {
			Set<Integer> knownIDs = new HashSet<>();
			int firstTool = getFirstToolInToolchain(name);
			knownIDs.add(firstTool);
			int nextTool = firstTool;
			while (true) {
				ToolInfo ti = null;
				int index = knownIDs.size();
				try {
					ti = getBuilderForTool(nextTool);
				} catch (NoSuchToolException e) {
					if (index == builderNames.length) {
						return true;
					}
				}
				if (knownIDs.contains(ti.NextToolID)) {
					throw new DatabaseException("Loop in toolchain");
				} else {
					nextTool = ti.NextToolID;
					knownIDs.add(nextTool);
					if (index >= builderNames.length) {
						return false;
					} else {
						String builder = builderNames[index];
						if (!builder.equals(ti.BuilderName)) {
							return false;
						}
					}
				}
			}
		} catch (NoSuchToolchainException e) {
			return false;
		}
	}

	private void addBuilder(String name, String builderPath, String builderExecutable, String failIfFile_exists, String failIfFileDoesntExist, String[] params, int expectedResult, int timeout, String[] modifiers, String expectedOutputFile, String expectedInputFile, int provideStdin, String[] stdoutOutputHandler, String[] stderrOutputHandler) throws DatabaseException {
		final String tableName = "builders";
		final TableField fname = this.getField(tableName, "name");
		final TableField fbpath = this.getField(tableName, "builder_path");
		final TableField ffailex = this.getField(tableName, "fail_if_file_exists");
		final TableField ffailnonex = this.getField(tableName, "fail_if_file_doesnt_exist");
		final TableField fret = this.getField(tableName, "expected_result");
		final TableField fpar = this.getField(tableName, "params");
		final TableField ftimeout = this.getField(tableName, "timeout");
		final TableField fexec = this.getField(tableName, "builder_executable");
		final TableField fexpectedOutputFile = this.getField(tableName, "expected_outputFile");
		final TableField fexpectedInputFile = this.getField(tableName, "expected_inputFile");
		final TableField fmod = this.getField(tableName, "modifiers");
		final TableField fprovide_stdin = this.getField(tableName, "provide_stdin");
		final TableField fstdout_output_handler = this.getField(tableName, "stdout_output_handler");
		final TableField fstderr_output_handler = this.getField(tableName, "stderr_output_handler");

		final TableField fvalid = this.getField(tableName, "valid");
		ValuedField[] values = new ValuedField[] { new ValuedField(fname, name), new ValuedField(fbpath, builderPath), new ValuedField(ffailex, failIfFile_exists), new ValuedField(ffailnonex, failIfFileDoesntExist), new ValuedField(fret, expectedResult), new ValuedField(fpar, serializeParams(params)), new ValuedField(ftimeout, timeout), new ValuedField(fexec, builderExecutable), new ValuedField(fexpectedOutputFile, expectedOutputFile), new ValuedField(fexpectedInputFile, expectedInputFile), new ValuedField(fmod, serializeParams(modifiers)), new ValuedField(fprovide_stdin, provideStdin), new ValuedField(fstdout_output_handler, serializeParams(stdoutOutputHandler)), new ValuedField(fstderr_output_handler, serializeParams(stderrOutputHandler)), new ValuedField(fvalid, 1) };
		if (!builderExists(name)) {
			this.insert(tableName, values);
		}
	}

	private class FirstToolInfo extends ToolInfo {
		public final String toolChainFirst;

		private FirstToolInfo(String toolChainFirst, String builderName, int toolID, int nextToolID) {
			super(builderName, toolID, nextToolID);
			this.toolChainFirst = toolChainFirst;
		}
	}

	private List<String> getToolChainNamesByFirstTool(int toolID) throws DatabaseException {
		List<String> lst = new ArrayList<>();
		final String tableName = "toolchain";
		final TableField ffirst = this.getField(tableName, "first_tool");
		final TableField fname = this.getField(tableName, "name");
		final TableField fvalid = this.getField(tableName, "valid");
		JsonArray res = this.select(tableName, new TableField[] { fname }, false, new ComparisionField(ffirst, toolID), new ComparisionField(fvalid, 1));
		for (JsonValue val : res.Value) {
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsString("name")) {
					String name = obj.getString("name").Value;
					lst.add(name);
				}
			}
		}
		return lst;
	}

	private FirstToolInfo getLinkForBuilder(String builder) throws DatabaseException {
		final String tableName = "tools";
		final TableField fid = this.getField(tableName, "ID");
		final TableField fbuilder = this.getField(tableName, "builder");
		final TableField fvalid = this.getField(tableName, "valid");
		JsonArray res = this.select(tableName, new TableField[] { fid }, false, new ComparisionField(fbuilder, builder), new ComparisionField(fvalid, 1));
		if (res.Value.size() == 1) {
			JsonValue val = res.Value.get(0);
			if (val.isObject()) {
				JsonObject obj = val.asObject();
				if (obj.containsNumber("ID") && obj.containsNumber("next")) {
					int toolID = obj.getNumber("ID").Value;
					int nextToolID = obj.getNumber("next").Value;
					List<String> lst = getToolChainNamesByFirstTool(toolID);
					String name = lst.size() == 1 ? lst.get(0) : null;
					return new FirstToolInfo(name, builder, toolID, nextToolID);
				}
			}
		}
		return null;
	}

	private class UniqueToolLink {
		public final int ID;

		private UniqueToolLink(int ID) {
			this.ID = ID;
		}
	}

	private UniqueToolLink uniqueToolLinkExistsForBuilders(String name, String... builders) throws DatabaseException {
		UniqueToolLink lnk = null;
		for (int i = 0; i < builders.length; i++) {
			String builder = builders[i];
			FirstToolInfo bfi = getLinkForBuilder(builder);
			if (bfi == null) {
				return null;
			} else if (bfi.toolChainFirst != null && i > 0) {
				return null;
			} else if (bfi.toolChainFirst == null && i == 0) {
				return null;
			} else if (i == 0) {
				lnk = new UniqueToolLink(bfi.ToolID);
			}
		}
		return lnk;
	}

	private int createLink(int prev, String builder) throws DatabaseException {
		final String tableName = "tools";
		final TableField fid = this.getField(tableName, "ID");
		final TableField fbuilder = this.getField(tableName, "builder");
		final TableField fnext = this.getField(tableName, "next");
		final TableField fvalid = this.getField(tableName, "valid");
		if (this.insert(tableName, new ValuedField(fbuilder, builder), new ValuedField(fnext, -1), new ValuedField(fvalid, 1))) {
			JsonArray res = this.select(tableName, new TableField[] { fid }, false, new ComparisionField(fbuilder, builder), new ComparisionField(fnext, -1), new ComparisionField(fvalid, 1));
			if (res.Value.size() == 0) {
				throw new DatabaseException("Failed to create link for " + builder + ": no insertion?");
			} else {
				JsonValue val = res.Value.get(res.Value.size() - 1);
				if (!val.isObject()) {
					throw new DatabaseException("Failed to create link for " + builder + ": no object?");
				} else {
					JsonObject obj = val.asObject();
					if (obj.containsNumber("ID")) {
						int currentID = obj.getNumber("ID").Value;
						if (prev >= 0) {
							this.update(tableName, prev, new ValuedField(fnext, currentID));
						}
						return currentID;
					} else {
						throw new DatabaseException("Failed to create link for " + builder + ": no fields?");
					}
				}
			}
		} else {
			throw new DatabaseException("Failed to create link for " + builder + ": insertion failed?");
		}
	}

	private UniqueToolLink createUniqueToolLinkAndGetFirstID(String... builders) throws DatabaseException {
		int nextID = -1;
		int firstID = -1;
		for (int i = 0; i < builders.length; i++) {
			String builder = builders[i];
			nextID = createLink(nextID, builder);
			if (i == 0) {
				firstID = nextID;
			}
		}
		return new UniqueToolLink(firstID);
	}

	private void createToolchain(String name, String prefix, boolean utilizeExistingLink, String[] runnerParams, String... builders) throws DatabaseException {
		for (String builder : builders) {
			if (!builderExists(builder)) {
				throw new DatabaseException("Cannot create new toolchain: '" + name + "' because builder '" + builder + "' doesn't exist");
			}
		}
		UniqueToolLink lnk = utilizeExistingLink ? uniqueToolLinkExistsForBuilders(name, builders) : null;
		if (lnk == null) {
			lnk = createUniqueToolLinkAndGetFirstID(builders);
		}

		final String tableName = "toolchain";
		final TableField fname = this.getField(tableName, "name");
		final TableField ffirst = this.getField(tableName, "first_tool");
		final TableField fprefix = this.getField(tableName, "target_path_prefix");
		final TableField fvalid = this.getField(tableName, "valid");
		final TableField frunner_params = this.getField(tableName, "runner_params");
		this.insert(tableName, new ValuedField(fname, name), new ValuedField(ffirst, lnk.ID), new ValuedField(fprefix, prefix), new ValuedField(frunner_params, serializeParams(runnerParams)), new ValuedField(fvalid, 1));
	}

	private void createToolchainIfItDoesntExist(String name, String prefix, String[] runnerParams, String... builders) throws DatabaseException {
		UniqueToolLink lnk = uniqueToolLinkExistsForBuilders(name, builders);
		if (lnk != null) { // Link exists -> it must belong to us
			List<String> usedInChains = getToolChainNamesByFirstTool(lnk.ID);
			if (usedInChains.contains(name) && usedInChains.size() == 1) { // Links exist for us only
				return;
			} else { // Links exist but not for us
				createToolchain(name, prefix, false, runnerParams, builders);
			}
		} else if (toolchainExists(name, builders)) { // No links exist -> toolchain exists
			createToolchain(name, prefix, true, runnerParams, builders);
		} else { // No toolchain
			createToolchain(name, prefix, false, runnerParams, builders);
		}
	}

	private void addNasmToolchain() throws DatabaseException {
		String preprocessorName = "ISU_CommonPreprocessor";
		{ // Preprocessor
			this.addBuilder(preprocessorName, "", "", "", "", new String[0], 0, 0, new String[] { "NasmBaseModifier" }, "processed.asm", "raw.asm", 0, new String[0], new String[0]);
		}

		// Get builder from settings class
		String nasmName = "ISU_NasmFromSettings";
		{ // Nasm
			String path = Settings.getNasmPath();
			int timeout = Settings.getNasmTimeout();
			String[] nparams = Settings.getNasmExecutableParams();
			String[] params = new String[nparams.length + 1];
			System.arraycopy(nparams, 0, params, 0, nparams.length);
			params[params.length - 1] = "$CWD$/run.asm";
			String executable = Settings.getNasmExecutableName();
			this.addBuilder(nasmName, path, executable, "", "", params, 0, timeout, new String[0], "", "run.asm", 0, new String[0], new String[0]);
		}
		String goLinkName = "ISU_GoLinkFromSettings";
		{ // GoLink
			String path = Settings.getGoLinkPath();
			int timeout = Settings.getLinkerTimeout();
			String[] params = Settings.getGoLinkExecutableParams();
			String executable = Settings.getGoLinkExecutableName();
			this.addBuilder(goLinkName, path, executable, "", "", params, 0, timeout, new String[0], Settings.getExecutableFileName(), "", 0, new String[0], new String[0]);
		}
		String[] nasmParams = Settings.getAsmExecutableRunnerParams();
		createToolchainIfItDoesntExist("ISU", "nasm/", nasmParams, preprocessorName, nasmName, goLinkName);
	}

	private void addGCCToolchain() throws DatabaseException {
		String GCCPreprocess = "IZP_GCCPreProcessor";
		{ // GCC
			String path = Settings.getGCCPath();
			int timeout = 1000;
			String finalExecutable = "processed.c";
			String[] params = new String[] { "$CWD$/unprocessed.c", "-H", "-E", "-o", "$CWD$/" + finalExecutable };
			String executable = Settings.getGCCExecutable();
			this.addBuilder(GCCPreprocess, path, executable, "", "", params, 0, timeout, new String[0], finalExecutable, "unprocessed.c", 0, new String[0], new String[] { "GCCIncludeChecker" });
		}
		String preprocessorName = "IZP_CommonPreprocessor";
		{ // Preprocessor
			this.addBuilder(preprocessorName, "", "", "", "", new String[0], 0, 0, new String[] { "GCCBaseModifier" }, "unprocessed.i", "preprocessed.c", 0, new String[0], new String[0]);
		}

		String GCCName = "IZP_GCCFromSettings";
		{ // GCC
			String path = Settings.getGCCPath();
			int timeout = 1000;
			String finalExecutable = Settings.getGCCFilalExecutable();
			String[] params = new String[] { "$CWD$/preprocessed.c", "-o", "$CWD$/" + finalExecutable };
			String executable = Settings.getGCCExecutable();
			this.addBuilder(GCCName, path, executable, "", "", params, 0, timeout, new String[0], finalExecutable, "preprocessed.c", 0, new String[0], new String[0]);
		}
		String[] runnerParams = Settings.getGccExecutableRunnerParams();
		createToolchainIfItDoesntExist("IZP", "gcc/", runnerParams, GCCPreprocess, preprocessorName, GCCName);
	}

	private void initDefaultToolchains() throws DatabaseException {
		addNasmToolchain();
		addGCCToolchain();
	}

	private void loadToolchains() throws NoSuchToolException, DatabaseException {
		synchronized (toolchains) {
			toolchains.clear();
			toolchains = getToolchains();
		}
	}
}
