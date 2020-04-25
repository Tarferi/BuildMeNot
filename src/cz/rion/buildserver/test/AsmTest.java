package cz.rion.buildserver.test;

import cz.rion.buildserver.db.RuntimeDB.BadResults;
import cz.rion.buildserver.test.TestManager.TestInput;
import cz.rion.buildserver.test.TestManager.TestResult;

public interface AsmTest {

	public String VerifyCode(BadResults badResults, String asm);

	public String GetFinalASM(String login, String asm);

	public String getID();

	public String getDescription();

	public String getTitle();

	public boolean isHidden();

	public TestResult perform(BadResults badResults, TestInput input);

	public String getInitialCode();

	public boolean isSecret();

	public String[] getAllowedInstructions();

}
