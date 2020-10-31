package cz.rion.buildserver.http.stateless;

import java.util.ArrayList;
import java.util.List;
import cz.rion.buildserver.db.VirtualFileManager.UserContext;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile;
import cz.rion.buildserver.db.VirtualFileManager.VirtualFile.VirtualFileException;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.http.HTTPResponse;
import cz.rion.buildserver.json.JsonValue;
import cz.rion.buildserver.json.JsonValue.JsonArray;
import cz.rion.buildserver.json.JsonValue.JsonObject;
import cz.rion.buildserver.utils.CachedData;
import cz.rion.buildserver.utils.CachedDataGetter;
import cz.rion.buildserver.utils.CachedDataWrapper2;
import cz.rion.buildserver.utils.CachedToolchainData2;
import cz.rion.buildserver.utils.CachedToolchainDataGetter2;
import cz.rion.buildserver.utils.CachedToolchainDataWrapper2;

public class StatelessGraphProviderClient extends StatelessFileProviderClient {

	private final StatelessInitData data;

	protected StatelessGraphProviderClient(StatelessInitData data) {
		super(data);
		this.data = data;
	}

	private Toolchain getRootToolchain() {
		return data.StaticDB.getRootToolchain();
	}

	protected JsonValue loadGraphs(ProcessState state) {
		return graphCache.get(state.Toolchain);
	}

	private VirtualFile getFile(String fileName, UserContext context) {
		List<VirtualFile> fo = data.Files.getFile(fileName, context);
		if (fo.isEmpty()) {
			return null;
		}
		return fo.get(0);
	}

	private JsonObject loadView(JsonValue jsn, UserContext context) {
		Toolchain toolchain = context.getToolchain();
		if (jsn.isObject()) {
			JsonObject obj = jsn.asObject();
			if (obj.containsArray("result")) {
				JsonArray arr = obj.getArray("result");
				JsonArray newArr = new JsonArray(new ArrayList<JsonValue>());
				for (JsonValue val : arr.Value) {
					if (val.isObject()) {
						JsonObject xobj = val.asObject();
						if (xobj.containsString("ToolChain")) {
							String tc = xobj.getString("ToolChain").Value;
							if (tc.equals(toolchain.getName())) {
								newArr.add(xobj);
							}
						}
					}
				}
				obj.remove("result");
				obj.add("result", newArr);
				return obj;
			}
		}
		return null;
	}

	private CachedToolchainData2<JsonValue> graphCache = new CachedToolchainDataWrapper2<>(1, new CachedToolchainDataGetter2<JsonValue>() {

		@Override
		public CachedData<JsonValue> createData(int refreshIntervalInSeconds, final Toolchain toolchain) {
			return new CachedDataWrapper2<>(refreshIntervalInSeconds, new CachedDataGetter<JsonValue>() {

				private UserContext rootToolchainContext = new UserContext() {

					@Override
					public Toolchain getToolchain() {
						return getRootToolchain();
					}

					@Override
					public String getLogin() {
						return "root";
					}

					@Override
					public String getAddress() {
						return "0.0.0.0";
					}

				};

				private UserContext toolchainContext = new UserContext() {

					@Override
					public Toolchain getToolchain() {
						return toolchain;
					}

					@Override
					public String getLogin() {
						return "root";
					}

					@Override
					public String getAddress() {
						return "0.0.0.0";
					}

				};

				@Override
				public JsonValue update() {

					// Preload database files
					List<VirtualFile> files = new ArrayList<>();
					data.Files.getFiles(files, toolchainContext);

					// Load tests
					VirtualFile src = getFile("graphs.cfg", rootToolchainContext);
					String contents;
					try {
						contents = src.read(toolchainContext);
					} catch (VirtualFileException e1) {
						e1.printStackTrace();
						return new JsonArray();
					}
					if (contents != null) {
						JsonValue val = JsonValue.parse(contents);
						if (val != null) {
							if (val.isArray()) {
								JsonArray result = new JsonArray(new ArrayList<JsonValue>());
								for (JsonValue v : val.asArray().Value) {
									try {
										if (!v.isObject()) {
											continue;
										}
										JsonObject obj = v.asObject();
										String view = obj.getString("View").Value;
										String x = obj.getString("X").Value;
										JsonArray y = obj.getArray("Y");

										JsonObject res = new JsonObject();

										VirtualFile loadedView = getFile(view, toolchainContext);
										if (loadedView == null) {
											continue;
										}
										String loadedViewContents = loadedView.read(toolchainContext);
										if (loadedViewContents != null) {
											JsonValue viewData = JsonValue.parse(loadedViewContents);
											if (viewData == null) {
												continue;
											}
											viewData = loadView(viewData, toolchainContext);
											if (viewData != null) {

												JsonObject dataColumns = new JsonObject();

												if (!viewData.isObject()) {
													continue;
												}
												JsonObject vobj = viewData.asObject();
												if (!vobj.containsArray("result")) {
													continue;
												}
												List<JsonValue> vres = vobj.getArray("result").Value;

												JsonArray xData = new JsonArray(new ArrayList<JsonValue>());
												JsonArray yData = new JsonArray(new ArrayList<JsonValue>());

												String[] yNames = new String[y.Value.size()];
												JsonArray[] yVals = new JsonArray[y.Value.size()];
												int index = 0;
												for (JsonValue vy : y.Value) {
													yNames[index] = vy.asObject().getString("Column").Value;
													yVals[index] = new JsonArray(new ArrayList<JsonValue>());
													index++;
												}

												// Load values for all columns
												for (JsonValue vd : vres) {
													JsonValue xVal = vd.asObject().get(x);
													for (int i = 0; i < yNames.length; i++) {
														JsonValue yVal = vd.asObject().get(yNames[i]);
														yVals[i].add(yVal);
													}
													xData.add(xVal);
												}
												dataColumns.add("x", xData);

												for (int i = 0; i < yNames.length; i++) {
													JsonObject yObj = y.Value.get(i).asObject();
													yObj.add("data", yVals[i]);
													yData.add(yObj);
												}

												dataColumns.add("y", yData);
												res.add("Name", obj.getString("Name"));
												res.add("X-label", obj.getString("X-label"));
												res.add("Y-label", obj.getString("Y-label"));
												if (obj.contains("Options")) {
													res.add("Options", obj.get("Options"));
												}
												if (obj.contains("LibOptions")) {
													res.add("LibOptions", obj.get("LibOptions"));
												}
												res.add("Data", dataColumns);

												result.add(res);
											}
										}
									} catch (Throwable e) {
										e.printStackTrace();
										continue;
									}
								}
								return result;
							}
						}
					}
					return null;
				}
			});
		}
	});

	@Override
	protected HTTPResponse handle(ProcessState state) {
		if (state.IsLoggedIn()) {
			if (state.Request.path.equals("/graph") && state.Request.method.equals("GET")) {
				int returnCode = 200;
				String type = "text/json;";
				String returnCodeDescription = "OK";
				JsonValue graphs = loadGraphs(state);
				String data = graphs == null ? "[]" : graphs.getJsonString();
				state.setIntention(Intention.COLLECT_GRAPHS, new JsonObject());
				return new HTTPResponse(state.Request.protocol, returnCode, returnCodeDescription, data, type, state.Request.cookiesLines);
			}
		}
		return super.handle(state);
	}

	@Override
	public void clearCache() {
		super.clearCache();
		graphCache.clear();
	}
}
