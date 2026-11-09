package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.FALL;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.WATER;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.pillar;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stair;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.stairTop;
import static dev.t3craft.hq.citadel.catalog.fountain.FountainKit.toward;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plaza fountain of stepped basins on a paved plinth: a wide pool whose stair rim is a bench, one or two
 * smaller bowls above it on drums, water that spills over wide lips from bowl to bowl, and a jet on top.
 *
 * <p>Concept: Trevi / Villa d'Este in the citadel palette. A one-block <b>plinth</b> (stairs all round, so it
 * is walked up from any side) carries everything. The pool bed is dark deepslate tile; the rim is a ring of
 * stone-brick stairs whose low step is a seat and whose back keeps the water in. Above it every bowl is
 * lighter than the one below (stone brick, polished diorite, smooth quartz), sits on a drum with a copper
 * (or gold) collar, curves in underneath with upside-down stairs and spills through lips that fall as real
 * water into the next basin. The top is a stem capped in copper with a water crown that falls into the
 * bowl. The paving follows the shape of the pool (dark inlay, stone brick, andesite bands, a light cross
 * of calcite for the walks) and, when the plot has room, benches, lamp posts and planters ring the plaza.
 *
 * <pre>
 *  section along the axis (2 tiers, h = 7)
 *                  ▒        y5-6  crown: water on a copper-capped stem, falls into the bowl
 *       ≈≈▒▒▒≈≈            y4    bowl water, stair rim (back keeps the water)
 *    ≈≈  ▓▓▓▓▓▓  ≈≈  falls  y3    bowl floor on a drum, underside of upside-down stairs
 *  ▟▓≈≈≈≈≈≈≈≈≈≈≈≈≈▓▙       y1    pool water, stair rim = bench
 *  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓       y0    plinth: pool bed + paving, stairs on the edge
 * </pre>
 *
 * <p>Size: 7–23 × 7–23 × 7+ and more (the figure scales with the plot: pool radius = half the plot minus
 * the plinth edge and a paved apron; the plaza gets benches and lamps from about 17 wide). The seed picks the
 * shape (round, octagonal or square), 2 or 3 bowls (3 needs radius 7 and height 9) and the accent (copper or
 * gold). Water is always held in: every pool cell has stair or block on its four sides, and the falls land
 * inside the basin below. No fire or lava anywhere.
 */
public final class FountainTieredBasins implements Variant {
	private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	@Override
	public String id() {
		return "fountain_tiered_basins";
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
		return 7;
	}

	@Override
	public int maxW() {
		return 23;
	}

	@Override
	public int maxD() {
		return 23;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		FountainKit k = new FountainKit(c, p);
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int n = Math.min(w, d);
		if (n % 2 == 0) {
			n--;
		}
		int half = (n - 1) / 2;
		int shape = k.hash(0, 0, 0, 3); // 0 round, 1 octagon, 2 square
		boolean gold = k.hash(1, 0, 0, 2) == 0;
		BlockState accent = gold ? b(Blocks.GOLD_BLOCK) : b(Blocks.CUT_COPPER.waxed().unaffected());

		int pave = half <= 4 ? 0 : half <= 7 ? 1 : half / 3; // paved apron between the pool and the plinth edge
		int rad = half - 1 - pave; // pool radius
		boolean three = rad >= 7 && h >= 9 && k.hash(2, 0, 0, 2) == 0;
		int r1;
		int r2 = 0;
		if (three) {
			r1 = Math.min(rad - 2, Math.round(rad * 0.70f));
			r2 = Math.min(r1 - 2, Math.max(2, Math.round(rad * 0.40f)));
		} else {
			r1 = rad >= 5 ? Math.min(rad - 2, Math.round(rad * 0.62f)) : rad == 4 ? 2 : 0;
		}
		// vertical unit: the figure grows taller with its radius (as far as the plot's height allows)
		int g = Math.max(1, Math.min(6, rad / 7));
		while (g > 1 && (three ? 2 + g + 1 + g + 3 : 2 + g + 3) > h - 1) {
			g--;
		}
		int f1 = r1 >= 2 ? 2 + g : 1;
		int f2 = f1 + 1 + g;
		int fTop = three ? f2 : f1;
		int x = r1 >= 2 ? Math.max(0, Math.min(g, h - 4 - fTop)) : 0; // extra stem height under the crown

		int cu = k.cu;
		int cv = k.cv;
		for (int dz = -cv; dz <= d - 1 - cv; dz++) {
			for (int dx = -cu; dx <= w - 1 - cu; dx++) {
				int u = cu + dx;
				int v = cv + dz;
				if (u == 0 || v == 0 || u == w - 1 || v == d - 1) {
					plinthEdge(k, dx, dz, u, v, w, d);
					continue;
				}
				if (ins(shape, dx, dz, rad)) {
					poolCell(k, shape, dx, dz, rad);
				} else {
					apron(k, shape, dx, dz, rad, half, pave);
				}
			}
		}

		// bowls
		if (r1 >= 2) {
			bowl(k, shape, r1, f1, rad, 1, 0, 1);
		}
		if (three) {
			bowl(k, shape, r2, f2, r1, 2, f1, f1 + 1);
		}
		int fLast = three ? f2 : f1;
		int rLast = three ? r2 : r1;
		int baseWater = rLast >= 2 ? fLast + 1 : 1; // the water level the crown falls into
		crown(k, fLast, x, baseWater, accent, rLast >= 2);

		// bubblers in a big pool: a ring of small copper-topped nozzles between the shore and the first bowl
		if (rad >= 9) {
			int rn = (rad + r1) / 2;
			int dd = Math.round(rn * 0.707f);
			int[][] at = {{rn, 0}, {-rn, 0}, {0, rn}, {0, -rn}, {dd, dd}, {-dd, dd}, {dd, -dd}, {-dd, -dd}};
			for (int[] a : at) {
				if (ins(shape, a[0], a[1], rad - 3) && !ins(shape, a[0], a[1], r1 + 2)) {
					k.set(a[0], 1, a[1], accent);
					k.set(a[0], 2, a[1], WATER);
					for (Direction dir : DIRS) {
						k.set(a[0] + dir.getStepX(), 2, a[1] + dir.getStepZ(), FALL);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ shapes

	/** Whether the cell is inside the figure of radius {@code r}: round, octagonal or square. */
	private static boolean ins(int shape, int dx, int dz, int r) {
		if (r < 0) {
			return false;
		}
		return switch (shape) {
			case 0 -> FountainKit.disk(dx, dz, r);
			case 1 -> FountainKit.oct(dx, dz, r);
			default -> Math.abs(dx) <= r && Math.abs(dz) <= r;
		};
	}

	/** The one-cell edge of the figure: inside, with a side that is not. */
	private static boolean rim(int shape, int dx, int dz, int r) {
		return ins(shape, dx, dz, r) && !(ins(shape, dx + 1, dz, r) && ins(shape, dx - 1, dz, r) && ins(shape, dx, dz + 1, r) && ins(shape, dx, dz - 1, r));
	}

	/** The smallest radius whose figure contains the cell (the ring the cell is on). */
	private static int ringIdx(int shape, int dx, int dz) {
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		while (!ins(shape, dx, dz, r)) {
			r++;
		}
		return r;
	}

	/** Direction from a rim cell towards the water: opposite its only open side, else towards the centre. */
	private static Direction inward(int shape, int dx, int dz, int r) {
		int open = 0;
		Direction last = null;
		for (Direction dir : DIRS) {
			if (!ins(shape, dx + dir.getStepX(), dz + dir.getStepZ(), r)) {
				open++;
				last = dir;
			}
		}
		return open == 1 ? last.getOpposite() : toward(dx, dz);
	}

	// ------------------------------------------------------------------ plinth, pool, apron

	/** [transition] the plinth edge is a ring of stairs rising inwards: the plaza is walked up from every side. */
	private static void plinthEdge(FountainKit k, int dx, int dz, int u, int v, int w, int d) {
		Direction f = u == 0 ? Direction.EAST : u == w - 1 ? Direction.WEST : v == 0 ? Direction.SOUTH : Direction.NORTH;
		k.set(dx, 0, dz, k.pick(dx, 0, dz, stair(Blocks.STONE_BRICK_STAIRS, f), stair(Blocks.STONE_BRICK_STAIRS, f),
				stair(Blocks.POLISHED_ANDESITE_STAIRS, f), stair(Blocks.DEEPSLATE_BRICK_STAIRS, f), stair(Blocks.MOSSY_STONE_BRICK_STAIRS, f)));
	}

	/** One cell of the big pool: dark bed and water inside, a base course and a stair rim (the seat) on the edge. */
	private static void poolCell(FountainKit k, int shape, int dx, int dz, int rad) {
		if (rim(shape, dx, dz, rad)) {
			// [palette] dark base course under a stone-brick bench rim; its back (inwards) holds the water
			k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.DEEPSLATE_BRICKS),
					b(Blocks.CRACKED_DEEPSLATE_BRICKS)));
			Direction f = inward(shape, dx, dz, rad);
			k.set(dx, 1, dz, k.pick(dx, 1, dz, stair(Blocks.STONE_BRICK_STAIRS, f), stair(Blocks.STONE_BRICK_STAIRS, f),
					stair(Blocks.POLISHED_ANDESITE_STAIRS, f), stair(Blocks.MOSSY_STONE_BRICK_STAIRS, f)));
		} else {
			k.set(dx, 0, dz, k.pick(dx, 0, dz, b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE),
					b(Blocks.CRACKED_DEEPSLATE_TILES)));
			k.set(dx, 1, dz, WATER);
		}
	}

	/** The paving round the pool (bands that follow its shape, a light cross for the walks) and its furniture. */
	private static void apron(FountainKit k, int shape, int dx, int dz, int rad, int half, int pave) {
		int m = ringIdx(shape, dx, dz) - rad;
		boolean cross = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
		BlockState s;
		if (m == 1) {
			s = k.pick(dx, 0, dz, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES));
		} else if (cross) {
			s = k.pick(dx, 0, dz, b(Blocks.CALCITE), b(Blocks.POLISHED_DIORITE), b(Blocks.CALCITE));
		} else {
			s = switch ((m / 2) % 3) {
				case 0 -> k.pick(dx, 0, dz, b(Blocks.STONE_BRICKS), b(Blocks.STONE_BRICKS), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.CRACKED_STONE_BRICKS));
				case 1 -> k.pick(dx, 0, dz, b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_ANDESITE), b(Blocks.ANDESITE));
				default -> k.pick(dx, 0, dz, b(Blocks.SMOOTH_STONE), b(Blocks.POLISHED_ANDESITE), b(Blocks.STONE_BRICKS));
			};
		}
		k.set(dx, 0, dz, s);

		// furniture on the ring just inside the plinth edge
		int bc = half - 1;
		if (Math.max(Math.abs(dx), Math.abs(dz)) != bc) {
			return;
		}
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		if (ax == bc && az == bc) {
			lamp(k, dx, dz);
			return;
		}
		if (pave < 2) {
			return;
		}
		int t = ax == bc ? az : ax;
		if (t <= 1 || t > bc - 2) {
			return;
		}
		int q = (t - 2) % 8;
		if (q <= 2) {
			// [finish] a bench: stairs with the back outwards, a seat towards the plaza
			Direction f = ax == bc ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			k.set(dx, 1, dz, k.pick(dx, 1, dz, stair(Blocks.STONE_BRICK_STAIRS, f), stair(Blocks.STONE_BRICK_STAIRS, f),
					stair(Blocks.POLISHED_DIORITE_STAIRS, f)));
		} else if (q == 4) {
			lamp(k, dx, dz);
		} else if (q == 6) {
			k.set(dx, 1, dz, k.pick(dx, 1, dz, b(Blocks.POTTED_FLOWERING_AZALEA), b(Blocks.POTTED_CORNFLOWER), b(Blocks.POTTED_PINK_TULIP)));
		}
	}

	/** A lamp post: a deepslate plinth, a wall shaft and a lantern. */
	private static void lamp(FountainKit k, int dx, int dz) {
		k.set(dx, 1, dz, b(Blocks.POLISHED_ANDESITE));
		k.set(dx, 2, dz, b(Blocks.STONE_BRICK_WALL));
		k.set(dx, 3, dz, b(Blocks.STONE_BRICK_WALL));
		k.set(dx, 4, dz, b(Blocks.LANTERN));
	}

	// ------------------------------------------------------------------ bowls

	/**
	 * A bowl of radius {@code r} with its floor at {@code f}: a drum under it, a ring of upside-down stairs
	 * (the cup profile), a stair rim that holds the water, and lips (a notch at the compass points, wider
	 * on bigger bowls) where the water falls into the basin below ({@code rb}, whose water is at
	 * {@code lowWater}).
	 */
	private static void bowl(FountainKit k, int shape, int r, int f, int rb, int tier, int baseY, int lowWater) {
		boolean light = tier >= 2;
		int dr = r >= 3 ? r - 2 : 0;
		// [depth] the drum: a stone-brick shaft with a collar of accent under the bowl
		for (int y = baseY + 1; y < f; y++) {
			for (int dz = -dr; dz <= dr; dz++) {
				for (int dx = -dr; dx <= dr; dx++) {
					if (!ins(shape, dx, dz, dr)) {
						continue;
					}
					if (dr == 0) {
						k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR));
					} else {
						k.set(dx, y, dz, k.pick(dx, y, dz, b(Blocks.STONE_BRICKS), b(Blocks.CHISELED_STONE_BRICKS), b(Blocks.STONE_BRICKS),
								b(Blocks.POLISHED_ANDESITE)));
					}
				}
			}
		}
		int wl = r >= 5 ? r / 4 : 0; // half-width of a lip beyond its centre cell
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				if (!ins(shape, dx, dz, r)) {
					continue;
				}
				if (!rim(shape, dx, dz, r)) {
					// [palette] each bowl is lighter than the one below
					k.set(dx, f, dz, light ? k.pick(dx, f, dz, b(Blocks.POLISHED_DIORITE), b(Blocks.CALCITE), b(Blocks.POLISHED_DIORITE))
							: k.pick(dx, f, dz, b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_DIORITE), b(Blocks.POLISHED_ANDESITE)));
					k.set(dx, f + 1, dz, WATER);
					continue;
				}
				Direction in = inward(shape, dx, dz, r);
				int sx = Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : 0;
				int sz = sx == 0 ? Integer.signum(dz) : 0;
				int ox = dx + sx;
				int oz = dz + sz;
				boolean lip = Math.min(Math.abs(dx), Math.abs(dz)) <= wl && !ins(shape, ox, oz, r) && ins(shape, ox, oz, rb) && !rim(shape, ox, oz, rb);
				if (lip) {
					// the notch: water carries on over the lip and falls into the basin below
					k.set(dx, f, dz, b(Blocks.POLISHED_DIORITE));
					k.set(dx, f + 1, dz, WATER);
					for (int y = lowWater + 1; y <= f + 1; y++) {
						k.set(ox, y, oz, FALL);
					}
					continue;
				}
				// [depth] the underside curves in towards the drum; the rim stair has its back to the water
				k.set(dx, f, dz, stairTop(light ? Blocks.POLISHED_DIORITE_STAIRS : Blocks.POLISHED_ANDESITE_STAIRS, in));
				k.set(dx, f + 1, dz, light ? k.pick(dx, f + 1, dz, stair(Blocks.SMOOTH_QUARTZ_STAIRS, in), stair(Blocks.SMOOTH_QUARTZ_STAIRS, in),
						stair(Blocks.POLISHED_DIORITE_STAIRS, in))
						: k.pick(dx, f + 1, dz, stair(Blocks.POLISHED_DIORITE_STAIRS, in), stair(Blocks.POLISHED_DIORITE_STAIRS, in),
								stair(Blocks.SMOOTH_QUARTZ_STAIRS, in)));
			}
		}
	}

	/**
	 * The top: a stem (quartz, capped in copper or gold) rising from the top bowl's floor (or from the pool, when
	 * the top has no bowl), and water on its cap that falls on four sides into the water below.
	 */
	private static void crown(FountainKit k, int f, int x, int baseWater, BlockState accent, boolean bowl) {
		int st = f + 2 + x; // y of the cap
		if (!bowl) {
			for (int y = 1; y < f; y++) {
				k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR));
			}
		}
		k.set(0, f, 0, bowl ? b(Blocks.POLISHED_DIORITE) : b(Blocks.CHISELED_QUARTZ_BLOCK));
		k.set(0, f + 1, 0, b(Blocks.CHISELED_QUARTZ_BLOCK));
		for (int y = f + 2; y < st; y++) {
			k.set(0, y, 0, pillar(Blocks.QUARTZ_PILLAR));
		}
		k.set(0, st, 0, accent);
		k.set(0, st + 1, 0, WATER);
		for (Direction dir : DIRS) {
			for (int y = baseWater + 1; y <= st + 1; y++) {
				k.set(dir.getStepX(), y, dir.getStepZ(), FALL);
			}
		}
	}
}
