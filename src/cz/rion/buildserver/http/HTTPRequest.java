package cz.rion.buildserver.http;

import java.util.List;
import java.util.Map;

public final class HTTPRequest {
	public final String method;
	public final String protocol;
	public final String path;
	public final byte[] data;
	public final String host;
	public final String authData;
	public final Map<String, String> headers;
	public final List<String> cookiesLines;
	public final String remoteAddress;
	public final boolean isSSL;
	private final String originlaPath;
	/**
	 * "http" for non-ssl and "https" for ssl
	 */
	public final String protocol_norm;

	public final HTTPRequest getAnotherHost(String newHost) {
		return new HTTPRequest(method, newHost, protocol, originlaPath, data, headers, cookiesLines, remoteAddress, isSSL);
	}

	public HTTPRequest(String method, String host, String protocol, String path, byte[] data, Map<String, String> headers, List<String> cookiesLines, String remoteAddress, boolean isSSL) {
		String authData = null;
		this.originlaPath = path;
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
		this.host = host;
		this.headers = headers;
		this.authData = authData;
		this.cookiesLines = cookiesLines;
		if (remoteAddress.startsWith("/")) {
			remoteAddress = remoteAddress.substring(1);
		}
		this.remoteAddress = remoteAddress;
		this.isSSL = isSSL;
		this.protocol_norm = isSSL ? "https" : "http";
	}
}