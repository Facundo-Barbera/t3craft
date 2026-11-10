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
 * section of {@code config/t3craft.json} (and, feat/build-near-player, the top-level
 * {@code "buildNearPlayer"} object). Read-only and additive: absent file/section/key → default;
 * invalid value → default plus a warning naming only the key. feat/config's store keeps unknown
 * sections when it saves, so this section survives without this class ever writing.
 * <pre>{@code
 * "build": {
 *   "maxBlocksPerTick": 96,    // global budget per server tick, shared by all running crews (still capped by 8 ms/tick)
 *   "maxConcurrentJobs": 3,    // jobs that place at the same time; the rest wait their turn
 *   "frontsPerJob": 8,         // parallel fronts per job (capped by the crew size)
 *   "maxEffectsPerTick": 8,    // placement sounds + particle bursts per tick, all jobs together
 *   "autoThrottle": true,      // lower the budget while the server tick is slow
 *   "targetMspt": 40,          // ...above this many ms per tick (smoothed)
 *   "fastFoundation": false,   // fix/build-radial-tune: off by default; true = the old fast stream (whole base, no ring limit)
 *   "foundationBlocksPerTick": 192, // most foundation blocks (ground + fill) per server tick (all jobs together)
 *   "foundationMsPerTick": 10, // ...and most wall-clock ms per server tick for them
 *   "blocksPerTick": 6,        // pace of one builder (floor), blocks per tick
 *   "crew": 8,                 // most builders per crew; crews grow with the visible part of the plan
 *   "order": "radial",         // feat/build-radial: "radial" (around the player, ring by ring) or "parts" (part by part);
 *                              // feat/builder-sites: "sites" (building sites with a crew each, docs/features/builder-sites.md)
 *   "radialRing": 5,           // width of a ring, blocks
 *   // fix/build-radial-tune (radial jobs):
 *   "groundBlocksPerTick": 32, // walkable surface (+ one support layer) per tick, ring by ring
 *   "fillBlocksPerTick": 128,  // hidden terrain below the surface per tick (low priority, rings behind)
 *   "foundationRingsAhead": 1, // the surface goes at most this many rings ahead of the ring being finished
 *   "builderRingsAhead": 0,    // builders work at most this many rings past the work radius (0: inside it)
 *   "fillRingsBehind": 2,      // the hidden fill trails the work radius by this many rings
 *   "climbBlocksPerTick": 10,  // pace of a builder raising a tall wall span (floor: blocksPerTick)
 *   // fix/build-walkable (radial jobs): a ground corridor ahead of the walking player, absolute priority
 *   "corridorLength": 20,      // blocks ahead of the player (while it moves); 0 = only the cells under its feet
 *   "corridorWidth": 7,        // width of the corridor, blocks
 *   "corridorBlocksPerTick": 64, // ground entries per tick for the corridor (and the start-up primer), before any other stream
 *   "corridorMsPerTick": 3,    // ...and most wall-clock ms per tick for them (not lowered by the MSPT / FPS throttle)
 *   // fix/builder-round-radius (radial jobs): a round work radius around the player
 *   "radiusMin": 5,            // blocks: the radius a build starts at, and falls back to when the player moves
 *   "radiusStep": 4,           // blocks: how far past the nearest unfinished column the radius grows once its disc is done
 *   "radialMinBuilders": 3,    // builders that always take work
 *   "entriesPerBuilder": 1200, // one more builder per this many entries of builder work within 24 blocks of the player
 *   "radialCrewMax": 12,       // crew of a big radial plan (60 000 entries or more): the most builders that can take work
 *   // feat/builder-sites (order "sites"):
 *   "siteCrewMin": 2,          // builders an open site gets at least
 *   "siteCrewMax": 6,          // ...and at most (one more per entriesPerBuilder entries it has left)
 *   "siteStorey": 5,           // height of a storey, blocks, where the site has no real floors
 *   "sitesOpen": 2             // sites open at once: the focus and the one being closed (one more when builders are to spare); 1 = one at a time
 * },
 * "buildNearPlayer": { "enabled": true, "radius": 48 }
 * }</pre>
 * Defaults and their reasons: docs/features/parallel-build.md, docs/features/build-near-player.md, docs/features/build-radial.md
 * (feat/build-radial lowered foundationBlocksPerTick 4096 → 192 and blocksPerTick 12 → 6: a visible pace;
 * fix/build-radial-tune turned fastFoundation off and added the ground / fill / ring / climb settings;
 * fix/build-walkable added the corridor settings).
 */
public record BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle,
		double targetMspt, boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew,
		boolean nearPlayer, int nearPlayerRadius, boolean radial, int radialRing, int groundBlocksPerTick, int fillBlocksPerTick,
		int foundationRingsAhead, int builderRingsAhead, int fillRingsBehind, double climbBlocksPerTick, int corridorLength, int corridorWidth,
		int corridorBlocksPerTick, double corridorMsPerTick, int radiusMin, int radiusStep, int radialMinBuilders, int entriesPerBuilder,
		int radialCrewMax, boolean sites, int siteCrewMin, int siteCrewMax, int siteStorey, int sitesOpen) {
	public static final String SECTION = "build";
	/** feat/build-near-player: top-level object {@code {enabled, radius}}. */
	public static final String NEAR_SECTION = "buildNearPlayer";
	/** Builders of a crew before it grows with the plan. */
	public static final int BASE_CREW = 4;
	/** Visible (non-foundation) entries per extra builder. */
	public static final int ENTRIES_PER_BUILDER = 3000;
	/** feat/build-radial: {@code build.order} values. */
	public static final String ORDER_RADIAL = "radial";
	public static final String ORDER_PARTS = "parts";
	/**
	 * feat/builder-sites: {@code build.order: "sites"}: like {@link #ORDER_RADIAL} (one merged plan, one crew, the player
	 * as the centre; {@link #radial} is true too) but scheduled by {@link SiteCursor}: building sites, a crew per site,
	 * storeys, a front anchored where the player arrived.
	 */
	public static final String ORDER_SITES = "sites";
	/** fix/build-radial-tune: fastFoundation off; ground 32, fill 128, rings ahead 1 / 1, fill 2 rings behind, climb 10. */
	/**
	 * fix/builder-round-radius: round work radius 5 blocks to start with, 4 blocks more each time the disc is done; builders
	 * stay inside it ({@code builderRingsAhead} 1 → 0); 3 builders at least, one more per 1200 entries of builder work
	 * within 24 blocks of the player, up to the crew (12 for a big radial plan).
	 */
	public static final BuildTuning DEFAULTS = new BuildTuning(96, 3, 8, 8, true, 40.0, false, 192, 10.0, 6.0, 8, true, 48, true, 5, 32, 128, 1, 0, 2,
			10.0, 20, 7, 64, 3.0, 5, 4, 3, 1200, 12, false, 2, 6, 5, 2);
	/** A radial plan with at least this many entries gets the bigger crew ({@link #radialCrewMax}) so the builders can grow with the work around the player. */
	public static final int RADIAL_BIG_PLAN = 60_000;

	public BuildTuning {
		maxBlocksPerTick = clamp(maxBlocksPerTick, 1, 4096);
		maxConcurrentJobs = clamp(maxConcurrentJobs, 1, 64);
		frontsPerJob = clamp(frontsPerJob, 1, 16);
		maxEffectsPerTick = clamp(maxEffectsPerTick, 0, 256);
		if (!(targetMspt >= 10 && targetMspt <= 1000)) {
			targetMspt = 40.0;
		}
		foundationBlocksPerTick = clamp(foundationBlocksPerTick, 64, 65536);
		if (!(foundationMsPerTick >= 1 && foundationMsPerTick <= 40)) {
			foundationMsPerTick = 10.0;
		}
		if (!(blocksPerTick >= 0.5 && blocksPerTick <= 64)) {
			blocksPerTick = 6.0;
		}
		crew = clamp(crew, 1, 16);
		nearPlayerRadius = clamp(nearPlayerRadius, 8, 512);
		radialRing = clamp(radialRing, 1, 64);
		groundBlocksPerTick = clamp(groundBlocksPerTick, 1, 4096);
		fillBlocksPerTick = clamp(fillBlocksPerTick, 0, 65536);
		foundationRingsAhead = clamp(foundationRingsAhead, 0, 16);
		builderRingsAhead = clamp(builderRingsAhead, 0, 16);
		fillRingsBehind = clamp(fillRingsBehind, 0, 64);
		if (!(climbBlocksPerTick >= 0.5 && climbBlocksPerTick <= 64)) {
			climbBlocksPerTick = 10.0;
		}
		corridorLength = clamp(corridorLength, 0, 64);
		corridorWidth = clamp(corridorWidth, 1, 15);
		corridorBlocksPerTick = clamp(corridorBlocksPerTick, 0, 4096);
		if (!(corridorMsPerTick >= 0.1 && corridorMsPerTick <= 20)) {
			corridorMsPerTick = 3.0;
		}
		radiusMin = clamp(radiusMin, 1, 64);
		radiusStep = clamp(radiusStep, 1, 32);
		radialMinBuilders = clamp(radialMinBuilders, 1, 16);
		entriesPerBuilder = clamp(entriesPerBuilder, 50, 1_000_000);
		radialCrewMax = clamp(radialCrewMax, 1, 16);
		siteCrewMin = clamp(siteCrewMin, 1, 16);
		siteCrewMax = clamp(siteCrewMax, siteCrewMin, 16);
		siteStorey = clamp(siteStorey, 3, 16);
		sitesOpen = clamp(sitesOpen, 1, 4);
	}

	/** fix/builder-round-radius constructor: the feat/builder-sites settings at their defaults (order not "sites"). */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt,
			boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew, boolean nearPlayer,
			int nearPlayerRadius, boolean radial, int radialRing, int groundBlocksPerTick, int fillBlocksPerTick, int foundationRingsAhead,
			int builderRingsAhead, int fillRingsBehind, double climbBlocksPerTick, int corridorLength, int corridorWidth, int corridorBlocksPerTick,
			double corridorMsPerTick, int radiusMin, int radiusStep, int radialMinBuilders, int entriesPerBuilder, int radialCrewMax) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, fastFoundation, foundationBlocksPerTick,
				foundationMsPerTick, blocksPerTick, crew, nearPlayer, nearPlayerRadius, radial, radialRing, groundBlocksPerTick, fillBlocksPerTick,
				foundationRingsAhead, builderRingsAhead, fillRingsBehind, climbBlocksPerTick, corridorLength, corridorWidth, corridorBlocksPerTick,
				corridorMsPerTick, radiusMin, radiusStep, radialMinBuilders, entriesPerBuilder, radialCrewMax, false, 2, 6, 5, 2);
	}

	/** fix/build-walkable constructor: the fix/builder-round-radius settings at their defaults. */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt,
			boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew, boolean nearPlayer,
			int nearPlayerRadius, boolean radial, int radialRing, int groundBlocksPerTick, int fillBlocksPerTick, int foundationRingsAhead,
			int builderRingsAhead, int fillRingsBehind, double climbBlocksPerTick, int corridorLength, int corridorWidth, int corridorBlocksPerTick,
			double corridorMsPerTick) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, fastFoundation, foundationBlocksPerTick,
				foundationMsPerTick, blocksPerTick, crew, nearPlayer, nearPlayerRadius, radial, radialRing, groundBlocksPerTick, fillBlocksPerTick,
				foundationRingsAhead, builderRingsAhead, fillRingsBehind, climbBlocksPerTick, corridorLength, corridorWidth, corridorBlocksPerTick,
				corridorMsPerTick, 5, 4, 3, 1200, 12);
	}

	/**
	 * fix/builder-round-radius: the crew a free radial plan of {@code planSize} entries gets: the usual {@code crew}, or
	 * {@link #radialCrewMax} when the plan is big ({@link #RADIAL_BIG_PLAN}), because the builders in use grow with the
	 * work around the player (an HQ's centre) and the usual 8 is not enough there.
	 */
	public int radialCrewFor(int crew, int planSize) {
		return planSize >= RADIAL_BIG_PLAN ? Math.max(crew, radialCrewMax) : crew;
	}

	/** Fronts (builders) of a free radial job with a crew of {@code crew}: like {@link #frontsFor} but up to {@link #radialCrewMax}. */
	public int radialFrontsFor(int crew) {
		return Math.max(1, Math.min(Math.max(frontsPerJob, radialCrewMax), crew));
	}

	/** fix/build-radial-tune constructor: the fix/build-walkable corridor settings at their defaults. */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt,
			boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew, boolean nearPlayer,
			int nearPlayerRadius, boolean radial, int radialRing, int groundBlocksPerTick, int fillBlocksPerTick, int foundationRingsAhead,
			int builderRingsAhead, int fillRingsBehind, double climbBlocksPerTick) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, fastFoundation, foundationBlocksPerTick,
				foundationMsPerTick, blocksPerTick, crew, nearPlayer, nearPlayerRadius, radial, radialRing, groundBlocksPerTick, fillBlocksPerTick,
				foundationRingsAhead, builderRingsAhead, fillRingsBehind, climbBlocksPerTick, 20, 7, 64, 3.0);
	}

	/** feat/build-radial constructor: the fix/build-radial-tune settings at their defaults. */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt,
			boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew, boolean nearPlayer,
			int nearPlayerRadius, boolean radial, int radialRing) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, fastFoundation, foundationBlocksPerTick,
				foundationMsPerTick, blocksPerTick, crew, nearPlayer, nearPlayerRadius, radial, radialRing, 32, 128, 1, 0, 2, 10.0);
	}

	/** feat/build-near-player constructor: the feat/build-radial settings at their defaults. */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt,
			boolean fastFoundation, int foundationBlocksPerTick, double foundationMsPerTick, double blocksPerTick, int crew, boolean nearPlayer,
			int nearPlayerRadius) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, fastFoundation, foundationBlocksPerTick,
				foundationMsPerTick, blocksPerTick, crew, nearPlayer, nearPlayerRadius, true, 5);
	}

	/** Pre-feat/build-near-player constructor: the new settings at their defaults. */
	public BuildTuning(int maxBlocksPerTick, int maxConcurrentJobs, int frontsPerJob, int maxEffectsPerTick, boolean autoThrottle, double targetMspt) {
		this(maxBlocksPerTick, maxConcurrentJobs, frontsPerJob, maxEffectsPerTick, autoThrottle, targetMspt, false, 192, 10.0, 6.0, 8, true, 48, true, 5);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** The {@code build.order} value these settings stand for: {@link #ORDER_SITES}, {@link #ORDER_RADIAL} or {@link #ORDER_PARTS}. */
	public String orderName() {
		return sites ? ORDER_SITES : radial ? ORDER_RADIAL : ORDER_PARTS;
	}

	/** Fronts for a job built by {@code crew} builders: one front per builder, at most {@link #frontsPerJob}. */
	public int frontsFor(int crew) {
		return Math.max(1, Math.min(frontsPerJob, crew));
	}

	/**
	 * feat/build-near-player: builders for a plan with {@code visibleEntries} entries the crew animates
	 * (foundation excluded): {@code asked} (at least {@link #BASE_CREW}), plus one per
	 * {@link #ENTRIES_PER_BUILDER}, at most {@link #crew}.
	 */
	public int crewFor(int asked, int visibleEntries) {
		int base = Math.max(1, Math.min(asked, crew));
		int grown = Math.max(base, BASE_CREW) + Math.max(0, visibleEntries) / ENTRIES_PER_BUILDER;
		return Math.max(base, Math.min(crew, grown));
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
		if (root == null) {
			return new Result(DEFAULTS, List.of());
		}
		BuildTuning d = DEFAULTS;
		JsonObject s = new JsonObject();
		if (root.has(SECTION)) {
			if (root.get(SECTION).isJsonObject()) {
				s = root.getAsJsonObject(SECTION);
			} else {
				warn.add(SECTION);
			}
		}
		int blocks = (int) number(SECTION, s, "maxBlocksPerTick", d.maxBlocksPerTick(), 1, 4096, true, warn);
		int jobs = (int) number(SECTION, s, "maxConcurrentJobs", d.maxConcurrentJobs(), 1, 64, true, warn);
		int fronts = (int) number(SECTION, s, "frontsPerJob", d.frontsPerJob(), 1, 16, true, warn);
		int effects = (int) number(SECTION, s, "maxEffectsPerTick", d.maxEffectsPerTick(), 0, 256, true, warn);
		boolean auto = bool(SECTION, s, "autoThrottle", d.autoThrottle(), warn);
		double mspt = number(SECTION, s, "targetMspt", d.targetMspt(), 10, 1000, false, warn);
		boolean fast = bool(SECTION, s, "fastFoundation", d.fastFoundation(), warn);
		int fBlocks = (int) number(SECTION, s, "foundationBlocksPerTick", d.foundationBlocksPerTick(), 64, 65536, true, warn);
		double fMs = number(SECTION, s, "foundationMsPerTick", d.foundationMsPerTick(), 1, 40, false, warn);
		double pace = number(SECTION, s, "blocksPerTick", d.blocksPerTick(), 0.5, 64, false, warn);
		int crew = (int) number(SECTION, s, "crew", d.crew(), 1, 16, true, warn);
		boolean radial = d.radial();
		boolean sites = d.sites();
		if (s.has("order")) {
			JsonElement e = s.get("order");
			String v = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString().trim().toLowerCase(java.util.Locale.ROOT) : "";
			if (ORDER_RADIAL.equals(v)) {
				radial = true;
				sites = false;
			} else if (ORDER_SITES.equals(v)) {
				radial = true; // the same merged plan, crew and player focus; only the cursor differs
				sites = true;
			} else if (ORDER_PARTS.equals(v)) {
				radial = false;
				sites = false;
			} else {
				warn.add(SECTION + ".order");
			}
		}
		int ring = (int) number(SECTION, s, "radialRing", d.radialRing(), 1, 64, true, warn);
		int ground = (int) number(SECTION, s, "groundBlocksPerTick", d.groundBlocksPerTick(), 1, 4096, true, warn);
		int fill = (int) number(SECTION, s, "fillBlocksPerTick", d.fillBlocksPerTick(), 0, 65536, true, warn);
		int gAhead = (int) number(SECTION, s, "foundationRingsAhead", d.foundationRingsAhead(), 0, 16, true, warn);
		int bAhead = (int) number(SECTION, s, "builderRingsAhead", d.builderRingsAhead(), 0, 16, true, warn);
		int behind = (int) number(SECTION, s, "fillRingsBehind", d.fillRingsBehind(), 0, 64, true, warn);
		double climb = number(SECTION, s, "climbBlocksPerTick", d.climbBlocksPerTick(), 0.5, 64, false, warn);
		int cLen = (int) number(SECTION, s, "corridorLength", d.corridorLength(), 0, 64, true, warn);
		int cWidth = (int) number(SECTION, s, "corridorWidth", d.corridorWidth(), 1, 15, true, warn);
		int cBlocks = (int) number(SECTION, s, "corridorBlocksPerTick", d.corridorBlocksPerTick(), 0, 4096, true, warn);
		double cMs = number(SECTION, s, "corridorMsPerTick", d.corridorMsPerTick(), 0.1, 20, false, warn);
		int rMin = (int) number(SECTION, s, "radiusMin", d.radiusMin(), 1, 64, true, warn);
		int rStep = (int) number(SECTION, s, "radiusStep", d.radiusStep(), 1, 32, true, warn);
		int rBuilders = (int) number(SECTION, s, "radialMinBuilders", d.radialMinBuilders(), 1, 16, true, warn);
		int perBuilder = (int) number(SECTION, s, "entriesPerBuilder", d.entriesPerBuilder(), 50, 1_000_000, true, warn);
		int rCrew = (int) number(SECTION, s, "radialCrewMax", d.radialCrewMax(), 1, 16, true, warn);
		int sMin = (int) number(SECTION, s, "siteCrewMin", d.siteCrewMin(), 1, 16, true, warn);
		int sMax = (int) number(SECTION, s, "siteCrewMax", d.siteCrewMax(), 1, 16, true, warn);
		int sStorey = (int) number(SECTION, s, "siteStorey", d.siteStorey(), 3, 16, true, warn);
		int sOpen = (int) number(SECTION, s, "sitesOpen", d.sitesOpen(), 1, 4, true, warn);
		boolean near = d.nearPlayer();
		int radius = d.nearPlayerRadius();
		if (root.has(NEAR_SECTION)) {
			if (root.get(NEAR_SECTION).isJsonObject()) {
				JsonObject n = root.getAsJsonObject(NEAR_SECTION);
				near = bool(NEAR_SECTION, n, "enabled", near, warn);
				radius = (int) number(NEAR_SECTION, n, "radius", radius, 8, 512, true, warn);
			} else {
				warn.add(NEAR_SECTION);
			}
		}
		return new Result(new BuildTuning(blocks, jobs, fronts, effects, auto, mspt, fast, fBlocks, fMs, pace, crew, near, radius, radial, ring, ground, fill, gAhead,
				bAhead, behind, climb, cLen, cWidth, cBlocks, cMs, rMin, rStep, rBuilders, perBuilder, rCrew, sites, sMin, sMax, sStorey, sOpen), List.copyOf(warn));
	}

	private static boolean bool(String section, JsonObject s, String key, boolean def, List<String> warn) {
		if (!s.has(key)) {
			return def;
		}
		JsonElement e = s.get(key);
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
			return e.getAsBoolean();
		}
		warn.add(section + "." + key);
		return def;
	}

	private static double number(String section, JsonObject s, String key, double def, double lo, double hi, boolean integer, List<String> warn) {
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
		warn.add(section + "." + key);
		return def;
	}
}
