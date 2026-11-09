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

/**
 * Plaza lantern: the two-block paper lantern (stone plinth, cherry post, a big paper box under a hip roof) on a
 * stepped dark plinth, the big sibling of the path post, for the corners and centres of plazas. One per corner at
 * most: it is tall and bright (light 15) and lights a plaza on its own.
 *
 * <ul>
 * <li><b>Transition to the ground</b>: on a 3×3 plot the plinth is a ring of stairs climbing to the lantern's
 *     foot, corners low, one of them a potted azalea; never a block dropped on the paving.</li>
 * <li><b>Scale</b>: 3 blocks to the roof (plinth + 2), so it stands taller than a person by a head.</li>
 * </ul>
 * Size: 1–3 × 1–3 × 3–5.
 */
public final class LampPaperPlaza implements Variant {
	@Override
	public String id() {
		return "lamp_paper_plaza";
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
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cu = p.w() / 2;
		int cv = p.d() / 2;
		p.set(c, cu, 0, cv, b(Blocks.POLISHED_DEEPSLATE));
		if (p.w() >= 3 && p.d() >= 3) {
			p.set(c, cu, 0, cv - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			p.set(c, cu, 0, cv + 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
			p.set(c, cu - 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.EAST));
			p.set(c, cu + 1, 0, cv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.WEST));
			int planted = p.hash(1, 0, 1, 4);
			int[][] corners = {{cu - 1, cv - 1}, {cu + 1, cv - 1}, {cu - 1, cv + 1}, {cu + 1, cv + 1}};
			for (int i = 0; i < 4; i++) {
				p.set(c, corners[i][0], 0, corners[i][1], i == planted ? b(Blocks.POTTED_FLOWERING_AZALEA) : slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
		p.set(c, cu, 1, cv, PaperLanterns.tall(false));
		p.set(c, cu, 2, cv, PaperLanterns.tall(true));
	}
}
