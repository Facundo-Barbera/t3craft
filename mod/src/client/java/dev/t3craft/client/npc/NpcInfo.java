package dev.t3craft.client.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/** One thread as sent by the bridge in {@code snapshot.npcs[]} / {@code npc.upsert}. */
public record NpcInfo(String id, String title, @Nullable String project, NpcState state, @Nullable String activity) {
	public static @Nullable NpcInfo fromJson(JsonObject o) {
		String id = str(o, "id");
		if (id == null || id.isEmpty()) {
			return null;
		}
		String title = str(o, "title");
		return new NpcInfo(id, title == null || title.isBlank() ? "(untitled)" : title, str(o, "project"),
				NpcState.fromWire(String.valueOf(str(o, "state"))), str(o, "activity"));
	}

	static @Nullable String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
}
