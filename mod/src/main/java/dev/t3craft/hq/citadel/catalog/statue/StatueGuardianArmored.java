package dev.t3craft.hq.citadel.catalog.statue;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Concept: an armoured guardian standing watch on a stepped pedestal. A blocky knight, chunky like a
 * golem: two blackstone-wall legs under a slab tasset, an iron cuirass with blackstone pauldrons, a
 * chiselled blackstone helm and a copper plume. A pink tabard (a wall banner) hangs over the chest at the
 * front and a magenta cape at the back. On wide plots a copper-tipped cherry spear stands at the right
 * hand and a shield banner hangs at the left. The pedestal climbs towards the figure in steps.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate tiles (cracked by hash) at the foot,
 *     polished deepslate, then stone bricks (mossy or cracked by hash), and a calcite top course under the
 *     feet; the figure itself is dark blackstone and iron, with gold and copper accents on top.</li>
 * <li><b>Depth</b>: every ring of the pedestal steps in towards the figure (stairs climbing to it, low
 *     slabs at the corners); the pauldrons are upside-down stairs that overhang the arms; the tabard
 *     and cape stand off the body on both faces.</li>
 * <li><b>Silhouette</b>: a figure on a pyramid, with the spear on wide plots breaking the symmetry
 *     like a real statue. Odd frontage gives a three-wide figure with pauldrons; even frontage gives a
 *     two-wide figure (pauldrons from 4 wide).</li>
 * <li><b>Finish</b>: lanterns on the two front corners of the lowest step on plots 4+ wide.</li>
 * </ul>
 * The figure always stands on the row {@code d / 2}; the pedestal is 2 high (3 with h ≥ 8 when there
 * is room for a step in front) and the figure needs 5 above it.
 *
 * <p>Size: 2–5 × 2–5 × 7+ (the hub asks for 2 × 2 × 8).
 */
public final class StatueGuardianArmored implements Variant {
	private static final DyeColor[] TABARD = {DyeColor.PINK, DyeColor.WHITE, DyeColor.LIGHT_GRAY};
	private static final DyeColor[] CAPE = {DyeColor.MAGENTA, DyeColor.RED, DyeColor.PINK};

	@Override
	public String id() {
		return "statue_guardian_armored";
	}

	@Override
	public Family family() {
		return Family.STATUE;
	}

	@Override
	public int minW() {
		return 2;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 7;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		boolean odd = (w & 1) == 1;
		int cl = (w - 1) / 2; // centre column(s): cl..cr (one for odd w, two for even w)
		int cr = w / 2;
		int fl = odd && w >= 3 ? cl - 1 : cl; // the figure's span: three wide on odd frontage, two on even
		int fr = odd && w >= 3 ? cr + 1 : cr;
		int vf = d / 2; // the figure's row
		int ped = p.h() >= 8 && vf >= 2 ? 3 : 2; // pedestal height (the figure stands on y = ped)

		pedestal(c, p, fl, fr, vf, ped);
		figure(c, p, odd, cl, cr, fl, fr, vf, ped);
	}

	// ------------------------------------------------------------------ pedestal

	/** Steps that climb from every side towards the figure's footprint ({@code fl..fr × vf}). */
	private static void pedestal(CitadelCanvas c, Plot p, int fl, int fr, int vf, int ped) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int du = u < fl ? fl - u : u > fr ? u - fr : 0;
				int dv = Math.abs(v - vf);
				int k = Math.max(du, dv);
				int top = ped - k; // y of this cell's step (a stair or slab), or the full height for k = 0
				if (k == 0) {
					for (int y = 0; y < ped; y++) {
						p.set(c, u, y, v, body(p, u, y, v, y == ped - 1));
					}
					continue;
				}
				if (top < 0) {
					continue;
				}
				for (int y = 0; y < top; y++) {
					p.set(c, u, y, v, body(p, u, y, v, false));
				}
				boolean corner = du == k && dv == k;
				boolean frontCorner = v == 0 && (u == 0 || u == w - 1) && w >= 4 && du > 0;
				if (frontCorner) {
					p.set(c, u, top, v, b(Blocks.LANTERN)); // [finish] lanterns flank the front
				} else if (corner) {
					p.set(c, u, top, v, slab(top == 0 ? Blocks.DEEPSLATE_TILE_SLAB : Blocks.POLISHED_DEEPSLATE_SLAB));
				} else {
					// [depth] a stair climbing towards the figure
					Direction up = dv == k ? (v < vf ? Direction.SOUTH : Direction.NORTH) : (u < fl ? Direction.EAST : Direction.WEST);
					p.set(c, u, top, v, stairs(stepBlock(top), up));
				}
			}
		}
	}

	/** [palette] dark heavy foot, polished middle, stone bricks above, a calcite course under the feet. */
	private static BlockState body(Plot p, int u, int y, int v, boolean cap) {
		if (cap) {
			return p.hash(u, y, v, 4) == 0 ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.CALCITE);
		}
		int hsh = p.hash(u, y, v, 5);
		return switch (y) {
			case 0 -> hsh == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
			case 1 -> b(Blocks.POLISHED_DEEPSLATE);
			default -> hsh == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : hsh == 1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
		};
	}

	private static net.minecraft.world.level.block.Block stepBlock(int y) {
		return switch (y) {
			case 0 -> Blocks.DEEPSLATE_TILE_STAIRS;
			case 1 -> Blocks.POLISHED_DEEPSLATE_STAIRS;
			default -> Blocks.STONE_BRICK_STAIRS;
		};
	}

	// ------------------------------------------------------------------ figure

	private static void figure(CitadelCanvas c, Plot p, boolean odd, int cl, int cr, int fl, int fr, int vf, int ped) {
		int w = p.w();
		int d = p.d();
		int legs = ped;
		int waist = ped + 1;
		int chest = ped + 2;
		int head = ped + 3;
		int crest = ped + 4;
		BlockState leg = b(Blocks.POLISHED_BLACKSTONE_WALL); // a wall post (no sides): a slim greave
		BlockState tasset = slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		BlockState cuirass = b(Blocks.IRON_BLOCK);
		BlockState helm = b(Blocks.CHISELED_POLISHED_BLACKSTONE);

		// legs: at the two ends of the figure's span; on odd frontage the middle stays open under the tasset
		p.set(c, fl, legs, vf, leg);
		p.set(c, fr, legs, vf, leg);
		for (int u = fl; u <= fr; u++) {
			p.set(c, u, waist, vf, u == fl || u == fr ? leg : tasset);
		}
		if (!odd || w < 3) {
			// two-wide figure: the waist is a belt of top slabs over both legs
			p.set(c, fl, waist, vf, tasset.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
			p.set(c, fr, waist, vf, tasset.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
		}

		// chest: iron cuirass on the centre column(s), pauldrons beside it
		for (int u = cl; u <= cr; u++) {
			p.set(c, u, chest, vf, cuirass);
		}
		BlockState pauldron = stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
		if (cl - 1 >= 0) {
			p.set(c, cl - 1, chest, vf, pauldron.setValue(StairBlock.FACING, Direction.EAST)); // [depth] overhangs the arm
		}
		if (cr + 1 < w) {
			p.set(c, cr + 1, chest, vf, pauldron.setValue(StairBlock.FACING, Direction.WEST));
		}
		if (!odd && w >= 4) {
			// two-wide figure with room beside it: arms hang under the pauldrons
			p.set(c, cl - 1, waist, vf, leg);
			p.set(c, cr + 1, waist, vf, leg);
		}

		// helm and copper plume
		for (int u = cl; u <= cr; u++) {
			p.set(c, u, head, vf, helm);
			if (cl == cr) {
				p.set(c, u, crest, vf, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			} else {
				// a two-wide head: a peaked helm (two stairs rising to the middle), not two rods that read as antennae
				p.set(c, u, crest, vf, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, u == cl ? Direction.EAST : Direction.WEST));
			}
		}

		// tabard at the front, cape at the back: wall banners hanging on the chest
		DyeColor tabard = TABARD[p.hash(0, 1, 0, TABARD.length)];
		DyeColor cape = CAPE[p.hash(0, 2, 0, CAPE.length)];
		if (vf >= 1) {
			for (int u = cl; u <= cr; u++) {
				p.put(c, u, chest, vf - 1, b(Blocks.WALL_BANNER.pick(tabard)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR);
			}
		}
		if (vf + 1 < d) {
			for (int u = cl; u <= cr; u++) {
				p.put(c, u, chest, vf + 1, b(Blocks.WALL_BANNER.pick(cape)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
						CitadelCanvas.DECOR);
			}
		}

		// wide odd plots: a spear at the right hand, a shield at the left
		if (odd && w >= 5) {
			int su = fr + 1;
			for (int y = legs; y < crest; y++) {
				p.set(c, su, y, vf, b(Blocks.CHERRY_FENCE)); // a lone post: no sides set
			}
			p.set(c, su, crest, vf, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState()); // the copper spearhead
			p.put(c, fl - 1, chest, vf, b(Blocks.WALL_BANNER.pick(DyeColor.WHITE)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST),
					CitadelCanvas.DECOR); // the shield, hung on the left pauldron
		}
	}
}
