package cz.rion.buildserver;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import cz.rion.buildserver.exceptions.DatabaseException;
import cz.rion.buildserver.exceptions.HTTPServerException;
import cz.rion.buildserver.http.HTTPServer;
import cz.rion.buildserver.json.JsonValue.JsonString;
import cz.rion.buildserver.ui.MainWindow;
import cz.rion.buildserver.wrappers.FileReadException;
import cz.rion.buildserver.wrappers.MyFS;

public class MAIN {

	private static void test1() {
		encode("Á");
		encode("Ä");
		encode("á");
		encode("ä");
		encode("É");
		encode("Ě");
		encode("Ë");
		encode("é");
		encode("ě");
		encode("ë");
		encode("Í");
		encode("Ï");
		encode("í");
		encode("ï");
		encode("Ó");
		encode("Ö");
		encode("ó");
		encode("ö");
		encode("Ú");
		encode("Ů");
		encode("Ü");
		encode("ú");
		encode("ů");
		encode("ü");
		encode("Ý");
		encode("Ÿ");
		encode("ý");
		encode("ÿ");
		encode("Č");
		encode("č");
		encode("Ď");
		encode("ď");
		encode("Ň");
		encode("ň");
		encode("Ř");
		encode("ř");
		encode("Š");
		encode("š");
		encode("Ť");
		encode("ť");
		encode("Ž");
		encode("ž");

	}

	private static String getString(byte[] buffer) {
		StringBuilder sb = new StringBuilder();
		for (byte b : buffer) {
			sb.append((b & 0xff) + ", ");
		}
		sb.setLength(sb.length() - 2);
		return sb.toString();
	}

	public static void main2(String[] args) throws FileReadException {
		String diakritika = "ÁÄáäÉĚËéěëÍÏíïÓÖóöÚŮÜúůüÝŸýÿČčĎďŇňŘřŠšŤťŽž";
		JsonString str =new JsonString(diakritika);
		String s = MyFS.readFile("web/index.js");
		System.out.println(getString(s.getBytes(Charset.forName("windows-1250"))));
	}

	private static void encode(String string) {
		Charset c = Charset.forName("windows-1250");
		ByteBuffer e = c.encode(string);

		System.out.println("E(" + string + ") = " + getString(e.array()));
	}

	public static void main(String[] args) {
		if (Settings.showUI()) {
			new MainWindow();
		}
		HTTPServer server;
		try {
			server = new HTTPServer(Settings.GetHTTPServerPort());
			server.run();
		} catch (HTTPServerException | DatabaseException e) {
			e.printStackTrace();
		}
	}
}
