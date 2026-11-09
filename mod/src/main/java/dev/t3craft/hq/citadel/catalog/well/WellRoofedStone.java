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
 * Village well that works and reads as one: a stone kerb with the water showing inside (the front is
 * lowered to a half-block lip between two chiseled corner pedestals), a windlass (a pale stripped-log axle
 * on top of two thick log posts, a crank lever on its front, a bucket on an iron chain right over the water)
 * under a light gable roof: stairs at a real 1:1 pitch, a thin slab lip for the eaves and a soffit of the
 * same material as the roof. The whole plot outside the kerb is flagstone paving with a lamp post on each
 * front corner, a bench, a trough, barrels and pots round it.
 *
 * <pre>
 *  front elevation (w = 7, core 3 x 3)
 *  y7        ▄██▄          ridge (roof material, soffit under it)
 *  y6     ▂▄▀▀▀▀▀▀▄▂       stairs + slab lip (thin eave)
 *  y5      ═══════         crossbars (stripped timber, along v)
 *  y4      ═╪═◦═╪═         pale axle on the posts, crank lever
 *  y3      ║  ┆  ║         log posts, chain
 *  y2     ◘║  ┆  ║◘        pedestal lanterns
 *  y1     ▣ ▣ ◘  ▣ ▣       stone bases, bucket
 *  y0   ▒▒▓▓▓▂≈▂▓▓▓▒▒      kerb (front lip lowered), water, paving
 * </pre>
 *
 * Roof, timber and stone sets come from the seed. Size: 3–8 × 3–8 × 8+ (the core is 3 × 3, or 4 × 4 from
 * 6 × 6 up; the rest of the plot is paving and furniture).
 */
public final class WellRoofedStone implements Variant {
	/** The roof's lowest row (the eaves). */
	private static final int ROOF = 6;
	/** The crossbars: the layer right under the roof. */
	private static final int BAR = ROOF - 1;
	/** The axle: on the post tops, under the crossbars. */
	private static final int AXLE = BAR - 1;

	/** A roof set: slope stairs, matching slab (the eave lip), ridge/soffit block, and the timber that goes with it. */
	private record RoofSet(Block stairs, Block slab, Block ridge, Block log, Block stripped, Block fence) {
	}

	private static final RoofSet[] ROOFS = {
		new RoofSet(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_FENCE),
		new RoofSet(Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_FENCE),
		new RoofSet(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_FENCE),
		new RoofSet(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_FENCE),
	};

	/** A stone set: three kerb tones (main, second, third), the chiseled corner, the bench stair and three paving slabs. */
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

	/** The well's geometry in the plot: a square core {@code cs × cs} with a thin eave lip. */
	private record Geo(int w, int d, int cs, int uL, int uR, int v0, int v1, int ovU) {
		static Geo of(Plot p) {
			int w = p.w();
			int d = p.d();
			int cs = Math.min(w, d) >= 6 ? 4 : 3;
			int uL = (w - cs) / 2;
			int uR = uL + cs - 1;
			int v0 = (d - cs) / 2;
			int v1 = v0 + cs - 1;
			int ovU = uL >= 2 && w - 1 - uR >= 2 ? 1 : 0;
			return new Geo(w, d, cs, uL, uR, v0, v1, ovU);
		}

		/** Middle column of the core (the first of the two on a 4 core's far side). */
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
		int si = p.hash(2, 0, 0, STONES.length);
		if (roof == ROOFS[0] && si == 1) {
			si = 0; // slate roof over a light kerb, never a muddy dark-on-dark well
		}
		StoneSet st = STONES[si];
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
	 * [depth] a stone kerb with the water inside. The front, between the two corner pedestals, drops to a
	 * half-block lip (so the water, the chain and the bucket show); each corner is a two-block pedestal
	 * (kerb + chiseled) with a lantern standing on it.
	 */
	private static void kerb(CitadelCanvas c, Plot p, Geo g, StoneSet st) {
		BlockState lantern = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int u = g.uL(); u <= g.uR(); u++) {
			for (int v = g.v0(); v <= g.v1(); v++) {
				boolean cu = u == g.uL() || u == g.uR();
				boolean cv = v == g.v0() || v == g.v1();
				if (!cu && !cv) {
					p.set(c, u, 0, v, b(Blocks.WATER));
				} else if (cu && cv) {
					p.set(c, u, 0, v, kerbBlock(p, st, u, v));
					p.set(c, u, 1, v, b(st.chiseled()));
					// a lantern hangs on a chain from the crossbar over the pedestal
					p.put(c, u, AXLE, v, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
					p.put(c, u, AXLE - 1, v, lantern, CitadelCanvas.HANGING);
				} else if (v == g.v0()) {
					p.set(c, u, 0, v, slab(st.slabA())); // the low front lip
				} else {
					p.set(c, u, 0, v, kerbBlock(p, st, u, v));
				}
			}
		}
	}

	// ------------------------------------------------------------------ posts, crossbars, windlass

	private static void frame(CitadelCanvas c, Plot p, Geo g, RoofSet roof, StoneSet st) {
		int va = g.va();
		int[] postV = g.cs() == 3 ? new int[] {va} : new int[] {va, g.v1() - 1};
		BlockState pale = log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.X);
		for (int u : new int[] {g.uL(), g.uR()}) {
			for (int v : postV) {
				// [depth] a thick log post on a stone base; the axle (or a log) is its capital
				p.set(c, u, 1, v, b(st.a()));
				int top = v == va ? AXLE - 1 : AXLE;
				for (int y = 2; y <= top; y++) {
					p.set(c, u, y, v, log(roof.log(), Direction.Axis.Y));
				}
			}
			// the crossbar over the post carries the roof and the lip
			for (int v = g.v0(); v <= g.v1(); v++) {
				p.set(c, u, BAR, v, log(roof.log(), Direction.Axis.Z));
			}
		}
		// the axle: a pale stripped log across the shaft, resting on the post tops; where the plot has a ring
		// it sticks out one block past each post, and the crank lever is on its east end
		int ax0 = g.uL() - (g.uL() >= 1 ? 1 : 0);
		int ax1 = g.uR() + (g.uR() + 1 < g.w() ? 1 : 0);
		for (int u = ax0; u <= ax1; u++) {
			p.set(c, u, AXLE, va, pale);
		}
		int cu = g.cs() == 4 ? g.uL() + 1 + p.hash(3, 0, 0, 2) : g.uc();
		if (g.uR() + 2 < g.w()) {
			p.put(c, g.uR() + 2, AXLE, va, b(Blocks.LEVER)
					.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), CitadelCanvas.DECOR);
		} else {
			p.put(c, cu, AXLE, g.v0(), b(Blocks.LEVER)
					.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);
		}
		// the bucket (a water cauldron) hangs on a short iron chain from the axle in the front gap, over the water
		p.put(c, cu, AXLE - 1, va, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
		p.put(c, cu, AXLE - 2, va, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ roof (y6+)

	/**
	 * [depth] gable roof, ridge along v (front to back, so the visitor looks into the open gable and sees the
	 * windlass), pitch 1:1 in stairs of one material up to a block ridge (or two stairs meeting). The soffit
	 * under the upper rows is the roof's own material, so the roof is one tone and one face-connected mass
	 * resting on the crossbars. The eaves are a thin lip of slabs one block past the posts. The gables stay open.
	 */
	private static void roof(CitadelCanvas c, Plot p, Geo g, RoofSet roof) {
		int cols = g.cs();
		BlockState soffit = b(roof.ridge());
		for (int r = 0; r < cols; r++) {
			int u = g.uL() + r;
			int t = Math.min(r, cols - 1 - r);
			int y = ROOF + t;
			boolean ridgeBlock = cols % 2 == 1 && r == cols / 2;
			boolean leftSide = r < cols - 1 - r; // rises towards +u
			for (int v = g.v0(); v <= g.v1(); v++) {
				BlockState st = ridgeBlock ? soffit : stairs(roof.stairs(), leftSide ? Direction.EAST : Direction.WEST);
				p.set(c, u, y, v, st);
				if (t >= 1) {
					p.set(c, u, y - 1, v, soffit);
				}
			}
		}
		if (g.ovU() > 0) {
			for (int v = g.v0(); v <= g.v1(); v++) {
				p.set(c, g.uL() - 1, ROOF, v, slab(roof.slab()));
				p.set(c, g.uR() + 1, ROOF, v, slab(roof.slab()));
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
				// [palette] flagstones: three slab tones mixed by hash; the whole plot outside the kerb is paved
				int n = p.hash(u, 0, v, 6);
				boolean approach = v < g.v0() && Math.abs(u - g.uc()) <= 1;
				if (approach) {
					n = 3 + n % 3; // the approach is the darker mix
				}
				p.set(c, u, 0, v, slab(n < 3 ? st.slabA() : n < 5 ? st.slabB() : st.slabC()));
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

	/** A lamp post: a stone foot, two fence posts and a lantern on top. */
	private static void lampPost(CitadelCanvas c, Plot p, RoofSet roof, StoneSet st, int u, int v) {
		if (u < 0 || v < 0 || u >= p.w() || v >= p.d()) {
			return;
		}
		p.set(c, u, 0, v, b(st.cc()));
		p.set(c, u, 1, v, b(roof.fence()));
		p.set(c, u, 2, v, b(roof.fence()));
		p.set(c, u, 3, v, b(Blocks.LANTERN));
	}

	private static void pot(CitadelCanvas c, Plot p, int u, int v) {
		if (u < 0 || v < 0 || u >= p.w() || v >= p.d()) {
			return;
		}
		p.set(c, u, 0, v, b(p.hash(u, 0, v, 2) == 0 ? Blocks.POTTED_FERN : Blocks.POTTED_AZALEA));
	}

	/**
	 * Uses round the well. Lamp posts mark the two front corners of the plot and, where the plot is big
	 * enough, a bench and a trough face each other across the well on the two sides (by seed) with barrels
	 * and pots behind; the walk round the kerb stays free. A plot with no such band gets a barrel and a
	 * trough cauldron in the two back corners.
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
			// pots on the back corners of the plot, when no trough or bench takes them
			if (hasL) {
				pot(c, p, 0, d - 1);
			}
			if (hasR) {
				pot(c, p, w - 1, d - 1);
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
		// lamp posts on the two front corners of the plot (outside the approach)
		if (g.v0() >= 1 && g.uL() >= 1 && w - 1 - g.uR() >= 1) {
			lampPost(c, p, roof, st, 0, 0);
			lampPost(c, p, roof, st, w - 1, 0);
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
