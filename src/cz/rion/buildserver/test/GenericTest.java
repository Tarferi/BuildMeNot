package cz.rion.buildserver.test;

import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;

public interface GenericTest {

	public String getToolchain();
	
	public String getID();

	public String getDescription();

	public String getTitle();

	public boolean isHidden();

	public boolean isSecret();
	
	public String getSubmittedCode();
	
	public TestResult perform(BadResults badResults, TestInput input);
	
	/*
	
	public String VerifyCode(BadResults badResults, String asm);
	
	public String GetFinalCode(String login, String asm);

	public String[] getAllowedInstructions();
	*/

}
