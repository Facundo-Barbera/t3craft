package dev.t3craft.client.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * One NPC as resolved by the bridge (protocol {@code NpcView}). The mod draws these fields as-is
 * and never derives colours or text from {@code state}.
 */
public record NpcInfo(
		String id,
		String title,
		String label,
		String envLabel,
		String sublabel,
		int color,
		@Nullable String activity,
		int priority,
		boolean settled) {
	private static final int FALLBACK_COLOR = 0xAAAAAA;

	public static @Nullable NpcInfo fromJson(JsonObject o) {
		String id = str(o, "id");
		if (id == null || id.isEmpty()) {
			return null;
		}
		String title = orElse(str(o, "title"), id);
		return new NpcInfo(
				id,
				title,
				orElse(str(o, "label"), title),
				orElse(str(o, "envLabel"), ""),
				orElse(str(o, "sublabel"), ""),
				parseColor(str(o, "color")),
				str(o, "activity"),
				o.has("priority") && o.get("priority").isJsonPrimitive() ? o.get("priority").getAsInt() : 99,
				o.has("settled") && o.get("settled").isJsonPrimitive() && o.get("settled").getAsBoolean());
	}

	/** {@code #RRGGBB} → 0xRRGGBB. */
	public static int parseColor(@Nullable String s) {
		if (s == null || !s.matches("#[0-9a-fA-F]{6}")) {
			return FALLBACK_COLOR;
		}
		return Integer.parseInt(s.substring(1), 16);
	}

	static @Nullable String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static String orElse(@Nullable String s, String def) {
		return s == null || s.isBlank() ? def : s;
	}
}
