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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Concept: a monumental lantern at the top of a flight of steps, like the beacon of a harbour square.
 * A short stairway climbs from the front to a stone platform; on it a square column (deepslate plinth,
 * stone-brick shaft with a chiselled band, calcite capital) holds a copper lamp head: a bowl of
 * upside-down copper stairs, a pearlescent light behind pink glass panes, copper lanterns on the four
 * corners of the bowl, a sloping copper cap and a gold knob with a copper rod. On plots 4+ wide the
 * steps run between two cheek walls with a lantern on each, and the back corners hold potted azaleas.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate bricks and tiles for the steps and platform,
 *     a calcite rim on the platform, stone bricks (mossy or cracked by hash) in the shaft, calcite at the
 *     capital, copper and gold for the lamp.</li>
 * <li><b>Depth</b>: the bowl corbels out over the capital, the cap slopes back in, the steps recede
 *     into the platform, and the cheek walls frame the stair.</li>
 * <li><b>Silhouette</b>: a column with a wide head, entered from one side only; unlike the obelisk it
 *     thickens at the top and unlike the guardian it is approached up the steps.</li>
 * <li><b>Light</b>: the visible light is decorative (a froglight behind glass, lanterns), as the art
 *     direction asks; nothing glows in the floor.</li>
 * </ul>
 * The column takes the centre column(s) of the platform (one or two per axis, by parity), so the head
 * is three or four wide; on narrower plots the head stops at the plot's sides (nothing is written outside).
 * The stair has two steps from h ≥ 10, one below.
 *
 * <p>Size: 2–5 × 2–5 × 8+ (the hub asks for 2 × 2 × 8).
 */
public final class StatueMonumentalLantern implements Variant {
	private static final BlockState COPPER = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();

	@Override
	public String id() {
		return "statue_monumental_lantern";
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
		return 8;
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
		int steps = Math.min(d - 1, p.h() >= 10 ? 2 : 1); // rows 0..steps-1: the stair; the rest: the platform at y = steps
		boolean cheeks = w >= 4;

		// --- the stair and its cheek walls
		for (int v = 0; v < steps; v++) {
			for (int u = 0; u < w; u++) {
				boolean cheek = cheeks && (u == 0 || u == w - 1);
				for (int y = 0; y < (cheek ? steps : v); y++) {
					p.set(c, u, y, v, platform(p, u, y, v, false));
				}
				if (!cheek) {
					p.set(c, u, v, v, stairs(v == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
				} else if (v == 0) {
					p.set(c, u, steps, v, b(Blocks.LANTERN)); // [finish] a lantern on each front cheek
				}
			}
		}

		// --- the platform
		for (int v = steps; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean rim = u == 0 || u == w - 1 || v == d - 1;
				for (int y = 0; y < steps; y++) {
					p.set(c, u, y, v, platform(p, u, y, v, rim && y == steps - 1));
				}
			}
		}
		if (cheeks && d - steps >= 2) {
			p.set(c, 0, steps, d - 1, b(Blocks.POTTED_FLOWERING_AZALEA));
			p.set(c, w - 1, steps, d - 1, b(Blocks.POTTED_AZALEA));
		}

		// --- the column, centred on the platform
		int ul = (w - 1) / 2;
		int ur = w / 2;
		int vl = steps + (d - 1 - steps) / 2;
		int vr = steps + (d - steps) / 2;
		boolean needle = ul == ur && vl == vr;
		int top = Math.min(p.h() - 1, steps + 10); // the rod
		int cap = needle ? top - 2 : top - 1; // the sloping cap (then the gold knob and the rod for a single column)
		int lamp = cap - 1;
		int bowl = lamp - 1;
		for (int y = steps; y < bowl; y++) {
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					p.set(c, u, y, v, column(p, u, y, v, y - steps, bowl - 1 - y));
				}
			}
		}

		// --- the lamp head: the ring of cells round the column, three layers
		BlockState bowlStair = stairs(Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Direction.NORTH).setValue(StairBlock.HALF, Half.TOP);
		BlockState capStair = stairs(Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Direction.NORTH);
		BlockState capSlab = slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected());
		BlockState pane = Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK).defaultBlockState();
		BlockState lantern = Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState();
		for (int v = vl - 1; v <= vr + 1; v++) {
			for (int u = ul - 1; u <= ur + 1; u++) {
				if (!p.has(u, 0, v)) {
					continue; // the head is cut by the plot's sides on narrow plots (nothing is written outside)
				}
				boolean core = u >= ul && u <= ur && v >= vl && v <= vr;
				if (core) {
					p.set(c, u, bowl, v, COPPER);
					p.set(c, u, lamp, v, b(Blocks.PEARLESCENT_FROGLIGHT));
					p.set(c, u, cap, v, COPPER);
					continue;
				}
				boolean corner = (u < ul || u > ur) && (v < vl || v > vr);
				Direction in = inward(u, v, ul, ur, vl, vr);
				if (corner) {
					p.set(c, u, bowl, v, slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
					p.set(c, u, lamp, v, lantern);
					p.set(c, u, cap, v, capSlab);
				} else {
					// [depth] the bowl corbels out, the cap slopes back up to the middle
					p.set(c, u, bowl, v, bowlStair.setValue(StairBlock.FACING, in));
					p.set(c, u, lamp, v, glass(pane, u, v, ul, ur, vl, vr));
					p.set(c, u, cap, v, capStair.setValue(StairBlock.FACING, in));
				}
			}
		}
		if (needle) {
			p.set(c, ul, cap + 1, vl, b(Blocks.GOLD_BLOCK));
			p.set(c, ul, top, vl, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		} else {
			for (int v = vl; v <= vr; v++) {
				for (int u = ul; u <= ur; u++) {
					p.set(c, u, top, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
				}
			}
		}
	}

	/** [palette] dark steps and platform; a calcite rim on the platform's top course. */
	private static BlockState platform(Plot p, int u, int y, int v, boolean rim) {
		if (rim) {
			return b(Blocks.CALCITE);
		}
		int hsh = p.hash(u, y, v, 5);
		if (y == 0) {
			return hsh == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
		}
		return hsh == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
	}

	/** [palette] plinth, shaft with a chiselled band, calcite capital ({@code fromTop} 0 = just under the bowl). */
	private static BlockState column(Plot p, int u, int y, int v, int course, int fromTop) {
		if (fromTop == 0) {
			return b(Blocks.CALCITE);
		}
		if (course == 0) {
			return b(Blocks.POLISHED_DEEPSLATE);
		}
		if (fromTop == 2) {
			return b(Blocks.CHISELED_STONE_BRICKS);
		}
		int hsh = p.hash(u, y, v, 6);
		return hsh == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : hsh == 1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
	}

	/** The side of a ring cell that faces the column. */
	private static Direction inward(int u, int v, int ul, int ur, int vl, int vr) {
		if (v < vl) {
			return Direction.SOUTH;
		}
		if (v > vr) {
			return Direction.NORTH;
		}
		return u < ul ? Direction.EAST : Direction.WEST;
	}

	/** A pink pane on a side of the head, joined to the light and to the pane beside it along the side. */
	private static BlockState glass(BlockState pane, int u, int v, int ul, int ur, int vl, int vr) {
		BlockState s = pane;
		for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
			int nu = u + dir.getStepX();
			int nv = v + dir.getStepZ();
			boolean core = nu >= ul && nu <= ur && nv >= vl && nv <= vr;
			boolean side = !core && (nu >= ul && nu <= ur || nv >= vl && nv <= vr) && (nu >= ul - 1 && nu <= ur + 1 && nv >= vl - 1 && nv <= vr + 1)
					&& !((nu < ul || nu > ur) && (nv < vl || nv > vr));
			s = s.setValue(prop(dir), core || side);
		}
		return s;
	}

	private static BooleanProperty prop(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}
}
