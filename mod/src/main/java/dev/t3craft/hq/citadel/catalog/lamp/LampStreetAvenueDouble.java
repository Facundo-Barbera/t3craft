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

/**
 * Avenue lamp: a wrought-iron lamp with a cross arm, so it lights both sides of a boulevard. A heavy
 * blackstone plinth with a gilded heart, a slim shaft of iron bars, a copper grate boss where the arm
 * springs, and a lantern hanging from each arm tip. With depth 3 the arm becomes a four-way cross (four
 * lanterns, a crossroads lamp). A single copper spike crowns the boss.
 *
 * <ul>
 * <li><b>Gradient dark → light</b>: blackstone and polished blackstone bricks at the foot, a polished
 *     blackstone wall as the lower shaft, iron bars above, then a copper boss and copper finials that
 *     are slightly weathered on some seeds.</li>
 * <li><b>Depth</b>: the plinth steps out (slab wings, stairs, planted corners), the arm is thin bars.</li>
 * <li><b>Connections by hand</b>: the iron-bar arms carry their sides explicitly.</li>
 * </ul>
 * Size: 3 × 1–3 × 6. The shaft stands on the middle row of the depth.
 */
public final class LampStreetAvenueDouble implements Variant {
	private static final BlockState BARS = b(Blocks.IRON_BARS);
	private static final BlockState WALL = b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL);
	private static final BlockState BOSS = b(Blocks.COPPER_GRATE.waxed().unaffected());
	private static final BlockState LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);

	@Override
	public String id() {
		return "lamp_street_avenue_double";
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
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	/** A copper spike; one in three is slightly weathered. */
	private static BlockState rod(Plot p, int u, int v) {
		return p.hash(u, 9, v, 3) == 0 ? b(Blocks.LIGHTNING_ROD.waxed().exposed()) : b(Blocks.LIGHTNING_ROD.waxed().unaffected());
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cu = 1;
		int cv = p.d() / 2;
		int armY = 4; // lanterns hang at y = 3, well above a head; the shaft is long and slim
		boolean cross = p.d() >= 3;

		// --- plinth: the darkest, heaviest part, with a gilded heart
		p.set(c, cu, 0, cv, b(Blocks.GILDED_BLACKSTONE));
		p.set(c, 0, 0, cv, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
		p.set(c, 2, 0, cv, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
		if (p.d() >= 2) {
			// front step: the plinth climbs to the shaft from the street side
			p.set(c, cu, 0, cv - 1, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH));
		}
		if (cross) {
			p.set(c, cu, 0, cv + 1, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.NORTH));
			p.set(c, 0, 0, cv - 1, p.hash(0, 0, 1, 2) == 0 ? slab(Blocks.BLACKSTONE_SLAB) : b(Blocks.POTTED_FERN));
			p.set(c, 2, 0, cv + 1, p.hash(2, 0, 2, 2) == 0 ? slab(Blocks.BLACKSTONE_SLAB) : b(Blocks.POTTED_FERN));
			p.set(c, 2, 0, cv - 1, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
			p.set(c, 0, 0, cv + 1, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
		}

		// --- shaft: dark wall first, then slim iron bars (lighter and thinner as it rises)
		p.set(c, cu, 1, cv, WALL);
		p.set(c, cu, 2, cv, WALL);
		p.set(c, cu, 3, cv, BARS);

		// --- copper boss and arms
		p.set(c, cu, armY, cv, BOSS);
		p.set(c, 0, armY, cv, BARS.setValue(BlockStateProperties.EAST, true));
		p.set(c, 2, armY, cv, BARS.setValue(BlockStateProperties.WEST, true));
		p.put(c, 0, armY - 1, cv, LANTERN, CitadelCanvas.HANGING);
		p.put(c, 2, armY - 1, cv, LANTERN, CitadelCanvas.HANGING);
		if (cross) {
			// the arms also run towards the front and the back: a four-lantern crossroads lamp
			p.set(c, cu, armY, cv - 1, BARS.setValue(BlockStateProperties.SOUTH, true));
			p.set(c, cu, armY, cv + 1, BARS.setValue(BlockStateProperties.NORTH, true));
			p.put(c, cu, armY - 1, cv - 1, LANTERN, CitadelCanvas.HANGING);
			p.put(c, cu, armY - 1, cv + 1, LANTERN, CitadelCanvas.HANGING);
		}

		// --- crown: one single copper spike over the boss
		p.set(c, cu, armY + 1, cv, rod(p, cu, cv));
	}
}
