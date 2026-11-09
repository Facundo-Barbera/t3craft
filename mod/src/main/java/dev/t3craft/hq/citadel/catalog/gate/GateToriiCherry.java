package dev.t3craft.hq.citadel.catalog.gate;

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
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Cherry and deepslate torii (ryobu style): two deepslate-footed cherry pillars, a protruding tie beam
 * (nuki), a black plaque with gold glyphs between two struts, and a deepslate-tile roof beam (kasagi)
 * whose ends sweep up. Hanging lanterns flank the opening; wide plots add stone lanterns and
 * secondary posts.
 *
 * <ul>
 * <li><b>Walkable centre</b>: the opening runs front → back through the middle, {@code oh} blocks high
 *     (3 at minimum, up to 5), with nothing but the two hanging lanterns at its edges.</li>
 * <li><b>Gradient dark → light</b>: polished deepslate foot, cherry shaft with stripped-cherry rings,
 *     stripped cherry tie beam, dark kasagi with a gold finial.</li>
 * <li><b>Depth</b>: the beam and roof protrude past the shafts in u and v; the plaque sits on a
 *     gold-glyph panel flanked by struts; the roof underside is bevelled with inverted stairs.</li>
 * </ul>
 * Size: 5–11 × 2–4 × 8–10.
 */
public final class GateToriiCherry implements Variant {
	private static final BlockState LOG = b(Blocks.CHERRY_LOG);
	private static final BlockState STRIPPED = b(Blocks.STRIPPED_CHERRY_WOOD);
	private static final BlockState PLAQUE = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	private static final BlockState GOLD = b(Blocks.GOLD_BLOCK);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "gate_torii_cherry";
	}

	@Override
	public Family family() {
		return Family.GATE;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 4;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int ov = w >= 9 ? 2 : w >= 7 ? 1 : 0; // roof/beam overhang past the shafts, in u
		int lp = ov; // left pillar column
		int rp = w - 1 - ov; // right pillar column
		int ow = rp - lp - 1; // clear opening width
		int oh = Math.min(5, p.h() - 5); // clear opening height
		int nuki = oh; // tie beam y
		int k0 = oh + 3; // kasagi lower layer
		int k1 = oh + 4; // kasagi upper layer (tips and centre slab)
		int pd = d % 2 == 0 ? 2 : 1; // shaft depth (centred)
		int v0 = (d - pd) / 2;
		int vc = v0; // the plane of beam and plaque

		pillar(c, p, lp, v0, pd, oh);
		pillar(c, p, rp, v0, pd, oh);

		// --- secondary posts (hikae-bashira) on wide plots, tied by the beam
		if (ov >= 2) {
			for (int side = -1; side <= 1; side += 2) {
				int u = side < 0 ? lp - 1 : rp + 1;
				for (int v = v0; v < v0 + pd; v++) {
					p.set(c, u, 0, v, b(Blocks.DEEPSLATE_BRICKS));
					for (int y = 1; y < oh - 1; y++) {
						p.set(c, u, y, v, LOG);
					}
					p.set(c, u, oh - 1, v, slab(Blocks.CHERRY_SLAB));
				}
			}
		}

		// --- nuki: stripped cherry tie beam, protruding past the shafts
		int nl = Math.max(0, lp - (ov >= 1 ? 1 : 0));
		int nr = Math.min(w - 1, rp + (ov >= 1 ? 1 : 0));
		for (int u = nl; u <= nr; u++) {
			for (int v = v0; v < v0 + pd; v++) {
				p.set(c, u, nuki, v, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			}
		}

		// --- plaque between the beams: black board with gold glyphs, struts at its sides
		int pw = ow <= 4 ? ow : (ow % 2 == 1 ? 3 : 4);
		int ps = lp + 1 + (ow - pw) / 2;
		for (int u = lp + 1; u <= rp - 1; u++) {
			boolean onPlaque = u >= ps && u < ps + pw;
			boolean strut = u == ps - 1 || u == ps + pw;
			for (int y = nuki + 1; y <= nuki + 2; y++) {
				if (onPlaque) {
					p.set(c, u, y, vc, plaque(p, u - ps, y - nuki - 1, pw));
					p.set(c, u, y, vc, plaque(p, u - ps, y - nuki - 1, pw));
				} else if (strut) {
					p.set(c, u, y, vc, LOG);
				}
			}
		}

		// --- hanging lanterns at the edges of the opening (the centre stays clear)
		for (int side = -1; side <= 1; side += 2) {
			int u = side < 0 ? lp + 1 : rp - 1;
			int lamp = nuki >= 4 ? 3 : 2; // light rule: lanterns hang at foot + 3 or higher (a 3-high opening has no room)
			for (int y = nuki - 1; y > lamp; y--) {
				p.put(c, u, y, vc, b(Blocks.IRON_CHAIN), CitadelCanvas.HANGING);
			}
			p.put(c, u, lamp, vc, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}

		// --- kasagi. Lower layer: a pink shimaki beam over the shaft depth, flanked by bevelled eaves of
		// deepslate (inverted stairs); end caps in deepslate. Upper layer: dark roof whose tips sweep up.
		for (int u = 0; u < w; u++) {
			boolean cap = u == 0 || u == w - 1;
			for (int v = 0; v < d; v++) {
				boolean shaft = v >= v0 && v < v0 + pd;
				if (shaft && !cap) {
					p.set(c, u, k0, v, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
				} else if (!shaft) {
					Direction in = v < v0 ? Direction.SOUTH : Direction.NORTH;
					p.set(c, u, k0, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, in).setValue(BlockStateProperties.HALF, Half.TOP));
				} else {
					p.set(c, u, k0, v, b(Blocks.DEEPSLATE_TILES));
				}
			}
		}
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int e = Math.min(u, w - 1 - u);
				boolean left = u < w - 1 - u;
				if (e == 0) {
					p.set(c, u, k1, v, stairs(Blocks.DEEPSLATE_TILE_STAIRS, left ? Direction.WEST : Direction.EAST));
				} else if (e == 1) {
					p.set(c, u, k1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
				} else {
					// ridge: slabs, thin so the beam below stays readable; cracked tiles break the line
					p.set(c, u, k1, v, p.hash(u, k1, v, 5) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		}
		// gold caps on the tips (the "kinzoku") and a cherry slab bridging the tip steps
		// (no gold cubes on the tips: the upswept stairs are the finish; gold stays on the plaque only)


		// --- stone lanterns (toro) outside the pillars when there is room
		if (ov >= 1) {
			toro(c, p, 0, vc);
			toro(c, p, w - 1, vc);
		}
	}

	/** One pillar: polished-deepslate foot, cherry shaft with stripped rings, plinth steps outside. */
	private static void pillar(CitadelCanvas c, Plot p, int u, int v0, int pd, int oh) {
		for (int v = v0; v < v0 + pd; v++) {
			p.set(c, u, 0, v, p.hash(u, 0, v, 2) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
			for (int y = 2; y < oh; y++) {
				boolean ring = y == 2 || y == oh - 1;
				p.set(c, u, y, v, ring ? STRIPPED : LOG);
			}
			p.set(c, u, oh, v, STRIPPED); // the pillar passes through the beam
		}
		// plinth steps in front of and behind the shaft (the opening side stays clear)
		if (v0 >= 1) {
			p.set(c, u, 0, v0 - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			p.set(c, u, 0, v0 + pd, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
		}
	}

	/** A stone lantern: brick foot, wall stem, lantern under a slab cap. */
	private static void toro(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 0, v, b(Blocks.DEEPSLATE_BRICKS));
		p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
		p.set(c, u, 2, v, LANTERN);
		p.set(c, u, 3, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
	}

	private static BlockState plaque(Plot p, int i, int row, int pw) {
		// glyph strokes in gold, the rest black bricks: a deterministic "calligraphy"
		boolean gold = (row == 1 && i % 2 == 0) || (row == 0 && i % 2 == 1 && p.hash(i, row, 0, 3) == 0);
		if (pw == 1) {
			gold = row == 1;
		}
		return gold ? GOLD : PLAQUE;
	}

}
