package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A dry rock garden (karesansui) to sit and look at: a raised sea of raked gravel with a cherry-wood
 * veranda (engawa) on one side, rock islands, a stone lantern and a small tree.
 *
 * <pre>
 *   gate (torii) ── stepping stones ──╮
 *   ░░░ raked sea ░░░  ◖◖ rocks ░░░   │  kerb (log)
 *   ░░░ ripples round  ◖  ░░ toro ░░  ╰─ engawa: open bench row, roofed walk (posts, tile roof,
 *   pine ░░░░░░░░░░░░░░░░░░░░░░░░░░░      shoji wall), cushions
 * </pre>
 *
 * <b>The sea.</b> Ridges are full blocks (gravel, sand or calcite, by seed) and grooves are bottom slabs of a
 * lighter stone, so the rake lines have real relief (half a block). Round every island the lines turn into
 * 4 ripple rings; away from the islands they run parallel to the engawa, across it or on the diagonal (by seed).
 * <b>Islands.</b> Rock groups of 3 or 5 stones of different sizes (a tall standing stone, a leaning one, small
 * ones) on a moss base with carpets and ferns (plants only on moss); a stone lantern (toro) with a tiled roof;
 * a small pine or cherry whose canopy starts 4 blocks up so nobody walks under leaves. <b>Walls.</b> Deepslate
 * curb, calcite plaster and a tile cap with cherry pillars, low at the front; the front gate is a small torii.
 *
 * <p>Function: the veranda has an open bench row facing the garden (stairs as seats, cushions) and a roofed
 * walk with posts, a shoji wall and floor cushions; a path of stepping stones (flush with the garden floor, so
 * no jumps) leads from the gate, over a half-slab step from the street, to the veranda and never touches the
 * raked sea. Every roof is at least 3 blocks above the floor.
 *
 * <p>Size: 12-24 x 12-24 x 5+; the plot sizes grow with the square root of the scale and the garden grows with
 * them without stretching the rocks: more rock islands, lanterns and trees, a longer veranda with more benches,
 * a bigger raked sea. By seed: the veranda's side (back, left or right), the rake pattern and its phase, the
 * palette, the gate's flank, the rock layouts and the tree. [depth] relief rake, stepped roofs, pillars, caps,
 * beams. [palette] gravel/sand/calcite + deepslate + cherry + stone greys + moss. [finish] ferns, carpets,
 * pebbles, lanterns, cushions.
 */
public final class GardenZenKaresansui implements Variant {
	private static final Block[] ROCK = {Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.COBBLED_DEEPSLATE};
	private static final Block[] ROCK_SLAB = {Blocks.STONE_SLAB, Blocks.ANDESITE_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB,
			Blocks.STONE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB};
	private static final Block[] ROCK_STAIRS = {Blocks.STONE_STAIRS, Blocks.ANDESITE_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
			Blocks.STONE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS};
	private static final Block[] STEP = {Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_BASALT,
			Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.ANDESITE};

	private static final Block[] STEP_LIGHT = {Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS, Blocks.STONE, Blocks.MOSSY_STONE_BRICKS, Blocks.SMOOTH_STONE,
			Blocks.STONE_BRICKS};

	private static final Direction[] LOCAL ={Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	@Override
	public String id() {
		return "garden_zen_karesansui";
	}

	@Override
	public Family family() {
		return Family.GARDEN;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 24;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Zen(c, p).run();
	}

	private static long key(int u, int v) {
		return ((long) (u + 1024) << 24) | (long) (v + 1024);
	}

	private static int ku(long k) {
		return (int) (k >> 24) - 1024;
	}

	private static int kv(long k) {
		return (int) (k & 0xFFFFFFL) - 1024;
	}

	private static final class Zen {
		static final byte FREE = 0;
		static final byte WALL = 1;
		static final byte DECK = 2;
		static final byte ISLAND = 3;
		static final byte PATH = 4;
		static final byte RESERVED = 7;

		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final int h;
		final Random rnd;
		final byte[][] kind;
		final float[][] dist;

		// engawa frame: a runs along the veranda, b grows towards its wall (b = sb - 1 is the wall)
		int side;
		int sa;
		int sb;
		int kerbB;
		int a0;
		int a1;
		int pa;
		int cu; // gate centre (u)
		int pal;
		int mode;
		int phase;

		Zen(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.h = p.h();
			this.rnd = p.rng(0x6A2E5C1L);
			this.kind = new byte[w][d];
			this.dist = new float[w][d];
		}

		// ------------------------------------------------------------------ frame helpers

		int uOf(int a, int bb) {
			return side == 0 ? a : side == 1 ? w - 1 - bb : bb;
		}

		int vOf(int a, int bb) {
			return side == 0 ? bb : a;
		}

		int aOf(int u, int v) {
			return side == 0 ? u : v;
		}

		int bOf(int u, int v) {
			return side == 0 ? v : side == 1 ? w - 1 - u : u;
		}

		/** Local direction of +b (towards the veranda's wall). */
		Direction toWall() {
			return side == 0 ? Direction.SOUTH : side == 1 ? Direction.WEST : Direction.EAST;
		}

		Direction.Axis alongA() {
			return side == 0 ? Direction.Axis.X : Direction.Axis.Z;
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		void setAB(int a, int y, int bb, BlockState s) {
			set(uOf(a, bb), y, vOf(a, bb), s);
		}

		boolean inside(int u, int v) {
			return u >= 1 && u <= w - 2 && v >= 1 && v <= d - 2;
		}

		boolean freeCell(int u, int v, boolean allowReserved) {
			if (!inside(u, v)) {
				return false;
			}
			byte k = kind[u][v];
			return k == FREE || allowReserved && k == RESERVED;
		}

		/** A cell that belongs to another feature: islands keep one raked cell away from it. */
		boolean blocked(int u, int v) {
			if (u < 0 || v < 0 || u >= w || v >= d) {
				return false;
			}
			byte k = kind[u][v];
			return k == DECK || k == ISLAND || k == PATH;
		}

		boolean isPost(int a) {
			return a == a0 || a == a1 || (a - a0) % 3 == 0 && Math.abs(a - pa) > 1;
		}

		// ------------------------------------------------------------------ run

		void run() {
			layout();
			walls();
			pillars();
			torii();
			deck();
			path();
			int sea = 0;
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					if (kind[u][v] == FREE) {
						sea++;
					}
				}
			}
			int groups = Math.max(1, Math.min(150, (int) Math.round(sea / 52.0)));
			int toros = Math.max(1, Math.min(40, sea / 170));
			int trees = Math.max(1, Math.min(24, 1 + sea / 240));
			tree(0);
			rockGroup(200);
			toro(200);
			for (int i = 1; i < groups; i++) {
				rockGroup(60);
			}
			for (int i = 1; i < toros; i++) {
				toro(40);
			}
			for (int i = 1; i < trees; i++) {
				tree(i);
			}
			rake();
		}

		// ------------------------------------------------------------------ layout

		void layout() {
			side = p.hash(0, 0, 0, 3);
			sa = side == 0 ? w : d;
			sb = side == 0 ? d : w;
			kerbB = sb - 5;
			pal = p.hash(5, 0, 0, 3);
			mode = p.hash(2, 0, 0, 3);
			phase = p.hash(2, 1, 0, 2);
			int span = sa - 2;
			// a shorter veranda on small plots leaves a back corner of the sea beside it, for the tree
			int deckLen = Math.max(7, Math.min(span - 4, 9 + Math.max(0, span - 9) / 3));
			int spare = span - deckLen;
			a0 = spare <= 8 ? (rnd.nextBoolean() ? 1 : 1 + spare) : 1 + rnd.nextInt(spare + 1);
			a1 = a0 + deckLen - 1;
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					dist[u][v] = 99f;
					if (u == 0 || v == 0 || u == w - 1 || v == d - 1) {
						kind[u][v] = WALL;
					}
				}
			}
			for (int a = a0; a <= a1; a++) {
				for (int bb = kerbB; bb <= sb - 2; bb++) {
					kind[uOf(a, bb)][vOf(a, bb)] = DECK;
				}
			}
			// gate: in the longest free run of the first row
			int bestLen = 0;
			int bestStart = 1;
			int start = -1;
			for (int u = 1; u <= w - 1; u++) {
				boolean f = u <= w - 2 && kind[u][1] == FREE;
				if (f && start < 0) {
					start = u;
				}
				if (!f && start >= 0) {
					if (u - start > bestLen) {
						bestLen = u - start;
						bestStart = start;
					}
					start = -1;
				}
			}
			boolean flank = rnd.nextBoolean();
			int want;
			if (side == 0) {
				want = flank ? bestStart + 2 : bestStart + bestLen - 3;
			} else {
				want = side == 1 ? bestStart + 1 : bestStart + bestLen - 2;
			}
			int lo = Math.max(3, bestStart + 1);
			int hi = Math.min(w - 4, bestStart + bestLen - 2);
			cu = lo <= hi ? Math.max(lo, Math.min(hi, want)) : bestStart + bestLen / 2;
			if (side == 0) {
				pa = Math.max(a0 + 2, Math.min(a1 - 2, cu));
			} else {
				pa = Math.max(a0 + 2, Math.min(a1 - 2, a0 + 2 + rnd.nextInt(3)));
			}
		}

		// ------------------------------------------------------------------ walls, gate

		BlockState curb(int u, int v) {
			return switch (p.hash(u, 0, v, 6)) {
				case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
				case 1 -> b(Blocks.COBBLED_DEEPSLATE);
				case 2 -> b(Blocks.POLISHED_DEEPSLATE);
				default -> b(Blocks.DEEPSLATE_BRICKS);
			};
		}

		boolean engawaEdge(int u, int v) {
			int a = aOf(u, v);
			return bOf(u, v) == sb - 1 && a >= a0 && a <= a1;
		}

		void walls() {
			BlockState log = b(Blocks.STRIPPED_CHERRY_LOG);
			BlockState beam = log.setValue(BlockStateProperties.AXIS, alongA());
			BlockState pane = b(Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE));
			if (side == 0) {
				pane = pane.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
			} else {
				pane = pane.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (kind[u][v] != WALL) {
						continue;
					}
					if (v == 0 && Math.abs(u - cu) <= 1) {
						set(u, 0, v, slab(Blocks.STONE_BRICK_SLAB)); // half step: street -> slab -> garden floor
						continue;
					}
					set(u, 0, v, curb(u, v));
					if (engawaEdge(u, v)) {
						int a = aOf(u, v);
						boolean post = (a - a0) % 3 == 0 || a == a1;
						set(u, 1, v, post ? log : pane);
						set(u, 2, v, post ? log : pane);
						set(u, 3, v, beam);
					} else if (v == 0) {
						set(u, 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					} else {
						set(u, 1, v, b(Blocks.CALCITE));
						set(u, 2, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					}
				}
			}
		}

		/** Cherry pillars every 4 cells and at the corners, with a lantern or a slab cap. */
		void pillars() {
			BlockState log = b(Blocks.STRIPPED_CHERRY_LOG);
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (kind[u][v] != WALL || engawaEdge(u, v)) {
						continue;
					}
					boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
					boolean horizontal = v == 0 || v == d - 1;
					int i = horizontal ? u : v;
					int len = horizontal ? w : d;
					if (!(corner || i % 4 == 0 || i == len - 1)) {
						continue;
					}
					if (v == 0 && Math.abs(u - cu) <= 3) {
						continue;
					}
					set(u, 1, v, log);
					set(u, 2, v, log);
					set(u, 3, v, p.hash(u, 3, v, 3) == 0 ? b(Blocks.LANTERN) : slab(Blocks.CHERRY_SLAB));
				}
			}
		}

		/** The front gate: two cherry posts and a lintel, a small torii. */
		void torii() {
			BlockState log = b(Blocks.STRIPPED_CHERRY_LOG);
			for (int du : new int[] {-2, 2}) {
				for (int y = 1; y <= 3; y++) {
					set(cu + du, y, 0, log);
				}
			}
			BlockState beam = log.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
			for (int u = cu - 2; u <= cu + 2; u++) {
				set(u, 4, 0, beam);
			}
			// the lintel's ends overhang as top slabs, 3 blocks over the wall below (no headroom flag)
			BlockState tip = slab(Blocks.DEEPSLATE_TILE_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			if (cu - 3 >= 1) {
				set(cu - 3, 4, 0, tip);
			}
			if (cu + 3 <= w - 2) {
				set(cu + 3, 4, 0, tip);
			}
			if (h >= 6) {
				for (int u = cu - 1; u <= cu + 1; u++) {
					set(u, 5, 0, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
		}

		// ------------------------------------------------------------------ engawa

		BlockState cushion(int a) {
			return b(Blocks.CARPET.pick(p.hash(a, 7, side, 2) == 0 ? DyeColor.PINK : DyeColor.RED));
		}

		void deck() {
			for (int a = a0; a <= a1; a++) {
				for (int bb = kerbB; bb <= sb - 2; bb++) {
					int u = uOf(a, bb);
					int v = vOf(a, bb);
					if (bb == kerbB) {
						set(u, 0, v, b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, alongA()));
					} else {
						set(u, 0, v, p.hash(u, 2, v, 6) == 0 ? b(Blocks.STRIPPED_CHERRY_WOOD) : b(Blocks.CHERRY_PLANKS));
					}
				}
			}
			int front = sb - 3;
			for (int a = a0; a <= a1; a++) {
				if (isPost(a)) {
					for (int y = 1; y <= 3; y++) {
						setAB(a, y, front, b(Blocks.STRIPPED_CHERRY_LOG));
					}
				}
				// roof: eave stairs, a tile ridge row, stairs on the wall beam; at least 3 blocks over the floor
				setAB(a, 4, front, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toWall()));
				setAB(a, 4, sb - 2, b(p.hash(a, 4, side, 4) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES));
				setAB(a, 4, sb - 1, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toWall().getOpposite()));
				if (h >= 6) {
					setAB(a, 5, sb - 2, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
			// open bench row (outside the roof): seats facing the garden and cushions, a free gap at the landing
			int r0 = sb - 4;
			int[][] runs = {{a0, pa - 2}, {pa + 2, a1}};
			for (int[] run : runs) {
				int i = run[0];
				while (i + 2 <= run[1]) {
					for (int k = 0; k < 3; k++) {
						setAB(i + k, 1, r0, stairs(Blocks.CHERRY_STAIRS, toWall()));
					}
					if (i + 3 <= run[1]) {
						// a low tea table with a lantern between two benches
						setAB(i + 3, 1, r0, slab(Blocks.CHERRY_SLAB));
						setAB(i + 3, 2, r0, b(Blocks.LANTERN));
					}
					i += 5;
				}
			}
			// floor cushions on the roofed walk
			for (int a : new int[] {pa - 2, pa + 2}) {
				if (a >= a0 && a <= a1 && !isPost(a)) {
					setAB(a, 1, front, cushion(a));
				}
			}
			// more cushions along a long veranda
			for (int a = a0 + 4; a < a1 - 1; a += 6) {
				if (!isPost(a) && Math.abs(a - pa) > 3) {
					setAB(a, 1, front, cushion(a + 11));
				}
			}
		}

		// ------------------------------------------------------------------ stepping stones

		void path() {
			int tx = uOf(pa, kerbB - 1);
			int tz = vOf(pa, kerbB - 1);
			int x = cu;
			int z = 1;
			int sgn = rnd.nextBoolean() ? 1 : -1;
			paintStone(x, z);
			paintStone(x - 1, z);
			paintStone(x + 1, z);
			boolean lastX = false;
			int guard = 0;
			while ((x != tx || z != tz) && guard++ < 4000) {
				int dx = tx - x;
				int dz = tz - z;
				boolean moveX;
				if (dx == 0) {
					moveX = false;
				} else if (dz == 0) {
					moveX = true;
				} else if (Math.abs(dz) >= Math.abs(dx)) {
					moveX = p.hash(x, 31, z, 4) == 0;
				} else {
					moveX = p.hash(x, 31, z, 4) != 0;
				}
				if (moveX) {
					x += Integer.signum(dx);
				} else {
					z += Integer.signum(dz);
				}
				lastX = moveX;
				paintStone(x, z);
				// the second stone beside it, perpendicular to the way
				int pu = lastX ? x : x + sgn;
				int pv = lastX ? z + sgn : z;
				if (!paintStone(pu, pv)) {
					paintStone(lastX ? x : x - sgn, lastX ? z - sgn : z);
				}
			}
		}

		boolean paintStone(int u, int v) {
			if (!freeCell(u, v, true)) {
				return false;
			}
			kind[u][v] = PATH;
			Block[] mat = pal == 2 ? STEP_LIGHT : STEP; // light stones on dark gravel, dark ones on pale rake
			set(u, 0, v, b(mat[p.hash(u, 9, v, mat.length)]));
			return true;
		}

		// ------------------------------------------------------------------ islands

		boolean siteOk(Collection<Long> foot, boolean allowReserved) {
			return siteOk(foot, allowReserved, 2);
		}

		boolean siteOk(Collection<Long> foot, boolean allowReserved, int margin) {
			for (long k : foot) {
				int u = ku(k);
				int v = kv(k);
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						if (!freeCell(u + du, v + dv, allowReserved)) {
							return false;
						}
					}
				}
				for (int du = -margin; du <= margin; du++) {
					for (int dv = -margin; dv <= margin; dv++) {
						if (blocked(u + du, v + dv)) {
							return false;
						}
					}
				}
			}
			return true;
		}

		/** Marks the island (the footprint plus one cell) and records the distance field the ripples follow. */
		Set<Long> commit(Collection<Long> foot) {
			Set<Long> isl = new HashSet<>();
			for (long k : foot) {
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						isl.add(key(ku(k) + du, kv(k) + dv));
					}
				}
			}
			for (long k : isl) {
				kind[ku(k)][kv(k)] = ISLAND;
				dist[ku(k)][kv(k)] = 0f;
			}
			for (long k : isl) {
				int u = ku(k);
				int v = kv(k);
				for (int du = -5; du <= 5; du++) {
					for (int dv = -5; dv <= 5; dv++) {
						int nu = u + du;
						int nv = v + dv;
						if (nu < 1 || nv < 1 || nu > w - 2 || nv > d - 2) {
							continue;
						}
						float dd = (float) Math.hypot(du, dv);
						if (dd < dist[nu][nv]) {
							dist[nu][nv] = dd;
						}
					}
				}
			}
			return isl;
		}

		/** Moss round the base: blocks, carpets and ferns (plants only stand on moss). */
		void mossRing(Set<Long> isl, Set<Long> foot) {
			for (long k : isl) {
				int u = ku(k);
				int v = kv(k);
				if (foot.contains(k)) {
					set(u, 0, v, b(Blocks.MOSS_BLOCK));
					continue;
				}
				set(u, 0, v, p.hash(u, 51, v, 8) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.MOSS_BLOCK));
				int r = p.hash(u, 52, v, 8);
				if (r <= 1) {
					set(u, 1, v, b(Blocks.MOSS_CARPET));
				} else if (r == 2) {
					set(u, 1, v, b(Blocks.FERN));
				}
			}
		}

		// ------------------------------------------------------------------ rocks

		Map<Long, Integer> heights(List<double[]> rocks) {
			Map<Long, Integer> hm = new HashMap<>();
			for (double[] r : rocks) {
				double x = r[0];
				double z = r[1];
				double rx = r[2];
				int peak = (int) r[3];
				int cx = (int) Math.round(x);
				int cz = (int) Math.round(z);
				for (int u = (int) Math.floor(x - rx); u <= (int) Math.ceil(x + rx); u++) {
					for (int v = (int) Math.floor(z - rx); v <= (int) Math.ceil(z + rx); v++) {
						double f = 1.0 - ((u - x) * (u - x) + (v - z) * (v - z)) / (rx * rx);
						if (f <= 0.05) {
							continue;
						}
						int hgt = (int) Math.round(peak * Math.pow(f, 1.0));
						if (u == cx && v == cz) {
							hgt = peak;
						} else {
							hgt += p.hash(u, 50, v, 3) - 1;
						}
						hgt = Math.max(1, Math.min(peak, hgt));
						hm.merge(key(u, v), hgt, Math::max);
					}
				}
			}
			return hm;
		}

		/** A group of 3 or 5 rocks of different sizes, never taller than the plot allows. */
		void rockGroup(int tries) {
			int maxPeak = Math.min(8, 2 * (h - 1)); // a standing stone up to 4 blocks
			boolean five = rnd.nextInt(3) == 0 && Math.min(sa - 2, sb - 6) >= 8;
			for (int attempt = 0; attempt < 3; attempt++) {
				int n = attempt == 0 && five ? 5 : 3;
				if (attempt == 0 && !five) {
					continue;
				}
				// the last attempt packs the three stones closer and smaller, for the tightest plots
				double cs = attempt == 2 ? 0.8 : 1.0;
				for (int t = 0; t < tries; t++) {
					int gx = 2 + rnd.nextInt(Math.max(1, w - 4));
					int gz = 2 + rnd.nextInt(Math.max(1, d - 4));
					double th = rnd.nextDouble() * Math.PI * 2;
					List<double[]> rocks = new ArrayList<>();
					rocks.add(new double[] {gx, gz, 1.9 * cs, maxPeak});
					rocks.add(new double[] {gx + Math.round(3.0 * cs * Math.cos(th)), gz + Math.round(3.0 * cs * Math.sin(th)), 1.5 * cs, Math.max(2, maxPeak * 5 / 8)});
					rocks.add(new double[] {gx + Math.round(2.7 * cs * Math.cos(th + 2.3)), gz + Math.round(2.7 * cs * Math.sin(th + 2.3)), 1.15, Math.max(1, maxPeak / 4)});
					if (n == 5) {
						rocks.add(new double[] {gx + Math.round(3.6 * Math.cos(th + 4.2)), gz + Math.round(3.6 * Math.sin(th + 4.2)), 1.3, Math.max(2, maxPeak * 3 / 8)});
						rocks.add(new double[] {gx + Math.round(2.2 * Math.cos(th + 1.2)), gz + Math.round(2.2 * Math.sin(th + 1.2)), 0.8, 1});
					}
					Map<Long, Integer> hm = heights(rocks);
					if (!siteOk(hm.keySet(), true)) {
						continue;
					}
					Set<Long> foot = new HashSet<>(hm.keySet());
					Set<Long> isl = commit(foot);
					mossRing(isl, foot);
					buildRocks(hm, foot);
					return;
				}
			}
		}

		void buildRocks(Map<Long, Integer> hm, Set<Long> foot) {
			for (Map.Entry<Long, Integer> e : hm.entrySet()) {
				int u = ku(e.getKey());
				int v = kv(e.getKey());
				int hgt = e.getValue();
				int full = hgt / 2;
				boolean edge = false;
				for (Direction dir : LOCAL) {
					if (!foot.contains(key(u + dir.getStepX(), v + dir.getStepZ()))) {
						edge = true;
					}
				}
				for (int y = 1; y <= full; y++) {
					int idx = p.hash(u, y, v, ROCK.length);
					if (edge && y == 1 && p.hash(u, 53, v, 3) < 2) {
						idx = 3; // mossy at the foot
					}
					set(u, y, v, b(ROCK[idx]));
				}
				int m = p.hash(u, full + 1, v, ROCK.length);
				if (hgt % 2 == 1) {
					Direction up = null;
					int best = hgt;
					for (Direction dir : LOCAL) {
						int nh = hm.getOrDefault(key(u + dir.getStepX(), v + dir.getStepZ()), 0);
						if (nh >= hgt + 2 && nh > best) {
							up = dir;
							best = nh;
						}
					}
					set(u, full + 1, v, up != null ? stairs(ROCK_STAIRS[m], up) : slab(ROCK_SLAB[m]));
				} else if (p.hash(u, 7, v, 3) == 0) {
					set(u, full + 1, v, b(Blocks.MOSS_CARPET)); // moss on top of the full blocks
				}
			}
		}

		// ------------------------------------------------------------------ stone lantern

		/** Toro: shaft, firebox with a lantern, a tiled hip roof 4 blocks up so nobody walks under it. */
		void toro(int tries) {
			for (int t = 0; t < tries; t++) {
				int tx = 2 + rnd.nextInt(Math.max(1, w - 4));
				int tz = 2 + rnd.nextInt(Math.max(1, d - 4));
				Set<Long> foot = new HashSet<>();
				foot.add(key(tx, tz));
				if (!siteOk(foot, false)) {
					continue;
				}
				Set<Long> isl = commit(foot);
				mossRing(isl, foot);
				set(tx, 0, tz, b(Blocks.MOSSY_STONE_BRICKS));
				set(tx, 1, tz, b(Blocks.STONE_BRICK_WALL));
				set(tx, 2, tz, b(Blocks.STONE_BRICK_WALL));
				set(tx, 3, tz, b(Blocks.LANTERN));
				set(tx, 4, tz, b(Blocks.STONE_BRICKS));
				for (Direction dir : LOCAL) {
					set(tx + dir.getStepX(), 4, tz + dir.getStepZ(), stairs(Blocks.STONE_BRICK_STAIRS, dir.getOpposite()));
				}
				for (int du = -1; du <= 1; du += 2) {
					for (int dv = -1; dv <= 1; dv += 2) {
						set(tx + du, 4, tz + dv, slab(Blocks.STONE_BRICK_SLAB));
					}
				}
				if (h >= 6) {
					set(tx, 5, tz, b(Blocks.STONE_BRICK_WALL));
				}
				return;
			}
		}

		// ------------------------------------------------------------------ tree

		/** A small pine (or a cherry): trunk on a moss bed, the canopy starts at y4 so the walk under it is free. */
		void tree(int idx) {
			int[][] corners = {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}};
			List<int[]> order = new ArrayList<>();
			List<int[]> far = new ArrayList<>(List.of(corners[2], corners[3]));
			List<int[]> near = new ArrayList<>(List.of(corners[0], corners[1]));
			Collections.shuffle(far, rnd);
			Collections.shuffle(near, rnd);
			order.addAll(far);
			order.addAll(near);
			for (int t = 0; t < 40 && idx > 0; t++) {
				order.add(new int[] {2 + rnd.nextInt(Math.max(1, w - 4)), 2 + rnd.nextInt(Math.max(1, d - 4))});
			}
			for (int[] cell : order) {
				int cx = cell[0];
				int cz = cell[1];
				if (cx < 2 || cz < 2 || cx > w - 3 || cz > d - 3) {
					continue;
				}
				if (cz < 6 && Math.abs(cx - cu) < 6) {
					continue; // never in front of the gate
				}
				Set<Long> foot = new HashSet<>();
				foot.add(key(cx, cz));
				if (!siteOk(foot, false, 1)) {
					continue;
				}
				Set<Long> isl = commit(foot);
				mossRing(isl, foot);
				for (int du = -2; du <= 2; du++) {
					for (int dv = -2; dv <= 2; dv++) {
						if (kind[cx + du][cz + dv] == FREE) {
							kind[cx + du][cz + dv] = RESERVED;
						}
					}
				}
				canopy(cx, cz, (p.hash(4, 0, 0, 2) + idx) % 2);
				return;
			}
		}

		void canopy(int cx, int cz, int type) {
			BlockState log = b(type == 0 ? Blocks.SPRUCE_LOG : Blocks.CHERRY_LOG);
			Block leafBlock = type == 0 ? Blocks.SPRUCE_LEAVES : Blocks.CHERRY_LEAVES;
			int layers = Math.max(1, Math.min(3, h - 4));
			for (int y = 1; y <= 3 + layers - 1; y++) {
				set(cx, y, cz, log);
			}
			for (int li = 0; li < layers; li++) {
				int y = 4 + li;
				int r = li == 0 ? 2 : li == 1 ? 1 : 0;
				for (int du = -r; du <= r; du++) {
					for (int dv = -r; dv <= r; dv++) {
						int ring = Math.abs(du) + Math.abs(dv);
						boolean corner = Math.abs(du) == r && Math.abs(dv) == r && r > 0;
						if (corner && (li > 0 || r == 2)) {
							continue;
						}
						if (r == 2 && ring >= 3 && p.hash(cx + du, y, cz + dv, 4) == 0) {
							continue; // ragged edge
						}
						if (du == 0 && dv == 0 && li < layers - 1) {
							continue; // the trunk
						}
						if (!inside(cx + du, cz + dv)) {
							continue; // nothing over the wall tops (2.5 of headroom there)
						}
						set(cx + du, y, cz + dv, leaves(leafBlock));
					}
				}
			}
		}

		// ------------------------------------------------------------------ rake

		void rake() {
			BlockState ridge;
			BlockState groove;
			switch (pal) {
				case 0 -> {
					ridge = b(Blocks.CALCITE);
					groove = slab(Blocks.ANDESITE_SLAB);
				}
				case 1 -> {
					ridge = b(Blocks.SAND);
					groove = slab(Blocks.STONE_SLAB);
				}
				default -> {
					ridge = b(Blocks.GRAVEL);
					groove = slab(Blocks.COBBLED_DEEPSLATE_SLAB);
				}
			}
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					byte k = kind[u][v];
					if (k != FREE && k != RESERVED) {
						continue;
					}
					int a = aOf(u, v);
					int bb = bOf(u, v);
					float dd = dist[u][v];
					boolean isRidge;
					if (dd <= 3.4f) {
						isRidge = Math.round(dd) % 2 == 1; // ripples round the islands
					} else if (mode == 0) {
						isRidge = Math.floorMod(bb + phase, 2) == 0; // lines along the veranda
					} else if (mode == 1) {
						isRidge = Math.floorMod(a + phase, 2) == 0; // lines across it
					} else {
						isRidge = Math.floorMod(a + bb + 2 * phase, 4) < 2; // diagonal bands
					}
					if (isRidge) {
						set(u, 0, v, ridge);
						if (p.hash(u, 11, v, 28) == 0) {
							set(u, 1, v, b(Blocks.STONE_BUTTON).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)); // a pebble
						}
					} else {
						set(u, 0, v, groove);
					}
				}
			}
		}
	}
}
