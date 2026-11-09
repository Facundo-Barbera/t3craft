package dev.t3craft.hq.citadel.catalog.annex;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Tool shed: a timber shed on a cobble plinth under one long lean-to roof of slabs that rises from
 * a pink cherry eave over the front porch to the back wall, and runs on over a stack of firewood
 * along one side. Shuttered windows, a lantern on the porch beam, a barrel by the door and a
 * grindstone and composter under the left eave.
 *
 * <pre>
 *  side section (d = 7, v →)                plan (w = 7, d = 7)
 *  y7             ▄▄█  back wall top        v0  ╋ ▬ ▬ ▬ ▬ . ╋   porch posts, deck, beam above
 *  y6         ▄▄▀▀ ██                       v1  . ▬ ✿ ▬ ▤ ▮ .   pot under window, barrel, stump
 *  y5     ▄▄▀▀ │   ██  lean-to roof          v2  ▫ ■ ▯ ░ ■ ≡ ≡   front wall, door ▯, window ░
 *  y4  ▀▀▀▀    │   ██  (half-block steps)    v3  ⚙ ■ . . ■ ≡ ≡   grindstone, firewood ≡
 *  y3  ═══     ■▄▄▄██  beam, wall plate      v4  ▫ ░ . . ■ ≡ ≡   side window with shutters ▫
 *  y1-2 │  ✿   ▯   ██  post, door           v5  ◙ ■ . . ■ ≡ ≡   composter
 *  y0  ▓ ▬ ▬   ▓▓▓▓▓▓  base, deck, plinth   v6  . ■ ■ ■ ■ ≡ ≡   back wall
 * </pre>
 *
 * Size: 5–8 × 5–8 × 7+ (on deep plots lower than 8 the last roof rows level off).
 */
public final class AnnexToolShed implements Variant {
	/** Walls y1..y3 (plate at y3); the roof starts at the front eave at y4. */
	private static final int WALL_TOP = 3;
	private static final int ROOF = 4;

	private static final BlockState POST = b(Blocks.DARK_OAK_LOG);
	private static final BlockState WALL = b(Blocks.SPRUCE_PLANKS);
	private static final BlockState PLATE_X = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState PLATE_Z = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final Block[] FIREWOOD = {Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.OAK_LOG, Blocks.DARK_OAK_LOG};

	@Override
	public String id() {
		return "annex_tool_shed";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 5;
	}

	@Override
	public int minH() {
		return 7;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 8;
	}

	/** The shed's box inside the plot. */
	private record Body(int u0, int u1, int vf, int vb, int door) {
		boolean wall(int u, int v) {
			boolean inV = v >= vf && v <= vb;
			boolean inU = u >= u0 && u <= u1;
			return (inV && (u == u0 || u == u1)) || (inU && (v == vf || v == vb));
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int lo = w >= 6 ? 1 : 0; // a free eave strip on the left
		int pileW = w >= 7 ? 2 : 1; // firewood columns on the right
		int porch = d >= 7 ? 2 : 1;
		int u0 = lo;
		int u1 = w - 1 - pileW;
		Body s = new Body(u0, u1, porch, d - 1, (u0 + u1) / 2);

		plinth(c, p, s);
		walls(c, p, s);
		porch(c, p, s); // before the windows: their pots replace deck slabs
		windows(c, p, s, lo);
		roof(c, p, s);
		firewood(c, p, s);
		if (lo == 1) {
			leftStrip(c, p, s);
		}
	}

	// ------------------------------------------------------------------ roof geometry

	/** Half-block steps of the lean-to over row v (capped so the roof stays inside the plot height). */
	private static int steps(Plot p, int v) {
		return Math.min(v, 2 * p.h() - 9);
	}

	/** The y of the roof slab over row v. */
	private static int roofY(Plot p, int v) {
		return ROOF + steps(p, v) / 2;
	}

	private static boolean roofTop(Plot p, int v) {
		return steps(p, v) % 2 == 1;
	}

	// ------------------------------------------------------------------ body

	/** [palette] the rough base: cobblestone, mossy cobblestone and cobbled deepslate mixed by hash. */
	private static BlockState rubble(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 4)) {
			case 0 -> b(Blocks.MOSSY_COBBLESTONE);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.COBBLESTONE);
		};
	}

	private static void plinth(CitadelCanvas c, Plot p, Body s) {
		for (int u = s.u0(); u <= s.u1(); u++) {
			for (int v = s.vf(); v <= s.vb(); v++) {
				p.set(c, u, 0, v, s.wall(u, v) ? rubble(p, u, 0, v) : WALL);
			}
		}
	}

	/**
	 * [depth] a timber frame: dark oak posts at the corners, spruce plank infill,
	 * a stripped dark oak plate round the top; the side and back walls close up to the roof.
	 */
	private static void walls(CitadelCanvas c, Plot p, Body s) {
		for (int u = s.u0(); u <= s.u1(); u++) {
			for (int v = s.vf(); v <= s.vb(); v++) {
				if (!s.wall(u, v)) {
					continue;
				}
				boolean side = u == s.u0() || u == s.u1();
				boolean end = v == s.vf() || v == s.vb();
				boolean corner = side && end;
				// [depth] intermediate posts every third cell on the side and back walls (not the front:
				// door and windows), so no plank panel is wider than 2
				boolean stud = !corner && v != s.vf()
						&& (side ? (v - s.vf()) % 3 == 0 : (u - s.u0()) % 3 == 0);
				for (int y = 1; y <= WALL_TOP; y++) {
					BlockState st;
					if (corner || stud) {
						st = POST;
					} else if (y == WALL_TOP) {
						st = side ? PLATE_Z : PLATE_X;
					} else if (v == s.vf() && u == s.door()) {
						st = b(Blocks.SPRUCE_DOOR)
								.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
								.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
					} else {
						st = WALL;
					}
					p.set(c, u, y, v, st);
				}
				// gable / back wall fill between the plate and the sloping roof
				for (int y = WALL_TOP + 1; y < roofY(p, v); y++) {
					BlockState st = WALL;
					if (corner || stud) {
						st = POST;
					} else if (y == WALL_TOP + 3) {
						st = side ? PLATE_Z : PLATE_X; // a second rail on tall gables
					} else if (p.hash(u, y, v, 5) == 0) {
						st = b(Blocks.STRIPPED_SPRUCE_LOG); // [palette] a weathered plank here and there
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	/**
	 * Windows: the front wall cells that are neither post nor door get a pane at y2 with a potted
	 * fern under it on the porch and a shutter on the side away from the door; the left wall gets one
	 * window in the middle, shuttered when there is an eave strip outside it.
	 */
	private static void windows(CitadelCanvas c, Plot p, Body s, int lo) {
		BlockState pane = b(Blocks.GLASS_PANE);
		int vp = s.vf() - 1; // the porch row against the front wall
		for (int u = s.u0() + 1; u < s.u1(); u++) {
			if (u == s.door()) {
				continue;
			}
			p.set(c, u, 2, s.vf(), pane.setValue(BlockStateProperties.WEST, u - 1 != s.door()).setValue(BlockStateProperties.EAST, u + 1 != s.door()));
			p.set(c, u, 0, vp, b(Blocks.POTTED_FERN));
			int away = u < s.door() ? u - 1 : u + 1;
			if (away != s.door()) {
				p.put(c, away, 2, vp, shutter(Direction.NORTH), CitadelCanvas.DECOR);
			}
		}
		int mv = (s.vf() + s.vb()) / 2;
		p.set(c, s.u0(), 2, mv, pane.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true));
		if (lo == 1) {
			p.put(c, s.u0() - 1, 2, mv - 1, shutter(Direction.WEST), CitadelCanvas.DECOR);
			p.put(c, s.u0() - 1, 2, mv + 1, shutter(Direction.WEST), CitadelCanvas.DECOR);
		}
	}

	/** An open spruce trapdoor lying flat against the wall behind it ({@code out} = the way it faces). */
	private static BlockState shutter(Direction out) {
		return b(Blocks.SPRUCE_TRAPDOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, out).setValue(BlockStateProperties.OPEN, true);
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] a lean-to of slabs climbing half a block per row from the front eave to the back,
	 * over the whole plot (porch, eave strip and woodpile included). The eave row is cherry (the pink
	 * line), the two verge edges dark oak, the field spruce. Where a top slab passes over a wall it
	 * becomes a double slab, so no half-block gap shows between wall and roof.
	 */
	private static void roof(CitadelCanvas c, Plot p, Body s) {
		int w = p.w();
		for (int v = 0; v < p.d(); v++) {
			int y = roofY(p, v);
			boolean top = roofTop(p, v);
			for (int u = 0; u < w; u++) {
				// [palette] a cherry fascia on all four edges frames a field of spruce, dark oak mixed in by hash
				boolean edge = v == 0 || v == p.d() - 1 || u == 0 || u == w - 1;
				Block mat = edge ? Blocks.CHERRY_SLAB : Blocks.DARK_OAK_SLAB; // one dark shade, so the slope reads as rows
				SlabType type = !top ? SlabType.BOTTOM : s.wall(u, v) ? SlabType.DOUBLE : SlabType.TOP;
				p.set(c, u, y, v, b(mat).setValue(BlockStateProperties.SLAB_TYPE, type));
			}
		}
	}

	// ------------------------------------------------------------------ porch

	/**
	 * [finish] the porch: a spruce slab deck in front of the shed, two posts on cobble bases at the
	 * front corners carrying a beam under the eave, a lantern hanging from it, a barrel by the
	 * door and a chopping stump in front of the firewood.
	 */
	private static void porch(CitadelCanvas c, Plot p, Body s) {
		int w = p.w();
		for (int u = s.u0(); u <= s.u1(); u++) {
			for (int v = 0; v < s.vf(); v++) {
				p.set(c, u, 0, v, slab(Blocks.SPRUCE_SLAB));
			}
		}
		for (int u : new int[] {0, w - 1}) {
			p.set(c, u, 0, 0, rubble(p, u, 0, 0));
			p.set(c, u, 1, 0, b(Blocks.DARK_OAK_FENCE));
			p.set(c, u, 2, 0, b(Blocks.DARK_OAK_FENCE));
		}
		for (int u = 0; u < w; u++) {
			p.set(c, u, WALL_TOP, 0, PLATE_X);
		}
		int lu = s.door() > s.u0() + 1 ? s.door() - 1 : s.door() + 1;
		p.put(c, lu, WALL_TOP - 1, 0, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		p.set(c, s.u1(), 0, s.vf() - 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		int su = s.u1() + 1;
		int sv = s.vf() - 1;
		if (!(su == w - 1 && sv == 0)) {
			p.set(c, su, 0, sv, b(Blocks.OAK_LOG)); // upright stump: end grain up
		}
	}

	// ------------------------------------------------------------------ firewood

	/**
	 * [palette] the woodpile: logs lying crosswise (ring ends to the side), oak, spruce, birch and
	 * dark oak mixed by hash, two or three high per row so the top edge is ragged, under the roof.
	 */
	private static void firewood(CitadelCanvas c, Plot p, Body s) {
		for (int u = s.u1() + 1; u < p.w(); u++) {
			for (int v = s.vf(); v <= s.vb(); v++) {
				int high = 2 + p.hash(u, 0, v, 2);
				for (int y = 0; y < high; y++) {
					Block log = FIREWOOD[p.hash(u, y, v, FIREWOOD.length)];
					p.set(c, u, y, v, b(log).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
				}
			}
		}
	}

	// ------------------------------------------------------------------ left eave strip

	/** Under the left eave: a grindstone, a composter and a barrel, by seed, between moss and ferns. */
	private static void leftStrip(CitadelCanvas c, Plot p, Body s) {
		for (int v = 1; v <= s.vb(); v++) {
			BlockState st = switch (p.hash(0, 0, v, 6)) {
				case 0 -> b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
				case 1 -> b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 4);
				case 2 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
				case 3 -> b(Blocks.FERN);
				case 4 -> b(Blocks.MOSS_CARPET);
				default -> null;
			};
			if (st != null) {
				p.set(c, 0, 0, v, st);
			}
		}
	}
}
