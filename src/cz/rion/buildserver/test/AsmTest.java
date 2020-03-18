package cz.rion.buildserver.test;

import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;

public interface AsmTest {

	public String CodeValid(String asm);
	
	public String getID();

	public String getDescription();
	
	public String getTitle();

	public TestResult perform(TestInput input);

	public String getInitialCode();

}
