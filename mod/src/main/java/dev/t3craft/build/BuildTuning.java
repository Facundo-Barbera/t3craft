package dev.t3craft.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Throughput settings shared by every builder (site, HQ, demo, debug), from the optional {@code "build"}
 * section of {@code config/t3craft.json}. Read-only and additive: absent file/section/key → default;
 * invalid value → default plus a warning naming only the key. feat/config's store keeps unknown
 * sections when it saves, so this section survives without this class ever writing.
 * <pre>{@code
 * "build": {
 *   "maxBlocksPerTick": 48,    // global budget per server tick, shared by all running jobs
 *   "maxConcurrentJobs": 3,    // jobs that place at the same time; the rest wait their turn
 *   "frontsPerJob": 4,         // parallel fronts per job (capped by the crew size)
 *   "maxEffectsPerTick": 8,    // placement sounds + particle bursts per tick, all jobs together
 *   "autoThrottle": true,      // lower the budget while the server tick is slow
 *   "targetMspt": 40           // ...above this many ms per tick (smoothed)
 * }
 * }</pre>
 * Defaults and their reasons: docs/feat-parallel-build.md.
 */
public record BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle,
		double targetMspt) {
	public static final String SECTION = "build";
	public static final BuildTuning DEFAULTS = new BuildTuning(48, 3, 4, 8, true, 40.0);

	public BuildTuning {
		maxBlocksPerTick = clamp(maxBlocksPerTick, 1, 4096);
		maxConcurrentJobs = clamp(maxConcurrentJobs, 1, 64);
		frontsPerJob = clamp(frontsPerJob, 1, 16);
		maxEffectsPerTick = clamp(maxEffectsPerTick, 0, 256);
		if (!(targetMspt >= 10 && targetMspt <= 1000)) {
			targetMspt = 40.0;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Fronts for a job built by {@code crew} builders: one front per builder, at most {@link #frontsPerJob}. */
	public int frontsFor(int crew) {
		return Math.max(1, Math.min(frontsPerJob, crew));
	}

	public record Result(BuildTuning tuning, List<String> warnings) {
	}

	public static Result load(Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return new Result(DEFAULTS, List.of());
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			return parse(root.isJsonObject() ? root.getAsJsonObject() : null);
		} catch (IOException | RuntimeException e) {
			return new Result(DEFAULTS, List.of(SECTION + " (unreadable config file)"));
		}
	}

	/** @param root the whole t3craft.json object (or null) */
	public static Result parse(JsonObject root) {
		List<String> warn = new ArrayList<>();
		if (root == null || !root.has(SECTION)) {
			return new Result(DEFAULTS, List.of());
		}
		if (!root.get(SECTION).isJsonObject()) {
			return new Result(DEFAULTS, List.of(SECTION));
		}
		JsonObject s = root.getAsJsonObject(SECTION);
		BuildTuning d = DEFAULTS;
		int blocks = (int) number(s, "maxBlocksPerTick", d.maxBlocksPerTick(), 1, 4096, true, warn);
		int jobs = (int) number(s, "maxConcurrentJobs", d.maxConcurrentJobs(), 1, 64, true, warn);
		int fronts = (int) number(s, "frontsPerJob", d.frontsPerJob(), 1, 16, true, warn);
		int effects = (int) number(s, "maxEffectsPerTick", d.maxEffectsPerTick(), 0, 256, true, warn);
		boolean auto = d.autoThrottle();
		if (s.has("autoThrottle")) {
			JsonElement e = s.get("autoThrottle");
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				auto = e.getAsBoolean();
			} else {
				warn.add(SECTION + ".autoThrottle");
			}
		}
		double mspt = number(s, "targetMspt", d.targetMspt(), 10, 1000, false, warn);
		return new Result(new BuildTuning(blocks, jobs, fronts, effects, auto, mspt), List.copyOf(warn));
	}

	private static double number(JsonObject s, String key, double def, double lo, double hi, boolean integer, List<String> warn) {
		if (!s.has(key) || s.get(key).isJsonNull()) {
			return def;
		}
		JsonElement e = s.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
			double v = e.getAsDouble();
			if (v >= lo && v <= hi && (!integer || v == Math.rint(v))) {
				return v;
			}
		}
		warn.add(SECTION + "." + key);
		return def;
	}
}
