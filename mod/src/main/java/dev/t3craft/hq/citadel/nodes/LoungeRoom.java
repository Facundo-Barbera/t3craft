package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

/**
 * The lounge: a sunken amphitheatre of radius 7. Half steps ({@code k = round(7 - d)}, 0 = rim .. 6 = bottom)
 * go down to {@code y = -3}: moss on the even rings, deepslate tiles with a slab on the odd ones, an accent
 * floor at the bottom. It removes the ground cell the zone painted first.
 *
 * <p>On the second ring from the rim ({@code k = 2}) the four diagonal sectors are shallow pools of water
 * (a footing under each, full blocks or slabs on every side, so the water cannot run); the axes stay moss
 * and are the paths down. Azalea bushes stand on the rim between the paths. The furniture (hearth pavilion,
 * sofas, trees, lamp posts) is {@link NodeDecor}'s.
 */
public final class LoungeRoom {
	/** Ring {@code k} of the pools, and the cells within this of an axis that stay paths. */
	private static final int POOL_RING = 2;
	private static final int PATH_HALF = 1;

	private LoungeRoom() {
	}

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}, {@code d} from the centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int k = NodeDecor.loungeStep(d);
		long g = BlockPos.asLong(x, 0, z);
		c.cells.remove(g);
		c.layers.remove(g);
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		c.set(x, solid, z, k % 2 == 0 ? (k == 6 ? c.accent() : MOSS) : FOOT);
		if (k % 2 == 1) {
			c.set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			c.set(x, y, z, AIR);
		}
		if (k == POOL_RING && Math.min(Math.abs(dx), Math.abs(dz)) > PATH_HALF) {
			c.set(x, solid - 1, z, FOOT);
			c.set(x, solid, z, WATER);
		}
		if (Math.abs(dx) == 5 && Math.abs(dz) == 4) {
			c.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			c.set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}
}
