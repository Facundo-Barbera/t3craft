package dev.t3craft.site;

/**
 * Fixed footprint and safety limits of a site. All selection, leveling and snapshot code reads
 * these; nothing outside a box that respects them is ever written.
 */
public final class SiteLimits {
	private SiteLimits() {
	}

	/** Side of the square footprint the base is built on (odd, centred on the chosen column). */
	public static final int FOOTPRINT = 15;
	/** Ring around the footprint that is blended into the surrounding terrain. */
	public static final int APRON = 2;
	/** Air cleared above the pad for the structure (the HQ design must fit in this height). */
	public static final int HEADROOM = 12;
	/** Highest minus lowest ground top inside the footprint; above this a site is refused. */
	public static final int MAX_RELIEF = 6;
	/** Highest minus lowest ground top inside the growth envelope (modules share one pad level). */
	public static final int MAX_ENVELOPE_RELIEF = 10;
	/** How far down below each column's ground top lava/structures are looked for. */
	public static final int SCAN_BELOW = MAX_RELIEF + 2;
	/** Hard cap on cells per snapshot/plan (the real box is ~19x24x19 ≈ 8.7k). */
	public static final int MAX_VOLUME = 32 * 32 * 48;
	/** Hard cap on chunk columns one snapshot box (one module) may touch. */
	public static final int MAX_CHUNKS = 9;
	/** Hard cap on chunk columns the growth reserve (envelope + apron) may touch. */
	public static final int MAX_RESERVE_CHUNKS = 36;
	/** {@code pick}: search radius (blocks) around the player, and candidate step. */
	public static final int SEARCH_RADIUS = 32;
	public static final int SEARCH_STEP = 2;
	/** A preview must be confirmed within this time. */
	public static final long PREVIEW_TTL_MS = 5 * 60_000L;
}
