package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

/**
 * Lantern gate: two cherry posts on dark stone feet and a beam across, with paper chochin hanging from it over the
 * way, in the three tones (paper, vermilion in the middle, rose), the middle one on a chain when there is room.
 * For path entrances, bridge heads and the mouth of a plaza: it frames the way and lights it. The way under it stays
 * clear (the lanterns hang at 3 blocks or higher from the foot).
 *
 * <ul>
 * <li><b>Gradient dark → light</b>: deepslate feet, cherry posts, a stripped cherry beam, slab caps on the posts.</li>
 * <li><b>Silhouette</b>: slab caps on the posts above the beam; the lanterns hang at two heights (the middle one
 *     lower, on its chain) so the row has a rhythm.</li>
 * </ul>
 * Size: 3–5 × 1–2 × 6–9.
 */
public final class LampPaperGate implements Variant {
	private static final BlockState POST = b(Blocks.STRIPPED_CHERRY_LOG);
	private static final BlockState FOOT = b(Blocks.POLISHED_DEEPSLATE);
	private static final BlockState CHAIN = b(Blocks.IRON_CHAIN);

	@Override
	public String id() {
		return "lamp_paper_gate";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 6;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 2;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int top = Math.min(p.h(), 8) - 2; // the beam row; the caps sit one above
		int last = p.w() - 1;
		BlockState beam = POST.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		for (int u : new int[] {0, last}) {
			p.set(c, u, 0, 0, FOOT);
			for (int y = 1; y < top; y++) {
				p.set(c, u, y, 0, POST);
			}
			p.set(c, u, top, 0, beam);
			p.set(c, u, top + 1, 0, slab(Blocks.CHERRY_SLAB));
		}
		for (int u = 1; u < last; u++) {
			p.set(c, u, top, 0, beam);
		}
		int mid = last / 2;
		for (int u = 1; u < last; u++) {
			String tone = u == mid ? "vermilion" : (u < mid ? "paper" : "rose");
			boolean chain = u == mid && top >= 6; // h >= 8
			if (chain) {
				p.put(c, u, top - 1, 0, CHAIN, CitadelCanvas.HANGING);
			}
			p.put(c, u, chain ? top - 2 : top - 1, 0, PaperLanterns.hanging(tone), CitadelCanvas.HANGING);
		}
	}
}
