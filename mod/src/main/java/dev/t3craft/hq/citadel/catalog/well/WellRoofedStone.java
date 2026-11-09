package dev.t3craft.hq.citadel.catalog.well;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Village well that works and reads as one: a stone kerb at bench height (one block above the ground,
 * half a block over the paving) with the water showing inside, a windlass (axle, crank lever, a bucket
 * on an iron chain) between two or four timber posts on stone bases, under a gable roof of stairs with a
 * real 1:1 pitch, eaves on every side and open gable ends facing the visitor. Round it: a small flagstone paving,
 * a bench, a water trough (two cauldrons) and barrels where the plot has room, and a free walk all
 * round the kerb.
 *
 * <pre>
 *  front elevation (w = 7, d = 7, core 3 x 3)       plan (core 3 x 3, plot 7 x 7)
 *  y6     ▄▄██▄▄    ridge                            . . . . . . .   . ground (ferns, moss)
 *  y5   ▄▀▀   ▀▀▄   roof stairs, 1:1 pitch           . ▒ ▒ ▒ ▒ ▒ .   ▒ paving (slabs)
 *  y4  ▀▀▀▀▀▀▀▀▀▀▀  eave row + timber soffit          B ▒ ▣ ▬ ▣ ▒ T   ▣ corner base + lantern
 *  y3  ═╪═══◦═══╪═  crossbars + axle (+ crank)        B ▒ ╫ ≈ ╫ ▒ T   ╫ post on its base, ≈ water
 *  y2  ◘ ║  ┆  ║ ◘  posts, lanterns, chain            B ▒ ▣ ▬ ▣ ▒ T   ▬ kerb (front: step stair)
 *  y1  ▣ ▣  ◘  ▣ ▣  bases, bucket                    . ▒ ▒ ▒ ▒ ▒ .   B bench, T trough
 *  y0  ▒▒▒▓▓≈▓▓▒▒▒  kerb, water, paving               . . . ▒ . . .   (approach, 3 wide)
 * </pre>
 *
 * Roof, timber and stone sets come from the seed. Size: 3–8 × 3–8 × 9+ (the core is 3 × 3, or 4 × 4
 * from 7 × 7 up; the rest of the plot becomes paving and furniture).
 */
public final class WellRoofedStone implements Variant {
	/** The roof's lowest row (the eaves). */
	private static final int ROOF = 5;
	/** The crossbars and the axle: the layer right under the roof. */
	private static final int BAR = ROOF - 1;

	/** A roof set: slope stairs, ridge block, trim stairs (eave row and gable edge), and the timber that goes with it. */
	private record RoofSet(Block stairs, Block ridge, Block eave, Block planks, Block log, Block stripped, Block fence) {
	}

	private static final RoofSet[] ROOFS = {
		new RoofSet(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_FENCE),
		new RoofSet(Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_FENCE),
		new RoofSet(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_FENCE),
		new RoofSet(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_FENCE),
	};

	/** A stone set: three kerb tones (main, second, third), the chiseled corner, the step stair and three paving slabs. */
	private record StoneSet(Block a, Block bb, Block cc, Block chiseled, Block stair, Block slabA, Block slabB, Block slabC) {
	}

	private static final StoneSet[] STONES = {
		new StoneSet(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS,
				Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.COBBLESTONE_SLAB),
		new StoneSet(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_BRICK_STAIRS,
				Blocks.DEEPSLATE_BRICK_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE_SLAB),
		new StoneSet(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.COBBLESTONE_STAIRS,
				Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.STONE_BRICK_SLAB),
	};

	/** The well's geometry in the plot: a square core {@code cs × cs} with its roof overhangs. */
	private record Geo(int w, int d, int cs, int uL, int uR, int v0, int v1, int ovU, int ovV) {
		static Geo of(Plot p) {
			int w = p.w();
			int d = p.d();
			int cs = Math.min(w, d) >= 6 ? 4 : 3;
			int uL = (w - cs) / 2;
			int uR = uL + cs - 1;
			int v0 = (d - cs) / 2;
			int v1 = v0 + cs - 1;
			int ovU = uL >= 1 && w - 1 - uR >= 1 ? 1 : 0;
			int ovV = 0; // the gables stay flush so the windlass can be seen from the front
			return new Geo(w, d, cs, uL, uR, v0, v1, ovU, ovV);
		}

		/** Middle column / row of the core (the first of the two on a 4 core's far side). */
		int uc() {
			return uL + cs / 2;
		}

		int vc() {
			return v0 + cs / 2;
		}

		/** The row the axle (and the bucket) runs along: the inner row next to the front. */
		int va() {
			return v0 + 1;
		}
	}

	@Override
	public String id() {
		return "well_roofed_stone";
	}

	@Override
	public Family family() {
		return Family.WELL;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 8;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Geo g = Geo.of(p);
		RoofSet roof = ROOFS[p.hash(1, 0, 0, ROOFS.length)];
		StoneSet st = STONES[p.hash(2, 0, 0, STONES.length)];
		surroundings(c, p, g, roof, st);
		kerb(c, p, g, st);
		frame(c, p, g, roof, st);
		roof(c, p, g, roof);
	}

	private static BlockState log(Block log, Direction.Axis axis) {
		return b(log).setValue(BlockStateProperties.AXIS, axis);
	}

	// ------------------------------------------------------------------ kerb (y0) and water

	/** [palette] kerb tones by hash: a main brick, a mossy/cracked second tone and a third. */
	private static BlockState kerbBlock(Plot p, StoneSet st, int u, int v) {
		int n = p.hash(u, 0, v, 6);
		return b(n < 3 ? st.a() : n < 5 ? st.bb() : st.cc());
	}

	/**
	 * [depth] the kerb is one full block, a bench height (half a block over the paving): water inside, a
	 * chiseled pedestal on every corner and a step stair in the middle of the front so it can be climbed.
	 */
	private static void kerb(CitadelCanvas c, Plot p, Geo g, StoneSet st) {
		for (int u = g.uL(); u <= g.uR(); u++) {
			for (int v = g.v0(); v <= g.v1(); v++) {
				boolean edge = u == g.uL() || u == g.uR() || v == g.v0() || v == g.v1();
				if (!edge) {
					p.set(c, u, 0, v, b(Blocks.WATER));
					continue;
				}
				p.set(c, u, 0, v, kerbBlock(p, st, u, v));
			}
		}
		p.set(c, g.uc(), 0, g.v0(), stairs(st.stair(), Direction.SOUTH)); // the step: rises towards the shaft
	}

	// ------------------------------------------------------------------ posts, crossbars, windlass

	private static void frame(CitadelCanvas c, Plot p, Geo g, RoofSet roof, StoneSet st) {
		BlockState lantern = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		int[] postV = g.cs() == 3 ? new int[] {g.v0() + 1} : new int[] {g.v0() + 1, g.v0() + 2};
		for (int u : new int[] {g.uL(), g.uR()}) {
			// [depth] corner pedestals; a lantern on a short chain hangs from the crossbar end over each of them
			for (int v : new int[] {g.v0(), g.v1()}) {
				p.set(c, u, 1, v, b(st.chiseled()));
				p.set(c, u, 2, v, slab(st.slabA())); // the pedestal's cap
				if (v == g.v1()) {
					// the lanterns hang at the back, so the front stays clear for the windlass
					p.put(c, u, BAR - 1, v, lantern, CitadelCanvas.HANGING);
				}
			}
			// the fence posts stand on stone bases under the crossbar
			for (int v : postV) {
				p.set(c, u, 1, v, b(st.a()));
				for (int y = 2; y < BAR; y++) {
					p.set(c, u, y, v, b(roof.fence()));
				}
			}
			// the crossbar over the end of the shaft carries the roof and holds the axle
			for (int v = g.v0(); v <= g.v1(); v++) {
				p.set(c, u, BAR, v, log(roof.stripped(), Direction.Axis.Z));
			}
		}
		// the axle across the shaft, between the crossbars; the crank lever sticks out of its front
		int va = g.va();
		int cu = g.cs() == 4 ? g.uL() + 1 + p.hash(3, 0, 0, 2) : g.uL() + 1;
		for (int u = g.uL() + 1; u <= g.uR() - 1; u++) {
			p.set(c, u, BAR - 1, va, log(roof.stripped(), Direction.Axis.X));
		}
		p.put(c, cu, BAR - 1, va - 1, b(Blocks.LEVER)
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);
		// the bucket (a water cauldron) hangs on an iron chain from the axle, just over the water
		for (int y = BAR - 2; y >= 2; y--) {
			p.put(c, cu, y, va, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
		}
		p.put(c, cu, 1, va, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ roof (y4+)

	/**
	 * [depth] gable roof, ridge along v (front to back, so the visitor looks into the open gable and sees the
	 * windlass), pitch 1:1 in stairs of one material from the eave to a block ridge (or two stairs meeting). Under every row from the second a timber block
	 * closes the soffit, so the whole roof is one face-connected mass resting on the crossbars: no loose
	 * overhang. The gable ends stay open.
	 */
	private static void roof(CitadelCanvas c, Plot p, Geo g, RoofSet roof) {
		int ru0 = g.uL() - g.ovU();
		int cols = g.uR() + g.ovU() - ru0 + 1;
		int rv0 = g.v0() - g.ovV();
		int rv1 = g.v1() + g.ovV();
		BlockState planks = b(roof.planks());
		for (int r = 0; r < cols; r++) {
			int u = ru0 + r;
			int t = Math.min(r, cols - 1 - r);
			int y = ROOF + t;
			boolean ridgeBlock = cols % 2 == 1 && r == cols / 2;
			boolean leftSide = r < cols - 1 - r; // rises towards +u
			for (int v = rv0; v <= rv1; v++) {
				BlockState st;
				if (ridgeBlock) {
					st = b(roof.ridge());
				} else {
					st = stairs(roof.stairs(), leftSide ? Direction.EAST : Direction.WEST);
				}
				p.set(c, u, y, v, st);
				if (t >= 1) {
					p.set(c, u, y - 1, v, planks); // timber soffit under the slope
				}
			}
		}
	}

	// ------------------------------------------------------------------ paving, ground, furniture

	private static void surroundings(CitadelCanvas c, Plot p, Geo g, RoofSet roof, StoneSet st) {
		int w = g.w();
		int d = g.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				if (u >= g.uL() && u <= g.uR() && v >= g.v0() && v <= g.v1()) {
					continue;
				}
				int dist = Math.max(Math.max(g.uL() - u, u - g.uR()), Math.max(g.v0() - v, v - g.v1()));
				boolean approach = v < g.v0() && Math.abs(u - g.uc()) <= 1;
				if (dist <= 1 || approach) {
					// [palette] flagstones: three slab tones mixed by hash
					int n = p.hash(u, 0, v, 6);
					p.set(c, u, 0, v, slab(n < 3 ? st.slabA() : n < 5 ? st.slabB() : st.slabC()));
				} else {
					int n = p.hash(u, 0, v, 10);
					if (n == 0) {
						p.set(c, u, 0, v, b(Blocks.FERN));
					} else if (n == 1) {
						p.set(c, u, 0, v, b(Blocks.MOSS_CARPET));
					}
				}
			}
		}
		furniture(c, p, g, roof, st);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	private static BlockState water() {
		return b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
	}

	/**
	 * Uses round the well, only in the band outside the paving ring so the walk round the kerb stays free:
	 * a bench and a trough on the two sides (by seed), barrels behind. A plot with no such band gets a
	 * barrel and a trough cauldron in the two back corners of the paving.
	 */
	private static void furniture(CitadelCanvas c, Plot p, Geo g, RoofSet roof, StoneSet st) {
		int w = g.w();
		int d = g.d();
		int bl = g.uL() - 2;
		int br = g.uR() + 2;
		int bb = g.v1() + 2;
		boolean hasL = bl >= 0;
		boolean hasR = br <= w - 1;
		boolean hasB = bb <= d - 1;
		int uc = g.uc();
		int vc = g.vc();
		boolean flip = p.hash(5, 0, 0, 2) == 1;
		int benchSide = 0; // -1 left, +1 right
		int troughSide = 0;
		if (hasL && hasR) {
			benchSide = flip ? 1 : -1;
			troughSide = -benchSide;
		} else if (hasL || hasR) {
			benchSide = hasL ? -1 : 1;
		}
		boolean benchBack = benchSide == 0 && hasB;
		boolean troughBack = benchSide != 0 && troughSide == 0 && hasB;
		if (benchSide != 0) {
			// the seat faces the well: the high side of the stair is its back, away from the shaft
			int u = benchSide < 0 ? bl : br;
			Direction away = benchSide < 0 ? Direction.WEST : Direction.EAST;
			for (int v = vc - 1; v <= vc + 1; v++) {
				if (v >= 0 && v < d) {
					p.set(c, u, 0, v, stairs(st.stair(), away));
				}
			}
		}
		if (troughSide != 0) {
			int u = troughSide < 0 ? bl : br;
			trough(c, p, u, vc, d);
		}
		if (hasB) {
			if (benchBack) {
				for (int u = uc - 1; u <= uc + 1; u++) {
					if (u >= 0 && u < w) {
						p.set(c, u, 0, bb, stairs(st.stair(), Direction.SOUTH));
					}
				}
			} else if (troughBack) {
				for (int k = -1; k <= 0; k++) {
					int u = uc + k;
					if (u >= 0 && u < w) {
						p.set(c, u, 0, bb, water());
					}
				}
				if (uc + 1 < w) {
					p.set(c, uc + 1, 0, bb, barrel());
				}
			} else {
				p.set(c, uc - 1, 0, bb, barrel());
				p.set(c, uc, 0, bb, barrel());
				p.set(c, uc - 1, 1, bb, barrel());
			}
		}
		if (!hasL && !hasR && !hasB) {
			int cv = g.v1() + 1;
			if (g.uL() - 1 >= 0 && cv < d) {
				p.set(c, g.uL() - 1, 0, cv, barrel());
			}
			if (g.uR() + 1 < w && cv < d) {
				p.set(c, g.uR() + 1, 0, cv, water());
			}
		}
	}

	/** A trough of two water cauldrons side by side along v (at column u, rows vc and vc + 1) with a barrel beside it. */
	private static void trough(CitadelCanvas c, Plot p, int u, int vc, int d) {
		for (int k = 0; k <= 1; k++) {
			if (vc + k < d) {
				p.set(c, u, 0, vc + k, water());
			}
		}
		if (vc - 1 >= 0) {
			p.set(c, u, 0, vc - 1, barrel());
		}
	}
}
