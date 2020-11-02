package cz.rion.buildserver.test;

import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;
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
	
	public TestResult perform(BadResults badResults, TestInput input);
	
}
