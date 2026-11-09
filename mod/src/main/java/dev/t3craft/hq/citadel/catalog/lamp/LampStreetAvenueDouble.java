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

/**
 * Avenue lamp: the lamp of the wide streets between rings. A stepped foot (3 × 3 of deepslate or blackstone
 * brick, stairs on the sides, slabs on the corners) carries a chiselled plinth and a tall shaft (5 to 7 layers by
 * the plot's height): heavy layers of wall or cut stone, optionally wrapped in a chain, and a collar. A capital
 * with corbels (upside-down stairs) and a boss of copper or gilt carry the arms, and a copper spike crowns it.
 *
 * <ul>
 * <li><b>Three heads by seed</b>: <i>crossbar</i> (a cross arm along the avenue, lanterns hung on chains, two per
 *     side from 9 wide), <i>candelabra</i> (two arms that end in wall posts with a standing lantern each, a third lantern
 *     on a crown block between them) and <i>crossroads</i> (arms also towards the street and the back, from 3 deep).</li>
 * <li><b>Four materials by seed</b>: light stone brick on a deepslate foot, deepslate with a calcite capital, or a cherry-armed light-stone one, or
 *     deepslate with waxed copper (cut copper at the foot, copper bars, chain and lanterns) and a little weathering.</li>
 * <li><b>Useful</b>: from 2 deep a name plate (wall sign) hangs on the shaft at eye height; from
 *     5 wide a waste bin (cauldron); from 7 wide a bench facing the street. Everything stands beside the foot, so
 *     the walk stays free.</li>
 * <li><b>Big plots (11+ wide)</b>: a flanking pair. Between them, from 5 deep, a median islet: a raised
 *     bed of moss and flowers edged with stairs (walked up from any side) around a small stele with a name plate
 *     and a lantern; shallower plots take a bench instead.</li>
 * <li><b>Light</b>: every lantern is a real light and hangs or stands at 4 blocks or higher, so the street under
 *     the arms is lit and has full headroom.</li>
 * <li><b>Support</b>: every chain hangs from an arm bar, every lantern from a chain or stands on a post, and the
 *     arms spring from the boss or the capital on top of the shaft.</li>
 * </ul>
 * Size: 5–13 × 1–7 × 9+ (the shaft grows with the height, up to 7).
 */
public final class LampStreetAvenueDouble implements Variant {
	/** One material set: foot, plinth, shaft, capital, arms and lanterns. */
	private record Mat(BlockState foot, BlockState footCracked, BlockState footSlab, Block footStairs, BlockState dado,
			BlockState thick, BlockState thick2, BlockState upper, Block bars, BlockState chain, Block corbel, BlockState capital,
			BlockState boss, BlockState lanternHang, BlockState lanternStand) {
	}

	private static Mat mat(int i) {
		BlockState deepFoot = b(Blocks.DEEPSLATE_BRICKS);
		BlockState deepCracked = b(Blocks.CRACKED_DEEPSLATE_BRICKS);
		BlockState deepSlab = slab(Blocks.DEEPSLATE_BRICK_SLAB);
		switch (i) {
			case 1:
				return new Mat(b(Blocks.POLISHED_BLACKSTONE_BRICKS), b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
						slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
						b(Blocks.CHISELED_TUFF_BRICKS), b(Blocks.POLISHED_DEEPSLATE_WALL), b(Blocks.DEEPSLATE_TILE_WALL),
						b(Blocks.POLISHED_DEEPSLATE_WALL), Blocks.IRON_BARS, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y),
						Blocks.TUFF_BRICK_STAIRS, b(Blocks.CALCITE), b(Blocks.GILDED_BLACKSTONE),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
			case 2:
				return new Mat(deepFoot, deepCracked, deepSlab, Blocks.DEEPSLATE_BRICK_STAIRS,
						b(Blocks.CHISELED_COPPER.waxed().exposed()), b(Blocks.POLISHED_DEEPSLATE_WALL),
						b(Blocks.DEEPSLATE_TILE_WALL), b(Blocks.DEEPSLATE_TILE_WALL), Blocks.COPPER_BARS.waxed().exposed(),
						b(Blocks.COPPER_CHAIN.waxed().exposed()).setValue(BlockStateProperties.AXIS, Direction.Axis.Y),
						Blocks.CUT_COPPER_STAIRS.waxed().exposed(), b(Blocks.CHISELED_COPPER.waxed().exposed()),
						b(Blocks.COPPER_GRATE.waxed().exposed()),
						b(Blocks.COPPER_LANTERN.waxed().unaffected()).setValue(BlockStateProperties.HANGING, true),
						b(Blocks.COPPER_LANTERN.waxed().unaffected()).setValue(BlockStateProperties.HANGING, false));
			case 3:
				return new Mat(deepFoot, deepCracked, deepSlab, Blocks.DEEPSLATE_BRICK_STAIRS,
						b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.DIORITE_WALL), b(Blocks.ANDESITE_WALL),
						b(Blocks.DIORITE_WALL), Blocks.CHERRY_FENCE,
						b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y),
						Blocks.CHERRY_STAIRS, b(Blocks.STRIPPED_CHERRY_LOG), b(Blocks.GILDED_BLACKSTONE),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
			default:
				return new Mat(deepFoot, deepCracked, deepSlab, Blocks.DEEPSLATE_BRICK_STAIRS,
						b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.STONE_BRICK_WALL), b(Blocks.MOSSY_STONE_BRICK_WALL),
						b(Blocks.STONE_BRICK_WALL), Blocks.IRON_BARS, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y),
						Blocks.CHERRY_STAIRS, b(Blocks.POLISHED_ANDESITE), b(Blocks.COPPER_GRATE.waxed().unaffected()),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true),
						b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, false));
		}
	}

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
		return 5;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 7;
	}

	// ------------------------------------------------------------------ helpers (never write outside the plot)

	private static void s(CitadelCanvas c, Plot p, int u, int y, int v, BlockState st) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, st);
		}
	}

	private static void hang(CitadelCanvas c, Plot p, int u, int y, int v, BlockState st) {
		if (p.has(u, y, v)) {
			p.put(c, u, y, v, st, CitadelCanvas.HANGING);
		}
	}

	private static BlockState bars(Block bar, boolean n, boolean e, boolean so, boolean w) {
		return b(bar).setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.EAST, e)
				.setValue(BlockStateProperties.SOUTH, so).setValue(BlockStateProperties.WEST, w);
	}

	private static BlockState corbel(Mat m, Direction towardShaft) {
		return stairs(m.corbel(), towardShaft).setValue(StairBlock.HALF, Half.TOP);
	}

	/** Is {@code k} (distance from the shaft along an arm of half length {@code len}) a place where a lantern hangs. */
	private static boolean chainAt(int len, int k) {
		return k == len || (len >= 4 && k == len - 2);
	}

	/** A name plate: a wall sign whose face looks towards the front. */
	private static BlockState plate() {
		return b(Blocks.CHERRY_WALL_SIGN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
	}

	// ------------------------------------------------------------------ build

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		Mat m = mat(p.hash(7, 7, 7, 4));
		int type = p.hash(5, 5, 5, 3);
		int yS = Math.max(5, Math.min(7, p.h() - 4));
		int cv = d / 2;
		int bv = d >= 3 ? Math.min(3, Math.min(cv, d - 1 - cv)) : 0;
		if (type == 2 && bv < 1) {
			type = 0;
		}
		if (w >= 11) {
			int cuA = 2;
			int cuB = w - 3;
			lamp(c, p, m, type, cuA, cv, 2, bv, yS, false);
			lamp(c, p, m, type, cuB, cv, 2, bv, yS, true);
			median(c, p, m, 4, w - 5, cv);
			return;
		}
		int cu = (w - 1) / 2;
		int a = Math.min(4, Math.min(cu, w - 1 - cu));
		lamp(c, p, m, type, cu, cv, a, bv, yS, true);
		int side = p.hash(6, 6, 6, 2) == 0 ? 1 : -1;
		s(c, p, cu + side * 2, 0, cv, b(Blocks.COMPOSTER)); // a waste bin beside the foot
		bench(c, p, m, cu - side * 3, cu - side * 2, cv);
	}

	/** A bench of stairs on the row {@code v}, from {@code u1} to {@code u2}, its seat open to the street. */
	private static void bench(CitadelCanvas c, Plot p, Mat m, int u1, int u2, int v) {
		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
			s(c, p, u, 0, v, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
	}

	/**
	 * One lamp whose shaft stands at {@code (cu, cv)}. {@code a}: half length of the arm along the avenue,
	 * {@code bv}: half length of the arms towards the street and the back (the crossroads head only),
	 * {@code yS}: the top layer of the shaft. {@code sign}: hang a name plate on the shaft.
	 */
	private void lamp(CitadelCanvas c, Plot p, Mat m, int type, int cu, int cv, int a, int bv, int yS, boolean sign) {
		// --- foot: the darkest part, stepped. Stairs climb to the shaft, slabs on the corners.
		s(c, p, cu, 0, cv, p.hash(cu, 0, cv, 3) == 0 ? m.footCracked() : m.foot());
		// the sides are stairs that climb to the shaft (walkable: nothing here is sealed under the arms)
		s(c, p, cu + 1, 0, cv, stairs(m.footStairs(), Direction.WEST));
		s(c, p, cu - 1, 0, cv, stairs(m.footStairs(), Direction.EAST));
		s(c, p, cu, 0, cv - 1, stairs(m.footStairs(), Direction.SOUTH));
		s(c, p, cu, 0, cv + 1, stairs(m.footStairs(), Direction.NORTH));
		for (int du = -1; du <= 1; du += 2) {
			for (int dv = -1; dv <= 1; dv += 2) {
				s(c, p, cu + du, 0, cv + dv, m.footSlab());
			}
		}
		// --- plinth, then the shaft: two heavy layers, then slim bars or a chain
		s(c, p, cu, 1, cv, m.dado());
		// a heavy wall shaft (or, by seed, a chain-wrapped one above the first layer), a collar at the top
		boolean chainShaft = p.hash(4, 4, 4, 2) == 0;
		for (int y = 2; y < yS; y++) {
			if (chainShaft && y >= 3) {
				s(c, p, cu, y, cv, m.chain());
			} else if (y >= 4) {
				s(c, p, cu, y, cv, m.upper());
			} else {
				s(c, p, cu, y, cv, p.hash(cu, y, cv, 3) == 0 ? m.thick2() : m.thick());
			}
		}
		s(c, p, cu, yS, cv, type == 1 ? m.capital() : m.dado());
		// --- the name plate of the avenue, at eye height on the front of the shaft
		if (sign && p.d() >= 2) {
			p.put(c, cu, 3, cv - 1, plate(), CitadelCanvas.DECOR);
		}
		if (type == 1) {
			candelabra(c, p, m, cu, cv, a, yS);
		} else {
			crossbar(c, p, m, type == 2, cu, cv, a, bv, yS);
		}
	}

	/** Cross arm (and, for the crossroads head, the arms to the street and the back) with hung lanterns. */
	private void crossbar(CitadelCanvas c, Plot p, Mat m, boolean crossroads, int cu, int cv, int a, int bv, int yS) {
		int yCap = yS + 1;
		int yArm = yS + 2;
		s(c, p, cu, yCap, cv, m.capital());
		s(c, p, cu, yArm, cv, m.boss());
		// corbels under the arms, where no chain hangs
		if (a >= 2 && !chainAt(a, 1)) {
			s(c, p, cu + 1, yCap, cv, corbel(m, Direction.WEST));
			s(c, p, cu - 1, yCap, cv, corbel(m, Direction.EAST));
		}
		for (int k = 1; k <= a; k++) {
			s(c, p, cu + k, yArm, cv, bars(m.bars(), false, k < a, false, true));
			s(c, p, cu - k, yArm, cv, bars(m.bars(), false, true, false, k < a));
			if (chainAt(a, k)) {
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					hang(c, p, cu + sgn * k, yCap, cv, m.chain());
					hang(c, p, cu + sgn * k, yS, cv, m.chain());
					hang(c, p, cu + sgn * k, yS - 1, cv, m.lanternHang());
				}
			}
		}
		if (crossroads) {
			if (bv >= 2 && !chainAt(bv, 1)) {
				s(c, p, cu, yCap, cv - 1, corbel(m, Direction.SOUTH));
				s(c, p, cu, yCap, cv + 1, corbel(m, Direction.NORTH));
			}
			for (int k = 1; k <= bv; k++) {
				s(c, p, cu, yArm, cv - k, bars(m.bars(), k < bv, false, true, false));
				s(c, p, cu, yArm, cv + k, bars(m.bars(), true, false, k < bv, false));
				if (chainAt(bv, k)) {
					for (int sgn = -1; sgn <= 1; sgn += 2) {
						hang(c, p, cu, yCap, cv + sgn * k, m.chain());
						hang(c, p, cu, yS, cv + sgn * k, m.chain());
						hang(c, p, cu, yS - 1, cv + sgn * k, m.lanternHang());
					}
				}
			}
		}
		s(c, p, cu, yArm + 1, cv, m.lanternStand());
		s(c, p, cu, yArm + 2, cv, rod());
	}

	/** Two arms that end in posts with a standing lantern, and a spike between them. */
	private void candelabra(CitadelCanvas c, Plot p, Mat m, int cu, int cv, int a, int yS) {
		int yArm = yS + 1;
		s(c, p, cu, yArm, cv, m.boss());
		for (int k = 1; k <= a; k++) {
			s(c, p, cu + k, yArm, cv, bars(m.bars(), false, k < a, false, true));
			s(c, p, cu - k, yArm, cv, bars(m.bars(), false, true, false, k < a));
			if (k == 1) {
				s(c, p, cu + 1, yS, cv, corbel(m, Direction.WEST));
				s(c, p, cu - 1, yS, cv, corbel(m, Direction.EAST));
			}
			if (chainAt(a, k)) {
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					s(c, p, cu + sgn * k, yArm + 1, cv, m.upper());
					s(c, p, cu + sgn * k, yArm + 2, cv, m.lanternStand());
				}
			}
		}
		s(c, p, cu, yArm + 1, cv, m.capital());
		s(c, p, cu, yArm + 2, cv, m.lanternStand());
		s(c, p, cu, yArm + 3, cv, rod()); // only when the plot is tall enough
	}

	private static BlockState rod() {
		return b(Blocks.LIGHTNING_ROD.waxed().unaffected()).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	// ------------------------------------------------------------------ the median between a pair

	/** The gap {@code u1..u2} between the two lamps: a bin, a bench, or (11+ wide, 5+ deep) a raised islet. */
	private void median(CitadelCanvas c, Plot p, Mat m, int u1, int u2, int cv) {
		int g = u2 - u1 + 1;
		int d = p.d();
		if (g == 1) {
			s(c, p, u1, 0, cv, b(Blocks.COMPOSTER));
			return;
		}
		if (g < 3 || d < 5) {
			int len = Math.min(g, 3);
			int start = u1 + (g - len) / 2;
			bench(c, p, m, start, start + len - 1, cv);
			return;
		}
		int iv0 = 1;
		int iv1 = d - 2;
		int um = (u1 + u2) / 2;
		int vm = d / 2;
		for (int v = iv0; v <= iv1; v++) {
			for (int u = u1; u <= u2; u++) {
				boolean eW = u == u1;
				boolean eE = u == u2;
				boolean eN = v == iv0;
				boolean eS = v == iv1;
				if ((eW || eE) && (eN || eS)) {
					s(c, p, u, 0, v, slab(Blocks.STONE_BRICK_SLAB));
				} else if (eN) {
					s(c, p, u, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
				} else if (eS) {
					s(c, p, u, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
				} else if (eW) {
					s(c, p, u, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
				} else if (eE) {
					s(c, p, u, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
				} else if (u == um && v == vm) {
					// the stele: it marks the way, a name plate on its face and a lantern on top
					s(c, p, u, 0, v, m.dado());
					s(c, p, u, 1, v, m.thick());
					s(c, p, u, 2, v, m.thick());
					s(c, p, u, 3, v, m.lanternStand());
					p.put(c, u, 2, v - 1, plate(), CitadelCanvas.DECOR);
				} else {
					s(c, p, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.GRASS_BLOCK) : b(Blocks.MOSS_BLOCK));
					s(c, p, u, 1, v, plant(p, u, v));
				}
			}
		}
	}

	private static BlockState plant(Plot p, int u, int v) {
		return switch (p.hash(u, 1, v, 6)) {
			case 0 -> b(Blocks.PINK_TULIP);
			case 1 -> b(Blocks.ALLIUM);
			case 2 -> b(Blocks.CORNFLOWER);
			case 3 -> b(Blocks.FERN);
			case 4 -> b(Blocks.FLOWERING_AZALEA);
			default -> b(Blocks.PINK_PETALS);
		};
	}
}
