package dev.t3craft.outbound;

import java.net.URI;

/** Sends one rendered request. Blocking; the emitter calls it from its own worker thread only. Must not throw. */
public interface Transport {
	Result send(URI url, Request request);
}
