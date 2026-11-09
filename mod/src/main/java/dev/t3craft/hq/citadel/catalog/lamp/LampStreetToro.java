package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Kasuga-style stone lantern (tōrō), all in stone: a stepped dark foot (kiso), a slim shaft (sao), a flared
 * platform (chūdai), a light box (hibukuro) whose four corner posts frame a glowing heart with open windows
 * on every side, a hat (kasa) that slopes down from a crown and lifts at its four corners, and a jewel
 * finial (hōju).
 *
 * <ul>
 * <li><b>Silhouette</b>: wide → slim → wide → open cage → wide hat → point. Every level has a different
 *     width, so it reads as a tōrō from far away, not as a stack of cubes.</li>
 * <li><b>Gradient</b>: deepslate foot, stone-brick shaft and platform, andesite cage, polished andesite hat
 *     with pale diorite tips (the curled-up corners catch the light), a diorite-wall jewel.</li>
 * <li><b>Depth</b>: the platform flares on upside-down stairs; the hat sides are stairs sloping out and the
 *     corners upper slabs, so the eave line rises at each corner (warabi-te).</li>
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

		// --- kiso: a dark stepped foot. Stairs step down from a polished heart; the corners are moss and a pot
		p.set(c, 1, y, 1, b(Blocks.POLISHED_DEEPSLATE));
		for (int i = 0; i < 4; i++) {
			int[] s = SIDES[i];
			Block st = p.hash(s[0], y, s[1], 3) == 0 ? Blocks.COBBLED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_BRICK_STAIRS;
			p.set(c, s[0], y, s[1], stairs(st, IN[i]));
		}
		int pot = p.hash(0, 0, 0, 4);
		for (int i = 0; i < 4; i++) {
			int[] k = CORNERS[i];
			BlockState s = i == pot ? b(p.hash(1, 0, 3, 2) == 0 ? Blocks.POTTED_FERN : Blocks.POTTED_AZALEA)
					: i == (pot + 2) % 4 ? slab(Blocks.MOSSY_COBBLESTONE_SLAB)
							: b(Blocks.MOSS_CARPET);
			p.set(c, k[0], y, k[1], s);
		}
		y++;

		// --- sao: a slim round shaft, so the lantern stands clear of its foot
		if (shaft) {
			p.set(c, 1, y++, 1, b(p.hash(1, y, 1, 3) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL));
		}

		// --- chūdai: a 3 × 3 platform that flares out on upside-down stairs, upper slabs at the corners
		p.set(c, 1, y, 1, b(Blocks.CHISELED_STONE_BRICKS));
		for (int i = 0; i < 4; i++) {
			int[] s = SIDES[i];
			p.set(c, s[0], y, s[1], top(stairs(Blocks.STONE_BRICK_STAIRS, IN[i])));
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], topSlab(Blocks.STONE_BRICK_SLAB));
		}
		y++;

		// --- hibukuro: four slim posts at the corners frame a glowing heart; every side is an open window
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], b(Blocks.ANDESITE_WALL));
		}
		p.set(c, 1, y, 1, b(Blocks.OCHRE_FROGLIGHT));
		y++;

		// --- kasa: a crown block, stairs sloping down to the eaves; the corners are pale stairs turned outward,
		// so they sit on the posts and their high step lifts the eave at each corner
		p.set(c, 1, y, 1, b(Blocks.POLISHED_ANDESITE));
		for (int i = 0; i < 4; i++) {
			int[] s = SIDES[i];
			p.set(c, s[0], y, s[1], stairs(Blocks.POLISHED_ANDESITE_STAIRS, IN[i]));
		}
		for (int[] k : CORNERS) {
			p.set(c, k[0], y, k[1], stairs(Blocks.POLISHED_DIORITE_STAIRS, k[0] == 0 ? Direction.WEST : Direction.EAST));
		}
		y++;

		// --- hōju: the jewel on the crown
		if (finial) {
			p.set(c, 1, y, 1, b(Blocks.DIORITE_WALL));
		}
	}

	private static BlockState top(BlockState stair) {
		return stair.setValue(StairBlock.HALF, Half.TOP);
	}

	private static BlockState topSlab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}
}
