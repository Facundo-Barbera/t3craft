package dev.t3craft.client.demo;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that defines the demo world, as plain data (no Minecraft types, so it is unit
 * tested). {@link VanillaDemoWorld} maps it onto the vanilla world-creation API.
 *
 * @param folderId    save folder under {@code saves/}; fixed, so "create vs open" is a lookup
 * @param displayName level name shown in the world list
 * @param seed        fixed seed (superflat ignores it for terrain, but it keeps the world reproducible)
 * @param originX     where the HQ is built (block coordinates of the plan origin)
 * @param originY     plan origin y: the default superflat surface is at y = -61 (grass), so -60 stands on it
 * @param originZ     plan origin z
 * @param rules       game rules forced once, when the world is first set up
 * @param dayTime     time of day set once on setup (6000 = noon); time does not advance afterwards
 * @param voidWorld   feat/hq-void-world: the world is (or will be created as) a void world: the HQ
 *                    stands on a floating island, the player starts on a starter island
 */
public record DemoWorldSpec(String folderId, String displayName, long seed, int originX, int originY, int originZ,
		Map<Rule, Boolean> rules, long dayTime, boolean voidWorld) {

	/** Game rules the demo forces. Names mirror the vanilla 26.x rules they map to. */
	public enum Rule {
		ADVANCE_TIME,
		ADVANCE_WEATHER,
		SPAWN_MOBS,
		SPAWN_MONSTERS,
		SPAWN_PATROLS,
		SPAWN_PHANTOMS,
		SPAWN_WANDERING_TRADERS
	}

	public static final String DEFAULT_FOLDER = "t3craft-hq";
	public static final String DEFAULT_NAME = "T3Craft HQ";
	/** "T3CRAFT" in ASCII, as a long: arbitrary but fixed. */
	public static final long DEFAULT_SEED = 0x543343524146544CL;
	public static final int DEFAULT_ORIGIN_X = 0;
	public static final int DEFAULT_ORIGIN_Y = -60;
	public static final int DEFAULT_ORIGIN_Z = 8;
	/** feat/hq-void-world: default origin height in a void world (the island's grass at 63, the void below). */
	public static final int VOID_ORIGIN_Y = 64;
	public static final long NOON = 6000L;

	/**
	 * Always creative, cheats on, peaceful, superflat (not configurable: that is what "demo" means).
	 * feat/hq-void-world: new worlds use the superflat "void" layers (one air layer, biome the_void, no
	 * structures, no decoration) unless {@code demo.world} is {@code "flat"}; see {@link #voidWorld}.
	 */
	public static final boolean CREATIVE = true;
	public static final boolean CHEATS = true;
	public static final boolean PEACEFUL = true;
	public static final boolean SUPERFLAT = true;
	public static final boolean STRUCTURES = false;

	public DemoWorldSpec(String folderId, String displayName, long seed, int originX, int originY, int originZ,
			Map<Rule, Boolean> rules, long dayTime) {
		this(folderId, displayName, seed, originX, originY, originZ, rules, dayTime, false);
	}

	public DemoWorldSpec {
		Objects.requireNonNull(folderId, "folderId");
		Objects.requireNonNull(displayName, "displayName");
		rules = Map.copyOf(rules);
	}

	public static DemoWorldSpec defaults() {
		return new DemoWorldSpec(DEFAULT_FOLDER, DEFAULT_NAME, DEFAULT_SEED, DEFAULT_ORIGIN_X, DEFAULT_ORIGIN_Y, DEFAULT_ORIGIN_Z,
				defaultRules(), NOON);
	}

	/** Fixed day, clear weather, nothing spawns. */
	public static Map<Rule, Boolean> defaultRules() {
		Map<Rule, Boolean> m = new EnumMap<>(Rule.class);
		for (Rule r : Rule.values()) {
			m.put(r, false);
		}
		return m;
	}

	public DemoWorldSpec withFolder(String folder, String name) {
		return new DemoWorldSpec(folder, name, seed, originX, originY, originZ, rules, dayTime, voidWorld);
	}

	public DemoWorldSpec withOrigin(int x, int y, int z) {
		return new DemoWorldSpec(folderId, displayName, seed, x, y, z, rules, dayTime, voidWorld);
	}

	public DemoWorldSpec withVoid(boolean v) {
		return new DemoWorldSpec(folderId, displayName, seed, originX, originY, originZ, rules, dayTime, v);
	}

	/**
	 * Whether {@code name} is acceptable as a save folder id: non-empty, no path separators or
	 * dots-only names, only characters vanilla keeps as-is. Invalid ids fall back to the default.
	 */
	public static boolean validFolderId(String name) {
		return name != null && !name.isBlank() && name.length() <= 64 && name.matches("[A-Za-z0-9_\\-]+");
	}
}
