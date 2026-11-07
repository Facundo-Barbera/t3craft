package dev.t3craft.client.link;

import com.google.gson.JsonObject;

/** Callbacks from {@link BridgeLink}. Always invoked on the Minecraft client thread. */
public interface BridgeListener {
	void onStatus(LinkStatus status, String detail);

	/** One decoded protocol envelope ({@code {v:1,type,...}}). */
	void onMessage(String type, JsonObject msg);
}
