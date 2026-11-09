package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.away;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.oct;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.slabTop;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stair;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stairTop;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.toward;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An octagonal pool with a verdigris hero in the middle and four built-in benches.
 *
 * <p>Concept: the plaza fountain of a university town: a cut-corner (octagonal) basin of calcite on a
 * dark deepslate base, a stepped plinth with four small corner pools, and a weathered-copper figure with a
 * raised staff and a gold crown. Four cherry-wood benches wrap the basin on the compass sides, each split by a
 * lamp post; the diagonals carry hedges and planters, so the plot reads as a place to sit, not an object.
 * What makes it memorable is the colour: the green copper against warm calcite and pink cherry, with gold on top.
 *
 * <pre>
 *  top view (n = 9, front at the bottom)           section through the axis
 *      . . p h p . .                                y6   ▲ crown + staff
 *     . h  b b L b b  h .       p planter           y5   ● head
 *    p b  ┌───────┐  b p        h hedge             y4  ─●─ torso and arms
 *    L    │ ≈ ▒ ≈ │    L       b bench seat          y3  ◢█◣ robe
 *    p b  └───────┘  b p        L lamp post          y2   ▒▒▒ plinth, corner pools
 *     . h  b b L b b  h .                            y1  ≈≈█≈≈ water, rim ▓
 *      . . p h p . .                                 y0  deepslate floor
 * </pre>
 *
 * Size: 9–11 × 9–11 × 7+ (the figure is centred; the rest of a bigger plot stays free for the leader's paving).
 */
public final class FountainOctagonStatue implements Variant {
	private static final int R = 4; // outer radius of the figure: a 9 × 9 octagon

	@Override
	public String id() {
		return "fountain_octagon_statue";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 7;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 11;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		FountainKit k = new FountainKit(c, p);
		basin(k);
		surroundings(k);
		plinth(k);
		statue(k);
	}

	/** The octagonal pool: dark base course, calcite body, a light cap, a lip of stairs on the compass points. */
	private static void basin(FountainKit k) {
		int r = R - 1;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (!oct(dx, dz, r)) {
					continue;
				}
				if (oct(dx, dz, r - 1)) {
					k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES),
							b(Blocks.CRACKED_DEEPSLATE_TILES)));
					k.set(dx, 1, dz, WATER);
					continue;
				}
				boolean axis = dx == 0 || dz == 0;
				k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS),
						b(Blocks.CRACKED_DEEPSLATE_BRICKS)));
				// [palette] body: calcite with a little stone brick, chiselled on the compass points
				k.set(dx, 1, dz, axis ? b(Blocks.CHISELED_STONE_BRICKS)
						: k.pick(dx, 1, dz, b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.STONE_BRICKS), b(Blocks.SMOOTH_QUARTZ)));
				// [depth] cap: slabs with a stair course on the corner cells, so the octagon's cuts catch the light
				boolean cut = Math.abs(dx) + Math.abs(dz) == (3 * r) / 2 && !axis && Math.abs(dx) != Math.abs(dz);
				k.set(dx, 2, dz, cut ? stair(Blocks.POLISHED_DIORITE_STAIRS, toward(dx, dz)) : slab(Blocks.POLISHED_DIORITE_SLAB));
			}
		}
	}

	/** Benches on the four compass sides with a lamp between them, hedges and planters on the diagonals. */
	private static void surroundings(FountainKit k) {
		for (int dz = -R; dz <= R; dz++) {
			for (int dx = -R; dx <= R; dx++) {
				if (!oct(dx, dz, R) || oct(dx, dz, R - 1)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				boolean flatX = ax == R; // the east/west flats
				boolean flatZ = az == R;
				if (flatX || flatZ) {
					int along = flatX ? az : ax;
					if (along == 0) {
						lamp(k, dx, dz);
					} else {
						bench(k, dx, dz, along == 2);
					}
				} else if (ax == 3 && az == 3) {
					hedge(k, dx, dz);
				} else {
					planter(k, dx, dz);
				}
			}
		}
	}

	/** [transition] a cherry bench seat: a stair with its back to the outside, an end post at the outer seats. */
	private static void bench(FountainKit k, int dx, int dz, boolean end) {
		Direction out = away(dx, dz);
		k.set(dx, 0, dz, stair(Blocks.CHERRY_STAIRS, out));
		if (end) {
			// [finish] arm-rest: a stripped-cherry post with a slab cap on the outer seat of each side
			k.set(dx, 1, dz, b(Blocks.CHERRY_FENCE));
			k.set(dx, 2, dz, slab(Blocks.CHERRY_SLAB));
		} else if (k.hash(dx, 1, dz, 3) == 0) {
			k.set(dx, 1, dz, b(Blocks.POTTED_PINK_TULIP)); // a pot on the seat now and then, for the lived-in look
		}
	}

	private static void lamp(FountainKit k, int dx, int dz) {
		k.set(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE));
		k.set(dx, 1, dz, b(Blocks.POLISHED_DEEPSLATE_WALL));
		k.set(dx, 2, dz, b(Blocks.DEEPSLATE_BRICK_WALL));
		k.set(dx, 3, dz, b(Blocks.LANTERN));
	}

	private static void hedge(FountainKit k, int dx, int dz) {
		k.set(dx, 0, dz, b(Blocks.MOSSY_STONE_BRICKS));
		k.set(dx, 1, dz, b(Blocks.FLOWERING_AZALEA_LEAVES));
		k.set(dx, 2, dz, b(Blocks.FLOWERING_AZALEA_LEAVES));
	}

	private static void planter(FountainKit k, int dx, int dz) {
		k.set(dx, 0, dz, b(Blocks.STONE_BRICKS));
		k.set(dx, 1, dz, k.pick(dx, 1, dz, b(Blocks.POTTED_FLOWERING_AZALEA), b(Blocks.POTTED_PINK_TULIP), b(Blocks.POTTED_CORNFLOWER)));
	}

	// ------------------------------------------------------------------ the middle

	/** A stepped plinth of dark stone with four little corner pools and a water curtain at each corner. */
	private static void plinth(FountainKit k) {
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				k.set(dx, 1, dz, k.pick(dx, 1, dz, b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.POLISHED_BLACKSTONE_BRICKS),
						b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)));
				if (dx == 0 && dz == 0) {
					k.set(0, 2, 0, b(Blocks.CHISELED_POLISHED_BLACKSTONE));
				} else if (dx == 0 || dz == 0) {
					k.set(dx, 2, dz, stair(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, toward(dx, dz)));
				} else {
					k.set(dx, 2, dz, WATER); // corner pool on the plinth, brimming over
				}
			}
		}
	}

	/** The hero: robe flaring on four stairs, torso with outstretched arms, head, crown, and a staff in the raised hand. */
	private static void statue(FountainKit k) {
		BlockState robe = b(Blocks.CUT_COPPER.waxed().oxidized());
		BlockState body = b(Blocks.CUT_COPPER.waxed().weathered());
		k.set(0, 3, 0, robe);
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			k.set(d.getStepX(), 3, d.getStepZ(), stair(Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), d.getOpposite()));
		}
		k.set(0, 4, 0, body);
		// arms: the left one down-slanting (a stair), the right one raised to the staff
		k.set(-1, 4, 0, stairTop(Blocks.CUT_COPPER_STAIRS.waxed().weathered(), Direction.EAST));
		k.set(1, 4, 0, slabTop(Blocks.CUT_COPPER_SLAB.waxed().weathered()));
		k.set(1, 5, 0, b(Blocks.LIGHTNING_ROD.waxed().oxidized()));
		k.set(1, 6, 0, b(Blocks.LIGHTNING_ROD.waxed().oxidized()));
		k.set(0, 5, 0, b(Blocks.CHISELED_COPPER.waxed().oxidized()));
		k.set(0, 6, 0, b(Blocks.GOLD_BLOCK));
	}
}
