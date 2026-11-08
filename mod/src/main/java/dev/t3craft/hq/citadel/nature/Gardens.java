package dev.t3craft.hq.citadel.nature;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.nature.Flora.hash;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.nature.Trees.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The planting of the citadel's core: the terraces between the ramps and the patio (its eight cherry
 * trees, its lawn and its pool). Called by {@code CitadelGrounds} column by column; none of them writes
 * outside the column it is given (the trees place their own column, see {@link Trees#placeColumn}).
 *
 * <ul>
 * <li>{@link #terrace}: a stone step's vegetation. Each step (two radial blocks wide) is one thing:
 *     odd steps have a low hedge against the riser and a bed of flowers in front; even steps are a
 *     meadow of patches (macizos) of one flower palette, tall flowers, tall grass, ferns or wildflowers.
 *     Beside the ramps only low plants grow, so the ramps stay clear.</li>
 * <li>{@link #patioCherries}: eight cherry trees on a ring of radius 19.3, round or slender, each with
 *     its roots and its fallen petals; the paving round the trunk turns into moss.</li>
 * <li>{@link #patioBeds}: macizos of flowers on the inner lawn, the planters and azaleas as before, and
 *     lily pads on the pool.</li>
 * </ul>
 */
public final class Gardens {
	private Gardens() {
	}

	/** Half width of a ramp plus a margin: nothing taller than a flower grows within this of a ramp's axis. */
	private static final double RAMP_CLEAR = 3.4 + 1.6;

	/**
	 * The vegetation of a terrace column whose stone step tops at {@code y = top} (the cell {@code top} of
	 * the column is already grass or moss) and whose half-block height is {@code halves}.
	 */
	public static void terrace(CitadelCanvas c, int x, int z, int top, int halves) {
		if (top + 1 >= 6) {
			return;
		}
		boolean nearRamp = RingCampusGeometry.radialDistance(x, z, 8) <= RAMP_CLEAR;
		int hh = hash(x, z, 401);
		int pct = hh % 100;
		int pal = top + hash(Math.floorDiv(x, 5), Math.floorDiv(z, 5), 403) % 3;
		if (nearRamp) {
			if (pct < 18) {
				Flora.plantOn(c, x, top, z, Flora.bloom(pal, hh >> 8));
			} else if (pct < 34) {
				Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
			}
			return;
		}
		boolean riser = (halves & 1) == 1;
		if (top % 2 == 1) {
			if (riser) {
				if (pct < 84) {
					c.set(x, top + 1, z, Flora.bush(hh >> 4));
				}
			} else if (pct < 78) {
				Flora.plantOn(c, x, top, z, Flora.bloom(pal, hh >> 8));
			}
			return;
		}
		int patch = hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 402 + top) % 10;
		switch (patch) {
			case 0, 1, 2 -> {
				if (pct < 66) {
					Flora.plantOn(c, x, top, z, Flora.bloom(pal + patch, hh >> 8));
				}
			}
			case 3 -> {
				if (pct < 30) {
					Flora.tallOn(c, x, top, z, Flora.bigBloom(pal));
				} else if (pct < 55) {
					Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
				}
			}
			case 4, 5 -> {
				if (pct < 42) {
					Flora.tallOn(c, x, top, z, Blocks.TALL_GRASS);
				} else if (pct < 70) {
					Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
				}
			}
			case 6 -> {
				if (pct < 50) {
					Flora.plantOn(c, x, top, z, Flora.wild(hh >> 8));
				}
			}
			case 7 -> {
				if (pct < 32) {
					Flora.plantOn(c, x, top, z, b(Blocks.FERN));
				}
			}
			default -> {
				if (pct < 14) {
					Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
				}
			}
		}
	}

	// ------------------------------------------------------------------ patio

	/** The species of the patio's tree {@code k}: round and slender cherries in turn. */
	private static Species patioSpecies(int k) {
		return (k & 1) == 0 ? Species.CHERRY_ROUND : Species.CHERRY_SLENDER;
	}

	/** One column of the eight cherry trees of the patio (ring of radius 19.3). */
	public static void patioCherries(CitadelCanvas c, int x, int z) {
		for (int k = 0; k < 8; k++) {
			double a = (k + 0.5) * Math.PI / 4;
			BlockPos t = RingCampusGeometry.polar(19.3, a, 0);
			int dx = x - t.getX();
			int dz = z - t.getZ();
			if (Trees.reaches(dx, dz)) {
				Trees.placeColumn(c, Trees.build(patioSpecies(k), hash(k, 19, 3)), t.getX(), t.getZ(), x, z);
			}
		}
	}

	/** One column of the patio's beds: planters and azaleas, flower macizos on the lawn, lily pads on the pool. */
	public static void patioBeds(CitadelCanvas c, int x, int z, double r) {
		if (r > 15.5) {
			if (r < 17.2 && c.at(x, 0, z) != null && c.at(x, 0, z).is(Blocks.WATER) && hash(x, z, 501) % 100 < 16 && Flora.free(c, x, 1, z)) {
				c.set(x, 1, z, b(Blocks.LILY_PAD));
			}
			return;
		}
		if (Math.floorMod(x * 5 + z * 3, 11) == 0 && Flora.planting(c, x, z)) {
			c.set(x, 1, z, Math.floorMod(x + z, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : c.kit.gardenPlanter());
			return;
		}
		int patch = hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 510) % 8;
		int hh = hash(x, z, 511);
		int pct = hh % 100;
		BlockState s = null;
		switch (patch) {
			case 0, 1 -> {
				if (pct < 70) {
					s = Flora.bloom(patch + 2, hh >> 8);
				}
			}
			case 2 -> {
				if (pct < 40) {
					Flora.tall(c, x, z, Flora.bigBloom(hh >> 9));
				}
			}
			case 3 -> {
				if (pct < 55) {
					s = Flora.wild(hh >> 8);
				}
			}
			case 4 -> {
				if (pct < 30) {
					Flora.tall(c, x, z, Blocks.TALL_GRASS);
				}
			}
			default -> {
				if (pct < 18) {
					s = b(Blocks.SHORT_GRASS);
				}
			}
		}
		if (s != null) {
			Flora.plant(c, x, z, s);
		}
	}
}
