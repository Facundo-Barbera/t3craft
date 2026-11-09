package dev.t3craft.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import dev.t3craft.client.config.T3CraftSettings.Notifications;
import dev.t3craft.client.config.T3CraftSettings.Portable;
import dev.t3craft.t3.Environments;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * JSON ⇄ {@link T3CraftSettings} with validation. Decoding never throws: an invalid value is
 * replaced by its default and reported in {@link Decoded#warnings()} (by key name only, never the
 * value, so a misplaced secret cannot leak into a log). Encoding merges into the original object so
 * keys owned by other features survive a save.
 */
public final class ConfigCodec {
	private ConfigCodec() {
	}

	public record Decoded(T3CraftSettings settings, List<String> warnings) {
	}

	// ---- decode -------------------------------------------------------------------------------

	/** {@code o} must already be migrated ({@link ConfigMigrations#migrate}). */
	public static Decoded decode(JsonObject o) {
		List<String> w = new ArrayList<>();
		T3CraftSettings d = T3CraftSettings.defaults();

		String mode = d.mode();
		String rawMode = str(o, "mode");
		if (rawMode != null) {
			String m = rawMode.trim().toLowerCase(Locale.ROOT);
			if (m.equals("direct") || m.equals("bridge")) {
				mode = m;
			} else {
				w.add("mode must be \"direct\" or \"bridge\"; using direct");
			}
		}

		String bridgeUrl = d.bridgeUrl();
		String rawUrl = str(o, "bridgeUrl");
		if (rawUrl != null) {
			if (validBridgeUrl(rawUrl.trim())) {
				bridgeUrl = rawUrl.trim();
			} else {
				w.add("bridgeUrl must be ws:// or wss:// without credentials; using default");
			}
		}

		String bridgeKey = str(o, "bridgeKey");
		if (bridgeKey != null && bridgeKey.isBlank()) {
			bridgeKey = null;
		}

		double window = d.recentWindowHours();
		int max = d.maxPerEnvironment();
		JsonObject vis = obj(o, "visibility");
		if (vis != null) {
			Double h = num(vis, "recentWindowHours");
			if (h != null) {
				if (validWindow(h)) {
					window = h;
				} else {
					w.add("visibility.recentWindowHours must be 0.." + (long) T3CraftSettings.MAX_WINDOW_HOURS);
				}
			}
			Double m = num(vis, "maxPerEnvironment");
			if (m != null) {
				if (validMax(m)) {
					max = m.intValue();
				} else {
					w.add("visibility.maxPerEnvironment must be 1.." + T3CraftSettings.MAX_PER_ENV_LIMIT);
				}
			}
		}
		boolean subagents = bool(o, "includeSubagents", d.includeSubagents(), w);

		Notifications n = decodeNotifications(obj(o, "notifications"), w);

		Map<String, EnvPrefs> envs = new LinkedHashMap<>();
		JsonObject envObj = obj(o, "environments");
		if (envObj != null) {
			for (Map.Entry<String, JsonElement> e : envObj.entrySet()) {
				if (!Environments.ID.matcher(e.getKey()).matches()) {
					w.add("environments: ignoring invalid id (must match " + Environments.ID.pattern() + ")");
					continue;
				}
				if (!e.getValue().isJsonObject()) {
					w.add("environments." + e.getKey() + " must be an object");
					continue;
				}
				EnvPrefs p = decodeEnv(e.getKey(), e.getValue().getAsJsonObject(), w);
				if (!p.equals(EnvPrefs.DEFAULT)) {
					envs.put(e.getKey(), p);
				}
			}
		} else if (o.has("environments")) {
			w.add("environments must be an object");
		}

		Portable portable = decodePortable(o, w);
		T3CraftSettings.Hud hud = decodeHud(o, w);
		T3CraftSettings.ChatPrefs chat = decodeChat(o, w);

		return new Decoded(new T3CraftSettings(T3CraftSettings.SCHEMA_VERSION, mode, bridgeUrl, bridgeKey, window, max, subagents, n, envs,
				portable, hud, chat), w);
	}

	/** {@code "chat": {"hiddenEnvironments": ["vps", ...]}}; absent → defaults (additive, no migration). */
	private static T3CraftSettings.ChatPrefs decodeChat(JsonObject o, List<String> w) {
		JsonObject c = obj(o, "chat");
		if (c == null) {
			if (o.has("chat") && !o.get("chat").isJsonNull()) {
				w.add("chat must be an object");
			}
			return T3CraftSettings.ChatPrefs.DEFAULT;
		}
		java.util.Set<String> hidden = new java.util.TreeSet<>();
		JsonElement h = c.get("hiddenEnvironments");
		if (h != null && h.isJsonArray()) {
			for (JsonElement e : h.getAsJsonArray()) {
				if (e.isJsonPrimitive() && Environments.ID.matcher(e.getAsString()).matches()) {
					hidden.add(e.getAsString());
				} else {
					w.add("chat.hiddenEnvironments: ignoring invalid id");
				}
			}
		} else if (h != null && !h.isJsonNull()) {
			w.add("chat.hiddenEnvironments must be an array");
		}
		return new T3CraftSettings.ChatPrefs(hidden);
	}

	private static Portable decodePortable(JsonObject o, List<String> w) {
		Portable d = Portable.defaults();
		JsonObject p = obj(o, "portable");
		if (p == null) {
			if (o.has("portable") && !o.get("portable").isJsonNull()) {
				w.add("portable must be an object");
			}
			return d;
		}
		return new Portable(bool(p, "heldScreen", d.heldScreen(), w));
	}

	private static T3CraftSettings.Hud decodeHud(JsonObject o, List<String> w) {
		T3CraftSettings.Hud d = T3CraftSettings.Hud.defaults();
		JsonObject h = obj(o, "hud");
		if (h == null) {
			if (o.has("hud") && !o.get("hud").isJsonNull()) {
				w.add("hud must be an object");
			}
			return d;
		}
		String pos = str(h, "position");
		String align = str(h, "align");
		if (pos != null && !T3CraftSettings.Hud.POSITIONS.contains(pos)) {
			w.add("hud.position must be top or bottom");
		}
		if (align != null && !T3CraftSettings.Hud.ALIGNS.contains(align)) {
			w.add("hud.align must be left, center or right");
		}
		return new T3CraftSettings.Hud(bool(h, "statusBar", d.statusBar(), w), pos == null ? d.position() : pos,
				align == null ? d.align() : align, bool(h, "hideWhenIdle", d.hideWhenIdle(), w), bool(h, "hologram", d.hologram(), w),
				bool(h, "attentionArrow", d.attentionArrow(), w));
	}

	private static Notifications decodeNotifications(@Nullable JsonObject o, List<String> w) {
		Notifications d = Notifications.defaults();
		if (o == null) {
			return d;
		}
		JsonObject s = obj(o, "sounds");
		JsonObject sounds = s == null ? new JsonObject() : s;
		return new Notifications(
				bool(o, "enabled", d.enabled(), w),
				bool(o, "chat", d.chat(), w),
				bool(o, "toast", d.toast(), w),
				bool(o, "sound", d.sound(), w),
				bool(sounds, "attention", d.soundAttention(), w),
				bool(sounds, "done", d.soundDone(), w),
				bool(sounds, "error", d.soundError(), w),
				bool(o, "connectionToasts", d.connectionToasts(), w),
				decodeAttention(obj(o, "attention"), w));
	}

	private static T3CraftSettings.Attention decodeAttention(@Nullable JsonObject o, List<String> w) {
		T3CraftSettings.Attention d = T3CraftSettings.Attention.defaults();
		if (o == null) {
			return d;
		}
		String bar = str(o, "actionBar");
		if (bar != null && !T3CraftSettings.Attention.BAR_MODES.contains(bar)) {
			w.add("notifications.attention.actionBar must be off, also or instead");
		}
		return new T3CraftSettings.Attention(bool(o, "windowTitle", d.windowTitle(), w), bool(o, "taskbarFlash", d.taskbarFlash(), w),
				bar == null ? d.actionBar() : bar);
	}

	private static EnvPrefs decodeEnv(String id, JsonObject o, List<String> w) {
		EnvPrefs d = EnvPrefs.DEFAULT;
		String label = str(o, "label");
		if (label != null) {
			label = sanitizeLabel(label);
		}
		Integer color = null;
		if (o.has("color") && !o.get("color").isJsonNull()) {
			color = parseColor(o.get("color"));
			if (color == null) {
				w.add("environments." + id + ".color must be \"#RRGGBB\"");
			}
		}
		boolean visible = bool(o, "visible", d.visible(), w);
		JsonObject f = obj(o, "filter");
		JsonObject filter = f == null ? new JsonObject() : f;
		boolean needs = bool(filter, "needsYou", d.showNeedsYou(), w);
		boolean working = bool(filter, "working", d.showWorking(), w);
		boolean recent = bool(filter, "recent", d.showRecent(), w);
		Double hours = num(filter, "recentWindowHours");
		if (hours != null && !validWindow(hours)) {
			w.add("environments." + id + ".filter.recentWindowHours out of range; using global");
			hours = null;
		}
		Double m = num(filter, "maxPerEnvironment");
		Integer max = null;
		if (m != null) {
			if (validMax(m)) {
				max = m.intValue();
			} else {
				w.add("environments." + id + ".filter.maxPerEnvironment out of range; using global");
			}
		}
		return new EnvPrefs(label, color, visible, needs, working, recent, hours, max);
	}

	// ---- encode -------------------------------------------------------------------------------

	/**
	 * Writes {@code s} into a copy of {@code base} (the file as read; may be empty). Unknown keys are
	 * kept; known ones are replaced.
	 */
	public static JsonObject encode(T3CraftSettings s, JsonObject base) {
		JsonObject o = base.deepCopy();
		o.addProperty("schemaVersion", T3CraftSettings.SCHEMA_VERSION);
		o.addProperty("mode", s.mode());
		o.addProperty("bridgeUrl", s.bridgeUrl());
		o.add("bridgeKey", s.bridgeKey() == null ? JsonNull.INSTANCE : new JsonPrimitive(s.bridgeKey()));
		JsonObject vis = obj(o, "visibility") == null ? new JsonObject() : obj(o, "visibility");
		vis.add("recentWindowHours", number(s.recentWindowHours()));
		vis.addProperty("maxPerEnvironment", s.maxPerEnvironment());
		o.add("visibility", vis);
		o.addProperty("includeSubagents", s.includeSubagents());

		Notifications n = s.notifications();
		JsonObject no = obj(o, "notifications") == null ? new JsonObject() : obj(o, "notifications");
		no.addProperty("enabled", n.enabled());
		no.addProperty("chat", n.chat());
		no.addProperty("toast", n.toast());
		no.addProperty("sound", n.sound());
		JsonObject sounds = obj(no, "sounds") == null ? new JsonObject() : obj(no, "sounds");
		sounds.addProperty("attention", n.soundAttention());
		sounds.addProperty("done", n.soundDone());
		sounds.addProperty("error", n.soundError());
		no.add("sounds", sounds);
		no.addProperty("connectionToasts", n.connectionToasts());
		JsonObject attention = obj(no, "attention") == null ? new JsonObject() : obj(no, "attention");
		attention.addProperty("windowTitle", n.attention().windowTitle());
		attention.addProperty("taskbarFlash", n.attention().taskbarFlash());
		attention.addProperty("actionBar", n.attention().actionBar());
		no.add("attention", attention);
		o.add("notifications", no);

		JsonObject envs = new JsonObject();
		s.environments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			EnvPrefs p = e.getValue();
			JsonObject eo = new JsonObject();
			eo.add("label", p.label() == null ? JsonNull.INSTANCE : new JsonPrimitive(p.label()));
			eo.add("color", p.color() == null ? JsonNull.INSTANCE : new JsonPrimitive(formatColor(p.color())));
			eo.addProperty("visible", p.visible());
			JsonObject f = new JsonObject();
			f.addProperty("needsYou", p.showNeedsYou());
			f.addProperty("working", p.showWorking());
			f.addProperty("recent", p.showRecent());
			f.add("recentWindowHours", p.windowHours() == null ? JsonNull.INSTANCE : number(p.windowHours()));
			f.add("maxPerEnvironment", p.max() == null ? JsonNull.INSTANCE : new JsonPrimitive(p.max()));
			eo.add("filter", f);
			envs.add(e.getKey(), eo);
		});
		o.add("environments", envs);

		JsonObject po = obj(o, "portable") == null ? new JsonObject() : obj(o, "portable");
		po.addProperty("heldScreen", s.portable().heldScreen());
		o.add("portable", po);

		JsonObject ho = obj(o, "hud") == null ? new JsonObject() : obj(o, "hud");
		ho.addProperty("statusBar", s.hud().statusBar());
		ho.addProperty("position", s.hud().position());
		ho.addProperty("align", s.hud().align());
		ho.addProperty("hideWhenIdle", s.hud().hideWhenIdle());
		ho.addProperty("hologram", s.hud().hologram());
		ho.addProperty("attentionArrow", s.hud().attentionArrow());
		o.add("hud", ho);

		JsonObject chat = obj(o, "chat") == null ? new JsonObject() : obj(o, "chat");
		com.google.gson.JsonArray hidden = new com.google.gson.JsonArray();
		s.chat().hiddenEnvironments().forEach(hidden::add);
		chat.add("hiddenEnvironments", hidden);
		o.add("chat", chat);

		// feat/hq-layouts: HQ layout (owned by HqLayoutConfig); only the default is filled in, never replaced
		if (!o.has(dev.t3craft.hq.layout.HqLayouts.CONFIG_KEY)) {
			o.addProperty(dev.t3craft.hq.layout.HqLayouts.CONFIG_KEY, dev.t3craft.hq.layout.HqLayouts.DEFAULT_ID);
		}
		return o;
	}

	// ---- validation helpers (shared with the screens) -----------------------------------------

	public static boolean validWindow(double h) {
		return Double.isFinite(h) && h >= 0 && h <= T3CraftSettings.MAX_WINDOW_HOURS;
	}

	public static boolean validMax(double m) {
		return Double.isFinite(m) && m == Math.rint(m) && m >= 1 && m <= T3CraftSettings.MAX_PER_ENV_LIMIT;
	}

	public static boolean validBridgeUrl(String raw) {
		try {
			URI u = URI.create(raw);
			return ("ws".equals(u.getScheme()) || "wss".equals(u.getScheme())) && u.getHost() != null && u.getUserInfo() == null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/** Trimmed, control characters removed, at most 32 chars; null when empty. */
	public static @Nullable String sanitizeLabel(String raw) {
		String s = raw.replaceAll("\\p{Cntrl}", "").trim();
		if (s.length() > 32) {
			s = s.substring(0, 32).trim();
		}
		return s.isEmpty() ? null : s;
	}

	/** {@code "#RRGGBB"}, {@code "RRGGBB"} or a number 0..0xFFFFFF; null when invalid. */
	public static @Nullable Integer parseColor(@Nullable JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) {
			double v = p.getAsDouble();
			return v == Math.rint(v) && v >= 0 && v <= 0xFFFFFF ? (int) v : null;
		}
		return parseColor(p.getAsString());
	}

	public static @Nullable Integer parseColor(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		if (!s.matches("[0-9a-fA-F]{6}")) {
			return null;
		}
		return Integer.parseInt(s, 16);
	}

	public static String formatColor(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	// ---- json helpers -------------------------------------------------------------------------

	private static JsonPrimitive number(double v) {
		return v == Math.rint(v) && Math.abs(v) < 1e15 ? new JsonPrimitive((long) v) : new JsonPrimitive(v);
	}

	private static @Nullable String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static @Nullable Double num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		try {
			return e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : Double.valueOf(e.getAsString().trim());
		} catch (NumberFormatException ex) {
			return Double.NaN; // reported by the range check
		}
	}

	private static @Nullable JsonObject obj(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static boolean bool(JsonObject o, String k, boolean def, List<String> w) {
		JsonElement e = o.get(k);
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		w.add(k + " must be true or false");
		return def;
	}
}
