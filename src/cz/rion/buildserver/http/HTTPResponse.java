package cz.rion.buildserver.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import cz.rion.buildserver.Settings;
import cz.rion.buildserver.exceptions.HTTPClientException;

public final class HTTPResponse {
	public final String protocol;
	public final int code;
	public final String codeDescription;
	public final byte[] data;
	public final String contentType;
	public final List<Entry<String, String>> additionalHeaderFields = new ArrayList<>();

	public HTTPResponse(String protocol, int code, String codeDescription, String data, String contentType, List<String> cookieLines) {
		this(protocol, code, codeDescription, data.getBytes(Settings.getDefaultCharset()), contentType, cookieLines);
	}

	public HTTPResponse(String protocol, int code, String codeDescription, byte[] data, String contentType, List<String> cookieLines) {
		this.protocol = protocol;
		this.code = code;
		this.codeDescription = codeDescription;
		this.data = data;
		this.contentType = contentType;
		for (String str : cookieLines) {
			this.addAdditionalHeaderField("Set-Cookie", str);
		}
	}
	
	public void write(HTTPResponseWriter client) throws HTTPClientException {
		try {
			client.write((protocol + " " + code + " " + codeDescription + "\r\n").getBytes(Settings.getDefaultCharset()));
			client.write(("Connection: close\r\n").getBytes(Settings.getDefaultCharset()));
			if (contentType != null) {
				client.write(("Content-Type: " + contentType + "\r\n").getBytes(Settings.getDefaultCharset()));
			}
			for (Entry<String, String> entry : additionalHeaderFields) {
				client.write((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes(Settings.getDefaultCharset()));
			}
			client.write(("Content-Length: " + data.length + "\r\n").getBytes(Settings.getDefaultCharset()));
			client.write(("\r\n").getBytes(Settings.getDefaultCharset()));
			client.write(data);
		} catch (IOException e) {
			throw new HTTPClientException("Failed to write response", e);
		}
	}

	public final void addAdditionalHeaderField(final String key, final String value) {
		Entry<String, String> entry = new Entry<String, String>() {

			private String _key = key;
			private String _value = value;

			@Override
			public String getKey() {
				return _key;
			}

			@Override
			public String getValue() {
				return _value;
			}

			@Override
			public String setValue(String value) {
				_value = value;
				return value;
			}
		};
		additionalHeaderFields.add(entry);
	}
}