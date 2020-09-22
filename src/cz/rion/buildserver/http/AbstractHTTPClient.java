package cz.rion.buildserver.http;

import cz.rion.buildserver.db.layers.staticDB.LayeredBuildersDB.Toolchain;

public abstract class AbstractHTTPClient {

	public static enum HTTPClientIntentType {
		GET_RESOURCE, GET_HTML, PERFORM_TEST, HACK, ADMIN, COLLECT_TESTS, UNKNOWN
	}

	private HTTPClientIntentType intention = HTTPClientIntentType.UNKNOWN;

	protected void setIntention(HTTPClientIntentType intention) {
		this.intention = intention;
	}

	protected void ToolChainKnown(Toolchain toolchain) {
	}

	public HTTPClientIntentType getIntention() {
		return intention;
	}

	protected AbstractHTTPClient() {
	}
}
