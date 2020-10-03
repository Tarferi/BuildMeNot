package cz.rion.buildserver.http.server;

import cz.rion.buildserver.exceptions.HTTPClientException;
import cz.rion.buildserver.exceptions.SwitchClientException;
import cz.rion.buildserver.http.HTTPRequest;
import cz.rion.buildserver.http.HTTPResponse;

public interface HTTPClientFactory {

	public HTTPRequest getRequest() throws SwitchClientException, HTTPClientException;

	public void writeResponse(HTTPResponse response) throws HTTPClientException;

	public void close();

	public String getRemoteAddress();
	
}
