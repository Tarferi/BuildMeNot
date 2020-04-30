package cz.rion.buildserver.http;

public abstract class AbstractHTTPClient {

	public static enum HTTPClientIntentType {
		GET_RESOURCE, GET_HTML, PERFORM_TEST, HACK, ADMIN, COLLECT_TESTS, UNKNOWN
	}

	private HTTPClientIntentType intention = HTTPClientIntentType.UNKNOWN;

	protected void setIntention(HTTPClientIntentType intention) {
		this.intention = intention;
	}
	
	public HTTPClientIntentType getIntention() {
		return intention;
	}

	protected AbstractHTTPClient() {
	}
}
