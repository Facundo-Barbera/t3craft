package dev.t3craft.client.npc;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Which of the mod's own skins an agent wears (pure Java, no Minecraft classes, so it is unit
 * tested). The PNGs live in {@code assets/t3craft/textures/entity/agent/} and are drawn by
 * {@code mod/tools/AgentSkinGen.java} (nothing downloaded, no Mojang or player skins):
 *
 * <ul>
 *   <li>The <b>character</b> (face, hair, trousers, accessory) comes from a stable hash of the
 *       thread's NPC id, so a thread always looks the same and agents look different.</li>
 *   <li>The <b>shirt</b> is the environment's colour: the configured
 *       {@code environments.<id>.color} snapped to the nearest of {@link #SHIRTS}, otherwise the
 *       same stable pick as the environment badge in the chat ({@code EnvBadge}).</li>
 *   <li>A <b>helper</b> (subagent) wears the apprentice variant of its parent's skin: same
 *       character and shirt, plus an apron and a cap.</li>
 * </ul>
 *
 * {@link Pick} is computed when an NPC's data changes, never per frame; {@link AgentSkinTextures}
 * caches the texture lookup and falls back to {@link #DEFAULT_PATH}.
 */
public final class AgentSkins {
	/** A character of the skin set; {@code slim} picks the 3-px-arm player model. */
	public record Character(String id, boolean slim) {
	}

	/** Must match {@code AgentSkinGen.CHARACTERS} (same order is not required, same ids and models are). */
	public static final List<Character> CHARACTERS = List.of(
			new Character("ada", true),
			new Character("bruno", false),
			new Character("chen", false),
			new Character("dalia", true),
			new Character("emil", false),
			new Character("fatima", true),
			new Character("gus", false),
			new Character("hana", true),
			new Character("ivo", false),
			new Character("juno", true),
			new Character("kofi", false),
			new Character("lia", true));

	/** Shirt colours (0xRRGGBB), the same tones as {@code EnvBadge.PALETTE} and {@code AgentSkinGen.SHIRTS}. */
	public static final int[] SHIRTS = {0x2F7F8A, 0x8A5A2F, 0x5A6FA8, 0x7A8A2F, 0x8A2F5E, 0x2F8A55, 0x8A7A2F, 0x6A4A8A};

	public static final String DIR = "entity/agent/";
	/** Own neutral skin used when a picked texture is missing (never the purple missing texture). */
	public static final String DEFAULT_PATH = DIR + "default";

	/**
	 * A resolved choice. {@link #path()} is the texture id path in the {@code t3craft} namespace
	 * without {@code textures/} and {@code .png} (e.g. {@code entity/agent/ada_3}).
	 */
	public record Pick(String path, boolean slim) {
		/** Resource path inside the jar: {@code assets/t3craft/textures/<path>.png}. */
		public String resource() {
			return "assets/t3craft/textures/" + path + ".png";
		}
	}

	public static final Pick DEFAULT = new Pick(DEFAULT_PATH, false);

	private AgentSkins() {
	}

	/** Skin of a thread's NPC ({@code npcId} = {@code "<env>:<threadId>"}). */
	public static Pick forThread(String npcId, @Nullable Integer envColor) {
		Character c = character(npcId);
		return new Pick(DIR + c.id() + "_" + shirt(envOf(npcId), envColor), c.slim());
	}

	/** Skin of a helper: the apprentice variant of its parent's skin. */
	public static Pick forHelper(String parentNpcId, @Nullable Integer envColor) {
		Character c = character(parentNpcId);
		return new Pick(DIR + "apprentice/" + c.id() + "_" + shirt(envOf(parentNpcId), envColor), c.slim());
	}

	/** Every skin the picks above can return, plus {@link #DEFAULT} (for tests and warm-up). */
	public static List<Pick> all() {
		java.util.ArrayList<Pick> out = new java.util.ArrayList<>();
		for (Character c : CHARACTERS) {
			for (int s = 0; s < SHIRTS.length; s++) {
				out.add(new Pick(DIR + c.id() + "_" + s, c.slim()));
				out.add(new Pick(DIR + "apprentice/" + c.id() + "_" + s, c.slim()));
			}
		}
		out.add(DEFAULT);
		return out;
	}

	/**
	 * Fallback: the pick if its texture exists, else {@link #DEFAULT}, else null (the caller then
	 * uses the vanilla default skin). {@code exists} gets a {@link Pick#path()}.
	 */
	public static @Nullable Pick usable(Pick pick, java.util.function.Predicate<String> exists) {
		if (exists.test(pick.path())) {
			return pick;
		}
		return exists.test(DEFAULT.path()) ? DEFAULT : null;
	}

	static Character character(String seed) {
		return CHARACTERS.get(Math.floorMod(mix(stableHash(seed == null ? "" : seed)), CHARACTERS.size()));
	}

	/** Shirt index: the configured colour's nearest palette entry, else the environment badge's pick. */
	static int shirt(String envId, @Nullable Integer envColor) {
		if (envColor != null) {
			return nearest(envColor);
		}
		return Math.floorMod(stableHash(envId), SHIRTS.length);
	}

	static int nearest(int rgb) {
		int best = 0;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < SHIRTS.length; i++) {
			long d = dist2(rgb, SHIRTS[i]);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	private static long dist2(int a, int b) {
		long dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
		long dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
		long db = (a & 0xFF) - (b & 0xFF);
		// Weighted (green counts most) so the snap looks right to the eye.
		return 3 * dr * dr + 4 * dg * dg + 2 * db * db;
	}

	/** {@code "<env>:<threadId>"} → {@code env}; no colon → the whole id. */
	public static String envOf(String npcId) {
		if (npcId == null) {
			return "";
		}
		int i = npcId.indexOf(':');
		return i < 0 ? npcId : npcId.substring(0, i);
	}

	/** FNV-1a, same as {@code EnvBadge.stableHash}: stable across runs and JVMs. */
	static int stableHash(String s) {
		int h = 0x811C9DC5;
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= 0x01000193;
		}
		return h;
	}

	/** Final avalanche (murmur3 fmix32) so ids that differ only at the end still spread. */
	static int mix(int h) {
		h ^= h >>> 16;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return h;
	}
}
