package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Stone street lantern (tōrō) in stone-brick, polished andesite and moss, 3–5 high. Read bottom to top:
 * foot (kiso) → shaft (sao) → flared platform (chūdai) → light chamber (hibukuro, a glowing froglight) →
 * hat (kasa) with its corners curled up → jewel (hōju).
 *
 * <ul>
 * <li><b>Two types by seed</b>: <i>kasuga</i> (slender: stepped foot round a shaft, platform, hat with a finial)
 *     and <i>yukimi</i> (low, on four short legs, with a domed hat). Moss by seed: clean, speckled or mossy.</li>
 * <li><b>Sizes</b>: 1 × 1 / 2-wide plots get a one-block column (wall shaft, a standing lantern, a hat block);
 *     3–4 wide the full 3 × 3 tōrō; 5 × 5 and more (height 5) the tōrō on a raised gravel plinth edged with
 *     stairs, with moss and potted plants; 7+ wide a flanking pair (by seed) with the path left free between.</li>
 * <li><b>Light</b>: the froglight (level 15) or the lantern lights the street; the cells under the platform
 *     cannot be walked, so nothing under the hat is a dark corner or a trap.</li>
 * <li><b>Support</b>: every slab touches the full block at the centre of its layer; the hat corners curl up on
 *     slabs that rest on the eave. Nothing hangs, so the settle pass has nothing to fix.</li>
 * </ul>
 * Size: 1–7 × 1–7 × 3–5 (taller plots keep the extra height empty).
 */
public final class LampStreetToro implements Variant {
	private static final int[][] SIDES = {{1, 0}, {1, 2}, {0, 1}, {2, 1}};
	/** The direction from each side cell to the centre (a stair facing it climbs towards the centre). */
	private static final Direction[] IN = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
	private static final int[][] CORNERS = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

	@Override
	public String id() {
		return "lamp_street_toro";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 3;
	}

	@Override
	public int maxW() {
		return 7;
	}

	@Override
	public int maxD() {
		return 7;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cap = Math.min(p.h(), 5);
		int w = p.w();
		int d = p.d();
		boolean low = p.hash(3, 3, 3, 2) == 1; // yukimi
		if (w < 3 || d < 3) {
			column(c, p, w / 2, d / 2, cap, low);
		} else if (w >= 7 && (d < 5 || p.hash(5, 5, 5, 2) == 0)) {
			// a pair that flanks the way through; the middle of the plot stays free
			toro(c, p, 0, (d - 3) / 2, 0, cap, low, false);
			toro(c, p, w - 3, (d - 3) / 2, 0, cap, low, false);
		} else if (w >= 5 && d >= 5 && cap >= 5) {
			plinth(c, p, cap, low);
		} else {
			toro(c, p, (w - 3) / 2, (d - 3) / 2, 0, cap, low, false);
		}
	}

	// ------------------------------------------------------------------ materials

	/** 0 clean, 1 speckled, 2 mossy: one value per plot. */
	private static int mossLevel(Plot p) {
		return p.hash(9, 9, 9, 3);
	}

	private static boolean mossy(Plot p, int u, int y, int v) {
		int level = mossLevel(p);
		return level > 0 && p.hash(u, y, v, 5) < level * 2;
	}

	private static BlockState brick(Plot p, int u, int y, int v) {
		return b(mossy(p, u, y, v) ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
	}

	private static BlockState brickSlab(Plot p, int u, int y, int v) {
		return slab(mossy(p, u, y, v) ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB);
	}

	private static BlockState brickStairs(Plot p, int u, int y, int v, Direction facing) {
		return stairs(mossy(p, u, y, v) ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, facing);
	}

	private static BlockState brickWall(Plot p, int u, int y, int v) {
		return b(mossy(p, u, y, v) ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL);
	}

	private static BlockState topSlab(BlockState bottom) {
		return bottom.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	// ------------------------------------------------------------------ column (1-2 wide plots)

	/** A one-block tōrō: thin wall shaft, a standing lantern in a stone niche, a hat block. */
	private static void column(CitadelCanvas c, Plot p, int u, int v, int hh, boolean low) {
		int y = 0;
		if (low) {
			// yukimi: one short leg, the lantern on it, a wide hat and a dome
			p.set(c, u, y++, v, brickWall(p, u, 0, v));
			p.set(c, u, y++, v, b(Blocks.LANTERN));
			p.set(c, u, y++, v, b(Blocks.POLISHED_ANDESITE));
			if (hh >= 4) {
				p.set(c, u, y, v, slab(Blocks.POLISHED_ANDESITE_SLAB));
			}
			return;
		}
		if (hh >= 4) {
			p.set(c, u, y++, v, brick(p, u, 0, v)); // kiso
		}
		if (hh >= 5) {
			p.set(c, u, y++, v, brickWall(p, u, 1, v)); // sao
		}
		p.set(c, u, y++, v, b(Blocks.CHISELED_STONE_BRICKS)); // chūdai
		p.set(c, u, y++, v, b(Blocks.LANTERN)); // hibukuro
		p.set(c, u, y, v, b(Blocks.POLISHED_ANDESITE)); // kasa
	}

	// ------------------------------------------------------------------ the 3 x 3 tōrō

	/**
	 * A full tōrō on the 3 × 3 cells {@code u0..u0+2, v0..v0+2}, its first layer at {@code y}, with {@code hh}
	 * layers of height. {@code garden}: the foot cells round the shaft hold plants (it stands on a gravel bed).
	 */
	private void toro(CitadelCanvas c, Plot p, int u0, int v0, int y, int hh, boolean low, boolean garden) {
		if (low && hh < 4) {
			low = false; // no room for legs
		}
		int cu = u0 + 1;
		int cv = v0 + 1;
		int yP;
		if (hh >= 4) {
			yP = y + 1;
			foot(c, p, u0, v0, y, low, garden);
		} else {
			yP = y;
		}
		int yC = yP + 1;
		int yH = yP + 2;
		int level = mossLevel(p);

		// --- chūdai: a platform that flares past the shaft (top slabs, or bottom slabs on the yukimi legs)
		p.set(c, cu, yP, cv, b(Blocks.CHISELED_STONE_BRICKS));
		for (int[] k : SIDES) {
			slabAt(c, p, u0 + k[0], yP, v0 + k[1], low);
		}
		for (int[] k : CORNERS) {
			slabAt(c, p, u0 + k[0], yP, v0 + k[1], low);
		}

		// --- hibukuro: the light
		p.set(c, cu, yC, cv, b(Blocks.OCHRE_FROGLIGHT));

		// --- kasa: a block over the light, an eave of top slabs round it
		// (kasuga: top slabs, so nobody can stand on the platform under it; yukimi: a flat eave of bottom slabs)
		p.set(c, cu, yH, cv, b(Blocks.POLISHED_ANDESITE));
		BlockState eave = slab(Blocks.POLISHED_ANDESITE_SLAB);
		for (int[] k : SIDES) {
			p.set(c, u0 + k[0], yH, v0 + k[1], low ? eave : topSlab(eave));
		}
		for (int[] k : CORNERS) {
			p.set(c, u0 + k[0], yH, v0 + k[1], low ? eave : topSlab(eave));
		}
		if (yH + 1 < y + hh) {
			// kasuga: the corners curl up on slabs that rest on the eave
			for (int[] k : CORNERS) {
				if (!low) {
					p.set(c, u0 + k[0], yH + 1, v0 + k[1], slab(Blocks.POLISHED_ANDESITE_SLAB));
				}
			}
			// hōju: a jewel on kasuga, a dome slab on yukimi
			p.set(c, cu, yH + 1, cv, low ? slab(Blocks.POLISHED_ANDESITE_SLAB) : b(Blocks.ANDESITE_WALL));
			if (level == 2 && !low) {
				for (int[] k : SIDES) {
					if (p.hash(u0 + k[0], yH, v0 + k[1], 2) == 0) {
						p.set(c, u0 + k[0], yH + 1, v0 + k[1], b(Blocks.MOSS_CARPET));
					}
				}
			}
		}
	}

	private static void slabAt(CitadelCanvas c, Plot p, int u, int y, int v, boolean low) {
		BlockState s = brickSlab(p, u, y, v);
		p.set(c, u, y, v, low ? s : topSlab(s));
	}

	/** The first layer: kasuga a stepped foot round a wall shaft, yukimi four legs under a squat body. */
	private void foot(CitadelCanvas c, Plot p, int u0, int v0, int y, boolean low, boolean garden) {
		int cu = u0 + 1;
		int cv = v0 + 1;
		int level = mossLevel(p);
		if (low) {
			p.set(c, cu, y, cv, brick(p, cu, y, cv));
			for (int[] k : CORNERS) {
				p.set(c, u0 + k[0], y, v0 + k[1], brickWall(p, u0 + k[0], y, v0 + k[1]));
			}
			if (garden) {
				for (int[] k : SIDES) {
					p.set(c, u0 + k[0], y, v0 + k[1], b(Blocks.MOSS_CARPET));
				}
			}
			return;
		}
		p.set(c, cu, y, cv, brickWall(p, cu, y, cv));
		for (int i = 0; i < 4; i++) {
			int u = u0 + SIDES[i][0];
			int v = v0 + SIDES[i][1];
			if (garden) {
				p.set(c, u, y, v, p.hash(u, y, v, 3) == 0 ? b(Blocks.POTTED_FERN) : b(Blocks.MOSS_CARPET));
			} else {
				p.set(c, u, y, v, brickStairs(p, u, y, v, IN[i]));
			}
		}
		int potted = p.hash(u0, y, v0, 4);
		for (int i = 0; i < 4; i++) {
			int u = u0 + CORNERS[i][0];
			int v = v0 + CORNERS[i][1];
			if (garden) {
				p.set(c, u, y, v, i == potted ? b(Blocks.POTTED_FLOWERING_AZALEA) : b(Blocks.MOSS_CARPET));
			} else if (level > 0 && p.hash(u, y, v, 2) == 0) {
				p.set(c, u, y, v, b(Blocks.MOSS_CARPET)); // moss on the ground at the foot
			} else {
				p.set(c, u, y, v, brickSlab(p, u, y, v));
			}
		}
	}

	// ------------------------------------------------------------------ plinth (5 x 5 and more)

	/**
	 * A tōrō on a small raised bed of gravel edged with stairs (climbed from any side), moss and potted plants
	 * in the bed. The plinth is 5 or 7 wide, centred in the plot; the tōrō takes the remaining 4 layers.
	 */
	private void plinth(CitadelCanvas c, Plot p, int cap, boolean low) {
		int zw = p.w() >= 7 ? 7 : 5;
		int zd = p.d() >= 7 ? 7 : 5;
		int zu = (p.w() - zw) / 2;
		int zv = (p.d() - zd) / 2;
		for (int v = zv; v < zv + zd; v++) {
			for (int u = zu; u < zu + zw; u++) {
				boolean west = u == zu;
				boolean east = u == zu + zw - 1;
				boolean north = v == zv;
				boolean south = v == zv + zd - 1;
				if ((west || east) && (north || south)) {
					p.set(c, u, 0, v, brickSlab(p, u, 0, v)); // corner: a low slab
				} else if (north) {
					p.set(c, u, 0, v, brickStairs(p, u, 0, v, Direction.SOUTH));
				} else if (south) {
					p.set(c, u, 0, v, brickStairs(p, u, 0, v, Direction.NORTH));
				} else if (west) {
					p.set(c, u, 0, v, brickStairs(p, u, 0, v, Direction.EAST));
				} else if (east) {
					p.set(c, u, 0, v, brickStairs(p, u, 0, v, Direction.WEST));
				} else {
					p.set(c, u, 0, v, b(Blocks.GRAVEL));
				}
			}
		}
		int cu = zu + zw / 2;
		int cv = zv + zd / 2;
		// the bed beyond the tōrō's own 3 x 3 (7-wide plinths): moss, ferns and an azalea in the corners
		for (int v = zv + 1; v < zv + zd - 1; v++) {
			for (int u = zu + 1; u < zu + zw - 1; u++) {
				if (Math.abs(u - cu) <= 1 && Math.abs(v - cv) <= 1) {
					continue;
				}
				boolean corner = (u == zu + 1 || u == zu + zw - 2) && (v == zv + 1 || v == zv + zd - 2);
				if (corner) {
					p.set(c, u, 1, v, b(p.hash(u, 1, v, 3) == 0 ? Blocks.POTTED_FERN : Blocks.POTTED_FLOWERING_AZALEA));
				} else if (p.hash(u, 1, v, 3) != 0) {
					p.set(c, u, 1, v, b(Blocks.MOSS_CARPET));
				}
			}
		}
		// the bed is one extra layer: the tōrō keeps its 5 (hat corners curled) when the plot is 6 high
		toro(c, p, cu - 1, cv - 1, 1, Math.min(p.h(), 6) - 1, low, true);
	}
}
