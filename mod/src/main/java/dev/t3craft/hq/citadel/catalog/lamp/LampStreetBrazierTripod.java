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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Brazier on a tripod: a shallow bowl of polished deepslate with a thin copper rim and an open fire,
 * carried by three dark legs that splay out to the left, the right and the back and lean in to a dark
 * collar under the bowl. The front stays open, so the fire and the empty space under the bowl read.
 *
 * <ul>
 * <li><b>Silhouette</b>: thin legs leaning in → a dark knot → a bowl that tapers to the knot and widens to
 *     the rim. The legs lean with a stair at their top (the high step towards the knot).</li>
 * <li><b>Contrast</b>: near-black nether-brick legs with blackstone knees, dark polished deepslate bowl, a thin copper rim; the flame is the brightest thing on it.</li>
 * <li><b>Safe fire</b>: a campfire (it never spreads) with nothing flammable round it.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. The legs are one to three blocks before they lean in.
 */
public final class LampStreetBrazierTripod implements Variant {
	/** The three leg feet (left, right, back) and the direction each one leans in, towards the centre. */
	private static final int[][] LEGS = {{0, 1}, {2, 1}, {1, 2}};
	private static final Direction[] LEAN = {Direction.EAST, Direction.WEST, Direction.NORTH};
	private static final int[][] SIDES = {{1, 0}, {1, 2}, {0, 1}, {2, 1}};
	private static final Direction[] IN = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
	private static final int[][] CORNERS = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

	@Override
	public String id() {
		return "lamp_street_brazier_tripod";
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
		return 3;
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
		int dish = Math.min(p.h() - 2, 4); // the bowl; the fire and the rim sit on it
		int knot = dish - 1; // where the legs meet, under the bowl
		int lean = knot - 1; // the leaning top of each leg
		boolean soul = p.hash(0, 0, 0, 4) == 0;

		// --- legs: thin dark fence posts, then a stair whose high step leans in to the knot
		for (int i = 0; i < 3; i++) {
			int u = LEGS[i][0];
			int v = LEGS[i][1];
			for (int y = 0; y < lean; y++) {
				p.set(c, u, y, v, b(Blocks.NETHER_BRICK_FENCE)); // thin dark iron-like legs
			}
			// the knee: a stair leaning in to the knot (on the lowest plots the leg is just a thin post)
			p.set(c, u, lean, v, lean == 0 ? b(Blocks.NETHER_BRICK_FENCE) : stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, LEAN[i]));
		}
		// --- the knot: a slim dark collar where the three legs meet, ash on the ground under it
		p.set(c, 1, knot, 1, b(Blocks.POLISHED_BLACKSTONE_WALL));
		if (knot >= 2) {
			p.set(c, 1, 0, 1, slab(Blocks.BLACKSTONE_SLAB));
		}

		// --- bowl: upside-down stairs taper from the rim down to the knot; upper slabs close the corners
		p.set(c, 1, dish, 1, b(Blocks.POLISHED_DEEPSLATE));
		for (int i = 0; i < 4; i++) {
			int[] s = SIDES[i];
			p.set(c, s[0], dish, s[1], stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, IN[i]).setValue(StairBlock.HALF, Half.TOP));
		}
		BlockState corner = slab(Blocks.POLISHED_DEEPSLATE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		for (int[] k : CORNERS) {
			p.set(c, k[0], dish, k[1], corner);
		}

		// --- rim: a thin ring of closed copper trapdoors (a 3-pixel plate) on all eight rim cells: a bright edge
		// on the dark bowl, with the fire standing well above it
		BlockState rim = b(Blocks.COPPER_TRAPDOOR.waxed().unaffected());
		for (int[] s : SIDES) {
			p.set(c, s[0], dish + 1, s[1], rim);
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], dish + 1, k[1], rim);
		}

		// --- the fire, in the open
		p.set(c, 1, dish + 1, 1, b(soul ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, true));
	}
}
