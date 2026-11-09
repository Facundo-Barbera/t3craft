package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.invisibleLight;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.deep;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.light;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.mid;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.stUp;
import static dev.t3craft.hq.citadel.catalog.house.StoneKit.topSlab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A stone manor with a court, built to be lived in. Two storeys (4 free blocks each) in an L or, on wide plots, a U of
 * 5-wide wings round a gated court: a fountain or a roofed well, a cherry tree, benches and flower beds. The back
 * wing opens on the court with a two-deep gallery (a ground arcade under a balcony); the side wings have court doors and arched
 * windows. Gabled roofs meet in the corners (cross gables), with a gable window on every end.
 *
 * <pre>
 *  L (mirrored by seed)                      U (plots 24+ wide)
 *  v0   hedge, gate steps                    ┌─S─┐┄gate┄┌─R─┐
 *  v1   ┌─S─┐ low wall ──gate── ┐            │   │court │   │
 *       │   │   court   (tree,  │            │   ├gallery┤   │
 *       │   │  fountain, bench) ┤            │   │      │   │
 *       ├───┴── gallery ────────┤            ├───┴──B───┴───┤
 *       │ hall(stair) │ kitchen │            └──────────────┘
 *       └──────B──────┴─────────┘
 * </pre>
 *
 * <b>Ground floor:</b> workshop (+ pantry), stair hall, kitchen / dining / lounge. <b>Upper floor:</b> bedrooms, a landing and
 * a study. The stair is a 2-wide flight made by hand in the hall: head-on first step, 2 free blocks over every step, a
 * landing at each end and a rail round the hole. Every room joins the next by 3-wide arched openings and has a door
 * to the court or gallery, so nothing is shut in. Roofs follow the walkability audit: nothing a person could stand on
 * without a way up, no furniture two blocks high.
 *
 * <p>Size: 19–26 × 19–26 × 18+ (the building stops at 38 and sits at the front of a bigger plot).
 */
public final class HouseStoneManorCourt implements Variant {
	private static final int BELT = 5;
	private static final int PLATE = 9;

	@Override
	public String id() {
		return "house_stone_manor_court";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 19;
	}

	@Override
	public int minD() {
		return 19;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 26;
	}

	@Override
	public int maxD() {
		return 26;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Bld(c, p).build();
	}

	// ------------------------------------------------------------------ palette

	/** [palette] roof: slate, verdigris copper or red tile, always over a stone-brick eave. */
	private record Scheme(Block stair, Block alt, Block solid, Block slab) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB),
			new Scheme(Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER_STAIRS.waxed().weathered(),
					Blocks.CUT_COPPER.waxed().oxidized(), Blocks.CUT_COPPER_SLAB.waxed().oxidized()),
			new Scheme(Blocks.BRICK_STAIRS, Blocks.POLISHED_GRANITE_STAIRS, Blocks.BRICKS, Blocks.BRICK_SLAB),
	};

	private enum Kind {
		WORKSHOP, PANTRY, HALL, KITCHEN, DINING, KDINING, LOUNGE, BEDROOM, STUDY, LANDING
	}

	/** [palette] foot → belt → upper: deepslate, stone bricks, calcite band, light stone. */
	private static BlockState mat(Plot p, int u, int y, int v) {
		return switch (y) {
			case 0, 1 -> deep(p, u, y, v);
			case 2 -> p.hash(u, y, v, 5) < 4 ? deep(p, u, y, v) : mid(p, u, y, v); // a plinth three blocks high
			case 3, 4 -> mid(p, u, y, v);
			case BELT -> b(Blocks.CALCITE);
			case 6 -> p.hash(u, y, v, 6) < 1 ? mid(p, u, y, v) : light(p, u, y, v);
			case 7 -> light(p, u, y, v);
			case 8 -> p.hash(u, y, v, 6) == 0 ? b(Blocks.CHISELED_STONE_BRICKS) : light(p, u, y, v);
			default -> b(Blocks.CALCITE);
		};
	}

	// ------------------------------------------------------------------ writer (mirrors the whole build by seed)

	private static final class Wr {
		final CitadelCanvas c;
		final Plot p;
		final boolean mir;

		Wr(CitadelCanvas c, Plot p, boolean mir) {
			this.c = c;
			this.p = p;
			this.mir = mir;
		}

		void set(int u, int y, int v, BlockState s) {
			put(u, y, v, s, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			if (mir) {
				u = p.w() - 1 - u;
				s = s.mirror(Mirror.FRONT_BACK);
			}
			p.put(c, u, y, v, s, layer);
		}

		BlockState at(int u, int y, int v) {
			int uu = mir ? p.w() - 1 - u : u;
			return p.has(uu, y, v) ? p.at(c, uu, y, v) : null;
		}

		boolean free(int u, int y, int v) {
			int uu = mir ? p.w() - 1 - u : u;
			if (!p.has(uu, y, v)) {
				return false;
			}
			BlockState s = p.at(c, uu, y, v);
			return s == null || s.isAir();
		}
	}

	// ------------------------------------------------------------------ the build

	private static final class Bld {
		final CitadelCanvas c;
		final Plot p;
		final Wr w;
		final int pw;
		final int pd;
		final int u0;
		final int u1;
		final int v0;
		final int v1;
		final int bv;
		final int g1;
		final int g2;
		final int cu0;
		final int cu1;
		final int cv0;
		final int cv1;
		final int gc;
		final int cyc;
		final boolean uShape;
		final boolean mir;
		final boolean treeEast;
		final boolean well;
		final Scheme sch;
		final boolean[][] wall;
		final byte[][] ex; // 1 exterior wall running along u, 2 along v, 3 inner wall
		final boolean[][] win;
		final boolean[][] sillAt;
		final List<Room> ground = new ArrayList<>();
		final List<Room> upper = new ArrayList<>();
		final List<Integer> sLines = new ArrayList<>(); // v of the cross walls of the side wings
		final List<Integer> bCols = new ArrayList<>(); // u of the cross walls of the back wing

		Bld(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			pw = p.w();
			pd = p.d();
			int fw = Math.min(pw, 38);
			int fd = Math.min(pd, 38);
			int ox = (pw - fw) / 2;
			u0 = ox + 1;
			u1 = ox + fw - 2;
			v0 = 1;
			v1 = fd - 2;
			bv = v1 - 6;
			g1 = bv - 1;
			g2 = bv - 2;
			Random r = p.rng(0x5A70_4A11L);
			uShape = fw >= 24 && r.nextInt(4) != 0;
			mir = !uShape && r.nextBoolean();
			sch = SCHEMES[r.nextInt(SCHEMES.length)];
			well = r.nextInt(3) == 0;
			treeEast = !uShape || r.nextBoolean();
			cu0 = u0 + 7;
			cu1 = uShape ? u1 - 7 : u1 - 1;
			cv0 = v0 + 1;
			cv1 = g2 - 1;
			gc = (cu0 + cu1) / 2;
			cyc = (cv0 + cv1) / 2;
			w = new Wr(c, p, mir);
			wall = new boolean[pw][pd];
			ex = new byte[pw][pd];
			win = new boolean[pw][pd];
			sillAt = new boolean[pw][pd];
		}

		// -------------------------------------------------------------- rooms

		/** A room: an interior strip cell by (i along, r across). r = 0 touches the outer wall, r = 4 the inner one. */
		final class Room {
			final int strip; // 0 back wing (along u), 1 side wing (along v), 2 right wing (along v)
			final int a0;
			final int a1;
			final boolean up;
			final Kind kind;

			Room(int strip, int a0, int a1, boolean up, Kind kind) {
				this.strip = strip;
				this.a0 = a0;
				this.a1 = a1;
				this.up = up;
				this.kind = kind;
			}

			int len() {
				return a1 - a0 + 1;
			}

			int u(int i, int r) {
				return switch (strip) {
					case 0 -> a0 + i;
					case 1 -> u0 + 1 + r;
					default -> u1 - 1 - r;
				};
			}

			int v(int i, int r) {
				return strip == 0 ? v1 - 1 - r : a0 + i;
			}

			Direction in() {
				return switch (strip) {
					case 0 -> Direction.NORTH;
					case 1 -> Direction.EAST;
					default -> Direction.WEST;
				};
			}

			Direction along() {
				return strip == 0 ? Direction.EAST : Direction.SOUTH;
			}

			boolean contains(int u, int v) {
				return switch (strip) {
					case 0 -> u >= a0 && u <= a1 && v >= bv + 1 && v <= v1 - 1;
					case 1 -> v >= a0 && v <= a1 && u >= u0 + 1 && u <= u0 + 5;
					default -> v >= a0 && v <= a1 && u >= u1 - 5 && u <= u1 - 1;
				};
			}

			/** The wall cell behind r = 0, as {u, v}. */
			int[] behind(int i) {
				return switch (strip) {
					case 0 -> new int[] {a0 + i, v1};
					case 1 -> new int[] {u0, a0 + i};
					default -> new int[] {u1, a0 + i};
				};
			}
		}

		static List<int[]> split(int lo, int hi) {
			int t = hi - lo + 1;
			int n = Math.max(1, Math.min(3, (t + 1) / 7));
			int free = t - (n - 1);
			int base = free / n;
			int extra = free % n;
			List<int[]> out = new ArrayList<>();
			int a = lo;
			for (int i = 0; i < n; i++) {
				int len = base + (i < extra ? 1 : 0);
				out.add(new int[] {a, a + len - 1});
				a += len + 1;
			}
			return out;
		}

		private void layout() {
			Kind[] sg = {Kind.WORKSHOP, Kind.PANTRY, Kind.WORKSHOP};
			Kind[] rg = {Kind.KITCHEN, Kind.PANTRY, Kind.PANTRY};
			List<int[]> sr = split(v0 + 1, bv - 1);
			for (int i = 0; i < sr.size(); i++) {
				ground.add(new Room(1, sr.get(i)[0], sr.get(i)[1], false, sg[i]));
				upper.add(new Room(1, sr.get(i)[0], sr.get(i)[1], true, Kind.BEDROOM));
				if (i + 1 < sr.size()) {
					sLines.add(sr.get(i)[1] + 1);
				}
				if (uShape) {
					ground.add(new Room(2, sr.get(i)[0], sr.get(i)[1], false, rg[i]));
					upper.add(new Room(2, sr.get(i)[0], sr.get(i)[1], true, Kind.BEDROOM));
				}
			}
			sLines.add(bv);
			ground.add(new Room(0, u0 + 1, u0 + 8, false, Kind.HALL));
			upper.add(new Room(0, u0 + 1, u0 + 8, true, Kind.LANDING));
			bCols.add(u0 + 9);
			List<int[]> br = split(u0 + 10, u1 - 1);
			Kind[][] gk = uShape
					? new Kind[][] {{Kind.DINING}, {Kind.DINING, Kind.LOUNGE}, {Kind.DINING, Kind.LOUNGE, Kind.DINING}}
					: new Kind[][] {{Kind.KDINING}, {Kind.KITCHEN, Kind.DINING}, {Kind.KITCHEN, Kind.DINING, Kind.LOUNGE}};
			Kind[][] uk = {{Kind.STUDY}, {Kind.STUDY, Kind.BEDROOM}, {Kind.STUDY, Kind.BEDROOM, Kind.BEDROOM}};
			for (int i = 0; i < br.size(); i++) {
				ground.add(new Room(0, br.get(i)[0], br.get(i)[1], false, gk[br.size() - 1][i]));
				upper.add(new Room(0, br.get(i)[0], br.get(i)[1], true, uk[br.size() - 1][i]));
				if (i + 1 < br.size()) {
					bCols.add(br.get(i)[1] + 1);
				}
			}
		}

		Room roomAt(List<Room> l, int u, int v) {
			for (Room r : l) {
				if (r.contains(u, v)) {
					return r;
				}
			}
			return null;
		}

		boolean inside(int u, int v) {
			boolean s = u >= u0 && u <= u0 + 6 && v >= v0 && v <= v1;
			boolean bk = u >= u0 && u <= u1 && v >= bv && v <= v1;
			boolean rr = uShape && u >= u1 - 6 && u <= u1 && v >= v0 && v <= v1;
			return s || bk || rr;
		}

		// -------------------------------------------------------------- runs (walls)

		/** A straight wall: {@code t} runs along it, {@code o} is the offset out of it (1 = outside). */
		final class Run {
			final boolean alongU;
			final int line;
			final int ta;
			final int tb;
			final Direction out;
			final boolean ext;
			final boolean eave;
			final boolean sills;

			Run(boolean alongU, int line, int ta, int tb, Direction out, boolean ext, boolean eave, boolean sills) {
				this.alongU = alongU;
				this.line = line;
				this.ta = ta;
				this.tb = tb;
				this.out = out;
				this.ext = ext;
				this.eave = eave;
				this.sills = sills;
			}

			int u(int t, int o) {
				return alongU ? t : line + o * out.getStepX();
			}

			int v(int t, int o) {
				return alongU ? line + o * out.getStepZ() : t;
			}

			void set(int t, int y, int o, BlockState s) {
				w.set(u(t, o), y, v(t, o), s);
			}

			BlockState stairIn(Block s, boolean inv) {
				BlockState st = stairs(s, out.getOpposite());
				return inv ? StoneKit.up(st) : st;
			}

			BlockState pane() {
				return StoneKit.pane(alongU);
			}

			BlockState tinted() {
				return StoneKit.tintedPane(alongU);
			}

			Direction minus() {
				return alongU ? Direction.WEST : Direction.NORTH;
			}

			Direction plus() {
				return alongU ? Direction.EAST : Direction.SOUTH;
			}
		}

		private void mark(int u, int v, byte k) {
			if (u >= 0 && u < pw && v >= 0 && v < pd) {
				wall[u][v] = true;
				ex[u][v] = k;
			}
		}

		private void paintRun(Run r) {
			for (int t = r.ta; t <= r.tb; t++) {
				int u = r.u(t, 0);
				int v = r.v(t, 0);
				for (int y = 0; y <= PLATE; y++) {
					w.set(u, y, v, mat(p, u, y, v));
				}
				mark(u, v, (byte) (r.ext ? (r.alongU ? 1 : 2) : 3));
			}
			// [finish] quoins: light corner columns that alternate calcite and polished andesite
			quoin(r.u(r.ta, 0), r.v(r.ta, 0));
			quoin(r.u(r.tb, 0), r.v(r.tb, 0));
		}

		private void quoin(int u, int v) {
			for (int y = 1; y <= PLATE - 1; y++) {
				w.set(u, y, v, y % 2 == 0 ? b(Blocks.CALCITE) : b(Blocks.POLISHED_ANDESITE));
			}
		}

		/** [depth] a buttress block against an outer corner, up to the eave. */
		private void buttress(int u, int v) {
			w.set(u, 0, v, deep(p, u, 0, v));
			for (int y = 1; y <= PLATE - 1; y++) {
				w.set(u, y, v, y % 2 == 0 ? b(Blocks.CALCITE) : b(Blocks.POLISHED_ANDESITE));
			}
		}

		private void paintPart(boolean alongU, int line, int t0, int t1, int door) {
			for (int t = t0; t <= t1; t++) {
				int u = alongU ? t : line;
				int v = alongU ? line : t;
				for (int y = 1; y <= BELT - 1; y++) {
					w.set(u, y, v, mid(p, u, y, v));
				}
				for (int y = BELT + 1; y <= PLATE; y++) {
					w.set(u, y, v, light(p, u, y, v));
				}
				mark(u, v, (byte) 3);
			}
			arch(alongU, line, door, 1);
			arch(alongU, line, door, 6);
		}

		/** A 3-wide, 3-high opening with an arch head (inverted stairs spring from both sides) in a cross wall. */
		private void arch(boolean alongU, int line, int c0, int yb) {
			for (int t = c0 - 1; t <= c0 + 1; t++) {
				int u = alongU ? t : line;
				int v = alongU ? line : t;
				for (int y = yb; y <= yb + 2; y++) {
					w.set(u, y, v, AIR);
				}
				BlockState top;
				if (t == c0) {
					top = AIR;
				} else {
					Direction hi = alongU ? (t < c0 ? Direction.WEST : Direction.EAST) : (t < c0 ? Direction.NORTH : Direction.SOUTH);
					top = stUp(Blocks.STONE_BRICK_STAIRS, hi);
				}
				w.set(u, yb + 3, v, top);
			}
		}

		// -------------------------------------------------------------- facades

		private void archRun(Run r, int c0, int yb) {
			for (int t = c0 - 1; t <= c0 + 1; t++) {
				for (int y = yb; y <= yb + 2; y++) {
					r.set(t, y, 0, AIR);
				}
				r.set(t, yb + 3, 0, t == c0 ? b(Blocks.CHISELED_STONE_BRICKS) : stUp(Blocks.STONE_BRICK_STAIRS, t < c0 ? r.minus() : r.plus()));
			}
		}

		private static List<int[]> cut(List<int[]> iv, int a, int bb) {
			List<int[]> nx = new ArrayList<>();
			for (int[] q : iv) {
				if (bb < q[0] || a > q[1]) {
					nx.add(q);
					continue;
				}
				if (q[0] <= a - 1) {
					nx.add(new int[] {q[0], a - 1});
				}
				if (bb + 1 <= q[1]) {
					nx.add(new int[] {bb + 1, q[1]});
				}
			}
			return nx;
		}

		private void facade(Run r, int lo, int hi, List<Integer> doors, boolean upperDoors, List<Integer> excl) {
			for (int c0 : doors) {
				archRun(r, c0, 1);
				if (upperDoors) {
					archRun(r, c0, 6);
				}
			}
			List<int[]> iv = new ArrayList<>();
			iv.add(new int[] {lo, hi});
			for (int c0 : doors) {
				iv = cut(iv, c0 - 2, c0 + 2);
			}
			for (int e : excl) {
				iv = cut(iv, e - 1, e + 1);
			}
			for (int[] q : iv) {
				int len = q[1] - q[0] + 1;
				if (len < 1) {
					continue;
				}
				int ww = 2;
				int n;
				if (len == 1) {
					ww = 1;
					n = 1;
				} else if (len <= 3) {
					n = 1;
					ww = len % 2 == 1 ? 1 : 2;
				} else {
					n = (len + 2) / 4;
				}
				int used = n * ww + (n - 1) * 2;
				int off = q[0] + (len - used) / 2;
				for (int k = 0; k < n; k++) {
					window(r, off + k * (ww + 2), ww, false);
					window(r, off + k * (ww + 2), ww, true);
				}
			}
			if (!upperDoors) {
				for (int c0 : doors) {
					window(r, c0, 1, true);
				}
			}
		}

		/** [depth] arched window: head of inverted stairs, sill, shutters; upper sills carry a flower box (nothing to stand on). */
		private void window(Run r, int a, int ww, boolean upper) {
			int y = upper ? 7 : 2;
			for (int i = 0; i < ww; i++) {
				int t = a + i;
				BlockState gp = upper ? r.tinted() : r.pane(); // [palette] tinted glass upstairs
				r.set(t, y, 0, gp);
				r.set(t, y + 1, 0, gp);
				r.set(t, y + 2, 0, ww == 1 ? b(Blocks.CHISELED_STONE_BRICKS) : stUp(Blocks.STONE_BRICK_STAIRS, i == 0 ? r.minus() : r.plus()));
				int u = r.u(t, 0);
				int v = r.v(t, 0);
				if (u >= 0 && u < pw && v >= 0 && v < pd) {
					win[u][v] = true;
				}
				if (!r.sills) {
					continue;
				}
				if (!upper) {
					r.set(t, y - 1, 1, r.stairIn(Blocks.STONE_BRICK_STAIRS, false));
					int su = r.u(t, 1);
					int sv = r.v(t, 1);
					if (su >= 0 && su < pw && sv >= 0 && sv < pd) {
						sillAt[su][sv] = true;
					}
				} else if (lvl(r.u(t, 1), r.v(t, 1)) == 0) {
					r.set(t, y - 1, 1, r.stairIn(Blocks.STONE_BRICK_STAIRS, true));
					r.set(t, y, 1, p.hash(r.u(t, 1), y, r.v(t, 1), 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				}
			}
			if (r.sills) {
				for (int k = y; k <= y + 1; k++) {
					r.set(a - 1, k, 1, StoneKit.shutter(Blocks.SPRUCE_TRAPDOOR, r.out));
					r.set(a + ww, k, 1, StoneKit.shutter(Blocks.SPRUCE_TRAPDOOR, r.out));
				}
			}
		}

		// -------------------------------------------------------------- floors

		private int stripOf(int u, int v) {
			if (v >= bv) {
				return 0;
			}
			if (v >= g2 && u > u0 + 6 && (!uShape || u < u1 - 6)) {
				return 0;
			}
			return u <= u0 + 6 ? 1 : 2;
		}

		private void floorCell(int u, int v) {
			Room rg = roomAt(ground, u, v);
			BlockState f0;
			if (rg == null) {
				f0 = p.hash(u, 0, v, 3) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
			} else if (rg.kind == Kind.DINING || rg.kind == Kind.LOUNGE) {
				f0 = p.hash(u, 0, v, 4) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS);
			} else {
				f0 = switch (p.hash(u, 0, v, 6)) {
					case 0 -> b(Blocks.POLISHED_ANDESITE);
					case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
					default -> b(Blocks.STONE_BRICKS);
				};
			}
			w.set(u, 0, v, f0);
			int st = stripOf(u, v);
			boolean beam = st == 0 ? u % 3 == 0 : v % 3 == 0;
			w.set(u, BELT, v, beam ? StoneKit.log(Blocks.STRIPPED_DARK_OAK_LOG, st == 0 ? Direction.Axis.Z : Direction.Axis.X) : b(Blocks.DARK_OAK_PLANKS));
		}

		private void floors() {
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (inside(u, v)) {
						floorCell(u, v);
					}
				}
			}
			int ge = uShape ? cu1 : u1;
			for (int u = cu0; u <= ge; u++) {
				for (int v = g2; v <= g1; v++) {
					floorCell(u, v);
				}
			}
		}

		// -------------------------------------------------------------- the build

		void build() {
			layout();
			floors();
			Run r1 = new Run(false, u0, v0, v1, Direction.WEST, true, true, true);
			Run r2 = new Run(true, v0, u0, u0 + 6, Direction.NORTH, true, false, true);
			Run r3 = new Run(true, v1, u0, u1, Direction.SOUTH, true, true, true);
			Run c1 = new Run(false, u0 + 6, v0, bv, Direction.EAST, false, true, true);
			Run c2 = new Run(true, bv, u0 + 6, uShape ? u1 - 6 : u1, Direction.NORTH, false, true, false);
			Run r4;
			Run r5 = null;
			Run c3 = null;
			if (uShape) {
				r4 = new Run(false, u1, v0, v1, Direction.EAST, true, true, true);
				r5 = new Run(true, v0, u1 - 6, u1, Direction.NORTH, true, false, true);
				c3 = new Run(false, u1 - 6, v0, bv, Direction.WEST, false, true, true);
			} else {
				r4 = new Run(false, u1, g2, v1, Direction.EAST, true, false, true);
			}
			paintRun(r1);
			paintRun(r2);
			paintRun(r3);
			paintRun(c1);
			paintRun(c2);
			paintRun(r4);
			if (uShape) {
				paintRun(r5);
				paintRun(c3);
			}
			buttress(u0 - 1, v0 - 1);
			buttress(u0 - 1, v1 + 1);
			buttress(u1 + 1, v1 + 1);
			if (uShape) {
				buttress(u1 + 1, v0 - 1);
			}
			partitions();
			// doors and windows
			List<Integer> sDoors = new ArrayList<>();
			List<Integer> rDoors = new ArrayList<>();
			List<Integer> bDoors = new ArrayList<>();
			for (Room rm : ground) {
				int cen = (rm.a0 + rm.a1) / 2;
				if (rm.strip == 1) {
					sDoors.add(Math.max(v0 + 2, Math.min(g2 - 3, cen)));
				} else if (rm.strip == 2) {
					rDoors.add(Math.max(v0 + 2, Math.min(g2 - 3, cen)));
				} else if (rm.kind == Kind.HALL) {
					bDoors.add(u0 + 8);
				} else {
					bDoors.add(Math.max(u0 + 12, Math.min(c2.tb - 2, cen)));
				}
			}
			facade(r1, v0 + 2, v1 - 2, new ArrayList<>(), false, sLines);
			facade(r2, u0 + 2, u0 + 4, new ArrayList<>(), false, new ArrayList<>());
			facade(r3, u0 + 2, u1 - 2, new ArrayList<>(), false, bCols);
			facade(c1, v0 + 2, g2 - 3, sDoors, false, sLines);
			facade(c2, u0 + 8, c2.tb - 2, bDoors, true, bCols);
			if (uShape) {
				facade(r4, v0 + 2, v1 - 2, new ArrayList<>(), false, new ArrayList<>());
				facade(r5, u1 - 4, u1 - 2, new ArrayList<>(), false, new ArrayList<>());
				facade(c3, v0 + 2, g2 - 3, rDoors, false, sLines);
			} else {
				facade(r4, bv + 2, v1 - 2, new ArrayList<>(), false, new ArrayList<>());
			}
			// [depth] pilasters on the cross-wall lines: full height up to the eave, with a capital
			pilasters(r1, sLines);
			pilasters(r3, bCols);
			pilasters(c1, sLines);
			if (uShape) {
				pilasters(r4, sLines);
				pilasters(c3, sLines);
			}
			gallery();
			stairHall();
			gableFill();
			roof();
			for (Room rm : ground) {
				furnish(rm);
			}
			for (Room rm : upper) {
				furnish(rm);
			}
			court();
			yard(r1, r2, r3, r4, r5);
			chimneys();
		}

		/** A pilaster one block proud wherever the eave runs right over it (so nothing can be stood on). */
		private void pilasters(Run r, List<Integer> lines) {
			for (int t : lines) {
				if (t <= r.ta + 1 || t >= r.tb - 1) {
					continue;
				}
				int u = r.u(t, 1);
				int v = r.v(t, 1);
				if (lvl(u, v) != 0) {
					continue;
				}
				w.set(u, 0, v, deep(p, u, 0, v));
				w.set(u, 1, v, b(Blocks.DEEPSLATE_BRICKS));
				for (int y = 2; y <= 7; y++) {
					w.set(u, y, v, b(Blocks.POLISHED_ANDESITE));
				}
				w.set(u, 8, v, b(Blocks.CHISELED_STONE_BRICKS));
				sillAt[u][v] = true;
			}
		}

		private void partitions() {
			for (int i = 0; i < sLines.size(); i++) {
				int line = sLines.get(i);
				paintPart(true, line, u0 + 1, u0 + 5, u0 + 3);
				if (uShape) {
					paintPart(true, line, u1 - 5, u1 - 1, u1 - 3);
				}
			}
			for (int q : bCols) {
				paintPart(false, q, bv + 1, v1 - 1, bv + 3);
			}
		}

		// -------------------------------------------------------------- gallery

		private void gallery() {
			int gs = cu0;
			int ge = cu1;
			int span = ge - gs;
			int bays = Math.max(1, span / 4);
			int[] pil = new int[bays + 1];
			for (int i = 0; i <= bays; i++) {
				pil[i] = gs + Math.round((float) i * span / bays);
			}
			for (int u = gs; u <= ge; u++) {
				boolean isPil = false;
				for (int q : pil) {
					if (q == u) {
						isPil = true;
					}
				}
				if (isPil) {
					// [depth] pillars: polished shaft, chiseled capital; the balcony posts stand on them
					for (int y = 1; y <= 3; y++) {
						w.set(u, y, g2, b(Blocks.POLISHED_ANDESITE));
					}
					w.set(u, 4, g2, b(Blocks.CHISELED_STONE_BRICKS));
					for (int y = 6; y <= 8; y++) {
						w.set(u, y, g2, b(Blocks.POLISHED_ANDESITE));
					}
					w.set(u, 9, g2, b(Blocks.CHISELED_STONE_BRICKS));
				} else {
					w.set(u, 6, g2, slab(Blocks.STONE_BRICK_SLAB)); // balcony parapet, a step high
				}
			}
			for (int i = 0; i < bays; i++) {
				int a = pil[i] + 1;
				int bb = pil[i + 1] - 1;
				if (bb - a >= 2) {
					w.set(a, 4, g2, stUp(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
					w.set(bb, 4, g2, stUp(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
				}
				int mid = (a + bb) / 2;
				w.put(mid, 4, g2, StoneKit.lantern(true), CitadelCanvas.HANGING);
				w.set(mid, 4, g1, invisibleLight(13));
				w.set(mid, 8, g1, invisibleLight(13));
			}
		}

		// -------------------------------------------------------------- stair

		/**
		 * A 2-wide flight along u in the hall, against the outer wall: 5 steps, the first met head-on, the top step in the
		 * floor's own layer, a hole of 4 cells over the flight with a rail round it.
		 */
		private void stairHall() {
			int a = u0 + 2;
			int[] lanes = {v1 - 1, v1 - 2};
			for (int j = 0; j < 5; j++) {
				int u = a + j;
				for (int v : lanes) {
					for (int y = 1; y <= j; y++) {
						w.set(u, y, v, b(Blocks.STONE_BRICKS));
					}
					w.set(u, 1 + j, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
					if (j <= 3) {
						w.set(u, BELT, v, AIR);
					}
				}
			}
			// rail on the upper floor: along the open side of the hole and behind the first step
			int rv = v1 - 3;
			List<int[]> rail = new ArrayList<>();
			for (int u = a - 1; u <= a + 3; u++) {
				rail.add(new int[] {u, rv});
			}
			rail.add(new int[] {a - 1, v1 - 1});
			rail.add(new int[] {a - 1, v1 - 2});
			for (int[] q : rail) {
				boolean n = has(rail, q[0], q[1] - 1);
				boolean e = has(rail, q[0] + 1, q[1]);
				boolean s = has(rail, q[0], q[1] + 1) || q[1] + 1 == v1; // the outer wall
				boolean wst = has(rail, q[0] - 1, q[1]) || q[0] - 1 == u0;
				w.set(q[0], 6, q[1], Blocks.DARK_OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, n)
						.setValue(BlockStateProperties.EAST, e).setValue(BlockStateProperties.SOUTH, s)
						.setValue(BlockStateProperties.WEST, wst));
			}
		}

		private static boolean has(List<int[]> l, int u, int v) {
			for (int[] q : l) {
				if (q[0] == u && q[1] == v) {
					return true;
				}
			}
			return false;
		}

		// -------------------------------------------------------------- roof

		/** Roof level of a cell (0 = eave row), -1 where there is none. Wings are gables; they cross at the corners. */
		int lvl(int u, int v) {
			int best = -1;
			if (u >= u0 - 1 && u <= u1 + 1 && v >= g2 - 1 && v <= v1 + 1) {
				best = Math.max(best, Math.min(v - (g2 - 1), (v1 + 1) - v));
			}
			if (u >= u0 - 1 && u <= u0 + 7 && v >= v0 - 1 && v <= v1) {
				best = Math.max(best, Math.min(u - (u0 - 1), (u0 + 7) - u));
			}
			if (uShape && u >= u1 - 7 && u <= u1 + 1 && v >= v0 - 1 && v <= v1) {
				best = Math.max(best, Math.min(u - (u1 - 7), (u1 + 1) - u));
			}
			return best;
		}

		private void gableFill() {
			for (int u = 0; u < pw; u++) {
				for (int v = 0; v < pd; v++) {
					if (!wall[u][v]) {
						continue;
					}
					int l = lvl(u, v);
					if (l < 2) {
						continue;
					}
					int top = PLATE - 1 + l;
					byte k = ex[u][v];
					boolean peak = false;
					if (k == 1 || k == 2) {
						int a = k == 1 ? lvl(u - 1, v) : lvl(u, v - 1);
						int bb = k == 1 ? lvl(u + 1, v) : lvl(u, v + 1);
						peak = l >= 4 && a < l && bb < l;
					}
					for (int y = PLATE + 1; y <= top; y++) {
						w.set(u, y, v, peak && y <= PLATE + 2 ? StoneKit.tintedPane(k == 1) : light(p, u, y, v));
					}
				}
			}
		}

		private void roof() {
			Direction[] ds = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
			for (int v = 0; v < pd; v++) {
				for (int u = 0; u < pw; u++) {
					int l = lvl(u, v);
					if (l < 0) {
						continue;
					}
					int y = PLATE + l;
					Direction high = null;
					int best = l;
					for (Direction d : ds) {
						int n = lvl(u + d.getStepX(), v + d.getStepZ());
						if (n > best) {
							best = n;
							high = d;
						}
					}
					if (high == null) {
						w.set(u, y, v, b(sch.solid()));
						// [finish] a copper rod where a ridge ends in the air, a slab cap along it
						boolean tip = false;
						for (Direction d : ds) {
							if (lvl(u + d.getStepX(), v + d.getStepZ()) < 0) {
								tip = true;
							}
						}
						w.set(u, y + 1, v, slab(sch.slab()));
						if (tip && l >= 3) {
							w.set(u, y + 2, v, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
						}
					} else {
						// [palette] tile courses: every third row of the slope in the alternate tile
						Block blk = l == 0 ? Blocks.STONE_BRICK_STAIRS : l % 3 == 2 ? sch.alt() : sch.stair();
						w.set(u, y, v, stairs(blk, high));
					}
				}
			}
		}

		private void chimneys() {
			for (Room rm : ground) {
				if (rm.kind == Kind.KITCHEN || rm.kind == Kind.KDINING) {
					if (rm.strip == 0) {
						stack(rm.a0 + 1, v1 - 4, true);
					} else {
						stack(u1 - 3, rm.a0 + 1, false);
					}
				}
				if (rm.kind == Kind.WORKSHOP && rm.strip == 1) {
					stack(u0 + 3, rm.a0 + 1, false);
				}
			}
		}

		private void stack(int u, int v, boolean alongU) {
			int ry = PLATE + lvl(u, v);
			for (int k = 0; k < 2; k++) {
				int uu = alongU ? u + k : u;
				int vv = alongU ? v : v + k;
				for (int y = ry + 1; y <= ry + 2; y++) {
					w.set(uu, y, vv, p.hash(uu, y, vv, 3) == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS));
				}
				w.set(uu, ry + 3, vv, slab(Blocks.DEEPSLATE_BRICK_SLAB));
			}
		}

		// -------------------------------------------------------------- furniture

		private BlockState fac(Block blk, Direction d) {
			BlockState s = blk.defaultBlockState();
			if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
			}
			if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) {
				s = s.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
			}
			return s;
		}

		private BlockState bed(DyeColor col, BedPart part, Direction toHead) {
			return b(Blocks.BED.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, toHead).setValue(BlockStateProperties.BED_PART, part);
		}

		private void fur(Room rm, int i, int r, int dy, BlockState s) {
			if (i < 0 || i >= rm.len() || r < 0 || r > 4) {
				return;
			}
			w.set(rm.u(i, r), (rm.up ? 6 : 1) + dy, rm.v(i, r), s);
		}

		private boolean winBehind(Room rm, int i) {
			int[] q = rm.behind(i);
			return win[q[0]][q[1]];
		}

		private void furnish(Room rm) {
			int n = rm.len();
			int mid = (n - 1) / 2;
			Direction in = rm.in();
			Direction out = in.getOpposite();
			switch (rm.kind) {
				case WORKSHOP -> {
					Block[] st = {Blocks.CRAFTING_TABLE, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE, Blocks.ANVIL, Blocks.LOOM,
							Blocks.BLAST_FURNACE};
					int k = 0;
					for (int i = 1; i < n - 1; i++) {
						if (i % 3 == 0) {
							continue;
						}
						fur(rm, i, 0, 0, fac(st[k++ % st.length], in));
					}
					fur(rm, 0, 4, 0, StoneKit.barrel());
					fur(rm, n - 1, 4, 0, fac(Blocks.CHEST, out));
					fur(rm, 0, 0, 0, StoneKit.barrel());
					fur(rm, n - 1, 0, 0, fac(Blocks.CHEST, in));
					lanterns(rm, new int[] {mid}, 3);
				}
				case PANTRY -> {
					for (int i = 0; i < n; i++) {
						BlockState s = switch (i % 3) {
							case 0 -> StoneKit.barrel();
							case 1 -> fac(Blocks.CHEST, in);
							default -> b(Blocks.HAY_BLOCK);
						};
						fur(rm, i, 0, 0, s);
					}
					fur(rm, 0, 4, 0, StoneKit.barrel());
					fur(rm, n - 1, 4, 0, StoneKit.barrel());
					lanterns(rm, new int[] {mid}, 3);
				}
				case HALL -> {
					for (int i = 0; i < n; i++) {
						fur(rm, i, 3, 0, b(Blocks.CARPET.pick(DyeColor.RED)));
					}
					fur(rm, 0, 4, 0, b(Blocks.POTTED_FLOWERING_AZALEA));
					lanterns(rm, new int[] {2, 6}, 3);
				}
				case KITCHEN, KDINING -> {
					Block[] st = {Blocks.FURNACE, Blocks.SMOKER, Blocks.CRAFTING_TABLE, Blocks.BARREL, Blocks.FURNACE, Blocks.SMOKER};
					int cnt = rm.kind == Kind.KDINING ? Math.min(3, n) : n;
					for (int i = 0; i < cnt; i++) {
						fur(rm, i, 0, 0, st[i % st.length] == Blocks.BARREL ? StoneKit.barrel() : fac(st[i % st.length], in));
					}
					if (rm.kind == Kind.KITCHEN) {
						for (int i = mid - 1; i <= mid + 1; i++) {
							fur(rm, i, 2, 0, slab(Blocks.SMOOTH_STONE_SLAB));
						}
						fur(rm, n - 1, 4, 0, fac(Blocks.CHEST, out));
						fur(rm, 0, 4, 0, StoneKit.barrel());
					} else {
						table(rm, Math.max(3, n - 4), n - 2, in);
					}
					lanterns(rm, new int[] {mid}, 3);
				}
				case DINING -> {
					table(rm, 1, n - 2, in);
					fur(rm, 0, 4, 0, b(Blocks.POTTED_CHERRY_SAPLING));
					fur(rm, n - 1, 4, 0, StoneKit.barrel());
					lanterns(rm, new int[] {1, n - 2}, 4);
				}
				case LOUNGE -> {
					for (int i = 1; i < n - 1; i++) {
						for (int r = 1; r <= 3; r++) {
							fur(rm, i, r, 0, b(Blocks.CARPET.pick(DyeColor.RED)));
						}
					}
					for (int i = mid - 1; i <= mid + 1; i++) {
						fur(rm, i, 3, 0, stairs(Blocks.DARK_OAK_STAIRS, in));
					}
					fur(rm, mid, 2, 0, slab(Blocks.DARK_OAK_SLAB));
					for (int i = 0; i < n; i += 2) {
						if (!winBehind(rm, i)) {
							for (int y = 0; y <= 3; y++) {
								fur(rm, i, 0, y, b(Blocks.BOOKSHELF));
							}
						}
					}
					lanterns(rm, new int[] {1, n - 2}, 4);
				}
				case BEDROOM -> {
					DyeColor[] cols = {DyeColor.RED, DyeColor.BLUE, DyeColor.WHITE, DyeColor.CYAN};
					int bi = 1;
					int[] pos = n >= 6 ? new int[] {1, n - 2} : new int[] {mid};
					for (int i : pos) {
						DyeColor col = cols[p.hash(rm.u(i, 0), 6, rm.v(i, 0), cols.length)];
						fur(rm, i, 0, 0, bed(col, BedPart.HEAD, out));
						fur(rm, i, 1, 0, bed(col, BedPart.FOOT, out));
						bi++;
					}
					if (n >= 6) {
						fur(rm, mid, 0, 0, fac(Blocks.CHEST, in));
					}
					fur(rm, 0, 4, 0, b(Blocks.POTTED_CHERRY_SAPLING));
					fur(rm, n - 1, 4, 0, StoneKit.barrel());
					for (int i = 1; i < n - 1; i++) {
						fur(rm, i, 2, 0, b(Blocks.CARPET.pick(DyeColor.GRAY)));
					}
				}
				case STUDY -> {
					for (int i = 0; i < n; i++) {
						if (winBehind(rm, i) || i == mid || lvl(rm.u(i, 0), rm.v(i, 0)) > 2) {
							continue; // a tall shelf only where the roof is right over it (nothing to stand on top)
						}
						for (int y = 0; y <= 3; y++) {
							fur(rm, i, 0, y, b(Blocks.BOOKSHELF));
						}
					}
					for (int i = mid - 1; i <= mid + 1; i++) {
						fur(rm, i, 2, 0, slab(Blocks.DARK_OAK_SLAB));
					}
					fur(rm, mid, 3, 0, stairs(Blocks.DARK_OAK_STAIRS, in));
					fur(rm, mid, 0, 0, fac(Blocks.LECTERN, in));
					fur(rm, 0, 4, 0, b(Blocks.CARTOGRAPHY_TABLE));
					fur(rm, n - 1, 4, 0, fac(Blocks.CHEST, out));
				}
				case LANDING -> {
					for (int i = 0; i < n; i++) {
						fur(rm, i, 3, 0, b(Blocks.CARPET.pick(DyeColor.BROWN)));
						fur(rm, i, 4, 0, b(Blocks.CARPET.pick(DyeColor.BROWN)));
					}
					fur(rm, n - 1, 4, 0, b(Blocks.POTTED_FERN));
				}
				default -> {
				}
			}
			// invisible light: the room is lit without a lamp on the floor
			for (int i = 1; i < n; i += 3) {
				w.set(rm.u(i, 2), rm.up ? 8 : 4, rm.v(i, 2), invisibleLight(13));
			}
		}

		/** A dining table on row 2 from {@code i0} to {@code i1} with chairs on both sides. */
		private void table(Room rm, int i0, int i1, Direction in) {
			for (int i = i0; i <= i1; i++) {
				fur(rm, i, 2, 0, slab(Blocks.DARK_OAK_SLAB));
				if ((i - i0) % 2 == 0) {
					fur(rm, i, 1, 0, stairs(Blocks.SPRUCE_STAIRS, in.getOpposite()));
					fur(rm, i, 3, 0, stairs(Blocks.SPRUCE_STAIRS, in));
				}
			}
		}

		private void lanterns(Room rm, int[] is, int r) {
			for (int i : is) {
				if (i >= 0 && i < rm.len()) {
					w.put(rm.u(i, r), 4, rm.v(i, r), StoneKit.lantern(true), CitadelCanvas.HANGING);
				}
			}
		}

		// -------------------------------------------------------------- court

		private void placeFree(int u, int y, int v, BlockState s) {
			if (w.free(u, y, v)) {
				w.set(u, y, v, s);
			}
		}

		private void court() {
			for (int u = cu0; u <= cu1; u++) {
				for (int v = cv0; v <= cv1; v++) {
					boolean path = Math.abs(u - gc) <= 1;
					boolean ring = u == cu0 || u == cu1 || v == cv0 || v == cv1;
					BlockState f;
					if (path) {
						f = switch (p.hash(u, 0, v, 5)) {
							case 0 -> b(Blocks.POLISHED_ANDESITE);
							case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
							default -> b(Blocks.STONE_BRICKS);
						};
					} else if (ring && p.hash(u, 9, v, 3) == 0) {
						f = b(Blocks.POLISHED_DEEPSLATE);
					} else {
						f = switch (p.hash(u, 0, v, 6)) {
							case 0 -> b(Blocks.MOSSY_COBBLESTONE);
							case 1 -> b(Blocks.ANDESITE);
							default -> b(Blocks.COBBLESTONE);
						};
					}
					w.set(u, 0, v, f);
				}
			}
			int cw = cu1 - cu0 + 1;
			int ch = cv1 - cv0 + 1;
			int rimR = cw >= 11 && ch >= 9 ? 3 : ch >= 7 ? 2 : 1;
			if (well) {
				well(gc - 1, cyc - 1);
			} else {
				fountain(gc, cyc, rimR);
			}
			// the tree in a front corner, a bench on the other side of the gate
			int tu = treeEast ? cu1 - 1 : cu0 + 1;
			tree(tu, cv0 + 1, cw >= 10 && ch >= 8);
			int bu = treeEast ? gc - 3 : gc + 3;
			Direction back = treeEast ? Direction.WEST : Direction.EAST;
			for (int v = cv0; v <= cv0 + 1; v++) {
				w.set(bu, 1, v, stairs(Blocks.STONE_BRICK_STAIRS, back));
			}
			// flower beds in the back corners and lamps beside the path
			for (int u : new int[] {cu0, cu1}) {
				w.set(u, 0, cv1, b(Blocks.MOSS_BLOCK));
				w.set(u, 1, cv1, p.hash(u, 1, cv1, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			}
			for (int s : new int[] {-1, 1}) {
				int u = gc + s * (rimR == 3 ? 4 : 3);
				if (u <= cu0 || u >= cu1) {
					continue;
				}
				// (one row in from the roof's eave, so nobody can stand on a lantern under it)
				w.set(u, 1, cv1 - 1, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
				w.set(u, 2, cv1 - 1, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
				w.set(u, 3, cv1 - 1, StoneKit.lantern(false));
			}
		}

		/** A basin of radius {@code rim} - 1 in water inside a slab rim; a pedestal with a lantern in the middle. */
		private void fountain(int cx, int cy, int rim) {
			for (int du = -rim; du <= rim; du++) {
				for (int dv = -rim; dv <= rim; dv++) {
					boolean edge = Math.abs(du) == rim || Math.abs(dv) == rim;
					int u = cx + du;
					int v = cy + dv;
					if (edge) {
						w.set(u, 0, v, b(Blocks.STONE_BRICKS));
						w.set(u, 1, v, slab(Blocks.STONE_BRICK_SLAB));
					} else {
						w.set(u, 0, v, b(Blocks.WATER));
					}
				}
			}
			if (rim >= 2) {
				w.set(cx, 0, cy, b(Blocks.STONE_BRICKS));
				w.set(cx, 1, cy, b(Blocks.CHISELED_STONE_BRICKS));
			} else {
				w.set(cx, 1, cy, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			}
			w.set(cx, 2, cy, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
			w.set(cx, 3, cy, StoneKit.lantern(false));
		}

		/** A roofed well on a 3x3 footprint, tall enough to walk round (the roof is 5 up). */
		private void well(int ou, int ov) {
			for (int du = 0; du < 3; du++) {
				for (int dv = 0; dv < 3; dv++) {
					int u = ou + du;
					int v = ov + dv;
					boolean centre = du == 1 && dv == 1;
					boolean corner = du != 1 && dv != 1;
					w.set(u, 0, v, centre ? b(Blocks.WATER) : p.hash(u, 0, v, 3) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
					if (!centre && !corner) {
						w.set(u, 1, v, slab(Blocks.STONE_BRICK_SLAB));
					}
					if (corner) {
						for (int y = 1; y <= 4; y++) {
							w.set(u, y, v, StoneKit.log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
						}
						w.set(u, 5, v, slab(Blocks.SPRUCE_SLAB));
					} else if (centre) {
						w.set(u, 5, v, b(Blocks.SPRUCE_PLANKS));
						w.set(u, 6, v, slab(Blocks.SPRUCE_SLAB));
					} else {
						Direction high = du == 1 ? (dv == 0 ? Direction.SOUTH : Direction.NORTH) : (du == 0 ? Direction.EAST : Direction.WEST);
						w.set(u, 5, v, stairs(Blocks.SPRUCE_STAIRS, high));
					}
				}
			}
			w.put(ou + 1, 4, ov + 1, StoneKit.lantern(true), CitadelCanvas.HANGING);
		}

		private void tree(int tu, int tv, boolean big) {
			w.set(tu, 0, tv, b(Blocks.MOSS_BLOCK));
			for (int y = 1; y <= 6; y++) {
				w.set(tu, y, tv, StoneKit.log(Blocks.CHERRY_LOG, Direction.Axis.Y));
			}
			// the crown starts at head height + 3 and never sits under a roof (nothing to stand on); a small court gets a slim one
			for (int y = 5; y <= 8; y++) {
				int r = big && y <= 6 ? 2 : 1;
				for (int du = -r; du <= r; du++) {
					for (int dv = -r; dv <= r; dv++) {
						boolean corner = Math.abs(du) == r && Math.abs(dv) == r;
						if ((du == 0 && dv == 0 && y <= 6) || (y != 7 && corner)) {
							continue;
						}
						// holes only in the outer ring of the low layers: a gap in a column would leave a 2-high pocket to stand in
						boolean ring = Math.abs(du) == 2 || Math.abs(dv) == 2;
						if ((ring && y <= 6 && p.hash(tu + du, y, tv + dv, 6) == 0) || lvl(tu + du, tv + dv) >= 0) {
							continue;
						}
						placeFree(tu + du, y, tv + dv, leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
			// a bed of ferns and petals round the trunk
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					if ((du != 0 || dv != 0) && w.free(tu + du, 1, tv + dv) && p.hash(tu + du, 1, tv + dv, 3) != 0) {
						w.set(tu + du, 0, tv + dv, b(Blocks.MOSS_BLOCK));
						w.set(tu + du, 1, tv + dv, p.hash(tu + du, 2, tv + dv, 2) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.PINK_PETALS.defaultBlockState());
					}
				}
			}
		}

		// -------------------------------------------------------------- the yard and the gate

		private void yard(Run r1, Run r2, Run r3, Run r4, Run r5) {
			// ledges round the outer walls: a planter bed, the step the sills rest on
			for (Run r : new Run[] {r1, r2, r3, r4, r5}) {
				if (r == null || !r.ext) {
					continue;
				}
				for (int t = r.ta; t <= r.tb; t++) {
					int u = r.u(t, 1);
					int v = r.v(t, 1);
					w.set(u, 0, v, deep(p, u, 0, v));
					if (!sillAt[u][v]) {
						BlockState pl = switch (p.hash(u, 1, v, 5)) {
							case 0 -> Blocks.FERN.defaultBlockState();
							case 1 -> Blocks.AZALEA.defaultBlockState();
							case 2 -> Blocks.FLOWERING_AZALEA.defaultBlockState();
							default -> null;
						};
						if (pl != null && w.free(u, 1, v)) {
							w.set(u, 1, v, pl);
						}
					}
				}
			}
			// the gate: two pillars, a lintel, open gates, steps outside
			int ge = uShape ? cu1 : u1;
			for (int s : new int[] {-2, 2}) {
				int u = gc + s;
				w.set(u, 0, v0, deep(p, u, 0, v0));
				w.set(u, 1, v0, b(Blocks.DEEPSLATE_BRICKS));
				w.set(u, 2, v0, b(Blocks.STONE_BRICKS));
				w.set(u, 3, v0, b(Blocks.POLISHED_ANDESITE));
				w.set(u, 4, v0, b(Blocks.CHISELED_STONE_BRICKS));
				w.set(u, 5, v0, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
				w.set(u, 6, v0, StoneKit.lantern(false));
			}
			for (int s = -1; s <= 1; s++) {
				int u = gc + s;
				w.set(u, 0, v0, b(Blocks.POLISHED_DEEPSLATE));
				w.set(u, 0, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
				if (s == 0) {
					w.set(u, 4, v0, b(Blocks.CHISELED_STONE_BRICKS));
				} else {
					w.set(u, 4, v0, stUp(Blocks.STONE_BRICK_STAIRS, s < 0 ? Direction.WEST : Direction.EAST));
					w.set(u, 1, v0, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
							.setValue(BlockStateProperties.OPEN, true));
				}
			}
			// the low wall round the court, with connections set by hand
			List<int[]> lw = new ArrayList<>();
			for (int u = cu0; u <= ge; u++) {
				if (Math.abs(u - gc) <= 1) {
					continue;
				}
				lw.add(new int[] {u, v0});
			}
			if (!uShape) {
				for (int v = v0 + 1; v <= g2 - 1; v++) {
					lw.add(new int[] {u1, v});
				}
			}
			for (int[] q : lw) {
				int u = q[0];
				int v = q[1];
				if (Math.abs(u - gc) == 2 && v == v0) {
					continue; // the gate pillar
				}
				boolean n = has(lw, u, v - 1);
				boolean e = has(lw, u + 1, v) || Math.abs(u + 1 - gc) == 2 && v == v0 || (u + 1 < pw && wall[u + 1][v]);
				boolean s = has(lw, u, v + 1) || (v + 1 < pd && wall[u][v + 1]);
				boolean wst = has(lw, u - 1, v) || Math.abs(u - 1 - gc) == 2 && v == v0 || (u - 1 >= 0 && wall[u - 1][v]);
				w.set(u, 0, v, b(Blocks.STONE_BRICKS));
				w.set(u, 1, v, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, n, e, s, wst, !((n && s && !e && !wst) || (e && wst && !n && !s))));
			}
			if (!uShape) {
				w.set(u1, 2, v0, StoneKit.wallBlock(Blocks.STONE_BRICK_WALL, false, false, false, false, true));
				w.set(u1, 3, v0, StoneKit.lantern(false));
			}
			// hedge along the street and down the open side
			for (int u = cu0; u <= ge; u++) {
				if (Math.abs(u - gc) <= 2) {
					continue;
				}
				w.set(u, 0, 0, p.hash(u, 0, 0, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
			}
			if (!uShape) {
				for (int v = 0; v <= g2 - 1; v++) {
					w.set(u1 + 1, 0, v, p.hash(u1, 0, v, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
	}
}
