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
 * A myōjin torii in vermilion: two slim red pillars on black feet, a tie beam (nuki) that runs through the
 * pillars and out past them, a dark plaque (gakuzuka) in the middle, a red under-beam (shimaki) and over
 * it a thin dark kasagi whose two tips sweep up. Everything sits in one plane, so the gate reads as lines
 * against the sky, not as a roof.
 *
 * <ul>
 * <li><b>Walkable centre</b>: the opening runs front → back between the pillars, 4 or more blocks high,
 *     with only the two hanging lanterns at its edges.</li>
 * <li><b>Silhouette</b>: the kasagi is a single slab line, wider than everything under it; its ends are
 *     stairs turned outward (the upturned tips) over upside-down stairs that taper the shimaki ends, so
 *     each end curves up. The nuki is shorter than the kasagi and longer than the pillar span.</li>
 * <li><b>Palette</b>: black deepslate and blackstone feet → stripped mangrove red (pillars, nuki, shimaki)
 *     → a dark kasagi line; a dark chiselled plaque (gold-faced on tall gates), stone lanterns outside on wide plots.</li>
 * </ul>
 * Size: 5–11 × 2–4 × 8+ (uses up to 10 blocks of height).
 */
public final class GateToriiCherry implements Variant {
	private static final BlockState RED = b(Blocks.STRIPPED_MANGROVE_LOG);
	private static final BlockState RED_X = RED.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
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
		int ov = w >= 9 ? 2 : w >= 7 ? 1 : 0; // kasagi overhang past the pillars, in u
		int lp = ov; // left pillar column
		int rp = w - 1 - ov; // right pillar column
		int vc = (d - 1) / 2; // the plane of the gate
		int top = Math.min(p.h() - 1, 9); // kasagi
		int shimaki = top - 1;
		int gap = p.h() >= 10 ? 2 : 1; // plaque rows between nuki and shimaki
		int nuki = shimaki - gap - 1;

		// --- pillars: black foot, red shaft up to the shimaki; plinth stairs in front and behind
		for (int u : new int[] {lp, rp}) {
			p.set(c, u, 0, vc, b(p.hash(u, 0, vc, 3) == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE));
			p.set(c, u, 1, vc, b(Blocks.POLISHED_BLACKSTONE)); // the black foot ring (kamaki), two blocks high
			for (int y = 2; y < shimaki; y++) {
				p.set(c, u, y, vc, RED);
			}
			for (int v : new int[] {vc - 1, vc + 1}) {
				if (p.has(u, 0, v)) {
					p.set(c, u, 0, v, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, v < vc ? Direction.SOUTH : Direction.NORTH));
				}
			}
			int out = u == lp ? u - 1 : u + 1;
			if (p.has(out, 0, vc)) {
				p.set(c, out, 0, vc, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, u == lp ? Direction.EAST : Direction.WEST));
			}
		}

		// --- nuki: runs through both pillars and sticks out one block past each (where the plot allows)
		for (int u = Math.max(0, lp - 1); u <= Math.min(w - 1, rp + 1); u++) {
			if (u != lp && u != rp) {
				p.set(c, u, nuki, vc, RED_X);
			}
		}

		// --- gakuzuka: a dark plaque in the middle, between nuki and shimaki, with a gold face on tall gates
		int ow = rp - lp - 1;
		int pc = lp + 1 + (ow - 1) / 2;
		for (int y = nuki + 1; y < shimaki; y++) {
			BlockState face = gap >= 2 && y == nuki + 1 ? b(Blocks.GOLD_BLOCK) : b(Blocks.CHISELED_POLISHED_BLACKSTONE);
			p.set(c, pc, y, vc, face);
			if (ow % 2 == 0) {
				p.set(c, pc + 1, y, vc, face);
			}
		}

		// --- shimaki: the red under-beam; with an overhang, its ends taper on upside-down stairs
		for (int u = 0; u < w; u++) {
			boolean end = ov >= 1 && (u == 0 || u == w - 1);
			if (end) {
				Direction in = u == 0 ? Direction.EAST : Direction.WEST;
				p.set(c, u, shimaki, vc, stairs(Blocks.MANGROVE_STAIRS, in).setValue(BlockStateProperties.HALF, Half.TOP));
			} else {
				p.set(c, u, shimaki, vc, RED_X);
			}
		}

		// --- kasagi: one thin dark slab line, the ends stairs turned outward so the tips sweep up
		for (int u = 0; u < w; u++) {
			if (u == 0 || u == w - 1) {
				p.set(c, u, top, vc, stairs(Blocks.DEEPSLATE_TILE_STAIRS, u == 0 ? Direction.WEST : Direction.EAST));
			} else {
				p.set(c, u, top, vc, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}

		// --- hanging lanterns at the edges of the opening (the centre stays clear)
		if (ow >= 3) {
			for (int u : new int[] {lp + 1, rp - 1}) {
				p.put(c, u, nuki - 1, vc, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}

		// --- stone lanterns under the kasagi tips when the plot is wide enough
		if (ov >= 2) {
			toro(c, p, 0, vc);
			toro(c, p, w - 1, vc);
		}
	}

	/** A small stone lantern: dark foot, stone stem, lantern under a pale cap. */
	private static void toro(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 0, v, b(Blocks.DEEPSLATE_BRICKS));
		p.set(c, u, 1, v, b(Blocks.STONE_BRICK_WALL));
		p.set(c, u, 2, v, LANTERN);
		p.set(c, u, 3, v, slab(Blocks.POLISHED_ANDESITE_SLAB));
	}
}
