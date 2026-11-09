package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.hanging;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.vine;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * A house in stepped terraces: two or three volumes, each set back from the one below, so that the roof
 * of every volume is the walkable garden terrace of the next one. A 2-wide outside stair climbs from the
 * ground to the first terrace (and, in the three-storey version, a second one to the next terrace), so
 * every level can be walked to and no floor is sealed.
 *
 * <pre>
 *  side section (front on the left), three storeys, steps back to the rear
 *                          ____/\____     studio: desk, lectern, shelves (4+ clear)
 *               .--garden--|  top   |
 *    .--garden--| living             |   rail, planters, bench, table, small tree
 *    | workshop |
 *  --stair--'            the stair runs up the side wall of each volume
 * </pre>
 *
 * Variation by seed: 2 or 3 storeys (if the plot is tall enough), the side of the stair (mirror), and the
 * way the volumes step back: type 0 recedes from the front and both sides, type 1 stays flush on the
 * stair-free side and recedes only from the front and the stair side. Every storey is 4 free blocks high
 * (a floor at {@code s - 1}, air {@code s..s+3}, a ceiling at {@code s + 4} that is the terrace of the
 * next volume). Palette: deepslate bricks (base), calcite plaster, stripped cherry (frame), stone bricks
 * (terraces and stairs), deepslate tiles (roof) with moss, azalea and cherry as the greenery.
 *
 * <p>Size: 18+ × 16+ × 17+. The house is at most 44 × 40 and sits in the middle of larger plots.
 */
public final class HouseTerraceTiers implements Variant {
	/** Floor to floor: 4 free blocks and a 1-block ceiling. */
	private static final int PITCH = 5;
	/** Width of the lane for the stair on the left, outside the first volume (rail, 2 steps). */
	private static final int LANE_W = 3;

	@Override
	public String id() {
		return "house_terrace_tiers";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 18;
	}

	@Override
	public int minD() {
		return 16;
	}

	@Override
	public int minH() {
		return 20;
	}

	@Override
	public int maxW() {
		return 34;
	}

	@Override
	public int maxD() {
		return 34;
	}

	// ------------------------------------------------------------------ geometry

	/** The rectangles (wall rings) of the volumes, bottom to top, in local u (a..b) and v (front..back). */
	private static final class Geo {
		final int n;
		final int[] ua;
		final int[] ub;
		final int[] va;
		final int[] ve;
		final int[] s;
		final int[] c;
		int type;

		Geo(int n) {
			this.n = n;
			ua = new int[n];
			ub = new int[n];
			va = new int[n];
			ve = new int[n];
			s = new int[n];
			c = new int[n];
		}

		boolean in(int i, int u, int v) {
			return u >= ua[i] && u <= ub[i] && v >= va[i] && v <= ve[i];
		}

		boolean inner(int i, int u, int v) {
			return u > ua[i] && u < ub[i] && v > va[i] && v < ve[i];
		}

		/** The door column of volume {@code i}: the middle of its front wall (sidelights either side). */
		int door(int i) {
			return (ua[i] + ub[i]) / 2;
		}

		static Geo plan(int w, int d, int h, int type, int want) {
			int[] rights = type == 0 ? new int[] {5, 3} : new int[] {0};
			int[] steps = type == 0 ? new int[] {6, 5} : new int[] {5, 4};
			for (int n = want; n >= 2; n--) {
				for (int right : rights) {
					for (int step : steps) {
						Geo g = new Geo(n);
						g.type = type;
						g.ua[0] = LANE_W;
						g.ub[0] = w - 2;
						g.va[0] = 2;
						g.ve[0] = d - 2;
						for (int i = 1; i < n; i++) {
							g.ua[i] = g.ua[i - 1] + 3;
							g.ub[i] = type == 0 ? g.ub[i - 1] - right : g.ub[i - 1];
							g.va[i] = g.va[i - 1] + step;
							g.ve[i] = g.ve[0];
						}
						for (int i = 0; i < n; i++) {
							g.s[i] = 1 + PITCH * i;
							g.c[i] = g.s[i] + 4;
						}
						int t = n - 1;
						boolean ok = g.ub[t] - g.ua[t] + 1 >= 8 && g.ve[t] - g.va[t] + 1 >= 7 && g.ve[0] >= (n == 3 ? 16 : 13) && h >= (n == 3 ? 20 : 17);
						if (ok) {
							return g;
						}
					}
				}
			}
			return null;
		}
	}

	/** Writes in local terms of the sub-box, mirrored left-right when {@code mir}. */
	private static final class B {
		final CitadelCanvas c;
		final Plot p;
		final int ou;
		final int ov;
		final int w;
		final boolean mir;

		B(CitadelCanvas c, Plot p, int ou, int ov, int w, boolean mir) {
			this.c = c;
			this.p = p;
			this.ou = ou;
			this.ov = ov;
			this.w = w;
			this.mir = mir;
		}

		void set(int u, int y, int v, BlockState s) {
			put(u, y, v, s, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			int uu = u;
			BlockState ss = s;
			if (mir) {
				uu = w - 1 - u;
				ss = s.mirror(Mirror.FRONT_BACK);
			}
			p.put(c, ou + uu, y, ov + v, ss, layer);
		}

		boolean free(int u, int y, int v) {
			int uu = mir ? w - 1 - u : u;
			BlockState s = p.at(c, ou + uu, y, ov + v);
			return s == null || s.isAir();
		}

		int hash(int u, int y, int v, int n) {
			return p.hash(u, y, v, n);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x7E44_ACE5L);
		int wc = Math.min(p.w(), 44);
		int dc = Math.min(p.d(), 40);
		boolean mir = r.nextBoolean();
		int type = r.nextInt(2);
		int want = r.nextInt(3) == 0 ? 2 : 3;
		Geo g = Geo.plan(wc, dc, p.h(), type, want);
		if (g == null) {
			return;
		}
		B x = new B(c, p, (p.w() - wc) / 2, (p.d() - dc) / 2, wc, mir);
		boolean[][] res = new boolean[wc][dc];
		reserve(g, res);
		for (int i = 0; i < g.n; i++) {
			volume(x, g, i);
		}
		for (int i = 0; i < g.n - 1; i++) {
			terrace(x, g, i, res);
		}
		flights(x, g);
		roof(x, g, p.h());
		cornice(x, g, res);
		for (int i = 0; i < g.n; i++) {
			int kind = i == 0 ? 0 : (g.n == 3 ? i : 3);
			interior(x, g, i, kind);
		}
		yard(x, g, wc, dc);
	}

	/** Cells of the lanes and their landings: no garden, no cornice. */
	private static void reserve(Geo g, boolean[][] res) {
		int w = res.length;
		int d = res[0].length;
		for (int u = 0; u <= 2; u++) {
			for (int v = 0; v <= 9; v++) {
				res[u][v] = true;
			}
		}
		for (int u = 3; u <= 5; u++) {
			for (int v = 6; v <= (g.n == 3 ? 15 : 8); v++) {
				if (u < w && v < d) {
					res[u][v] = true;
				}
			}
		}
		for (int u = 0; u < w; u++) {
			res[u][1] = true;
		}
		if (g.n == 3) {
			for (int u = 6; u <= 8; u++) {
				for (int v = 12; v <= 15; v++) {
					res[u][v] = true;
				}
			}
		}
	}

	// ------------------------------------------------------------------ materials

	private static BlockState darkStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE);
	}

	private static BlockState midStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS);
	}

	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.QUARTZ_BRICKS);
	}

	private static BlockState paver(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS);
	}

	private static BlockState roofStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES);
	}

	private static BlockState post() {
		return b(Blocks.STRIPPED_DARK_OAK_LOG);
	}

	private static BlockState face(Block blk, Direction f) {
		return blk.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState glassPane(Plot p, int u, int y, int v, boolean alongU) {
		boolean pink = p.hash(u, y, v, 7) == 0;
		return pane(pink ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	/** A stair with an outer corner at a ring corner (the high quarter on the inside diagonal). */
	private static BlockState ringStair(Block blk, boolean n, boolean s, boolean w, boolean e, boolean top) {
		Direction f;
		StairsShape sh = StairsShape.STRAIGHT;
		if (n && w) {
			f = Direction.SOUTH;
			sh = StairsShape.OUTER_LEFT;
		} else if (n && e) {
			f = Direction.SOUTH;
			sh = StairsShape.OUTER_RIGHT;
		} else if (s && w) {
			f = Direction.NORTH;
			sh = StairsShape.OUTER_RIGHT;
		} else if (s && e) {
			f = Direction.NORTH;
			sh = StairsShape.OUTER_LEFT;
		} else if (n) {
			f = Direction.SOUTH;
		} else if (s) {
			f = Direction.NORTH;
		} else if (w) {
			f = Direction.EAST;
		} else {
			f = Direction.WEST;
		}
		return stair(blk, f, top).setValue(BlockStateProperties.STAIRS_SHAPE, sh);
	}

	// ------------------------------------------------------------------ volumes: walls, floors, ceilings, doors

	/** The wall block at ring cell (u, v), layer {@code t} 0..3 over the floor of storey i. */
	private static BlockState wall(Plot p, Geo g, int i, int u, int v, int t) {
		int a = g.ua[i];
		int bb = g.ub[i];
		int v0 = g.va[i];
		int e = g.ve[i];
		boolean horiz = v == v0 || v == e;
		int k = horiz ? u - a : v - v0;
		int len = horiz ? bb - a + 1 : e - v0 + 1;
		boolean corner = (u == a || u == bb) && (v == v0 || v == e);
		boolean pil = corner || (k % 4 == 0 && len - 1 - k >= 2);
		if (pil) {
			return post();
		}
		if (t == 3) {
			return log(Blocks.STRIPPED_CHERRY_LOG, horiz ? Direction.Axis.X : Direction.Axis.Z);
		}
		boolean panel = (k / 4) % 3 == 1;
		boolean window = k % 4 != 0 && !panel;
		if (window) {
			if (t == 0) {
				// the sill: a thin low wall in the wall plane under the glass, so the glass sits in a niche
				BlockState sill = b(i == 0 ? Blocks.DEEPSLATE_BRICK_WALL : Blocks.STONE_BRICK_WALL).setValue(BlockStateProperties.UP, false);
				return horiz ? sill.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW).setValue(BlockStateProperties.WEST_WALL, WallSide.LOW)
						: sill.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW).setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW);
			}
			return glassPane(p, u, t, v, horiz);
		}
		if (t == 0) {
			return i == 0 ? darkStone(p, u, 0, v) : midStone(p, u, 0, v);
		}
		return plaster(p, u, t, v);
	}

	private static void volume(B x, Geo g, int i) {
		Plot p = x.p;
		int s = g.s[i];
		int cy = g.c[i];
		boolean top = i == g.n - 1;
		for (int u = g.ua[i]; u <= g.ub[i]; u++) {
			for (int v = g.va[i]; v <= g.ve[i]; v++) {
				boolean ring = u == g.ua[i] || u == g.ub[i] || v == g.va[i] || v == g.ve[i];
				if (ring) {
					if (i == 0) {
						x.set(u, 0, v, b(Blocks.DEEPSLATE_TILES));
					}
					for (int t = 0; t <= 3; t++) {
						x.set(u, s + t, v, wall(p, g, i, u, v, t));
					}
					boolean horiz = v == g.va[i] || v == g.ve[i];
					x.set(u, cy, v, top ? log(Blocks.STRIPPED_CHERRY_LOG, horiz ? Direction.Axis.X : Direction.Axis.Z)
							: mix(p, u, cy, v, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.MOSSY_STONE_BRICKS));
				} else {
					if (i == 0) {
						x.set(u, 0, v, mix(p, u, 0, v, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES));
					} else {
						x.set(u, g.c[i - 1], v, mix(p, u, 0, v, Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, Blocks.SPRUCE_PLANKS));
					}
					x.set(u, cy, v, top ? b(Blocks.CHERRY_PLANKS) : paver(p, u, cy, v));
				}
			}
		}
		// the door: a double leaf in the middle of the front wall, a transom, posts either side
		int du = g.door(i);
		int v0 = g.va[i];
		x.set(du, s, v0, door(Blocks.CHERRY_DOOR, Direction.NORTH, true).setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT));
		x.set(du, s + 1, v0, door(Blocks.CHERRY_DOOR, Direction.NORTH, false).setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT));
		x.set(du, s + 2, v0, pane(Blocks.GLASS_PANE, true));
		for (int k = -1; k <= 1; k += 2) {
			for (int t = 0; t <= 2; t++) {
				x.set(du + k, s + t, v0, pane(t == 2 ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, true));
			}
			x.set(du + k, s + 3, v0, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
		}
		for (int u : new int[] {du - 2, du + 2}) {
			for (int t = 0; t <= 3; t++) {
				x.set(u, s + t, v0, post());
			}
		}
	}

	// ------------------------------------------------------------------ terraces

	private static boolean gap(Geo g, int i, int u, int v) {
		if (i == 0) {
			return u == LANE_W && (v == 6 || v == 7);
		}
		return i == 1 && g.n == 3 && u == g.ua[1] && (v == 13 || v == 14);
	}

	private static boolean wallLink(Set<Integer> set, int u, int v) {
		return set.contains(u * 1024 + v);
	}

	private static BlockState parapetWall(Set<Integer> set, int u, int v) {
		boolean n = wallLink(set, u, v - 1);
		boolean s = wallLink(set, u, v + 1);
		boolean w = wallLink(set, u - 1, v);
		boolean e = wallLink(set, u + 1, v);
		boolean straight = (n && s && !e && !w) || (e && w && !n && !s);
		return b(Blocks.STONE_BRICK_WALL).setValue(BlockStateProperties.UP, !straight)
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE).setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, w ? WallSide.LOW : WallSide.NONE).setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE);
	}

	/** The roof of volume i: parapet with posts, a paved promenade, lawn, planters, a bench, a table and a small tree. */
	private static void terrace(B x, Geo g, int i, boolean[][] res) {
		int cy = g.c[i];
		int ua = g.ua[i];
		int ub = g.ub[i];
		int va = g.va[i];
		int ve = g.ve[i];
		int na = g.ua[i + 1];
		int nb = g.ub[i + 1];
		int nv = g.va[i + 1];
		// ---- parapet
		Set<Integer> walls = new HashSet<>();
		Set<Integer> posts = new HashSet<>();
		for (int u = ua; u <= ub; u++) {
			for (int v = va; v <= ve; v++) {
				boolean ring = u == ua || u == ub || v == va || v == ve;
				if (!ring || gap(g, i, u, v) || g.in(i + 1, u, v)) {
					continue;
				}
				boolean corner = (u == ua || u == ub) && (v == va || v == ve);
				boolean horiz = v == va || v == ve;
				int k = horiz ? u - ua : v - va;
				boolean edge = isSkipped(g, i, horiz ? u - 1 : u, horiz ? v : v - 1) || isSkipped(g, i, horiz ? u + 1 : u, horiz ? v : v + 1);
				if (corner || k % 5 == 0 || edge) {
					posts.add(u * 1024 + v);
				} else {
					walls.add(u * 1024 + v);
				}
			}
		}
		Set<Integer> linked = new HashSet<>(walls);
		linked.addAll(posts);
		for (int key : walls) {
			x.set(key / 1024, cy + 1, key % 1024, parapetWall(linked, key / 1024, key % 1024));
		}
		for (int key : posts) {
			int u = key / 1024;
			int v = key % 1024;
			int t = g.n - 1;
			boolean underEave = i == g.n - 2 && u >= g.ua[t] - 1 && u <= g.ub[t] + 1 && v >= g.va[t] - 1 && v <= g.ve[t] + 1;
			if (underEave) {
				// a pillar that carries the eave
				for (int y = cy + 1; y < g.c[t]; y++) {
					x.set(u, y, v, y == cy + 1 ? b(Blocks.POLISHED_ANDESITE) : post());
				}
			} else {
				x.set(u, cy + 1, v, b(Blocks.POLISHED_ANDESITE));
				x.set(u, cy + 2, v, b(Blocks.LANTERN));
			}
		}
		// ---- garden window in front of the next volume
		int xl = na + 1;
		int xr = ub - 1;
		int promV = nv - 1;
		boolean sideTree = g.type == 0 && i < g.n - 2 && ub - nb - 1 >= 2 && ve - nv >= 5;
		int trunkU = sideTree ? ub - 1 : xr - 1;
		int trunkV = sideTree ? nv + 3 : va + 2;
		boolean tree = sideTree || xr - xl >= 7;
		for (int u = ua + 1; u < ub; u++) {
			for (int v = va + 1; v < ve; v++) {
				if (g.in(i + 1, u, v)) {
					continue;
				}
				boolean reserved = res[u][v];
				boolean prom = v == promV || (v >= nv && (u == na - 1 || u == nb + 1));
				if (reserved || prom) {
					continue; // stays the paver of the slab
				}
				int dpar = Math.min(Math.min(u - ua, ub - u), Math.min(v - va, ve - v));
				boolean nearTree = tree && Math.abs(u - trunkU) <= 3 && Math.abs(v - trunkV) <= 2;
				boolean benchCell = xr - xl >= 4 && v == va + 1 && u >= xl && u < xl + 3;
				boolean planter = dpar == 1 && ((u + v) / 3) % 2 == 0 && !nearTree && !benchCell && !(i == 0 && u == LANE_W + 1);
				if (planter) {
					x.set(u, cy + 1, v, b(Blocks.MOSS_BLOCK));
					int k = x.hash(u, cy, v, 8);
					BlockState plant = switch (k) {
						case 0, 1 -> b(Blocks.FLOWERING_AZALEA);
						case 2 -> b(Blocks.AZALEA);
						case 3 -> b(Blocks.PINK_TULIP);
						case 4 -> b(Blocks.ALLIUM);
						case 5 -> b(Blocks.CORNFLOWER);
						case 6 -> b(Blocks.FERN);
						default -> b(Blocks.LILY_OF_THE_VALLEY);
					};
					x.set(u, cy + 2, v, plant);
				} else {
					x.set(u, cy + 1, v, b(Blocks.MOSS_CARPET));
				}
			}
		}
		if (xr - xl >= 4) {
			// a bench against the parapet: three stairs, high back to the rail
			for (int u = xl; u < xl + 3; u++) {
				x.set(u, cy + 1, va + 1, stair(Blocks.CHERRY_STAIRS, Direction.NORTH, false));
			}
		}
		if (xr - xl >= 11) {
			// a table with a stool each side
			int tu = xl + 6;
			int tv = va + 2;
			x.set(tu, cy + 1, tv, b(Blocks.CHERRY_FENCE));
			x.set(tu, cy + 2, tv, b(Blocks.CHERRY_PRESSURE_PLATE));
			x.set(tu - 1, cy + 1, tv, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
			x.set(tu + 1, cy + 1, tv, stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
		}
		if (tree) {
			x.set(trunkU, cy + 1, trunkV, b(Blocks.MOSS_BLOCK));
			for (int dy = 2; dy <= 4; dy++) {
				x.set(trunkU, cy + dy, trunkV, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
			}
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					if (du != 0 || dv != 0) {
						x.set(trunkU + du, cy + 4, trunkV + dv, leaves(Blocks.CHERRY_LEAVES));
					}
					x.set(trunkU + du, cy + 5, trunkV + dv, leaves(Blocks.CHERRY_LEAVES));
				}
			}
			x.set(trunkU, cy + 6, trunkV, leaves(Blocks.CHERRY_LEAVES));
			x.set(trunkU + 1, cy + 6, trunkV, leaves(Blocks.CHERRY_LEAVES));
			x.set(trunkU - 1, cy + 6, trunkV, leaves(Blocks.CHERRY_LEAVES));
			x.set(trunkU, cy + 6, trunkV + 1, leaves(Blocks.CHERRY_LEAVES));
			x.set(trunkU, cy + 6, trunkV - 1, leaves(Blocks.CHERRY_LEAVES));
		}
	}

	private static boolean isSkipped(Geo g, int i, int u, int v) {
		return gap(g, i, u, v) || (i + 1 < g.n && g.in(i + 1, u, v));
	}

	// ------------------------------------------------------------------ stairs outside

	/**
	 * A flight of {@code rise} 2-wide steps along the wall, from row {@code r0}, up from layer {@code l0}: cores
	 * of stone bricks under each step, a cheek wall on the outer side (one block above the walking line), a
	 * 2-row landing of the same height and a guard behind it. The first step is met from the front.
	 */
	private static void flight(B x, int c0, int r0, int l0, int rise) {
		Plot p = x.p;
		for (int j = 0; j < rise; j++) {
			int r = r0 + j;
			for (int u = c0; u <= c0 + 1; u++) {
				for (int y = l0; y < l0 + j; y++) {
					x.set(u, y, r, midStone(p, u, y, r));
				}
				x.set(u, l0 + j, r, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
			}
			for (int y = l0; y <= l0 + j + 1; y++) {
				BlockState cheek = y == l0 + j + 1 ? b(Blocks.POLISHED_ANDESITE) : j % 3 == 2 ? post() : y < l0 + 2 ? darkStone(p, c0 - 1, y, r) : midStone(p, c0 - 1, y, r);
				x.set(c0 - 1, y, r, cheek);
			}
		}
		for (int r = r0 + rise; r <= r0 + rise + 1; r++) {
			for (int u = c0; u <= c0 + 1; u++) {
				for (int y = l0; y < l0 + rise - 1; y++) {
					x.set(u, y, r, midStone(p, u, y, r));
				}
				x.set(u, l0 + rise - 1, r, paver(p, u, l0 + rise - 1, r));
			}
			for (int y = l0; y <= l0 + rise; y++) {
				x.set(c0 - 1, y, r, y == l0 + rise ? b(Blocks.POLISHED_ANDESITE) : midStone(p, c0 - 1, y, r));
			}
		}
		int rg = r0 + rise + 2;
		for (int u = c0 - 1; u <= c0 + 1; u++) {
			for (int y = l0; y <= l0 + rise; y++) {
				x.set(u, y, rg, y == l0 + rise ? b(Blocks.POLISHED_ANDESITE) : midStone(p, u, y, rg));
			}
		}
		// lamps on the cheek: at the foot and over the guard
		x.set(c0 - 1, l0 + 2, r0, b(Blocks.LANTERN));
		x.set(c0 - 1, l0 + rise + 1, rg, b(Blocks.LANTERN));
		x.set(c0 + 1, l0 + rise + 1, rg, b(Blocks.LANTERN));
	}

	private static void flights(B x, Geo g) {
		flight(x, 1, 0, 0, 6);
		if (g.n == 3) {
			flight(x, LANE_W + 1, 8, g.c[0] + 1, 5);
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * A hip roof on the rectangle {@code a..bb × v0..e} whose eave ring sits at layer {@code pp}: stairs on every
	 * ring (outer corners set), stepping in one block per layer for {@code tMax} rings, a flat ridge on the rest. The
	 * mass under the stairs is solid, so there is no attic to seal.
	 */
	private static void hip(B x, int a, int bb, int v0, int e, int pp, int tMax, int h, boolean cap, Block eave) {
		Plot p = x.p;
		for (int u = a; u <= bb; u++) {
			for (int v = v0; v <= e; v++) {
				int r = Math.min(Math.min(u - a, bb - u), Math.min(v - v0, e - v));
				if (r < tMax) {
					boolean n = v == v0 + r;
					boolean s = v == e - r;
					boolean w = u == a + r;
					boolean ee = u == bb - r;
					x.set(u, pp + r, v, ringStair(r == 0 ? eave : Blocks.DEEPSLATE_TILE_STAIRS, n, s, w, ee, false));
					for (int y = pp + 1; y < pp + r; y++) {
						x.set(u, y, v, roofStone(p, u, y, v));
					}
				} else {
					for (int y = pp + 1; y < pp + tMax; y++) {
						x.set(u, y, v, roofStone(p, u, y, v));
					}
					x.set(u, pp + tMax, v, b(Blocks.POLISHED_DEEPSLATE));
					if (cap && pp + tMax + 1 <= h - 1) {
						x.set(u, pp + tMax + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					}
				}
			}
		}
	}

	/**
	 * The roof of the last volume: a hip roof with a cherry eave and, when there is height for it, a second small
	 * roof on a glowing glass lantern in the middle (a double roof), a gold-quiet finial and two brick chimneys.
	 */
	private static void roof(B x, Geo g, int h) {
		int i = g.n - 1;
		int pp = g.c[i];
		int a = g.ua[i] - 1;
		int bb = g.ub[i] + 1;
		int v0 = g.va[i] - 1;
		int e = g.ve[i] + 1;
		int minDim = Math.min(bb - a + 1, e - v0 + 1);
		int room = h - 1 - pp;
		int tMax = Math.min(Math.min(3, (minDim - 1) / 2), room - 1);
		if (tMax < 1) {
			return;
		}
		// the lantern: inner rectangle after the rings, if it is at least 5 across and the height allows 2 rings
		int ia = a + tMax;
		int ib = bb - tMax;
		int ja = v0 + tMax;
		int jb = e - tMax;
		int m = Math.min(ib - ia + 1, jb - ja + 1);
		int y1 = pp + tMax + 1;
		int pb = y1 + 2;
		int tt = Math.min(2, (m - 1) / 2);
		while (tt >= 1 && pb + tt + 1 > h - 1) {
			tt--;
		}
		boolean lantern = m >= 5 && tt >= 1;
		hip(x, a, bb, v0, e, pp, tMax, h, !lantern, Blocks.CHERRY_STAIRS);
		if (lantern) {
			for (int u = ia; u <= ib; u++) {
				for (int v = ja; v <= jb; v++) {
					int r = Math.min(Math.min(u - ia, ib - u), Math.min(v - ja, jb - v));
					if (r >= 2) {
						for (int y = y1; y <= pb; y++) {
							x.set(u, y, v, b(Blocks.OCHRE_FROGLIGHT));
						}
					} else if (r == 1) {
						boolean horiz = v == ja + 1 || v == jb - 1;
						boolean corner = (u == ia + 1 || u == ib - 1) && horiz;
						for (int y = y1; y <= y1 + 1; y++) {
							x.set(u, y, v, corner ? b(Blocks.STRIPPED_CHERRY_LOG) : pane(Blocks.GLASS_PANE, horiz));
						}
						x.set(u, pb, v, log(Blocks.STRIPPED_CHERRY_LOG, horiz ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
			hip(x, ia, ib, ja, jb, pb, tt, h, true, Blocks.CHERRY_STAIRS);
			int top = pb + tt + 2;
			if (top <= h - 1) {
				x.set((ia + ib) / 2, top, (ja + jb) / 2, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			}
		}
		// two chimneys through the slopes, either side of the ridge
		int cv = (v0 + e) / 2;
		if (tMax >= 2 && pp + tMax + 3 <= h - 1) {
			for (int cu : new int[] {a + 1, bb - 1}) {
				for (int y = pp + 2; y <= pp + tMax + 2; y++) {
					x.set(cu, y, cv, x.hash(cu, y, cv, 3) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS));
				}
				x.set(cu, pp + tMax + 3, cv, slab(Blocks.DEEPSLATE_BRICK_SLAB));
			}
		}
	}

	/** Inverted stairs one block out under every wall top: the cornice, and the support of the eave. */
	private static void cornice(B x, Geo g, boolean[][] res) {
		for (int i = g.n - 1; i < g.n; i++) {
			int y = g.s[i] + 3;
			int a = g.ua[i] - 1;
			int bb = g.ub[i] + 1;
			int v0 = g.va[i] - 1;
			int e = g.ve[i] + 1;
			for (int u = a; u <= bb; u++) {
				for (int v = v0; v <= e; v++) {
					boolean ring = u == a || u == bb || v == v0 || v == e;
					if (!ring || u < 0 || v < 0 || u >= res.length || v >= res[0].length || res[u][v] || !x.free(u, y, v)) {
						continue;
					}
					if (i > 0 && !g.inner(i - 1, u, v)) {
						continue;
					}
					x.set(u, y, v, ringStair(i == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, v == v0, v == e, u == a, u == bb, true));

				}
			}
		}
	}

	// ------------------------------------------------------------------ interiors

	private static boolean aisle(Geo g, int i, int u, int v) {
		int du = g.door(i);
		return v <= g.va[i] + 2 && u >= du - 2 && u <= du + 2;
	}

	private static void place(B x, Geo g, int i, int u, int y, int v, BlockState st) {
		if (aisle(g, i, u, v) || !x.free(u, y, v)) {
			return;
		}
		x.set(u, y, v, st);
	}

	private static void bed(B x, Geo g, int i, int u, int v, DyeColor col) {
		int s = g.s[i];
		if (aisle(g, i, u, v) || !x.free(u, s, v) || !x.free(u, s, v + 1)) {
			return;
		}
		BlockState base = Blocks.BED.pick(col).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		x.set(u, s, v, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		x.set(u, s, v + 1, base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	/**
	 * Furnishes storey i. Kind 0: workshop (stations along the back wall, chests and barrels, a work island in
	 * wide rooms); 1: home (beds, table and stools, kitchen, shelves, a rug); 2: studio (desk, lectern, map and
	 * enchanting tables, shelves); 3: home and study in one. Furniture only hugs the walls, so the middle and
	 * the way from the door stay 3 or more wide, and everything is under a ceiling 4 blocks up.
	 */
	private static void interior(B x, Geo g, int i, int kind) {
		int s = g.s[i];
		int iu0 = g.ua[i] + 1;
		int iu1 = g.ub[i] - 1;
		int iv0 = g.va[i] + 1;
		int iv1 = g.ve[i] - 1;
		int iw = iu1 - iu0 + 1;
		int id = iv1 - iv0 + 1;
		int cu = (iu0 + iu1) / 2;
		boolean big = iw >= 8 && id >= 7;
		// lanterns hung from the ceiling, 4 apart, clear of the door aisle
		for (int v = iv0 + 3; v <= iv1 - 1; v += 4) {
			for (int u = iu0 + 1; u <= iu1 - 1; u += 4) {
				x.put(u, s + 3, v, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			}
		}
		// pots in the front corners
		place(x, g, i, iu0, s, iv0, b(Blocks.POTTED_FERN));
		place(x, g, i, iu1, s, iv0, b(Blocks.POTTED_FLOWERING_AZALEA));
		switch (kind) {
			case 0 -> workshop(x, g, i, iu0, iu1, iv0, iv1, iw, id, cu);
			case 1 -> home(x, g, i, iu0, iu1, iv0, iv1, big, cu, true);
			case 2 -> studio(x, g, i, iu0, iu1, iv0, iv1, iu0, cu);
			default -> {
				home(x, g, i, iu0, iu1, iv0, iv1, big, cu, false);
				studio(x, g, i, iu0, iu1, iv0, iv1, iu0 + 4, cu);
			}
		}
	}

	private static void workshop(B x, Geo g, int i, int iu0, int iu1, int iv0, int iv1, int iw, int id, int cu) {
		int s = g.s[i];
		BlockState[] stations = {
				b(Blocks.CRAFTING_TABLE),
				b(Blocks.SMITHING_TABLE),
				face(Blocks.BLAST_FURNACE, Direction.NORTH),
				b(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
				face(Blocks.STONECUTTER, Direction.NORTH),
				b(Blocks.FLETCHING_TABLE),
				face(Blocks.LOOM, Direction.NORTH),
		};
		for (int u = iu0; u <= iu1; u++) {
			int k = u - iu0;
			if (k % 2 == 0) {
				place(x, g, i, u, s, iv1, stations[(k / 2) % stations.length]);
			} else {
				place(x, g, i, u, s, iv1, x.hash(u, 0, iv1, 2) == 0 ? b(Blocks.BARREL) : face(Blocks.CHEST, Direction.NORTH));
			}
		}
		for (int v = iv0 + 3; v < iv1; v += 2) {
			place(x, g, i, iu0, s, v, face(Blocks.CHEST, Direction.EAST));
			BlockState rs = (v / 2) % 3 == 0 ? face(Blocks.SMOKER, Direction.WEST) : (v / 2) % 3 == 1 ? face(Blocks.FURNACE, Direction.WEST) : b(Blocks.BARREL);
			place(x, g, i, iu1, s, v, rs);
		}
		if (iw >= 11 && id >= 9) {
			int vm = (iv0 + iv1) / 2;
			place(x, g, i, cu - 1, s, vm, b(Blocks.CRAFTING_TABLE));
			place(x, g, i, cu, s, vm, face(Blocks.ANVIL, Direction.EAST));
			place(x, g, i, cu + 1, s, vm, b(Blocks.SMITHING_TABLE));
		}
	}

	private static void home(B x, Geo g, int i, int iu0, int iu1, int iv0, int iv1, boolean big, int cu, boolean full) {
		int s = g.s[i];
		bed(x, g, i, iu0, iv1 - 1, DyeColor.PINK);
		place(x, g, i, iu0 + 1, s, iv1, b(Blocks.BARREL));
		bed(x, g, i, iu0 + 2, iv1 - 1, DyeColor.WHITE);
		// shelves along the left wall
		for (int v = iv0 + 3; v <= iv0 + 4 && v < iv1 - 2; v++) {
			place(x, g, i, iu0, s, v, b(Blocks.BOOKSHELF));
			place(x, g, i, iu0, s + 1, v, b(Blocks.BOOKSHELF));
			place(x, g, i, iu0, s + 2, v, b(Blocks.BOOKSHELF));
		}
		if (!full) {
			return;
		}
		// table and stools by the right wall
		int tu = iu1 - 2;
		int tv = (iv0 + iv1) / 2 + 1;
		place(x, g, i, tu, s, tv, b(Blocks.CHERRY_FENCE));
		place(x, g, i, tu, s + 1, tv, b(Blocks.CHERRY_PRESSURE_PLATE));
		place(x, g, i, tu - 1, s, tv, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
		place(x, g, i, tu + 1, s, tv, stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
		if (big) {
			// kitchen along the right wall, behind the table
			place(x, g, i, iu1, s, iv1, face(Blocks.SMOKER, Direction.WEST));
			place(x, g, i, iu1, s, iv1 - 1, b(Blocks.CAULDRON));
			place(x, g, i, iu1, s, iv1 - 2, face(Blocks.CHEST, Direction.WEST));
			place(x, g, i, iu1 - 1, s, iv1, b(Blocks.CRAFTING_TABLE));
			// a rug in the middle
			int vm = (iv0 + iv1) / 2 - 1;
			for (int u = cu - 1; u <= cu + 1; u++) {
				for (int v = vm - 1; v <= vm + 1; v++) {
					place(x, g, i, u, s, v, Blocks.CARPET.pick(DyeColor.PINK).defaultBlockState());
				}
			}
		}
	}

	private static void studio(B x, Geo g, int i, int iu0, int iu1, int iv0, int iv1, int from, int cu) {
		int s = g.s[i];
		int to = iu1;
		// back wall: shelves in the corners, desk run with lectern, map table and enchanting table between
		for (int k = 0; k < 2; k++) {
			int u = k == 0 ? from : to;
			place(x, g, i, u, s, iv1, b(Blocks.BOOKSHELF));
			place(x, g, i, u, s + 1, iv1, b(Blocks.BOOKSHELF));
			place(x, g, i, u, s + 2, iv1, b(Blocks.BOOKSHELF));
		}
		int span = to - from - 1;
		BlockState[] run = {
				face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true),
				b(Blocks.CARTOGRAPHY_TABLE),
				slab(Blocks.CHERRY_SLAB),
				b(Blocks.ENCHANTING_TABLE),
				slab(Blocks.CHERRY_SLAB),
		};
		for (int k = 0; k < span; k++) {
			int u = from + 1 + k;
			place(x, g, i, u, s, iv1, run[k % run.length]);
			if (k % 2 == 1) {
				place(x, g, i, u, s, iv1 - 1, stair(Blocks.CHERRY_STAIRS, Direction.NORTH, false));
			}
		}
		// right wall: a long shelf
		for (int v = iv0 + 3; v <= iv0 + 5 && v < iv1 - 1; v++) {
			place(x, g, i, iu1, s, v, b(Blocks.BOOKSHELF));
			place(x, g, i, iu1, s + 1, v, b(Blocks.BOOKSHELF));
			place(x, g, i, iu1, s + 2, v, b(Blocks.BOOKSHELF));
		}
		place(x, g, i, iu0, s, iv1 - 2, face(Blocks.LECTERN, Direction.EAST).setValue(BlockStateProperties.HAS_BOOK, true));
	}

	// ------------------------------------------------------------------ yard (ground level, outside the walls)

	private static BlockState hedge(B x, int u, int v) {
		return leaves(x.hash(u, 0, v, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
	}

	private static void yard(B x, Geo g, int wc, int dc) {
		Plot p = x.p;
		int du = g.door(0);
		int v0 = g.va[0];
		int ub = g.ub[0];
		int ve = g.ve[0];
		// front: a step up to the door, planters and lamp posts, a hedge elsewhere
		for (int u = LANE_W; u <= ub + 1; u++) {
			if (u >= du - 2 && u <= du + 2) {
				x.set(u, 0, v0 - 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
			} else if (u == du - 5 || u == du + 5) {
				for (int y = 0; y <= 2; y++) {
					x.set(u, y, v0 - 1, y == 2 ? b(Blocks.LANTERN) : b(Blocks.STONE_BRICK_WALL));
				}
			} else if ((u >= du - 4 && u <= du - 3) || (u >= du + 3 && u <= du + 4)) {
				x.set(u, 0, v0 - 1, b(Blocks.MOSS_BLOCK));
				x.set(u, 1, v0 - 1, x.hash(u, 1, v0, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.POTTED_FERN));
			} else {
				x.set(u, 0, v0 - 1, hedge(x, u, v0));
			}
		}
		// right and back margins: a hedge, a barrel or a composter now and then, vines on the dark base
		for (int v = v0; v <= ve + 1; v++) {
			int k = x.hash(wc, 0, v, 7);
			x.set(g.ub[0] + 1, 0, v, k == 0 ? b(Blocks.BARREL) : hedge(x, g.ub[0] + 1, v));
			if (v <= ve && x.hash(1, 2, v, 3) == 0) {
				x.put(g.ub[0] + 1, 2, v, vine(Direction.WEST), CitadelCanvas.DECOR);
			}
		}
		for (int u = LANE_W; u <= g.ub[0]; u++) {
			int k = x.hash(u, 0, dc, 7);
			x.set(u, 0, ve + 1, k == 0 ? b(Blocks.COMPOSTER) : hedge(x, u, ve + 1));
			if (x.hash(u, 2, ve, 3) == 0) {
				x.put(u, 2, ve + 1, vine(Direction.NORTH), CitadelCanvas.DECOR);
			}
		}
		// left of the volume, past the stair: a hedge behind the lane
		for (int v = 10; v <= ve + 1; v++) {
			x.set(LANE_W - 1, 0, v, hedge(x, LANE_W - 1, v));
			x.set(0, 0, v, hedge(x, 0, v));
		}
	}
}
