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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Kasuga-style stone lantern (tōrō), all in stone: a low dark plinth (kiso), a slim shaft (sao), a flared
 * platform (chūdai), a light chamber (hibukuro) of four corner posts round a glowing heart, open on every
 * face, a thin wide hat (kasa) of slabs round a raised crown, and a jewel finial (hōju).
 *
 * <ul>
 * <li><b>Silhouette</b>: low plinth → slim shaft → flared platform → open lit chamber → thin hat → crown →
 *     point. Heavy pieces are half blocks (plinth, hat), so the lantern reads light, not as a stack of cubes.</li>
 * <li><b>Gradient</b>: deepslate plinth, stone-brick shaft and platform, stone-brick posts round a warm light,
 *     a polished andesite hat, a pale diorite-wall jewel.</li>
 * <li><b>Depth</b>: the platform flares on upside-down stairs; the chamber is open between its posts; the
 *     crown stands half a block above the hat's slab eave.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Height 6 is the full tōrō; 5 drops the finial; 4 also drops the shaft.
 */
public final class LampStreetToro implements Variant {
	private static final int[][] SIDES = {{1, 0}, {1, 2}, {0, 1}, {2, 1}};
	/** The direction from each side cell to the centre. */
	private static final Direction[] IN = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
	private static final int[][] CORNERS = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

	@Override
	public String id() {
		return "lamp_street_toro";
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
		boolean shaft = p.h() >= 5;
		boolean finial = p.h() >= 6;
		int y = 0;

		// --- kiso: a low plinth: a polished heart in a ring of deepslate slabs, one corner a potted fern
		p.set(c, 1, y, 1, b(Blocks.POLISHED_DEEPSLATE));
		for (int[] k : SIDES) {
			p.set(c, k[0], y, k[1], slab(p.hash(k[0], 0, k[1], 3) == 0 ? Blocks.COBBLED_DEEPSLATE_SLAB : Blocks.DEEPSLATE_BRICK_SLAB));
		}
		int pot = p.hash(0, 0, 0, 4);
		for (int i = 0; i < 4; i++) {
			int[] k = CORNERS[i];
			p.set(c, k[0], y, k[1], i == pot ? b(Blocks.POTTED_FERN) : slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		y++;

		// --- sao: a slim round shaft, so the lantern stands clear of its foot
		if (shaft) {
			p.set(c, 1, y++, 1, b(p.hash(1, 1, 1, 3) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL));
		}

		// --- chūdai: a square platform flaring on upside-down stairs, upper slabs at the corners
		p.set(c, 1, y, 1, b(Blocks.CHISELED_STONE_BRICKS));
		for (int i = 0; i < 4; i++) {
			p.set(c, SIDES[i][0], y, SIDES[i][1], stairs(Blocks.STONE_BRICK_STAIRS, IN[i]).setValue(StairBlock.HALF, Half.TOP));
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], slab(Blocks.STONE_BRICK_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		}
		y++;

		// --- hibukuro: a chamber of four stone corner posts round a glowing heart, open on all four faces
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], b(Blocks.STONE_BRICK_WALL));
		}
		p.set(c, 1, y, 1, b(Blocks.OCHRE_FROGLIGHT));
		y++;

		// --- kasa: a thin square hat of slabs (a wide, light eave) round a raised crown block
		p.set(c, 1, y, 1, b(Blocks.POLISHED_ANDESITE));
		for (int[] k : SIDES) {
			p.set(c, k[0], y, k[1], slab(Blocks.POLISHED_ANDESITE_SLAB));
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], slab(Blocks.POLISHED_ANDESITE_SLAB));
		}
		y++;

		// --- hōju: the jewel on the crown
		if (finial) {
			p.set(c, 1, y, 1, b(Blocks.DIORITE_WALL));
		}
	}
}
