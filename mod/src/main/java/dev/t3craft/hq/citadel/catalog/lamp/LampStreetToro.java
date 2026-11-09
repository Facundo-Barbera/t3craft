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
 * Japanese stone lantern (tōrō): a stepped mossy base, a slim post, a wide plate, a one-block light box
 * that glows, a dark hat roof whose corners curl up, and a small finial. The silhouette is the point:
 * wide → thin → wide → glowing neck → wide hat → point. Because the light box is a single block, the hat
 * and the plate really overhang it.
 *
 * <ul>
 * <li><b>Gradient</b>: mossy cobblestone and cracked stone at the foot, stone bricks in the post, light
 *     polished andesite and smooth stone in the plate, a warm froglight in the neck and a dark deepslate
 *     tile hat on top (stone that has weathered).</li>
 * <li><b>Depth</b>: the base steps in, the plate and the hat overhang, the hat corners are upside-down
 *     slabs so the eaves curl up.</li>
 * <li><b>Transition to the ground</b>: moss carpet and azalea pots in the base corners.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Height 6 gets the full tōrō; lower plots drop the post, then the finial.
 */
public final class LampStreetToro implements Variant {
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
		int cu = 1;
		int cv = 1;
		boolean post = p.h() >= 5;
		boolean finial = p.h() >= 6;
		int y = 0;
		int[][] sides = {{cu, cv - 1, 0}, {cu, cv + 1, 1}, {cu - 1, cv, 2}, {cu + 1, cv, 3}};
		Direction[] toCentre = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
		int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

		// --- base: a stepped square, darker and mossier than the rest
		p.set(c, cu, y, cv, b(p.hash(cu, 0, cv, 2) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.CHISELED_STONE_BRICKS));
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(p.hash(s[0], 0, s[1], 3) == 0 ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLESTONE_STAIRS, toCentre[s[2]]));
		}
		int pot = p.hash(0, 0, 0, 4);
		for (int i = 0; i < 4; i++) {
			BlockState s = i == pot ? b(p.hash(1, 0, 3, 2) == 0 ? Blocks.POTTED_AZALEA : Blocks.POTTED_CHERRY_SAPLING)
					: i == (pot + 2) % 4 ? slab(Blocks.MOSSY_COBBLESTONE_SLAB)
							: b(Blocks.MOSS_CARPET);
			p.set(c, corners[i][0], y, corners[i][1], s);
		}
		y++;

		// --- post: a thin stone column
		if (post) {
			p.set(c, cu, y++, cv, b(Blocks.STONE_BRICK_WALL));
		}

		// --- plate (nakadai): light stone, a plus of upside-down stairs round a polished heart
		p.set(c, cu, y, cv, b(Blocks.POLISHED_ANDESITE));
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(Blocks.POLISHED_ANDESITE_STAIRS, toCentre[s[2]]).setValue(StairBlock.HALF, Half.TOP));
		}
		y++;

		// --- light box (hibukuro): a single glowing block, nothing around it
		p.set(c, cu, y, cv, b(Blocks.OCHRE_FROGLIGHT));
		y++;

		// --- hat (kasa): dark tile stairs climbing to the middle, corners curl up (upside-down slabs)
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(Blocks.DEEPSLATE_TILE_STAIRS, toCentre[s[2]]));
		}
		BlockState tip = slab(Blocks.DEEPSLATE_TILE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		for (int[] k : corners) {
			p.set(c, k[0], y, k[1], tip);
			if (finial) {
				// the eaves really curl up: a second slab on top of each corner
				p.set(c, k[0], y + 1, k[1], slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
		p.set(c, cu, y, cv, b(Blocks.POLISHED_DEEPSLATE));
		y++;

		// --- finial (hōju): a pale jewel, a candle on the crown
		if (finial) {
			p.set(c, cu, y, cv, b(Blocks.WHITE_CANDLE));
		} else if (y < p.h()) {
			p.set(c, cu, y, cv, slab(Blocks.SMOOTH_STONE_SLAB));
		}
	}
}
