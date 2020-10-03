package cz.rion.buildserver.http;

import java.io.IOException;

public interface HTTPResponseWriter {

	void write(byte[] bytes) throws IOException;

}
