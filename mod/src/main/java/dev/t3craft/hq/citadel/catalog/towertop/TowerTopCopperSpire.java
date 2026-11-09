package dev.t3craft.hq.citadel.catalog.towertop;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a slate-and-copper spire crowning a tower shaft, the kind that marks a citadel skyline.
 * At {@code y = 0} (the shaft's top course) a corbelled cornice of upside-down stone-brick stairs runs
 * round the full footprint with chiseled corners; on it stands a crenellated parapet (merlons with
 * smooth-stone caps, slab crenels) whose four corners rise as dark pinnacles carrying lanterns. Inside
 * (one block in, two on footprints of 7+, leaving a walk) the spire tapers in rings: a pink cherry
 * eave course, then deepslate tiles, every ring one block narrower on each side, with slab hips. The
 * rings are flatter at the bottom and steeper at the top when there is height to spare (a broach
 * flare), and the top ring is a copper needle. Gabled dormer lucarnes with pink glass sit on the
 * faces long enough for them (small windows on shorter faces), and a waxed copper finial (cut,
 * chiseled, lightning rod; weathered/exposed patina mixed in by hash) closes the point.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: polished deepslate floor, stone bricks (mossy,
 *     cracked) for cornice and parapet, smooth-stone caps; deepslate tiles (cracked mixed in) and
 *     tile stairs for the spire; cherry eave and dormer roofs; copper at the top.</li>
 * <li><b>Depth</b>: corbel stairs under the parapet, merlon/crenel rhythm, pinnacles, dormers that
 *     break the roof plane with their own gables and a recessed window (the cell behind the pane is
 *     air), hips marked by slabs.</li>
 * <li><b>Symmetry</b>: every ring and dormer is centred on the footprint for odd and even sizes; an
 *     even top ends in a 2-wide copper cap with a rod on each cell.</li>
 * </ul>
 *
 * <pre>
 *  section (w = 9, h = 10)
 *  y9       ╿        rod
 *  y8       ▣        chiseled copper
 *  y7       ▤        copper needle
 *  y6      ◢▤◣
 *  y5     ◢▓▓▓◣      tile rings (steeper at the top)
 *  y4    ◢▓▓▓▓▓◣
 *  y3 ♦  ▓▓ ⌂ ▓▓  ♦  dormer gable, pinnacle lanterns
 *  y2 ┃ ◢▓ ▒ ▓◣ ┃   cherry eave, pink window
 *  y1 █▄█ ▓▓▓ █▄█    parapet: merlons and crenels
 *  y0 ◥▀▀▀▀▀▀▀▀▀◤    corbelled cornice
 * </pre>
 *
 * Size: 3–9 × 3–9 × 6+ (the spire takes up to about {@code 2 × rings + 3} layers; more height stays empty).
 */
public final class TowerTopCopperSpire implements Variant {
	@Override
	public String id() {
		return "tower_top_copper_spire";
	}

	@Override
	public Family family() {
		return Family.TOWER_TOP;
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
		return 6;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		// the spire starts one block in from the parapet: an inset of 2 on big tops left a small spire in a wide yard
		int in0 = 1;

		cornice(c, p, in0);
		parapet(c, p);

		// the rings: each one shrinks by one on each side along every axis still wider than 2
		List<int[]> rings = new ArrayList<>();
		int u0 = in0;
		int u1 = w - 1 - in0;
		int v0 = in0;
		int v1 = d - 1 - in0;
		while (true) {
			rings.add(new int[] {u0, u1, v0, v1});
			if (u1 - u0 + 1 <= 2 && v1 - v0 + 1 <= 2) {
				break;
			}
			if (u1 - u0 + 1 > 2) {
				u0++;
				u1--;
			}
			if (v1 - v0 + 1 > 2) {
				v0++;
				v1--;
			}
		}
		int n = rings.size(); // ≤ 4 for 9-wide: 7 (or 5), 5, 3, 1
		int[] top = rings.get(n - 1);
		boolean point = top[0] == top[1] && top[2] == top[3];

		// height budget: rings need n layers at least (one stair course each), the finial 1..3
		int fin = Math.min(2, p.h() - 1 - n); // ≥ 1 because h ≥ 6 and n ≤ 4
		int extra = p.h() - 1 - fin - n;
		int[] rise = new int[n];
		java.util.Arrays.fill(rise, 1);
		// spare height goes to the tiled body (ring n-2 down to 0, up to 3 courses each), so the spire tapers
		// in tall tiers; the copper needle gets at most 2 courses. Giving it to the needle first made a short
		// tiled stub with a tall copper stick on it.
		boolean grew = true;
		while (extra > 0 && grew) {
			grew = false;
			for (int i = n - 2; i >= 0 && extra > 0; i--) {
				if (rise[i] < 2) { // 2 courses (blocks, then stairs) is the steep profile; 3 made vertical walls
					rise[i]++;
					extra--;
					grew = true;
				}
			}
		}
		if (extra > 0 && n >= 2 && point && rise[n - 1] < 2) { // a 2 × 2 top already gets its copper pyramid
			rise[n - 1]++;
		}

		int y = 1;
		for (int i = 0; i < n; i++) {
			int[] r = rings.get(i);
			for (int k = 0; k < rise[i]; k++) {
				ring(c, p, r, y + k, i, n, k == rise[i] - 1);
			}
			y += rise[i];
		}
		finial(c, p, top, y, fin);

		// lucarnes on ring 0
		int[] r0 = rings.get(0);
		int a = r0[1] - r0[0] + 1;
		int bd = r0[3] - r0[2] + 1;
		boolean full0 = rise[0] >= 2; // ring 0 has a full course under its eave
		for (int face = 0; face < 4; face++) {
			int len = face < 2 ? a : bd;
			int across = face < 2 ? bd : a;
			if (across < 3) {
				continue;
			}
			if (len >= 5) {
				lucarne(c, p, r0, face, true);
			} else if (len >= 3 && full0) {
				lucarne(c, p, r0, face, false);
			}
		}
	}

	// ------------------------------------------------------------------ cornice and parapet (y0..y3)

	private static void cornice(CitadelCanvas c, Plot p, int in0) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				BlockState st;
				if (eu && ev) {
					st = b(Blocks.CHISELED_STONE_BRICKS);
				} else if (eu || ev) {
					// [depth] corbel: upside-down stair, full side inwards, the cut on the outside
					Direction in = v == 0 ? Direction.SOUTH : v == d - 1 ? Direction.NORTH : u == 0 ? Direction.EAST : Direction.WEST;
					Block blk = p.hash(u, 0, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS;
					st = stairs(blk, in).setValue(BlockStateProperties.HALF, Half.TOP);
				} else {
					// [palette] the walk: dark polished floor with tiles mixed in
					st = p.hash(u, 0, v, 3) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	private static void parapet(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				if (!eu && !ev) {
					continue;
				}
				if (eu && ev) {
					// [finish] corner pinnacle: merlon, dark post, lantern
					p.set(c, u, 1, v, b(Blocks.STONE_BRICKS));
					p.set(c, u, 2, v, b(Blocks.POLISHED_DEEPSLATE_WALL)
							.setValue(BlockStateProperties.UP, true)
							.setValue(BlockStateProperties.NORTH_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.SOUTH_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
							.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE));
					p.set(c, u, 3, v, b(Blocks.LANTERN));
					continue;
				}
				int i = ev ? u : v;
				int len = ev ? w : d;
				boolean merlon = Math.min(i, len - 1 - i) % 2 == 0;
				if (merlon) {
					int k = p.hash(u, 1, v, 5);
					p.set(c, u, 1, v, k == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : k == 1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
					p.set(c, u, 2, v, slab(Blocks.STONE_BRICK_SLAB)); // same tone as the merlon: light caps read as loose cubes
				} else {
					p.set(c, u, 1, v, slab(Blocks.STONE_BRICK_SLAB));
				}
			}
		}
	}

	// ------------------------------------------------------------------ spire rings

	/**
	 * One course of ring {@code i}: full blocks, or (on its last course) stairs climbing inwards on the
	 * faces of axes wider than 2, slabs on the hips, full blocks inside.
	 */
	private static void ring(CitadelCanvas c, Plot p, int[] r, int y, int i, int n, boolean stairCourse) {
		int a = r[1] - r[0] + 1;
		int bd = r[3] - r[2] + 1;
		boolean needle = i == n - 1 && n >= 2;
		// the upper rings (from 3 wide down) are copper, so the point is a copper cone and not a 1 × 1 post
		boolean cone = n >= 3 && i >= n - 2;
		for (int v = r[2]; v <= r[3]; v++) {
			for (int u = r[0]; u <= r[1]; u++) {
				boolean onU = stairCourse && a > 2 && (u == r[0] || u == r[1]);
				boolean onV = stairCourse && bd > 2 && (v == r[2] || v == r[3]);
				BlockState st;
				if (onU && onV) {
					st = slab(i == 0 ? Blocks.CHERRY_SLAB : cone ? Blocks.CUT_COPPER_SLAB.waxed().unaffected() : Blocks.DEEPSLATE_TILE_SLAB); // hip
				} else if (onU || onV) {
					Direction up = onU ? (u == r[0] ? Direction.EAST : Direction.WEST) : (v == r[2] ? Direction.SOUTH : Direction.NORTH);
					Block blk = i == 0 ? Blocks.CHERRY_STAIRS
							: cone ? copper(p, u, y, v, Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Blocks.CUT_COPPER_STAIRS.waxed().exposed(),
									Blocks.CUT_COPPER_STAIRS.waxed().weathered())
							: p.hash(u, y, v, 5) == 0 ? Blocks.POLISHED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					st = stairs(blk, up);
				} else if (needle || cone) {
					st = b(copper(p, u, y, v, Blocks.CUT_COPPER.waxed().unaffected(), Blocks.CUT_COPPER.waxed().exposed(),
							Blocks.CUT_COPPER.waxed().weathered()));
				} else {
					st = p.hash(u, y, v, 4) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
				}
				p.set(c, u, y, v, st);
			}
		}
	}

	/**
	 * [palette] waxed copper with a hint of patina: mostly fresh, one cell in eight exposed. (A third of the
	 * cells in exposed/weathered read as a green checkerboard, like missing textures, in the first render.)
	 */
	private static Block copper(Plot p, int u, int y, int v, Block fresh, Block exposed, Block weathered) {
		return p.hash(u, y, v, 8) == 0 ? exposed : fresh;
	}

	/** The finial on the top ring's cells, from {@code y} up, {@code fin} layers. */
	private static void finial(CitadelCanvas c, Plot p, int[] top, int y, int fin) {
		boolean point = top[0] == top[1] && top[2] == top[3];
		BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
		if (top[1] - top[0] == 1 && top[3] - top[2] == 1) {
			// a 2 × 2 top: a little copper pyramid (outer-corner stairs rising to the middle), not four rods
			for (int v = top[2]; v <= top[3]; v++) {
				for (int u = top[0]; u <= top[1]; u++) {
					Direction inU = u == top[0] ? Direction.EAST : Direction.WEST;
					Direction inV = v == top[2] ? Direction.SOUTH : Direction.NORTH;
					StairsShape shape = inV == inU.getClockWise() ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
					p.set(c, u, y, v, stairs(Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), inU).setValue(StairBlock.SHAPE, shape));
				}
			}
			return;
		}
		for (int v = top[2]; v <= top[3]; v++) {
			for (int u = top[0]; u <= top[1]; u++) {
				int yy = y;
				if (point && fin >= 3) {
					p.set(c, u, yy++, v, b(copper(p, u, yy, v, Blocks.CUT_COPPER.waxed().unaffected(), Blocks.CUT_COPPER.waxed().exposed(),
							Blocks.CUT_COPPER.waxed().weathered())));
				}
				if (fin >= 2) {
					p.set(c, u, yy++, v, point ? b(Blocks.CHISELED_COPPER.waxed().unaffected()) : b(Blocks.CUT_COPPER.waxed().unaffected()));
				}
				p.set(c, u, yy, v, rod);
			}
		}
	}

	// ------------------------------------------------------------------ lucarnes

	/**
	 * A window centred on one face of ring 0 at y 1, the cell behind it emptied (a dark recess). With
	 * {@code gable}, a small cherry gable roof over it at y 2–3 that breaks the spire's roof plane.
	 * Faces: 0 front (v = v0), 1 back (v = v1), 2 left (u = u0), 3 right (u = u1); {@code t} runs along
	 * the face (u on front/back, v on the sides), {@code k} inwards from it.
	 */
	private static void lucarne(CitadelCanvas c, Plot p, int[] r, int face, boolean gable) {
		boolean fb = face < 2;
		int t0 = fb ? r[0] : r[2];
		int t1 = fb ? r[1] : r[3];
		int len = t1 - t0 + 1;
		int ct = (t0 + t1) / 2; // the (left) middle cell
		boolean even = len % 2 == 0;
		Direction plus = fb ? Direction.EAST : Direction.SOUTH;
		Direction minus = plus.getOpposite();
		BlockState pane = Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK).defaultBlockState()
				.setValue(BlockStateProperties.NORTH, !fb)
				.setValue(BlockStateProperties.SOUTH, !fb)
				.setValue(BlockStateProperties.EAST, fb)
				.setValue(BlockStateProperties.WEST, fb);

		int last = even ? ct + 1 : ct;
		for (int t = ct; t <= last; t++) {
			put(c, p, r, face, t, 0, 1, pane);
			put(c, p, r, face, t, 1, 1, CitadelCanvas.AIR);
		}
		if (!gable) {
			return;
		}
		BlockState left = stairs(Blocks.CHERRY_STAIRS, plus);
		BlockState right = stairs(Blocks.CHERRY_STAIRS, minus);
		if (!even) {
			put(c, p, r, face, ct - 1, 0, 2, left);
			put(c, p, r, face, ct, 0, 2, b(Blocks.STRIPPED_CHERRY_WOOD));
			put(c, p, r, face, ct + 1, 0, 2, right);
			put(c, p, r, face, ct, 0, 3, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		} else {
			put(c, p, r, face, ct - 1, 0, 2, left);
			put(c, p, r, face, ct, 0, 2, b(Blocks.STRIPPED_CHERRY_WOOD));
			put(c, p, r, face, ct + 1, 0, 2, b(Blocks.STRIPPED_CHERRY_WOOD));
			put(c, p, r, face, ct + 2, 0, 2, right);
			put(c, p, r, face, ct, 0, 3, left);
			put(c, p, r, face, ct + 1, 0, 3, right);
		}
	}

	/** Writes at face-local {@code (t, k)} of ring {@code r}, at height {@code y}. */
	private static void put(CitadelCanvas c, Plot p, int[] r, int face, int t, int k, int y, BlockState s) {
		switch (face) {
			case 0 -> p.set(c, t, y, r[2] + k, s);
			case 1 -> p.set(c, t, y, r[3] - k, s);
			case 2 -> p.set(c, r[0] + k, y, t, s);
			default -> p.set(c, r[1] - k, y, t, s);
		}
	}
}
