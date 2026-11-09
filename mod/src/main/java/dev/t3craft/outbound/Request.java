package dev.t3craft.outbound;

import java.util.Map;

/** What a {@link Template} produces: the body and the extra headers of one POST (the URL stays out). */
public record Request(String contentType, Map<String, String> headers, String body) {
	public Request {
		headers = Map.copyOf(headers);
	}
}
