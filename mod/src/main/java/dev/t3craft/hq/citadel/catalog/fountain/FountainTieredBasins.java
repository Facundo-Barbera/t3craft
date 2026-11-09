package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.FALL;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.disk;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.pillar;
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
 * A monumental three-tier fountain: a wide deepslate basin, a polished-stone bowl above it and a small
 * cup on top, each one lighter than the one below, with the water spilling over four lips per tier and
 * falling into the next basin.
 *
 * <p>Concept: Trevi / Villa d'Este in the citadel palette. Dark pooled base (deepslate, cracked and
 * mossy), polished andesite and diorite bowls, quartz caps and a gold-studded top cup. What makes it
 * memorable is the silhouette (a wide skirt, a flared mid bowl, a slim cup and a jet) and the cascades:
 * the bowls are cups, not slabs, because the underside of every bowl is a ring of upside-down stairs
 * that narrows towards the stem.
 *
 * <pre>
 *  section through the axis (n = 9, h = 7)          y6  ▓ jet       cap slabs
 *                                                   y5    ▒▒▒      top cup, 4 lips
 *      jet          ·                               y4  ▓▓ ▒ ▓▓   mid bowl cap + stem
 *     cup        ▒▒▒▒▒                              y3  ▓ ≈≈≈ ▓   mid bowl water, 4 lips
 *   mid bowl  ▓▓▓▓≈≈≈▓▓▓▓      ← falls              y2  ▓▓▓▓▓▓▓   mid floor / cornice
 *   basin ▓▓▓▓▓▓▓≈≈≈≈≈▓▓▓▓▓▓▓                       y1  rim ≈ water ≈ rim
 *                                                   y0  deepslate floor + dark rim base
 * </pre>
 *
 * Size: 7–11 × 7–11 × 7+ (a round figure centred in the plot; at 7 wide the top cup is a single urn,
 * from 9 wide a real cup with its own lips; extra height lifts the top cup, so its cascade falls further).
 */
public final class FountainTieredBasins implements Variant {
	private static final BlockState GOLD = b(Blocks.GOLD_BLOCK);

	@Override
	public String id() {
		return "fountain_tiered_basins";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
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
		int n = Math.min(Math.min(p.w(), p.d()), 11);
		if (n % 2 == 0) {
			n--;
		}
		int big = (n - 1) / 2; // radius of the lower basin
		int mid = big - 1; // rim radius of the mid bowl
		int cup = mid - 2; // rim radius of the top cup (0: a single urn)
		int extra = Math.max(0, p.h() - 7); // extra height lifts the bowls, so the cascades fall further
		int lift2 = Math.min(2, (extra + 1) / 2);
		int lift3 = Math.min(2, extra - lift2);

		basin(k, big);
		corners(k, big);
		plinth(k);
		midBowl(k, mid, lift2);
		int y0 = 4 + lift2; // first layer above the mid bowl's cap
		if (cup >= 1) {
			topCup(k, cup, y0, y0 + lift3);
		} else {
			urn(k, y0, y0 + lift3);
		}
	}

	// ------------------------------------------------------------------ lower basin

	/** The wide pool: dark floor, a two-course rim (deepslate under stone brick) and a light cap with four pilasters. */
	private static void basin(FountainKit k, int r) {
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (!disk(dx, dz, r)) {
					continue;
				}
				boolean axis = dx == 0 || dz == 0;
				if (!disk(dx, dz, r - 1)) {
					// [palette] rim: dark base course, stone-brick body, light polished cap
					k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS),
							b(Blocks.CRACKED_DEEPSLATE_BRICKS)));
					k.set(dx, 1, dz, axis ? b(Blocks.CHISELED_STONE_BRICKS)
							: k.pick(dx, 1, dz, b(Blocks.CALCITE), b(Blocks.STONE_BRICKS), b(Blocks.CALCITE), b(Blocks.MOSSY_STONE_BRICKS),
									b(Blocks.POLISHED_ANDESITE)));
					// [depth] the rim stays low (top at y2) so the cascades above it show; pilasters on the diagonals
					// rise one block with a chiselled cap and a lantern
					boolean pilaster = Math.abs(dx) == Math.abs(dz) && Math.abs(dx) >= r - 1 && r >= 3;
					if (pilaster) {
						k.set(dx, 2, dz, b(Blocks.CHISELED_STONE_BRICKS));
						k.set(dx, 3, dz, b(Blocks.LANTERN));
					}
				} else {
					k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE),
							b(Blocks.CRACKED_DEEPSLATE_TILES)));
					k.set(dx, 1, dz, WATER);
				}
			}
		}
	}

	/** [transition] lamp posts and planters in the cells the round basin leaves free in the corners of the box. */
	private static void corners(FountainKit k, int r) {
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (disk(dx, dz, r)) {
					continue;
				}
				if (Math.abs(dx) == r && Math.abs(dz) == r) {
					k.set(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE));
					k.set(dx, 1, dz, b(Blocks.DEEPSLATE_BRICK_WALL));
					k.set(dx, 2, dz, b(Blocks.LANTERN));
				} else {
					k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.STONE_BRICKS)));
					k.set(dx, 1, dz, k.pick(dx, 1, dz, b(Blocks.POTTED_FLOWERING_AZALEA), b(Blocks.POTTED_CORNFLOWER),
							b(Blocks.POTTED_PINK_TULIP), b(Blocks.POTTED_FLOWERING_AZALEA)));
				}
			}
		}
	}

	/** A stepped plinth in the middle of the pool (stairs climbing to a centre block, planted corners cut as slabs). */
	private static void plinth(FountainKit k) {
		k.set(0, 1, 0, b(Blocks.POLISHED_DEEPSLATE));
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				if (dx == 0 || dz == 0) {
					k.set(dx, 1, dz, stair(Blocks.DEEPSLATE_BRICK_STAIRS, toward(dx, dz)));
				} else {
					k.set(dx, 1, dz, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		}
	}

	// ------------------------------------------------------------------ mid bowl

	/**
	 * The mid bowl: floor at y2, a ring of upside-down stairs under the rim (the cup profile), the rim at y3
	 * with its water, a quartz cap at y4. Four lips on the compass points let the water fall into the basin.
	 */
	private static void midBowl(FountainKit k, int r, int lift) {
		int f = 2 + lift; // y of the floor
		for (int y = 2; y < f; y++) {
			k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR)); // stem under a lifted bowl
		}
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (!disk(dx, dz, r)) {
					continue;
				}
				boolean ring = !disk(dx, dz, r - 1);
				boolean lip = ring && (dx == 0 || dz == 0);
				if (!ring) {
					k.set(dx, f, dz, k.pick(dx, 2, dz, b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_DIORITE)));
					if (dx != 0 || dz != 0) {
						k.set(dx, f + 1, dz, WATER);
					}
					continue;
				}
				if (lip) {
					for (int y = 2; y <= f; y++) {
						k.set(dx, y, dz, FALL); // the cascade drops out of the lip, into the basin
					}
					k.set(dx, f + 1, dz, WATER);
					continue;
				}
				// [depth] the underside curves in towards the stem: upside-down stairs facing the centre
				k.set(dx, f, dz, stairTop(Blocks.POLISHED_ANDESITE_STAIRS, toward(dx, dz)));
				k.set(dx, f + 1, dz, k.pick(dx, 3, dz, b(Blocks.POLISHED_DIORITE), b(Blocks.POLISHED_DIORITE), b(Blocks.POLISHED_ANDESITE)));
				k.set(dx, f + 2, dz, slab(Blocks.SMOOTH_QUARTZ_SLAB));
			}
		}
		// pedestal in the middle of the bowl, carrying the top cup
		k.set(0, f + 1, 0, b(Blocks.CHISELED_QUARTZ_BLOCK));
	}

	// ------------------------------------------------------------------ top cup

	/** The top cup: a small flared bowl on a quartz stem, gold studs on the diagonals, four lips and a jet. */
	private static void topCup(FountainKit k, int r, int y0, int a) {
		for (int y = y0; y < a; y++) {
			k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR));
		}
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (!disk(dx, dz, r)) {
					continue;
				}
				boolean ring = !disk(dx, dz, r - 1);
				boolean lip = ring && (dx == 0 || dz == 0);
				if (!ring) {
					k.set(dx, a, dz, b(Blocks.POLISHED_DIORITE));
					k.set(dx, a + 1, dz, WATER);
					if (dx == 0 && dz == 0) {
						k.set(0, a + 2, 0, b(Blocks.END_ROD)); // a pale finial: no free source to spill
					}
					continue;
				}
				if (lip) {
					for (int y = y0; y <= a; y++) {
						k.set(dx, y, dz, FALL);
					}
					k.set(dx, a + 1, dz, WATER);
					continue;
				}
				k.set(dx, a, dz, stairTop(Blocks.POLISHED_DIORITE_STAIRS, toward(dx, dz)));
				boolean stud = Math.abs(dx) == Math.abs(dz);
				k.set(dx, a + 1, dz, stud ? GOLD : b(Blocks.POLISHED_DIORITE));
				k.set(dx, a + 2, dz, slab(Blocks.SMOOTH_QUARTZ_SLAB));
			}
		}
	}

	/** At the smallest size the top is an urn on the stem: a quartz vessel brimming over with a jet. */
	private static void urn(FountainKit k, int y0, int a) {
		for (int y = y0; y <= a; y++) {
			k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR));
		}
		k.set(0, a + 1, 0, b(Blocks.CHISELED_QUARTZ_BLOCK));
		k.set(0, a + 2, 0, b(Blocks.END_ROD)); // finial, not a free source
		for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			k.set(d.getStepX(), a, d.getStepZ(), stairTop(Blocks.POLISHED_DIORITE_STAIRS, d.getOpposite()));
			k.set(d.getStepX(), a + 1, d.getStepZ(), FALL);
		}
	}
}
