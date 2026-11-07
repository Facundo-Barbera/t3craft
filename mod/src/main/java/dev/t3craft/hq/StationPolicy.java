package dev.t3craft.hq;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The single rule that decides whether a thread still holds a <b>station</b> in the world (desk,
 * computer, screens, nametag, NPC) or releases it to the archive. Every path that builds the desk
 * roster goes through {@link #split}: the NPC registry (which feeds the NPCs, the workstations and
 * the portable list), the demo HQ and, when it lands, the site HQ.
 *
 * <p>A thread <b>releases</b> its station when it is
 * <ul>
 *   <li>settled ({@link Verdict#SETTLED}),</li>
 *   <li>archived ({@link Verdict#ARCHIVED}), or</li>
 *   <li>{@code done} and inactive for more than {@link #doneIdleMs()} ({@link Verdict#DONE_IDLE}).
 *   Inactivity counts from the thread's last message or run ({@link Facts#lastActivityMs()}); when the
 *   source has no timestamps, from the moment {@link DoneClock} first saw it done.</li>
 * </ul>
 * Everything else ({@code working}, {@code needs_attention}, {@code error}, {@code idle}, a recent
 * {@code done}) keeps its station. Pure; the only state is in {@link DoneClock}.
 *
 * <p>Config ({@code config/t3craft.json}, additive, every key optional):
 * <pre>{@code "stations": { "release": true, "releaseDoneAfterMinutes": 5 }}</pre>
 *
 * @param release   false = nothing is ever released (old behaviour)
 * @param doneIdleMs how long a {@code done} thread may stay inactive before it releases its station
 */
public record StationPolicy(boolean release, long doneIdleMs) {
	public static final String KEY = "stations";
	public static final int DEFAULT_DONE_IDLE_MINUTES = 5;
	public static final StationPolicy DEFAULTS = new StationPolicy(true, DEFAULT_DONE_IDLE_MINUTES * 60_000L);
	/** {@code ThreadState.DONE.wire}. */
	public static final String DONE = "done";

	public StationPolicy {
		doneIdleMs = Math.max(0, doneIdleMs);
	}

	/** Why a thread keeps or releases its station. */
	public enum Verdict {
		KEEP, SETTLED, ARCHIVED, DONE_IDLE;

		public boolean released() {
			return this != KEEP;
		}
	}

	/**
	 * What the policy needs to know about one thread.
	 *
	 * @param state          thread state, wire form ({@code working}, {@code done}, ...)
	 * @param lastActivityMs last message/run (epoch ms), 0 when unknown
	 */
	public record Facts(boolean settled, boolean archived, String state, long lastActivityMs) {
		public boolean done() {
			return DONE.equals(state);
		}
	}

	public Verdict judge(Facts f, long nowMs) {
		if (!release) {
			return Verdict.KEEP;
		}
		if (f.archived()) {
			return Verdict.ARCHIVED;
		}
		if (f.settled()) {
			return Verdict.SETTLED;
		}
		if (f.done() && f.lastActivityMs() > 0 && nowMs - f.lastActivityMs() > doneIdleMs) {
			return Verdict.DONE_IDLE;
		}
		return Verdict.KEEP;
	}

	public boolean holdsStation(Facts f, long nowMs) {
		return !judge(f, nowMs).released();
	}

	/** The roster split in two, both in input order. */
	public record Split<T>(List<T> stationed, List<T> released) {
		public Split {
			stationed = List.copyOf(stationed);
			released = List.copyOf(released);
		}
	}

	/**
	 * Splits a roster. {@code clock} fills in the inactivity of {@code done} threads whose facts carry
	 * no timestamp (pass {@code null} to never release those); it forgets ids that are not in
	 * {@code items}.
	 */
	public <T> Split<T> split(Collection<T> items, Function<T, String> id, Function<T, Facts> facts, @Nullable DoneClock clock, long nowMs) {
		List<T> kept = new ArrayList<>();
		List<T> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (T t : items) {
			String key = id.apply(t);
			seen.add(key);
			Facts f = facts.apply(t);
			if (clock != null && f.lastActivityMs() <= 0) {
				f = new Facts(f.settled(), f.archived(), f.state(), clock.doneSince(key, f.done(), nowMs));
			}
			(holdsStation(f, nowMs) ? kept : out).add(t);
		}
		if (clock != null) {
			clock.retain(seen);
		}
		return new Split<>(kept, out);
	}

	/**
	 * Since when each thread has been {@code done}, for sources without timestamps (the bridge): the
	 * first time it was seen done. A thread that leaves {@code done} (or the roster) starts over.
	 */
	public static final class DoneClock {
		private final Map<String, Long> since = new HashMap<>();

		/** Epoch ms since {@code id} has been done, or 0 when it is not done. */
		public long doneSince(String id, boolean done, long nowMs) {
			if (!done) {
				since.remove(id);
				return 0;
			}
			return since.computeIfAbsent(id, k -> nowMs);
		}

		public void retain(Set<String> ids) {
			since.keySet().retainAll(ids);
		}

		public void clear() {
			since.clear();
		}
	}

	// ---- config -------------------------------------------------------------------------------

	/** Reads the {@code stations} block of the whole config; missing or wrong keys → defaults. */
	public static StationPolicy parse(@Nullable JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		boolean release = DEFAULTS.release;
		JsonElement r = o.get("release");
		if (r != null && r.isJsonPrimitive() && r.getAsJsonPrimitive().isBoolean()) {
			release = r.getAsBoolean();
		}
		long idle = DEFAULTS.doneIdleMs;
		JsonElement m = o.get("releaseDoneAfterMinutes");
		if (m != null && m.isJsonPrimitive() && m.getAsJsonPrimitive().isNumber()) {
			double minutes = m.getAsDouble();
			if (minutes >= 0 && minutes <= 7 * 24 * 60) {
				idle = Math.round(minutes * 60_000d);
			}
		}
		return new StationPolicy(release, idle);
	}

	/** Reads {@code file} (config/t3craft.json); missing or unreadable → defaults, never throws. */
	public static StationPolicy load(java.nio.file.Path file) {
		try {
			if (!java.nio.file.Files.exists(file)) {
				return DEFAULTS;
			}
			JsonElement e = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file));
			return parse(e.isJsonObject() ? e.getAsJsonObject() : null);
		} catch (Exception e) {
			return DEFAULTS;
		}
	}
}
