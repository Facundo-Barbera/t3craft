package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Path lamp: a stripped cherry post on a dark stone foot with a paper bracket lantern on its front (and, on a plot
 * deep enough, one on its back, so the path is lit from both sides). For paths and street edges, one per stretch.
 *
 * <ul>
 * <li><b>Gradient dark → light</b>: polished deepslate foot, a polished deepslate wall plinth, the cherry post, a
 *     cherry slab cap; the warm paper is the brightest thing and sits near the top, where it lights the way.</li>
 * <li><b>Depth</b>: the lantern hangs one cell off the post on its iron bracket; the cap overhangs it.</li>
 * <li>With depth 1 there is no cell for a bracket: the standing andon sits on top of the post instead.</li>
 * </ul>
 * Size: 1–3 × 1–3 × 4–8. The post's tone alternates (paper, rose) with the seed, vermilion is left to gates.
 */
public final class LampPaperPost implements Variant {
	private static final BlockState FOOT = b(Blocks.POLISHED_DEEPSLATE);
	private static final BlockState PLINTH = b(Blocks.POLISHED_DEEPSLATE_WALL);
	private static final BlockState POST = b(Blocks.STRIPPED_CHERRY_LOG);

	@Override
	public String id() {
		return "lamp_paper_post";
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
		return 4;
	}

	@Override
	public int maxW() {
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cu = p.w() / 2;
		int cv = p.d() >= 2 ? 1 : 0; // the post one cell back, so the front cell takes the lantern
		int top = Math.min(p.h(), 8) - 1;
		String tone = p.hash(0, 0, 0, 2) == 0 ? "paper" : "rose";

		p.set(c, cu, 0, cv, FOOT);
		int y = 1;
		if (top >= 5) {
			p.set(c, cu, y++, cv, PLINTH);
		}
		for (; y < top; y++) {
			p.set(c, cu, y, cv, POST);
		}
		if (p.d() < 2) {
			// no room for a bracket: the andon stands on top of the post
			p.set(c, cu, top, cv, PaperLanterns.standing());
			return;
		}
		p.set(c, cu, top, cv, slab(Blocks.CHERRY_SLAB));
		int ly = top - 1;
		p.set(c, cu, ly, cv - 1, PaperLanterns.wall(Direction.NORTH, tone));
		if (p.d() >= 3) {
			p.set(c, cu, ly, cv + 1, PaperLanterns.wall(Direction.SOUTH, tone));
		}
	}
}
