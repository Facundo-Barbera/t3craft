package dev.t3craft.hq.citadel.catalog.stall;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Fruit stall: a striped wool awning that starts over the counter and steps up towards the back,
 * dark-oak posts, a light plank counter with melons, pumpkins and berries, and an open apron in front
 * of it with barrel crates heaped with produce (so the goods read from the street, not under the roof).
 *
 * <pre>
 *  section (v →)                         plan (front at the bottom)
 *  y4         ▄▄▄▄ awning (stripes)       back   [barrels + goods + board]
 *  y3      ▄▄▄                             aisle  [rug]
 *  y2      ▒ tabs, lanterns                v1     P [counter ▒ ▒ ▒] P
 *  y1  goods  goods                        v0     [crate][crate][crate][crate]  (open air)
 *  y0  crates posts, counter, rug
 * </pre>
 *
 * Size: 4–6 × 3–5 × 5+.
 */
public final class StallFruitAwning implements Variant {
	private static final int LIP = 3;
	private static final int ROOF = 4;

	@Override
	public String id() {
		return "stall_fruit_awning";
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

	/** [palette] the awning's two stripe colours per seed; the timber stays dark and the counter light. */
	private static final DyeColor[][] STRIPES = {
			{DyeColor.PINK, DyeColor.WHITE},
			{DyeColor.MAGENTA, DyeColor.WHITE},
			{DyeColor.PINK, DyeColor.YELLOW}, // yellow stands in for the citadel's gold accent
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int back = p.d() - 1;
		DyeColor[] st = STRIPES[p.hash(3, 3, 3, STRIPES.length)];

		awning(c, p, st);
		posts(c, p);
		counter(c, p);
		apron(c, p);
		backWall(c, p, back);
		aisle(c, p, back);
		lights(c, p);
	}

	// ------------------------------------------------------------------ awning (y3..y4, from v 1)

	private static BlockState wool(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}

	/**
	 * [depth] stripes run front to back; the lip over the counter hangs lower than the rest, with a
	 * tab dropping one more block under every first stripe (a scalloped edge); the roof steps up over
	 * the vendor's side, the riser closed by upside-down stairs.
	 */
	private static void awning(CitadelCanvas c, Plot p, DyeColor[] st) {
		int w = p.w();
		int d = p.d();
		for (int v = 1; v < d; v++) {
			int y = v < 2 ? LIP : ROOF;
			for (int u = 0; u < w; u++) {
				p.set(c, u, y, v, wool(st[u % 2]));
			}
			if (v == 2) {
				for (int u = 0; u < w; u++) {
					p.set(c, u, LIP, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
		for (int u = 1; u < w - 1; u += 2) {
			p.set(c, u, LIP - 1, 1, wool(st[0])); // scalloped tabs under the lip
		}
		// [finish] scalloped valance along both sides of the high roof too: tabs drop under every
		// coloured stripe, so the awning reads as cloth from the side views
		for (int v = 3; v < d - 1; v++) { // not on the back row: the back posts reach that height
			if ((v & 1) == 1) {
				p.set(c, 0, ROOF - 1, v, wool(st[0]));
				p.set(c, w - 1, ROOF - 1, v, wool(st[(w - 1) % 2]));
			}
		}
	}

	// ------------------------------------------------------------------ posts

	private static void posts(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int u : new int[] {0, w - 1}) {
			for (int v : new int[] {1, d - 1}) {
				int top = v == 1 ? LIP - 1 : ROOF - 1;
				p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE)); // [palette] dark footing...
				for (int y = 1; y <= top; y++) {
					p.set(c, u, y, v, y == top ? b(Blocks.STRIPPED_CHERRY_LOG) : b(Blocks.CHERRY_FENCE)); // ...slim pink shaft, pale collar
				}
			}
		}
	}

	// ------------------------------------------------------------------ counter and goods (v 1)

	private static BlockState body(Plot p, int u) {
		return switch (p.hash(u, 0, 1, 4)) {
			case 0 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH);
			case 1 -> b(Blocks.BIRCH_PLANKS);
			default -> b(Blocks.OAK_PLANKS);
		};
	}

	/** One produce block by kind; berries are a ripe bush. */
	private static BlockState goods(int kind) {
		return switch (kind) {
			case 0 -> b(Blocks.MELON);
			case 1 -> b(Blocks.PUMPKIN);
			default -> b(Blocks.SWEET_BERRY_BUSH).setValue(BlockStateProperties.AGE_3, 3);
		};
	}

	private static void counter(CitadelCanvas c, Plot p) {
		int w = p.w();
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 0, 1, body(p, u));
			int k = p.hash(u, 1, 1, 6);
			if (k < 3) {
				p.set(c, u, 1, 1, goods(k));
			} else if (k == 3) {
				p.set(c, u, 1, 1, b(Blocks.DECORATED_POT));
			} else if (k == 4) {
				p.set(c, u, 1, 1, b(Blocks.POTTED_RED_TULIP));
			}
		}
	}

	// ------------------------------------------------------------------ the apron (v 0): crates in the open

	/** [depth] a row of barrel crates, each heaped with one kind of produce, kinds alternating so no two neighbours match. */
	private static void apron(CitadelCanvas c, Plot p) {
		int w = p.w();
		int shift = p.hash(2, 2, 2, 3);
		for (int u = 0; u < w; u++) {
			p.set(c, u, 0, 0, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP)); // an open crate
			int kind = (u + shift) % 3;
			p.set(c, u, 1, 0, goods(kind));
			if (p.hash(u, 7, 0, 3) == 0 && p.h() > 2) {
				p.set(c, u, 2, 0, kind == 2 ? b(Blocks.SWEET_BERRY_BUSH).setValue(BlockStateProperties.AGE_3, 3) : goods(kind)); // a heap two high
			}
		}
	}

	// ------------------------------------------------------------------ the back wall

	private static void backWall(CitadelCanvas c, Plot p, int back) {
		int w = p.w();
		for (int u = 1; u < w - 1; u++) {
			p.set(c, u, 0, back, p.hash(u, 0, back, 2) == 0 ? b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH)
					: b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			p.set(c, u, 1, back, goods(p.hash(u, 1, back, 3)));
			p.set(c, u, 2, back, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X)); // [depth] a header beam over the shelf
		}
	}

	private static void aisle(CitadelCanvas c, Plot p, int back) {
		int w = p.w();
		for (int v = 2; v < back; v++) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, 0, v, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
			}
		}
		// [finish] a hay bale with a pumpkin on one side
		if (back >= 3) {
			int side = p.hash(1, 1, 1, 2) == 0 ? 0 : w - 1;
			p.set(c, side, 0, 2, b(Blocks.HAY_BLOCK));
			p.set(c, side, 1, 2, b(Blocks.PUMPKIN));
		}
	}

	// ------------------------------------------------------------------ light

	private static void lights(CitadelCanvas c, Plot p) {
		int w = p.w();
		BlockState lamp = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		for (int u = 2; u < w - 1; u += 2) {
			p.put(c, u, LIP - 1, 1, lamp, CitadelCanvas.HANGING); // between the tabs, on the white stripes
		}
		if (p.d() >= 4) {
			p.put(c, w / 2, ROOF - 1, 3, lamp, CitadelCanvas.HANGING); // under the high roof, over the aisle
		}
	}
}
