package dev.t3craft.hq.lead;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: when a thread counts as a <b>lead</b> (a coordinator that hands out work) and gets
 * its own office in the HQ. Config ({@code config/t3craft.json}, additive, every key optional):
 *
 * <pre>{@code
 * "leadOffice": {
 *   "enabled": true,
 *   "minAgeHours": 6,          // the thread must be at least this old
 *   "recentChildren": 3,       // ... and have handed out this many tasks/subagents
 *   "recentWindowHours": 24,   //     in this window,
 *   "workingChildren": 2,      // ... or have this many children working right now
 *   "holdMinutes": 45,         // hysteresis: a lead keeps the office this long after it stops qualifying
 *   "minHoldMinutes": 10,      // and never loses it sooner than this after getting it
 *   "maxOffices": 3,           // at most this many offices (the most active leads), 0..4
 *   "briefing": true           // new children stop by the lead's office before going to their desk
 * }
 * }</pre>
 */
public record LeadPolicy(boolean enabled, long minAgeMs, int recentChildren, long recentWindowMs, int workingChildren, long holdMs,
		long minHoldMs, int maxOffices, boolean briefing) {
	public static final String KEY = "leadOffice";
	/** Hard cap on offices whatever the config says (each one is a module of the HQ). */
	public static final int MAX_OFFICES = 4;
	public static final LeadPolicy DEFAULTS = new LeadPolicy(true, hours(6), 3, hours(24), 2, minutes(45), minutes(10), 3, true);

	public LeadPolicy {
		minAgeMs = Math.max(0, minAgeMs);
		recentChildren = Math.max(1, recentChildren);
		recentWindowMs = Math.max(minutes(1), recentWindowMs);
		workingChildren = Math.max(1, workingChildren);
		holdMs = Math.max(0, holdMs);
		minHoldMs = Math.max(0, minHoldMs);
		maxOffices = Math.clamp(maxOffices, 0, MAX_OFFICES);
	}

	private static long hours(double h) {
		return Math.round(h * 3_600_000d);
	}

	private static long minutes(double m) {
		return Math.round(m * 60_000d);
	}

	// ---- the shared instance ------------------------------------------------------------------

	private static volatile @Nullable LeadPolicy active;

	/** The policy in force ({@link #DEFAULTS} until the client loads the config). */
	public static LeadPolicy active() {
		LeadPolicy p = active;
		return p == null ? DEFAULTS : p;
	}

	public static void use(@Nullable LeadPolicy policy) {
		active = policy;
	}

	// ---- config -------------------------------------------------------------------------------

	/** The {@code leadOffice} block of the whole config; missing or wrong keys keep their defaults. */
	public static LeadPolicy parse(@Nullable JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		LeadPolicy d = DEFAULTS;
		return new LeadPolicy(
				bool(o, "enabled", d.enabled),
				hours(num(o, "minAgeHours", d.minAgeMs / 3_600_000d, 0, 24 * 365)),
				(int) num(o, "recentChildren", d.recentChildren, 1, 1000),
				hours(num(o, "recentWindowHours", d.recentWindowMs / 3_600_000d, 0.02, 24 * 30)),
				(int) num(o, "workingChildren", d.workingChildren, 1, 1000),
				minutes(num(o, "holdMinutes", d.holdMs / 60_000d, 0, 7 * 24 * 60)),
				minutes(num(o, "minHoldMinutes", d.minHoldMs / 60_000d, 0, 7 * 24 * 60)),
				(int) num(o, "maxOffices", d.maxOffices, 0, MAX_OFFICES),
				bool(o, "briefing", d.briefing));
	}

	/** Reads {@code file} (config/t3craft.json); missing or unreadable gives the defaults, never throws. */
	public static LeadPolicy load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			JsonElement e = com.google.gson.JsonParser.parseString(Files.readString(file));
			return parse(e.isJsonObject() ? e.getAsJsonObject() : null);
		} catch (Exception e) {
			return DEFAULTS;
		}
	}

	private static boolean bool(JsonObject o, String k, boolean def) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
	}

	private static double num(JsonObject o, String k, double def, double min, double max) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return def;
		}
		double v = e.getAsDouble();
		return v >= min && v <= max ? v : def;
	}
}
