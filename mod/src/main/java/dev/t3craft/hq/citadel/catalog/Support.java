package dev.t3craft.hq.citadel.catalog;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * fix/settle-sources: helpers so a piece is born face-connected to its support (what {@code CitadelSettle} and
 * {@code PlanReport}'s FLOATING test ask for), instead of leaving the settle pass to drop the loose part.
 *
 * <p>The usual case is a stair roof: every ring one block in and one up touches the ring below only at an edge, so
 * from the second ring up each ring is a group of its own. {@link #roofUnder} puts a full block of the roof's own
 * material in the cell under a ring cell (the cell just inside the ring below), which touches that ring on a
 * face. It only shows from under the roof, and a roof under a roof is how a builder thickens one.
 */
public final class Support {
	private Support() {
	}

	/**
	 * The full block that goes with a stair, slab or wall block: {@code cherry_stairs} to {@code cherry_planks},
	 * {@code deepslate_tile_stairs} to {@code deepslate_tiles}, {@code bamboo_mosaic_stairs} to {@code bamboo_mosaic}.
	 * Anything else with no full block of its name gives dark oak planks.
	 */
	public static BlockState full(Block roof) {
		String name = BuiltInRegistries.BLOCK.getKey(roof).getPath();
		String base = name;
		for (String suffix : new String[] {"_stairs", "_slab", "_wall"}) {
			if (base.endsWith(suffix)) {
				base = base.substring(0, base.length() - suffix.length());
				break;
			}
		}
		for (String candidate : new String[] {base + "_planks", base, base + "s", base + "_block"}) {
			Identifier id = Identifier.fromNamespaceAndPath("minecraft", candidate);
			if (BuiltInRegistries.BLOCK.containsKey(id)) {
				return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
			}
		}
		return Blocks.DARK_OAK_PLANKS.defaultBlockState();
	}

	/**
	 * Fills local {@code (u, y, v)} with {@code filler} when nothing is there yet (air or nothing painted) and it is
	 * inside the plot. True when the cell holds something afterwards.
	 */
	public static boolean fillIfFree(CitadelCanvas c, Plot p, int u, int y, int v, BlockState filler) {
		if (!p.has(u, y, v)) {
			return false;
		}
		BlockState at = p.at(c, u, y, v);
		if (at != null && !at.isAir()) {
			return true;
		}
		p.set(c, u, y, v, filler);
		return true;
	}

	/** Whether local {@code (u, y, v)} holds a block already (anything but air). */
	public static boolean present(CitadelCanvas c, Plot p, int u, int y, int v) {
		BlockState at = p.at(c, u, y, v);
		return at != null && !at.isAir();
	}

	/**
	 * A roof cell of {@code roof} that sits one block in and one up from the ring below gets a full block of the same
	 * material under it (if that cell is free), so it touches the ring below on a face.
	 */
	public static void roofUnder(CitadelCanvas c, Plot p, int u, int y, int v, Block roof) {
		fillIfFree(c, p, u, y - 1, v, full(roof));
	}
}
