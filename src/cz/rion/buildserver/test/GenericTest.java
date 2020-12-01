package cz.rion.buildserver.test;

import java.util.List;
import java.util.Set;

import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.StaticDB;
import cz.rion.buildserver.db.VirtualFileManager;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
import cz.rion.buildserver.test.TestManager.RunnerLogger;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;

public interface GenericTest {

	public Toolchain getToolchain();

	public String getID();

	public String getDescription();

	public String getTitle();

	public boolean isHidden();

	public boolean isSecret();

	public String getInitialCode();

	public TestResult perform(RunnerLogger logger, BadResults badResults, TestInput input);

	public StaticDB getStaticDB();

	public VirtualFileManager getFiles();

	public Set<String> getPriorTestsIDs();

	public String getBuilder();

	public List<GenericTestWindow> getWindowData();
}
