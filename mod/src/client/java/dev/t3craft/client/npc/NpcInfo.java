package dev.t3craft.client.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.NpcView;
import org.jspecify.annotations.Nullable;

/**
 * One NPC as drawn by the mod. Filled either from the bridge's protocol {@code NpcView} JSON
 * (bridge mode) or from {@link NpcView} computed in the mod (direct mode). The renderer only reads
 * these fields.
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
		boolean settled,
		String state,
		String groupId,
		String groupLabel,
		String skin,
		@Nullable String particle) {
	private static final int FALLBACK_COLOR = 0xAAAAAA;

	public static NpcInfo fromView(NpcView v) {
		return new NpcInfo(v.id(), v.title(), v.label(), v.envLabel(), v.sublabel(), v.color(), v.activity(), v.priority(),
				v.settled(), v.state().wire, v.groupId(), v.groupLabel(), v.skin(), v.particle());
	}

	public static @Nullable NpcInfo fromJson(JsonObject o) {
		String id = str(o, "id");
		if (id == null || id.isEmpty()) {
			return null;
		}
		String title = orElse(str(o, "title"), id);
		String envLabel = orElse(str(o, "envLabel"), "");
		String state = orElse(str(o, "state"), "idle");
		String env = orElse(str(o, "env"), envLabel);
		return new NpcInfo(
				id,
				title,
				orElse(str(o, "label"), title),
				envLabel,
				orElse(str(o, "sublabel"), ""),
				parseColor(str(o, "color")),
				str(o, "activity"),
				o.has("priority") && o.get("priority").isJsonPrimitive() ? o.get("priority").getAsInt() : 99,
				o.has("settled") && o.get("settled").isJsonPrimitive() && o.get("settled").getAsBoolean(),
				state,
				orElse(str(o, "groupId"), env),
				orElse(str(o, "groupLabel"), orElse(str(o, "project"), "(no project)")),
				orElse(str(o, "skin"), env + "/" + state),
				str(o, "particle"));
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
