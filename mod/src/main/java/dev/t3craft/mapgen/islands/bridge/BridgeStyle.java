package dev.t3craft.mapgen.islands.bridge;

import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.islands.Island;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The look of a bridge, picked per island pair from its length, its rise and a stable hash of the pair
 * ({@link #pick}). Vanilla blocks only. Every style has a three-wide walkable deck (full blocks, bottom slabs
 * and stairs, see {@link DeckProfile}) with a curb and a continuous rail on both sides; what differs is the
 * material and what holds it up:
 *
 * <ul>
 *   <li>{@link #PLANK}: short wooden span, spruce deck, fence rail, log legs and a cross beam every four steps,
 *       a lantern on the rail above each pair of legs.</li>
 *   <li>{@link #ARCH}: stone brick arch, the underside filled down to a soffit curve that is deepest at the
 *       islands and shallowest in the middle, wall rail with lanterns, one lantern hanging under the crown.</li>
 *   <li>{@link #SUSPENSION}: dark oak deck that sags slightly in the middle (a light catenary), log towers with
 *       lanterns at both ends, a chain cable on each side dipping from the towers to the rail with vertical
 *       chain hangers, and chains with lanterns hanging under the deck.</li>
 * </ul>
 */
public enum BridgeStyle {
	PLANK("plank", Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_FENCE),
	ARCH("stone arch", Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_WALL),
	SUSPENSION("suspension", Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_FENCE);

	/** Decks this long or shorter are always a plank span. */
	public static final int SHORT = 16;
	/** Decks this long or longer (and nearly level) are usually a suspension bridge. */
	public static final int LONG = 36;
	/** Most a suspension deck sags in the middle, in half blocks. */
	public static final int MAX_SAG_HALVES = 4;

	private final String label;
	private final Block deck;
	private final Block slab;
	private final Block stairs;
	private final Block curb;
	private final Block rail;

	BridgeStyle(String label, Block deck, Block slab, Block stairs, Block curb, Block rail) {
		this.label = label;
		this.deck = deck;
		this.slab = slab;
		this.stairs = stairs;
		this.curb = curb;
		this.rail = rail;
	}

	public String label() {
		return label;
	}

	public Block deck() {
		return deck;
	}

	public Block slab() {
		return slab;
	}

	public Block stairs() {
		return stairs;
	}

	public Block curb() {
		return curb;
	}

	public Block rail() {
		return rail;
	}

	/** How far the middle of the deck sags below the straight line, in half blocks: only the suspension bridge sags. */
	public int sagHalves(int length) {
		return this == SUSPENSION ? Math.max(0, Math.min(MAX_SAG_HALVES, length / 10)) : 0;
	}

	/**
	 * The style of the bridge between {@code a} and {@code b} (ids ordered: {@code a} first). Short decks are
	 * planks; decks too steep for half-block steps (they need stairs) are an arch or planks; long, nearly level
	 * decks are mostly suspension bridges; the rest is a stable mix. Pure: the same pair always gets the same
	 * style.
	 *
	 * @param length deck length in steps
	 * @param rise   height of {@code b}'s edge above {@code a}'s, in blocks
	 * @param steep  the deck needs stairs ({@link DeckProfile#needsStairs})
	 */
	public static BridgeStyle pick(Island a, Island b, int length, int rise, boolean steep) {
		int roll = Noise.hash(a.seed(), a.gx() * 1031 + b.gx(), a.gz() * 1031 + b.gz(), 40, 100);
		int r = Math.abs(rise);
		if (length <= SHORT) {
			return PLANK;
		}
		if (steep) {
			return roll < 60 ? ARCH : PLANK;
		}
		if (length >= LONG && r <= 4) {
			return roll < 65 ? SUSPENSION : ARCH;
		}
		if (roll < 45) {
			return ARCH;
		}
		if (roll < 80 || r > 4) {
			return PLANK;
		}
		return SUSPENSION;
	}
}
