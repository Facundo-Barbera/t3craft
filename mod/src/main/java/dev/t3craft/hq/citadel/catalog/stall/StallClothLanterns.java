package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Cloth and lantern stall: no roof, so the silhouette is tall and open. Two cherry posts carry a
 * crossbeam with banners hanging down the front and fence arms with lanterns over the apron; behind
 * the counter, bolts of wool hang from a back rail; on the counter, folded cloth (wool + carpet).
 *
 * <pre>
 *  section (v →)                  plan
 *  y4  arm──lantern                back   [rail with hanging bolts]
 *  y3  ═beam══ banner hangs         aisle  [checker rug]
 *  y2  folded cloth  bolts          v1     P [counter: cloth, loom] P
 *  y1  cloth, loom   bolts          v0     [runner under the banners]
 *  y0  posts, counter, rug
 * </pre>
 *
 * Size: 4–6 × 3–5 × 5+.
 */
public final class StallClothLanterns implements Variant {
	@Override
	public String id() {
		return "stall_cloth_lanterns";
	}

	@Override
	public Family family() {
		return Family.STALL;
	}

	@Override
	public int minW() {
		return 4;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 6;
	}

	@Override
	public int maxD() {
		return 5;
	}

	/** [palette] three dye sets per seed: bright cloth against the dark-cherry timber. */
	private static final DyeColor[][] SETS = {
			{DyeColor.CYAN, DyeColor.MAGENTA, DyeColor.YELLOW, DyeColor.WHITE},
			{DyeColor.BLUE, DyeColor.PINK, DyeColor.ORANGE, DyeColor.WHITE},
			{DyeColor.PURPLE, DyeColor.LIME, DyeColor.RED, DyeColor.WHITE},
	};

	private static BlockState wool(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}

	private static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	private static BooleanProperty side(Direction d) {
		return switch (d) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.EAST;
		};
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		DyeColor[] set = SETS[p.hash(5, 5, 5, SETS.length)];
		int back = p.d() - 1;

		frontFrame(c, p, set);
		counter(c, p, set);
		backRail(c, p, set, back);
		floor(c, p, set, back);
		apron(c, p, set);
	}

	// ------------------------------------------------------------------ front frame: posts, beam, banners, arms

	private static void frontFrame(CitadelCanvas c, Plot p, DyeColor[] set) {
		int w = p.w();
		BlockState lamp = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int u : new int[] {0, w - 1}) {
			int toward = u == 0 ? 1 : -1;
			p.set(c, u, 0, 1, b(Blocks.STRIPPED_CHERRY_LOG)); // [palette] a pale stripped base...
			p.set(c, u, 1, 1, b(Blocks.CHERRY_FENCE));
			p.set(c, u, 2, 1, b(Blocks.CHERRY_FENCE));
			// [depth] the post head: a log collar where the beam and the arm meet it
			p.set(c, u, 3, 1, b(Blocks.STRIPPED_CHERRY_LOG));
			p.set(c, u, 4, 1, b(Blocks.CHERRY_FENCE).setValue(side(Direction.NORTH), true)); // ...a slim shaft, then the arm springs forward
			p.set(c, u, 4, 0, b(Blocks.CHERRY_FENCE).setValue(side(Direction.SOUTH), true));
			p.put(c, u, 3, 0, lamp, CitadelCanvas.HANGING);
			if (p.h() > 5) {
				p.set(c, u, 5, 1, slab(Blocks.CHERRY_SLAB)); // finial
			}
		}
		// the crossbeam between the posts (logs along u), with a dark slab cap
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 3, 1, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		}
		// [depth] pennants hang in front of the beam, alternately two and three blocks long, each ending
		// in a white tip; their colours rotate through the set (wool, not banner blocks: they read from afar)
		for (int u = 1; u < w - 1; u++) {
			DyeColor col = set[(u + p.hash(0, 0, 0, 3)) % 3];
			int len = (u & 1) == 1 ? 2 : 1; // short enough to leave the folded cloth on the counter in view
			for (int k = 0; k < len; k++) {
				p.set(c, u, 3 - k, 0, wool(k == len - 1 ? set[3] : col));
			}
		}
	}

	// ------------------------------------------------------------------ counter (v 1): folded cloth

	private static void counter(CitadelCanvas c, Plot p, DyeColor[] set) {
		int w = p.w();
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 0, 1, p.hash(u, 0, 1, 3) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH) : b(Blocks.DARK_OAK_PLANKS));
			DyeColor a = set[p.hash(u, 1, 1, 3)];
			DyeColor o = set[(p.hash(u, 1, 1, 3) + 1 + p.hash(u, 2, 1, 2)) % 3];
			switch (p.hash(u, 3, 1, 4)) {
				case 0 -> {
					p.set(c, u, 1, 1, wool(a)); // a stack of folded bolts
					p.set(c, u, 2, 1, carpet(o));
				}
				case 1 -> p.set(c, u, 1, 1, wool(a));
				case 2 -> {
					p.set(c, u, 1, 1, carpet(a));
				}
				default -> p.set(c, u, 1, 1, b(Blocks.LOOM).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			}
		}
	}

	// ------------------------------------------------------------------ back rail: bolts of cloth hang

	private static void backRail(CitadelCanvas c, Plot p, DyeColor[] set, int back) {
		int w = p.w();
		if (back < 2) {
			return;
		}
		for (int u : new int[] {0, w - 1}) {
			p.set(c, u, 0, back, b(Blocks.STRIPPED_CHERRY_LOG));
			p.set(c, u, 1, back, b(Blocks.CHERRY_FENCE));
			p.set(c, u, 2, back, b(Blocks.CHERRY_FENCE));
			p.set(c, u, 3, back, b(Blocks.STRIPPED_CHERRY_LOG));
		}
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 3, back, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // the rail
			DyeColor top = set[(u + 1) % 3];
			DyeColor low = set[(u + 2) % 3];
			p.set(c, u, 2, back, wool(top)); // [palette] each bolt shades from one dye to a second
			p.set(c, u, 1, back, wool(low));
			p.set(c, u, 0, back, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		}
	}

	// ------------------------------------------------------------------ floor and apron

	private static void floor(CitadelCanvas c, Plot p, DyeColor[] set, int back) {
		int w = p.w();
		for (int v = 2; v < back; v++) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, 0, v, carpet(((u + v) & 1) == 0 ? set[0] : set[3])); // a checker rug
			}
		}
		// side posts of the aisle are open; a lamp stands on a barrel at one corner
		if (back >= 3) {
			p.set(c, 1, 0, back - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			p.set(c, 1, 1, back - 1, b(Blocks.LANTERN));
		}
	}

	private static void apron(CitadelCanvas c, Plot p, DyeColor[] set) {
		int w = p.w();
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 0, 0, carpet(DyeColor.RED)); // a runner in front of the counter, under the banners
		}
	}
}
