package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.DIRS;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.OUT;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.RIM;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.PondKit.put;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Koi pond of a Japanese garden: a raised, organic pool (two overlapping lobes with a wobbling edge) held by a
 * ring of rocks of different size and colour (andesite, mossy cobblestone, cobblestone, a little gravel), with a
 * mossy step on the outside. The bed is pale sand and gravel under one block of water, so the orange and white
 * koi lying on it are easy to read, with lily pads on top. A small cherry footbridge crosses the pond; when the
 * pond is wide enough it lands on a mossy islet with a stone lantern and a flowering azalea, and carries on to
 * the far bank. Each corner of the plot gets one thing to use or look at: a cherry bench with a back that faces the
 * water, a stone lantern, a bamboo clump in a stone-edged planter and a dwarf cherry (bent trunk, cloud crown). The
 * rim has pale stretches of calcite and diorite, standing stones of different size, and moss beds with small flowers;
 * a few sea lanterns glow in the bed since the water is opaque from above.
 *
 * <pre>
 *  top view (front = bottom)          side section along the bridge
 *   T . . . . . . C                    lantern          lantern
 *   . ▓ ▓ ▓ ▓ ▓ . .                     ╷  ▬▬▬ slab y2  ╷
 *   L ▓ ~ ~ ~ ▓ . B   ~ water           ▓ stair stair ISLET stair stair ▓   water y1, sand y0
 *   . ▓ = o = ▓ . .   = bridge          rim                               rim
 *   . ▓ ~ ~ ~ ▓ . .   o islet
 * </pre>
 *
 * Size 7–13 × 7–13 × 5+ (plots grow with the square root of the citadel scale, and everything here is built
 * from the plot size: bigger plots get a bigger pond, more koi, rocks, islets of reeds and extra corners).
 * Walking: ground, a flat slab path, stair, stair, then the deck; every rise is half a block. A pond with fewer than
 * 5 open cells on its widest row has no bridge (it would cover all the water).
 */
public final class FountainKoiPond implements Variant {
	private static final int ISLAND = 3;

	@Override
	public String id() {
		return "fountain_koi_pond";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 13;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Build(c, p).run();
	}

	/** One build: the mask of the pond and every pass over it. */
	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final int h;
		final Random rng;
		final double cu;
		final double cv;
		/** OUT / WATER / RIM / ISLAND per cell. */
		final int[][] k;
		/** Cells already taken by a feature, a path or the bridge. */
		final boolean[][] res;
		/** Height of the walking surface over each cell (stairs 1, rim 2, deck 2.5); -1 over water. Decides where leaves may hang. */
		final double[][] foot;
		final boolean[][] shaded;
		boolean bridge;
		int vb = -1;
		int wl;
		int wr;
		int rl;
		int rr;
		boolean island;
		int iu;
		int toroSide = 1;

		Build(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.h = p.h();
			this.rng = p.rng(0x4B01L);
			this.cu = (w - 1) / 2.0;
			this.cv = (d - 1) / 2.0;
			this.k = new int[w][d];
			this.res = new boolean[w][d];
			this.foot = new double[w][d];
			this.shaded = new boolean[w][d];
		}

		void run() {
			mask();
			crossing();
			basin();
			boulders();
			if (bridge) {
				buildBridge();
			}
			if (island) {
				islandDecor();
			}
			water();
			corners();
			shore();
			tree();
			lilies();
		}

		// ------------------------------------------------------------------ 1. the pond mask

		/** Two overlapping lobes with a wobbling edge; water stays inside {@code 1..w-2 × 1..d-2} so a rim always fits. */
		void mask() {
			double ru = Math.max(1.7, cu - (w <= 8 ? 1.0 : 1.25));
			double rv = Math.max(1.7, cv - (d <= 8 ? 1.0 : 1.25));
			double ph1 = rng.nextDouble() * Math.PI * 2;
			double ph2 = rng.nextDouble() * Math.PI * 2;
			double a2 = 0.10 + rng.nextDouble() * 0.08;
			double a3 = 0.06 + rng.nextDouble() * 0.06;
			double lx = (rng.nextBoolean() ? 1 : -1) * (0.35 + rng.nextDouble() * 0.2);
			double lz = (rng.nextBoolean() ? 1 : -1) * (0.30 + rng.nextDouble() * 0.2);
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					double du = (u - cu) / ru;
					double dv = (v - cv) / rv;
					double th = Math.atan2(dv, du);
					double r = 1 + a2 * Math.sin(2 * th + ph1) + a3 * Math.sin(3 * th + ph2) + (p.hash(u, 0, v, 5) - 2) * 0.05;
					boolean main = du * du + dv * dv <= r * r;
					double lu = (u - (cu + lx * ru)) / (0.55 * ru);
					double lv = (v - (cv + lz * rv)) / (0.55 * rv);
					if (main || lu * lu + lv * lv <= 1.0) {
						k[u][v] = WATER;
					}
				}
			}
			clean();
		}

		/** Drops single-cell spurs, fills one-cell pinches, keeps the largest piece of water. */
		void clean() {
			for (int pass = 0; pass < 2; pass++) {
				List<int[]> drop = new ArrayList<>();
				for (int u = 1; u < w - 1; u++) {
					for (int v = 1; v < d - 1; v++) {
						if (k[u][v] == WATER && count(u, v, WATER) <= 1) {
							drop.add(new int[] {u, v});
						}
					}
				}
				for (int[] a : drop) {
					k[a[0]][a[1]] = OUT;
				}
			}
			List<int[]> fill = new ArrayList<>();
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					if (k[u][v] == OUT && count(u, v, WATER) >= 3) {
						fill.add(new int[] {u, v});
					}
				}
			}
			for (int[] a : fill) {
				k[a[0]][a[1]] = WATER;
			}
			int[][] label = new int[w][d];
			int best = 0;
			int bestSize = 0;
			int next = 0;
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] != WATER || label[u][v] != 0) {
						continue;
					}
					next++;
					int size = 0;
					ArrayDeque<int[]> q = new ArrayDeque<>();
					q.add(new int[] {u, v});
					label[u][v] = next;
					while (!q.isEmpty()) {
						int[] a = q.poll();
						size++;
						for (Direction dir : DIRS) {
							int nu = a[0] + dir.getStepX();
							int nv = a[1] + dir.getStepZ();
							if (nu >= 0 && nu < w && nv >= 0 && nv < d && k[nu][nv] == WATER && label[nu][nv] == 0) {
								label[nu][nv] = next;
								q.add(new int[] {nu, nv});
							}
						}
					}
					if (size > bestSize) {
						bestSize = size;
						best = next;
					}
				}
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] == WATER && label[u][v] != best) {
						k[u][v] = OUT;
					}
				}
			}
		}

		int count(int u, int v, int what) {
			int n = 0;
			for (Direction dir : DIRS) {
				int nu = u + dir.getStepX();
				int nv = v + dir.getStepZ();
				if (nu >= 0 && nu < w && nv >= 0 && nv < d && k[nu][nv] == what) {
					n++;
				}
			}
			return n;
		}

		int waterIn(int v) {
			int n = 0;
			for (int u = 0; u < w; u++) {
				n += k[u][v] == WATER ? 1 : 0;
			}
			return n;
		}

		// ------------------------------------------------------------------ 2. the crossing, the islet, the rim

		void crossing() {
			int off = rng.nextInt(3) - 1; // the bridge row sits a little off the middle, by the seed
			int target = (int) Math.round(cv) + off;
			int[] sign = {1, -1};
			for (int dist = 0; dist < d && vb < 0; dist++) {
				for (int sg : sign) {
					int v = target + sg * dist;
					if (v >= 2 && v <= d - 3 && waterIn(v) >= 5) {
						vb = v;
						break;
					}
				}
			}
			if (vb >= 0) {
				wl = -1;
				for (int u = 0; u < w; u++) {
					if (k[u][vb] == WATER) {
						wl = wl < 0 ? u : wl;
						wr = u;
					}
				}
				for (int u = wl; u <= wr; u++) {
					k[u][vb] = WATER; // the crossing row is one unbroken stretch of water
				}
				for (int u : new int[] {wl - 1, wr + 1}) {
					for (int sv = vb - 1; sv <= vb + 1; sv += 2) {
						if (u >= 0 && u < w && k[u][sv] == WATER) {
							k[u][sv] = OUT; // the bridge foot is open above: no water may touch it
						}
					}
				}
				bridge = true;
				rl = wl - 1;
				rr = wr + 1;
				int run = wr - wl + 1;
				if (run >= 5) {
					island = true;
					iu = wl + 2 + rng.nextInt(run - 4); // which side of the pond the islet sits on
					toroSide = rng.nextBoolean() ? 1 : -1;
					for (int du = -1; du <= 1; du++) {
						for (int dv = -1; dv <= 1; dv++) {
							boolean corner = du != 0 && dv != 0;
							int u = iu + du;
							int v = vb + dv;
							if (k[u][v] == WATER && !(corner && p.hash(u, 3, v, 3) == 0)) {
								k[u][v] = ISLAND;
							}
						}
					}
				}
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] == OUT && PondKit.touches(k, u, v, WATER)) {
						k[u][v] = RIM;
					}
				}
			}
		}

		boolean nearBridge(int u, int v) {
			return bridge && Math.abs(v - vb) <= 2 && u >= rl - 1 && u <= rr + 1;
		}

		// ------------------------------------------------------------------ 3. basin: bed, water, rim, islet

		void basin() {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					switch (k[u][v]) {
						case WATER -> {
							p.set(c, u, 0, v, bed(u, v));
							p.set(c, u, 1, v, PondKit.water());
							foot[u][v] = -1;
						}
						case RIM -> {
							p.set(c, u, 0, v, rimBase(u, v));
							p.set(c, u, 1, v, rimTop(u, v));
							foot[u][v] = 2;
							res[u][v] = true;
						}
						case ISLAND -> {
							p.set(c, u, 0, v, p.hash(u, 0, v, 2) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.STONE_BRICKS));
							p.set(c, u, 1, v, islandTop(u, v));
							foot[u][v] = 2;
							res[u][v] = true;
						}
						default -> {
						}
					}
				}
			}
		}

		BlockState bed(int u, int v) {
			int n = p.hash(u, 9, v, 10);
			return n < 5 ? b(Blocks.SAND) : n < 7 ? b(Blocks.GRAVEL) : n == 7 ? b(Blocks.CLAY) : b(Blocks.SAND);
		}

		BlockState rimBase(int u, int v) {
			return switch (p.hash(u, 0, v, 6)) {
				case 0 -> b(Blocks.COBBLED_DEEPSLATE);
				case 1 -> b(Blocks.TUFF);
				case 2, 3 -> b(Blocks.ANDESITE);
				case 4 -> b(Blocks.STONE);
				default -> b(Blocks.MOSSY_COBBLESTONE);
			};
		}

		/** Lighter top stones: andesite, mossy cobble, plain cobble, brick, a little gravel. */
		BlockState rimTop(int u, int v) {
			if (p.hash(u / 2, 21, v / 2, 4) == 0) { // a pale stretch of the rim: calcite and diorite, so the ring is not all grey
				return switch (p.hash(u, 22, v, 4)) {
					case 0 -> b(Blocks.DIORITE);
					case 1 -> b(Blocks.POLISHED_DIORITE);
					default -> b(Blocks.CALCITE);
				};
			}
			return switch (p.hash(u, 1, v, 10)) {
				case 0, 1 -> b(Blocks.MOSSY_COBBLESTONE);
				case 2, 3 -> b(Blocks.ANDESITE);
				case 4 -> b(Blocks.STONE);
				case 5 -> b(Blocks.GRAVEL);
				case 6 -> b(Blocks.COBBLESTONE);
				case 7 -> b(Blocks.MOSSY_STONE_BRICKS);
				default -> b(Blocks.ANDESITE);
			};
		}

		BlockState islandTop(int u, int v) {
			return switch (p.hash(u, 1, v, 7)) {
				case 0, 1, 2 -> b(Blocks.MOSS_BLOCK);
				case 3, 4 -> b(Blocks.MOSSY_COBBLESTONE);
				case 5 -> b(Blocks.STONE_BRICKS);
				default -> b(Blocks.ANDESITE);
			};
		}

		/** Rocks of different size on the rim: low slabs and moss, single stones, and a few bigger clusters. */
		void boulders() {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] != RIM || nearBridge(u, v)) {
						continue;
					}
					if (v < cv - 1) {
						// the front stays low so the water shows from the street: only moss and a flower here and there
						switch (p.hash(u, 17, v, 9)) {
							case 0 -> flowerRim(u, v);
							case 1 -> put(c, p, u, 2, v, b(Blocks.MOSS_CARPET));
							default -> {
							}
						}
						continue;
					}
					switch (p.hash(u, 7, v, 16)) {
						case 0 -> put(c, p, u, 2, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
						case 1 -> put(c, p, u, 2, v, b(Blocks.MOSS_CARPET));
						case 2 -> put(c, p, u, 2, v, slab(Blocks.ANDESITE_SLAB));
						case 3 -> put(c, p, u, 2, v, slab(Blocks.POLISHED_DIORITE_SLAB));
						case 4 -> put(c, p, u, 2, v, b(Blocks.COBBLESTONE_WALL)); // a narrow standing stone
						case 5 -> put(c, p, u, 2, v, b(Blocks.MOSSY_COBBLESTONE_WALL));
						case 6, 7 -> flowerRim(u, v);
						default -> {
						}
					}
				}
			}
			List<int[]> rim = new ArrayList<>();
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] == RIM && !nearBridge(u, v) && v >= cv - 1) {
						rim.add(new int[] {u, v});
					}
				}
			}
			int clusters = Math.min(rim.size() / 3, 1 + (w * d) / 110);
			Collections.shuffle(rim, rng);
			for (int i = 0; i < clusters; i++) {
				int u = rim.get(i)[0];
				int v = rim.get(i)[1];
				rock(u, v, 1 + p.hash(u, 8, v, 2));
				for (Direction dir : DIRS) {
					int nu = u + dir.getStepX();
					int nv = v + dir.getStepZ();
					if (p.has(nu, 0, nv) && k[nu][nv] == RIM && !nearBridge(nu, nv) && p.hash(nu, 8, nv, 3) != 0) {
						rock(nu, nv, 1);
					}
				}
			}
		}

		/** A moss bed on the rim top with a small flower in it. */
		void flowerRim(int u, int v) {
			p.set(c, u, 1, v, b(Blocks.MOSS_BLOCK));
			put(c, p, u, 2, v, switch (p.hash(u, 18, v, 5)) {
				case 0 -> b(Blocks.AZURE_BLUET);
				case 1 -> b(Blocks.CORNFLOWER);
				case 2 -> b(Blocks.PINK_TULIP);
				case 3 -> b(Blocks.LILY_OF_THE_VALLEY);
				default -> b(Blocks.OXEYE_DAISY);
			});
		}

		/** A rock {@code height} blocks above the rim top (y 2 upwards), capped with a slab, moss or a pale stone. */
		void rock(int u, int v, int height) {
			int top = Math.min(height, h - 3);
			for (int i = 0; i < top; i++) {
				put(c, p, u, 2 + i, v, i == 0 ? rimTop(u, v + 31) : rimTop(u + 17, v));
			}
			BlockState cap = switch (p.hash(u, 11, v, 4)) {
				case 0 -> b(Blocks.MOSS_CARPET);
				case 1 -> slab(Blocks.POLISHED_DIORITE_SLAB);
				default -> slab(Blocks.MOSSY_COBBLESTONE_SLAB);
			};
			put(c, p, u, 2 + top, v, cap);
			foot[u][v] = 2 + top;
		}

		// ------------------------------------------------------------------ 4. the bridge

		/**
		 * Cherry footbridge along u at row {@code vb}, walkable without a jump. From each bank: a flat slab path,
		 * a stair on the rim cell (y 0, top 1), a stair over a stone pier (y 1, top 2), then the deck as bottom slabs
		 * (y 2, top 2.5). With an islet the deck stops one step above it (the islet top is at 2) and the other half
		 * starts on its far side. Rails of cherry fence run both sides, with a log post and a lantern at each end.
		 */
		void buildBridge() {
			int from = rl;
			int to = rr;
			// paths out to the plot edge
			for (int u = rl - 1; u >= 0; u--) {
				path(u);
			}
			for (int u = rr + 1; u < w; u++) {
				path(u);
			}
			if (island) {
				span(rl, iu - 2, Direction.EAST, true);
				span(rr, iu + 2, Direction.WEST, true);
			} else {
				int n = to - from + 1;
				int mid = from + (n - 1) / 2;
				span(rl, mid, Direction.EAST, false);
				span(rr, mid + 1, Direction.WEST, false);
			}
		}

		void path(int u) {
			res[u][vb] = true;
			foot[u][vb] = 0.5;
			BlockState s = switch (p.hash(u, 2, vb, 3)) {
				case 0 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
				case 1 -> slab(Blocks.ANDESITE_SLAB);
				default -> slab(Blocks.STONE_BRICK_SLAB);
			};
			p.set(c, u, 0, vb, s);
		}

		/**
		 * One half of the crossing: from the foot rim cell {@code foot} (asc = towards the pond) to the last deck cell
		 * {@code last}, inclusive. The cells between hold the stair, the slabs and the rails.
		 */
		void span(int foot0, int last, Direction asc, boolean lanternEnd) {
			int step = asc == Direction.EAST ? 1 : -1;
			put(c, p, foot0, 0, vb, stairs(Blocks.STONE_BRICK_STAIRS, asc));
			put(c, p, foot0, 1, vb, CitadelCanvas.AIR);
			put(c, p, foot0, 2, vb, CitadelCanvas.AIR);
			res[foot0][vb] = true;
			foot[foot0][vb] = 1;
			int first = foot0 + step;
			int len = Math.abs(last - first) + 1;
			for (int i = 0; i < len; i++) {
				int u = first + step * i;
				if (i == 0) {
					put(c, p, u, 0, vb, rimBase(u, vb));
					put(c, p, u, 1, vb, stairs(Blocks.CHERRY_STAIRS, asc));
					foot[u][vb] = 2;
				} else {
					put(c, p, u, 2, vb, slab(Blocks.CHERRY_SLAB));
					foot[u][vb] = 2.5;
				}
				res[u][vb] = true;
			}
			for (int sv = vb - 1; sv <= vb + 1; sv += 2) {
				for (int i = 0; i < len; i++) {
					int u = first + step * i;
					boolean end = i == 0 || i == len - 1;
					boolean post = end || i % 3 == 0;
					if (post) {
						put(c, p, u, 0, sv, rimBase(u, sv));
						put(c, p, u, 1, sv, b(Blocks.STRIPPED_CHERRY_LOG));
					}
					boolean towardsFirst = i > 0;
					boolean towardsLast = i < len - 1 || !lanternEnd; // without an islet the two rails meet on the crest
					boolean west = step > 0 ? towardsFirst : towardsLast;
					boolean east = step > 0 ? towardsLast : towardsFirst;
					put(c, p, u, 2, sv, PondKit.fence(false, false, west, east));
					if (i == 0 || (i == len - 1 && lanternEnd)) {
						put(c, p, u, 3, sv, b(Blocks.LANTERN));
					}
					res[u][sv] = true;
					foot[u][sv] = 3;
				}
			}
		}

		// ------------------------------------------------------------------ 5. the islet's lantern and bush

		void islandDecor() {
			int tv = vb + toroSide;
			if (k[iu][tv] != ISLAND) {
				tv = vb - toroSide;
			}
			if (k[iu][tv] == ISLAND) {
				toro(iu, 2, tv);
				res[iu][tv] = true;
				int bv = 2 * vb - tv;
				if (k[iu][bv] == ISLAND) {
					p.set(c, iu, 1, bv, b(Blocks.MOSS_BLOCK));
					put(c, p, iu, 2, bv, b(Blocks.FLOWERING_AZALEA));
					res[iu][bv] = true;
				}
			}
			for (int du = -1; du <= 1; du += 2) {
				int u = iu + du;
				int v = vb + toroSide;
				if (u >= 0 && u < w && k[u][v] == ISLAND && !res[u][v]) {
					p.set(c, u, 1, v, b(Blocks.MOSS_BLOCK));
					put(c, p, u, 2, v, p.hash(u, 5, v, 2) == 0 ? b(Blocks.FERN) : b(Blocks.AZALEA));
					res[u][v] = true;
				}
			}
		}

		// ------------------------------------------------------------------ 6. water life: rocks, koi, lily pads

		boolean openWater(int u, int v) {
			if (k[u][v] != WATER || (bridge && Math.abs(v - vb) <= 1 && u >= rl && u <= rr)) {
				return false;
			}
			return true;
		}

		void water() {
			List<int[]> wet = new ArrayList<>();
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					if (openWater(u, v)) {
						wet.add(new int[] {u, v});
					}
				}
			}
			Collections.shuffle(wet, rng);
			int area = w * d;
			// stepping rocks: lone stones that break up the surface
			int rocks = Math.min(Math.max(area / 40, 1), 40);
			for (int[] a : wet) {
				if (rocks <= 0) {
					break;
				}
				int u = a[0];
				int v = a[1];
				boolean room = area < 80 ? count(u, v, WATER) == 4 : surrounded(u, v); // small ponds: one stone is enough
				if (k[u][v] == WATER && !(bridge && Math.abs(v - vb) <= 2) && room) {
					k[u][v] = ISLAND;
					res[u][v] = true;
					p.set(c, u, 0, v, rimBase(u, v));
					p.set(c, u, 1, v, rimTop(u, v));
					if (p.hash(u, 12, v, 3) == 0) {
						put(c, p, u, 2, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
					}
					foot[u][v] = 2;
					rocks--;
				}
			}
			// koi: two or three cells long, orange and white, lying on the bed
			int koi = Math.min(2 + area / 45, 30);
			int placed = 0;
			for (int[] a : wet) {
				if (placed >= koi) {
					break;
				}
				int u = a[0];
				int v = a[1];
				if (k[u][v] != WATER) {
					continue;
				}
				Direction t = DIRS[p.hash(u, 13, v, 4)];
				int tu = u + t.getStepX();
				int tv = v + t.getStepZ();
				boolean white = placed % 3 == 1;
				p.set(c, u, 0, v, white ? b(Blocks.CALCITE) : b(Blocks.RAW_COPPER_BLOCK));
				if (p.has(tu, 0, tv) && openWater(tu, tv) && k[tu][tv] == WATER) {
					p.set(c, tu, 0, tv, placed % 3 == 2 ? b(Blocks.RAW_COPPER_BLOCK) : b(Blocks.CALCITE));
				}
				placed++;
			}
			// the water is opaque from above, so a few sea lanterns glow in the bed where the fish are
			int glow = 1 + area / 45;
			for (int i = wet.size() - 1; i >= 0 && glow > 0; i--) {
				int u = wet.get(i)[0];
				int v = wet.get(i)[1];
				BlockState bed = p.at(c, u, 0, v);
				boolean plain = bed != null && (bed.is(Blocks.SAND) || bed.is(Blocks.GRAVEL) || bed.is(Blocks.CLAY));
				if (k[u][v] == WATER && plain && !(bridge && Math.abs(v - vb) <= 2)) {
					p.set(c, u, 0, v, b(Blocks.SEA_LANTERN));
					glow--;
				}
			}
		}

		/** Lily pads on open water, but never under the crown (a pad can be walked on, and would get a low roof). */
		void lilies() {
			for (int u = 1; u < w - 1; u++) {
				for (int v = 1; v < d - 1; v++) {
					if (openWater(u, v) && k[u][v] == WATER && !shaded[u][v] && p.hash(u, 5, v, 3) == 0) {
						put(c, p, u, 2, v, b(Blocks.LILY_PAD));
					}
				}
			}
		}

		boolean surrounded(int u, int v) {
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					int nu = u + du;
					int nv = v + dv;
					if (nu < 0 || nu >= w || nv < 0 || nv >= d || k[nu][nv] != WATER) {
						return false;
					}
				}
			}
			return true;
		}

		// ------------------------------------------------------------------ 7. corners: bench, lantern, bamboo, tree

		boolean free(int u, int v) {
			return u >= 0 && u < w && v >= 0 && v < d && k[u][v] == OUT && !res[u][v] && !PondKit.touches(k, u, v, RIM);
		}

		int treeU = -1;
		int treeV = -1;

		void corners() {
			int[][] corner = {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}};
			// 0 tree, 1 lantern, 2 bamboo, 3 bench. The tall things go to the back corners so they never hide the water
			// from the front; the lantern and the bench stay at the front, looking at the pond.
			List<Integer> front = new ArrayList<>(List.of(1, 3));
			List<Integer> back = new ArrayList<>(List.of(0, 2));
			Collections.shuffle(front, rng);
			Collections.shuffle(back, rng);
			List<Integer> roles = List.of(front.get(0), front.get(1), back.get(0), back.get(1));
			for (int i = 0; i < 4; i++) {
				int su = corner[i][0];
				int sv = corner[i][1];
				List<int[]> region = new ArrayList<>();
				for (int du = 0; du <= 2; du++) {
					for (int dv = 0; dv <= 2; dv++) {
						int u = su == 0 ? du : w - 1 - du;
						int v = sv == 0 ? dv : d - 1 - dv;
						if (free(u, v)) {
							region.add(new int[] {u, v});
						}
					}
				}
				if (region.isEmpty()) {
					continue;
				}
				region.sort((a, bb) -> Double.compare(dist(a), dist(bb)));
				int[] near = region.get(0);
				int[] far = region.get(region.size() - 1);
				switch (roles.get(i)) {
					case 0 -> {
						if (free(su, sv)) {
							treeU = su;
							treeV = sv;
						} else {
							treeU = far[0];
							treeV = far[1];
						}
						res[treeU][treeV] = true;
					}
					case 1 -> toroGround(near[0], near[1]);
					case 2 -> bamboo(far[0], far[1]);
					default -> bench(near[0], near[1]);
				}
			}
			// bigger plots: more of the same, scattered by the seed
			int extra = Math.min((w * d) / 150, 24);
			List<int[]> spots = new ArrayList<>();
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (free(u, v)) {
						spots.add(new int[] {u, v});
					}
				}
			}
			Collections.shuffle(spots, rng);
			int[] cycle = {2, 1, 3, 2};
			for (int i = 0, n = 0; i < spots.size() && n < extra; i++) {
				int u = spots.get(i)[0];
				int v = spots.get(i)[1];
				if (!free(u, v)) {
					continue;
				}
				switch (cycle[n % cycle.length]) {
					case 1 -> toroGround(u, v);
					case 2 -> bamboo(u, v);
					default -> bench(u, v);
				}
				n++;
			}
		}

		double dist(int[] a) {
			return Math.hypot(a[0] - cu, a[1] - cv);
		}

		void toroGround(int u, int v) {
			toro(u, 0, v);
			res[u][v] = true;
			foot[u][v] = 9;
		}

		/** Stone lantern from {@code base}: plinth, post, lamp house, lantern, mossy roof, finial (shorter on low plots). */
		void toro(int u, int base, int v) {
			put(c, p, u, base, v, b(Blocks.POLISHED_ANDESITE));
			put(c, p, u, base + 1, v, b(Blocks.STONE_BRICK_WALL));
			if (base + 5 < h) {
				put(c, p, u, base + 2, v, b(Blocks.CHISELED_STONE_BRICKS));
				put(c, p, u, base + 3, v, b(Blocks.LANTERN));
				put(c, p, u, base + 4, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
				put(c, p, u, base + 5, v, b(Blocks.STONE_BRICK_WALL));
			} else {
				put(c, p, u, base + 2, v, b(Blocks.LANTERN));
				put(c, p, u, base + 3, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
			}
		}

		/** A bamboo clump on podzol, with a little fern: the anchor cell and the free cells next to it. */
		void bamboo(int u0, int v0) {
			int n = 0;
			List<int[]> bed = new ArrayList<>();
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					int u = u0 + du;
					int v = v0 + dv;
					if (!free(u, v) || (n >= 4 && (du != 0 || dv != 0)) || (du != 0 && dv != 0 && p.hash(u, 14, v, 2) == 0)) {
						continue;
					}
					res[u][v] = true;
					p.set(c, u, 0, v, b(Blocks.PODZOL));
					int top = Math.min(h - 1, 3 + p.hash(u, 1, v, 3));
					for (int y = 1; y <= top; y++) {
						BlockState s = b(Blocks.BAMBOO);
						if (y == top) {
							s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.LARGE);
						} else if (y == top - 1) {
							s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.SMALL);
						}
						put(c, p, u, y, v, s);
					}
					foot[u][v] = 9;
					bed.add(new int[] {u, v});
					n++;
				}
			}
			// planter edge: stone-brick stairs rising towards the bed on its four sides, slabs on the diagonals
			List<int[]> edge = new ArrayList<>();
			for (int[] a : bed) {
				for (int du = -1; du <= 1; du++) {
					for (int dv = -1; dv <= 1; dv++) {
						int u = a[0] + du;
						int v = a[1] + dv;
						if (free(u, v)) {
							edge.add(new int[] {u, v});
							res[u][v] = true;
						}
					}
				}
			}
			for (int[] e : edge) {
				Direction toBed = null;
				for (Direction dir : DIRS) {
					int nu = e[0] + dir.getStepX();
					int nv = e[1] + dir.getStepZ();
					for (int[] a : bed) {
						if (a[0] == nu && a[1] == nv && toBed == null) {
							toBed = dir;
						}
					}
				}
				boolean mossy = p.hash(e[0], 23, e[1], 2) == 0;
				if (toBed != null) {
					p.set(c, e[0], 0, e[1], stairs(mossy ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, toBed));
					foot[e[0]][e[1]] = 1;
				} else {
					p.set(c, e[0], 0, e[1], slab(mossy ? Blocks.MOSSY_STONE_BRICK_SLAB : Blocks.STONE_BRICK_SLAB));
					foot[e[0]][e[1]] = 0.5;
				}
			}
		}

		/** A stone bench of up to three seats facing the water, with an azalea behind. */
		void bench(int u0, int v0) {
			Direction face = Direction.NORTH;
			double du = cu - u0;
			double dv = cv - v0;
			if (Math.abs(du) > Math.abs(dv)) {
				face = du > 0 ? Direction.EAST : Direction.WEST;
			} else {
				face = dv > 0 ? Direction.SOUTH : Direction.NORTH;
			}
			Direction back = face.getOpposite();
			Direction tan = face.getClockWise();
			// cherry bench with a back: three seats facing the water, a cherry rail behind them between two
			// stripped-log posts (one carries a lantern), a flowering azalea behind that
			BlockState rail = b(Blocks.CHERRY_FENCE).setValue(PondKit.side(tan), true).setValue(PondKit.side(tan.getOpposite()), true);
			for (int i = -1; i <= 1; i++) {
				int u = u0 + i * tan.getStepX();
				int v = v0 + i * tan.getStepZ();
				if (!free(u, v)) {
					continue;
				}
				// the stair rises towards its facing: the back of the seat is away from the water
				p.set(c, u, 0, v, stairs(Blocks.CHERRY_STAIRS, back));
				res[u][v] = true;
				foot[u][v] = 1;
				int bu = u + back.getStepX();
				int bv = v + back.getStepZ();
				if (free(bu, bv)) {
					put(c, p, bu, 0, bv, rail);
					res[bu][bv] = true;
					foot[bu][bv] = 9;
					int au = bu + back.getStepX();
					int av = bv + back.getStepZ();
					if (free(au, av) && i == 0) {
						put(c, p, au, 0, av, p.hash(au, 16, av, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
						res[au][av] = true;
					}
				}
			}
			for (int i = -2; i <= 2; i += 4) {
				int pu = u0 + i * tan.getStepX() + back.getStepX();
				int pv = v0 + i * tan.getStepZ() + back.getStepZ();
				if (free(pu, pv)) {
					put(c, p, pu, 0, pv, b(Blocks.STRIPPED_CHERRY_LOG));
					put(c, p, pu, 1, pv, b(Blocks.STRIPPED_CHERRY_LOG));
					if (i < 0) {
						put(c, p, pu, 2, pv, b(Blocks.LANTERN));
					}
					res[pu][pv] = true;
					foot[pu][pv] = 9;
				}
			}
		}

		boolean nearRim(int u, int v) {
			for (int du = -2; du <= 2; du++) {
				for (int dv = -2; dv <= 2; dv++) {
					int nu = u + du;
					int nv = v + dv;
					if (nu >= 0 && nu < w && nv >= 0 && nv < d && k[nu][nv] == RIM) {
						return true;
					}
				}
			}
			return false;
		}

		/** Moss, ferns and small flowers close to the water. */
		BlockState wetPlant(int u, int v) {
			return switch (p.hash(u, 24, v, 9)) {
				case 0 -> b(Blocks.AZURE_BLUET);
				case 1 -> b(Blocks.PINK_PETALS);
				case 2, 3 -> b(Blocks.MOSS_CARPET);
				case 4 -> b(Blocks.FERN);
				case 5 -> b(Blocks.LILY_OF_THE_VALLEY);
				case 6 -> b(Blocks.CORNFLOWER);
				default -> null;
			};
		}

		// ------------------------------------------------------------------ 8. shore: mossy steps up to the rim, plants

		void shore() {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] != OUT || res[u][v]) {
						continue;
					}
					int rims = 0;
					Direction toRim = null;
					for (Direction dir : DIRS) {
						int nu = u + dir.getStepX();
						int nv = v + dir.getStepZ();
						if (p.has(nu, 0, nv) && k[nu][nv] == RIM) {
							rims++;
							toRim = dir;
						}
					}
					if (rims == 1) {
						net.minecraft.world.level.block.Block s = switch (p.hash(u, 4, v, 4)) {
							case 0 -> Blocks.MOSSY_COBBLESTONE_STAIRS;
							case 1 -> Blocks.ANDESITE_STAIRS;
							case 2 -> Blocks.COBBLESTONE_STAIRS;
							default -> Blocks.MOSSY_STONE_BRICK_STAIRS;
						};
						p.set(c, u, 0, v, stairs(s, toRim));
						foot[u][v] = 1;
					} else if (rims > 1) {
						p.set(c, u, 0, v, slab(p.hash(u, 4, v, 2) == 0 ? Blocks.ANDESITE_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB));
						foot[u][v] = 0.5;
					} else {
						BlockState plant = nearRim(u, v) ? wetPlant(u, v) : PondKit.shorePlant(p, u, v);
						if (plant != null) {
							p.set(c, u, 0, v, plant);
						}
					}
				}
			}
		}

		// ------------------------------------------------------------------ 9. the cherry tree (last, so it can keep clear of what is walked)

		/**
		 * A young cherry tree in its corner leaning its crown over the pond. A leaf is only placed where somebody
		 * walking below keeps 2.85 blocks of headroom (over water: from the surface up) and only when it connects
		 * to the trunk, so nothing hangs loose.
		 */
		void tree() {
			if (treeU < 0) {
				return;
			}
			int top = h - 1; // the highest layer a leaf may use
			int ty = Math.min(3, top - 1); // the trunk stands to ty, then bends one cell towards the pond
			if (ty < 2) {
				return;
			}
			double du0 = cu - treeU;
			double dv0 = cv - treeV;
			int su = (int) Math.signum(du0);
			int sv = (int) Math.signum(dv0);
			int[][] opts = Math.abs(du0) >= Math.abs(dv0) ? new int[][] {{su, 0}, {0, sv}} : new int[][] {{0, sv}, {su, 0}};
			int bu = treeU;
			int bv = treeV;
			boolean bent = false;
			if (ty >= 3) {
				for (int[] o : opts) {
					if ((o[0] != 0 || o[1] != 0) && free(treeU + o[0], treeV + o[1])) {
						bu = treeU + o[0];
						bv = treeV + o[1];
						bent = true;
						break;
					}
				}
			}
			for (int y = 0; y <= ty; y++) {
				put(c, p, treeU, y, treeV, b(y < 2 ? Blocks.CHERRY_LOG : Blocks.STRIPPED_CHERRY_LOG));
			}
			List<int[]> seeds = new ArrayList<>();
			seeds.add(new int[] {treeU, ty, treeV});
			if (bent) {
				Direction.Axis axis = bu != treeU ? Direction.Axis.X : Direction.Axis.Z;
				put(c, p, bu, ty, bv, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, axis));
				seeds.add(new int[] {bu, ty, bv});
				if (ty + 1 <= top) {
					put(c, p, bu, ty + 1, bv, b(Blocks.CHERRY_LOG));
					seeds.add(new int[] {bu, ty + 1, bv});
				}
				res[bu][bv] = true;
			}
			// a cloud crown: three flat lumps (the main one over the water side, a small one higher, a low one further out)
			int ps = p.hash(treeU, 30, treeV, 2) == 0 ? 1 : -1;
			int lu = su == 0 ? 1 : su;
			int lv = sv == 0 ? 1 : sv;
			double cy = Math.max(3.6, top - 1.2);
			boolean small = w <= 8 || d <= 8; // the crown must leave water showing on a small pond
			double reach = small ? 0.4 : 1.0;
			double[][] lumps = small
					? new double[][] {
						{bu + reach * lu, cy, bv + reach * lv, 2.1, 1.0},
						{bu - 0.3 * lu - 1.2 * ps * lv, cy + 0.8, bv - 0.3 * lv + 1.2 * ps * lu, 1.5, 0.85},
					}
					: new double[][] {
						{bu + reach * lu, cy, bv + reach * lv, 2.5, 1.2},
						{bu - 0.2 * lu - 1.4 * ps * lv, cy + 0.9, bv - 0.2 * lv + 1.4 * ps * lu, 1.8, 0.95},
						{bu + 1.8 * lu + 1.3 * ps * lv, cy - 0.7, bv + 1.8 * lv - 1.3 * ps * lu, 1.6, 0.8},
					};
			boolean[][][] leaf = new boolean[w][h][d];
			for (int u = 0; u < w; u++) {
				for (int y = 0; y < h; y++) {
					for (int v = 0; v < d; v++) {
						double e = 9;
						for (double[] l : lumps) {
							e = Math.min(e, sq((u - l[0]) / l[3]) + sq((y - l[1]) / l[4]) + sq((v - l[2]) / l[3]));
						}
						boolean log = (u == treeU && v == treeV && y <= ty) || (bent && u == bu && v == bv && (y == ty || y == ty + 1));
						if (e <= 1.0 && !log && p.hash(u, y, v, e > 0.6 ? 3 : 9) != 0 && roomFor(u, y, v)) {
							leaf[u][y][v] = true;
						}
					}
				}
			}
			boolean[][][] keep = new boolean[w][h][d];
			ArrayDeque<int[]> q = new ArrayDeque<>(seeds);
			int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			while (!q.isEmpty()) {
				int[] a = q.poll();
				for (int[] o : around) {
					int u = a[0] + o[0];
					int y = a[1] + o[1];
					int v = a[2] + o[2];
					if (u >= 0 && u < w && y >= 0 && y < h && v >= 0 && v < d && leaf[u][y][v] && !keep[u][y][v]) {
						keep[u][y][v] = true;
						q.add(new int[] {u, y, v});
					}
				}
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (k[u][v] != RIM) {
						continue;
					}
					int hi = -1;
					for (int y = 3; y < h; y++) {
						hi = keep[u][y][v] ? y : hi;
					}
					for (int y = 2; y <= hi; y++) {
						keep[u][y][v] = true; // no gap between the rim top and the crown: nobody can stand under it
					}
				}
			}
			for (int u = 0; u < w; u++) {
				for (int y = 0; y < h; y++) {
					for (int v = 0; v < d; v++) {
						if (keep[u][y][v]) {
							shaded[u][v] |= y < 6;
							put(c, p, u, y, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
						}
					}
				}
			}
			for (Direction dir : DIRS) {
				int pu = treeU + dir.getStepX();
				int pv = treeV + dir.getStepZ();
				if (free(pu, pv) && p.hash(pu, 6, pv, 2) == 0) {
					put(c, p, pu, 0, pv, b(Blocks.PINK_PETALS));
				}
			}
		}

		/**
		 * Whether a leaf may hang at {@code (u, y, v)}. Over open water from the surface up. Over a rim top only when the
		 * crown is carried down to it (the audit calls a standable cell under a roof that nobody reaches sealed); never
		 * over a rock in the water. Elsewhere only where whoever stands below keeps 2.85 blocks of headroom.
		 */
		boolean roomFor(int u, int y, int v) {
			if (u == treeU && v == treeV) {
				return y >= 3;
			}
			if (k[u][v] == WATER) {
				return y >= 2;
			}
			if (k[u][v] == RIM) {
				return y >= 3; // the crown is carried down to the rim top (see tree()): the rim stone below is then filled, not walkable
			}
			if (k[u][v] == ISLAND) {
				return false;
			}
			double top = foot[u][v];
			BlockState g = p.at(c, u, 0, v);
			if (g != null && (g.is(Blocks.AZALEA) || g.is(Blocks.FLOWERING_AZALEA))) {
				top = Math.max(top, 1.0); // an azalea bush is a step you can stand on
			}
			return y >= (int) Math.ceil(top + 2.85);
		}

		static double sq(double x) {
			return x * x;
		}
	}
}
