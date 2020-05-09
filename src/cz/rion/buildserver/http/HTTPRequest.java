package cz.rion.buildserver.http;

import java.util.List;
import java.util.Map;

public final class HTTPRequest {
	public final String method;
	public final String protocol;
	public final String path;
	public final byte[] data;
	public final String authData;
	public final Map<String, String> headers;
	public final List<String> cookiesLines;

	public HTTPRequest(String method, String protocol, String path, byte[] data, Map<String, String> headers, List<String> cookiesLines) {
		String authData = null;
		if (path.contains("/auth/")) {
			String[] np = path.split("/auth/", 2);
			path = np[0] + "/";
			authData = np[1];
			np = authData.split("/", 2);
			if (np.length == 2) { // "http://xxx.yyy/test/abc/auth/123/def
				path += np[1];
				authData = np[0];
			}
		}

		this.method = method;
		this.protocol = protocol;
		this.path = path;
		this.data = data;
		this.headers = headers;
		this.authData = authData;
		this.cookiesLines = cookiesLines;
	}
}