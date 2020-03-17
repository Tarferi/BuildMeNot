package cz.rion.buildserver;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.GoLinkExecutionException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.exceptions.NasmExecutionException;
import cz.rion.buildserver.exceptions.RuntimeExecutionException;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.ui.MainWindow;
import cz.rion.buildserver.wrappers.NasmWrapper;
import cz.rion.buildserver.wrappers.NasmWrapper.RunResult;

public class MAIN {

	public static void main(String[] args) {
		MainWindow wnd = new MainWindow();

		HTTPServer server;
		try {
			server = new HTTPServer(8000);
			server.run();
		} catch (HTTPServerException | DatabaseException e) {
			e.printStackTrace();
		}
	}

	public static void main2(String[] args) {
		String code = "%include \"rw32-2018.inc\"\r\n" + "\r\n" + "section .data\r\n" + "	sMessage db \"Hello World!\",EOL,0\r\n" + "\r\n" + "section .text\r\n" + "\r\n" + "CMAIN:\r\n" + "	push ebp\r\n" + "	mov ebp,esp\r\n" + "\r\n" + "	mov esi,sMessage\r\n" + "	call ReadInt8\r\n" + "call WriteInt8\r\n"+ "call WriteInt8\r\n" + "; zde muzete psat vas kod\r\n" + "\r\n" + "	pop ebp\r\n" + "	ret\r\n" + "";
		try {
			RunResult result = NasmWrapper.run("test01", code, "10\r\n15\r\n", 2000, true, true);
			return;
		} catch (NasmExecutionException e) {
			e.printStackTrace();
		} catch (GoLinkExecutionException e) {
			e.printStackTrace();
		} catch (RuntimeExecutionException e) {
			e.printStackTrace();
		}
	}

}
