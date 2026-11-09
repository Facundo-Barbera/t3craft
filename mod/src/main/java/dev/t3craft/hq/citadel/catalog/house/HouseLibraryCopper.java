package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.door;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintBlock;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintPane;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A small round library under a copper dome: an eight-sided drum of calcite piers on a dark plinth with
 * tall arched windows (shelves and lecterns glow behind them), a hemispherical dome of waxed cut copper
 * that goes from new orange at the drum to verdigris at the crown, with ribs, four stained oculi and a gold
 * lantern finial, and a temple portico with two columns and a pediment.
 *
 * <p>Concept: Radcliffe Camera and the Pantheon, shrunk to a pocket library in the citadel palette. The copper patina
 * gradient is the memorable thing: from the iso view it reads as a green-orange jewel on pale stone.
 *
 * <pre>
 *   y15-17   lightning rod, lantern        plan: v0 portico (columns, steps)
 *   y8-13    copper dome, ribs, oculi            v fv  flat door front
 *   y7       cornice, corbels                    drum of radius R centred at the back of the plot
 *   y3-6     arched windows between piers        hedges in the four corners
 *   y0-2     dark plinth, pilasters
 * </pre>
 *
 * Size: 9–11 × 11–13 × 18+.
 */
public final class HouseLibraryCopper implements Variant {
	@Override
	public String id() {
		return "house_library_copper";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 11;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 13;
	}

	private static final DyeColor[] JEWELS = {DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.YELLOW, DyeColor.CYAN};

	/** The rotunda's geometry, measured from the plot. */
	private record Geo(double cu, double cv, double r, int fv, int lo, int hi) {
		double dist(int u, int v) {
			return Math.hypot(u - cu, v - cv);
		}

		/** Position inside the octagon sector, 0..1: piers at the ends, windows round 0.5. */
		double sector(int u, int v) {
			double a = Math.atan2(v - cv, u - cu);
			double s = a / (Math.PI / 4) + 0.5; // pier boundaries at k*45° + 22.5° so windows face the 8 directions
			return s - Math.floor(s);
		}

		Direction out(int u, int v) {
			double du = u - cu;
			double dv = v - cv;
			return Math.abs(du) > Math.abs(dv) ? (du > 0 ? Direction.EAST : Direction.WEST) : (dv > 0 ? Direction.SOUTH : Direction.NORTH);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random rng = p.rng(0x11B2_A417L);
		int w = p.w();
		int d = p.d();
		double r = Math.min((w - 2) / 2.0, (d - 4) / 2.0);
		double cu = (w - 1) / 2.0;
		double cv = (d - 2) - r;
		int fv = (int) Math.ceil(cv - r - 1e-6);
		Geo g = new Geo(cu, cv, r, fv, (w - 1) / 2, w / 2);

		floorAndPlinth(c, p, g);
		drum(c, p, g);
		cornice(c, p, g);
		dome(c, p, g);
		front(c, p, g);
		interior(c, p, g, rng);
		garden(c, p, g, rng);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState plinth(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static BlockState pierStone(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 5) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.CALCITE);
	}

	private static Block copper(int level) {
		return switch (level) {
			case 0 -> Blocks.CUT_COPPER.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER.waxed().weathered();
			default -> Blocks.CUT_COPPER.waxed().oxidized();
		};
	}

	private static Block copperSlab(int level) {
		return switch (level) {
			case 0 -> Blocks.CUT_COPPER_SLAB.waxed().unaffected();
			case 1 -> Blocks.CUT_COPPER_SLAB.waxed().exposed();
			case 2 -> Blocks.CUT_COPPER_SLAB.waxed().weathered();
			default -> Blocks.CUT_COPPER_SLAB.waxed().oxidized();
		};
	}

	private static Block chiseled(int level) {
		return switch (level) {
			case 0 -> Blocks.CHISELED_COPPER.waxed().unaffected();
			case 1 -> Blocks.CHISELED_COPPER.waxed().exposed();
			case 2 -> Blocks.CHISELED_COPPER.waxed().weathered();
			default -> Blocks.CHISELED_COPPER.waxed().oxidized();
		};
	}

	private static BlockState paneFor(Geo g, int u, int v, boolean tint, Plot p, int y) {
		boolean alongU = g.out(u, v) == Direction.NORTH || g.out(u, v) == Direction.SOUTH;
		return tint ? tintPane(JEWELS[p.hash(u, y, v, JEWELS.length)], alongU) : clearPane(alongU);
	}

	// ------------------------------------------------------------------ plinth and floor

	private static void floorAndPlinth(CitadelCanvas c, Plot p, Geo g) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double dist = g.dist(u, v);
				if (dist <= g.r() - 1.0) {
					// [palette] inside: dark oak with a calcite rosette in the middle
					p.set(c, u, 0, v, dist <= 1.5 ? b(Blocks.POLISHED_DIORITE) : (dist <= 2.6 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.DARK_OAK_PLANKS)));
				} else if (dist <= g.r()) {
					p.set(c, u, 0, v, plinth(p, u, 0, v));
				} else if (dist <= g.r() + 1.0 && p.has(u, 0, v)) {
					// [depth] a stone kerb ring where the drum meets the ground
					p.set(c, u, 0, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ drum

	private static void drum(CitadelCanvas c, Plot p, Geo g) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double dist = g.dist(u, v);
				double sec = g.sector(u, v);
				boolean pier = sec < 0.2 || sec > 0.8;
				if (dist > g.r() - 1.0 && dist <= g.r()) {
					for (int y = 1; y <= 8; y++) {
						BlockState st;
						if (y <= 2) {
							st = plinth(p, u, y, v);
						} else if (y == 8) {
							st = b(Blocks.CHISELED_STONE_BRICKS);
						} else if (pier) {
							st = pierStone(p, u, y, v);
						} else if (y >= 6) {
							st = paneFor(g, u, v, true, p, y);
						} else {
							st = paneFor(g, u, v, false, p, y);
						}
						p.set(c, u, y, v, st);
					}
				}
				// [depth] pilasters: a stone strip a block proud of the drum at every pier, with a sloped cap
				if (dist > g.r() && dist <= g.r() + 1.0 && (sec < 0.1 || sec > 0.9)) {
					for (int y = 1; y <= 6; y++) {
						p.set(c, u, y, v, y <= 2 ? plinth(p, u, y, v) : pierStone(p, u, y, v));
					}
					p.set(c, u, 7, v, stair(Blocks.STONE_BRICK_STAIRS, opposite(g.out(u, v)), false));
				} else if (dist > g.r() && dist <= g.r() + 1.0 && !pier && p.has(u, 1, v)) {
					p.set(c, u, 2, v, slab(Blocks.POLISHED_ANDESITE_SLAB, true)); // [depth] a windowsill under every window
				}
			}
		}
	}

	private static Direction opposite(Direction dir) {
		return dir.getOpposite();
	}

	/** A cornice that overhangs the drum: upside-down stairs, the hanging piece towards the wall. */
	private static void cornice(CitadelCanvas c, Plot p, Geo g) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double dist = g.dist(u, v);
				if (dist > g.r() && dist <= g.r() + 0.95) {
					p.set(c, u, 8, v, stair(Blocks.STONE_BRICK_STAIRS, opposite(g.out(u, v)), true));
				}
			}
		}
	}

	// ------------------------------------------------------------------ dome

	/**
	 * [finish] a hemispherical shell of waxed cut copper, layer by layer, one level of patina per height: new orange
	 * at the drum, exposed, weathered, and verdigris at the crown. Eight chiselled ribs follow the pier lines.
	 */
	private static void dome(CitadelCanvas c, Plot p, Geo g) {
		int w = p.w();
		int d = p.d();
		double rd = g.r() + 0.3;
		int layers = (int) Math.round(rd) + 1;
		double[] rk = new double[layers + 1];
		for (int k = 0; k <= layers; k++) {
			double t = (k + 0.5) / (layers + 0.5);
			rk[k] = rd * Math.sqrt(Math.max(0.0, 1 - t * t));
		}
		for (int k = 0; k < layers; k++) {
			int y = 9 + k;
			int base = k <= 1 ? 2 : 3; // weathered skirt, verdigris crown
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					double dist = g.dist(u, v);
					boolean top = rk[k + 1] < 0.7;
					if (dist > rk[k] + 0.15 || (!top && dist <= Math.min(rk[k + 1] - 0.3, rk[k] - 1.25))) {
						continue;
					}
					// one patina stage per ring: weathered skirt, oxidized (verdigris) above; no per-block noise
					int level = base;
					double sec = g.sector(u, v);
					boolean rib = (sec < 0.07 || sec > 0.93) && k > 0;
					BlockState st;
					if (k == 1 && sec > 0.4 && sec < 0.6 && (Math.abs(u - g.cu()) < 0.6 || Math.abs(v - g.cv()) < 0.6)) {
						st = tintBlock(JEWELS[p.hash(u, 0, v, JEWELS.length)]); // oculi at the four compass points
					} else if (rib) {
						st = b(chiseled(2)); // chiselled ribs in the weathered tone: relief without colour noise
					} else {
						st = b(copper(level));
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		// finial: a stepped cap, a copper lantern-room and a lightning rod
		int uc = g.lo();
		int vc = (int) Math.round(g.cv());
		int top = 9 + layers;
		p.set(c, uc, top, vc, b(copperSlab(3)));
		p.set(c, uc, top + 1, vc, lantern(false));
		p.set(c, uc, top + 2, vc, b(Blocks.LIGHTNING_ROD.waxed().oxidized()));
		if (g.hi() != g.lo()) {
			p.set(c, g.hi(), top, vc, b(copperSlab(3)));
		}
	}

	// ------------------------------------------------------------------ front: door wall and portico

	private static void front(CitadelCanvas c, Plot p, Geo g) {
		int lo = g.lo();
		int hi = g.hi();
		int fv = g.fv();
		// the flat door front, five (or six) wide, on the drum's front chord
		for (int u = lo - 2; u <= hi + 2; u++) {
			for (int y = 1; y <= 8; y++) {
				BlockState st;
				boolean door = u >= lo && u <= hi;
				if (u == lo - 2 || u == hi + 2) {
					st = y <= 2 ? plinth(p, u, y, fv) : y == 8 ? b(Blocks.CHISELED_STONE_BRICKS) : pierStone(p, u, y, fv);
				} else if (door && y <= 2) {
					st = door(Blocks.DARK_OAK_DOOR, Direction.NORTH, y == 2);
				} else if (door && y == 3) {
					st = b(Blocks.CHISELED_STONE_BRICKS);
				} else if (y == 8) {
					st = b(Blocks.CHISELED_STONE_BRICKS);
				} else if (y >= 4 && y <= 7 && door) {
					st = tintPane(JEWELS[p.hash(u, y, fv, JEWELS.length)], true); // transom
				} else if (y == 1 || y == 2) {
					st = plinth(p, u, y, fv);
				} else {
					st = y <= 7 && y >= 3 ? clearPane(true) : pierStone(p, u, y, fv);
				}
				p.set(c, u, y, fv, st);
			}
		}
		// portico: floor, two columns, entablature, pediment roof along v
		for (int u = lo - 3; u <= hi + 3; u++) {
			for (int v = 0; v < fv; v++) {
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			}
		}
		for (int u : new int[] {lo - 3, hi + 3}) {
			p.set(c, u, 1, 0, b(Blocks.POLISHED_DEEPSLATE));
			for (int y = 2; y <= 5; y++) {
				p.set(c, u, y, 0, b(Blocks.QUARTZ_PILLAR));
			}
			p.set(c, u, 6, 0, b(Blocks.CHISELED_STONE_BRICKS)); // capital
		}
		for (int u = lo - 3; u <= hi + 3; u++) {
			p.set(c, u, 7, 0, b(u == lo - 3 || u == hi + 3 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS));
		}
		// roof rows (v 0..fv-1): stairs both ways with a full ridge when w is odd
		for (int v = 0; v < fv; v++) {
			for (int u = lo - 3; u <= hi + 3; u++) {
				int off = u <= lo ? lo - u : u - hi;
				int y = 8 + (3 - off);
				boolean ridge = lo == hi && u == lo;
				p.set(c, u, y, v, ridge ? b(Blocks.POLISHED_DEEPSLATE) : stair(Blocks.DEEPSLATE_TILE_STAIRS, u <= lo ? Direction.EAST : Direction.WEST, false));
				if (v == 0 && !ridge) {
					p.set(c, u, y, v, stair(Blocks.CHERRY_STAIRS, u <= lo ? Direction.EAST : Direction.WEST, false)); // pink rake
				}
			}
		}
		// pediment face: calcite triangle under the front roof edge with a medallion
		for (int u = lo - 2; u <= hi + 2; u++) {
			int off = u <= lo ? lo - u : u - hi;
			for (int y = 8; y < 8 + (3 - off); y++) {
				p.set(c, u, y, 0, p.hash(u, y, 0, 4) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE));
			}
		}
		p.set(c, lo, 9, 0, tintBlock(DyeColor.ORANGE));
		if (hi != lo) {
			p.set(c, hi, 9, 0, tintBlock(DyeColor.ORANGE));
		}
		// steps and lanterns
		for (int u = lo - 1; u <= hi + 1; u++) {
			p.set(c, u, 0, 0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		p.put(c, lo - 2, 6, 0, lantern(true), CitadelCanvas.HANGING);
		p.put(c, hi + 2, 6, 0, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ interior (seen through the windows)

	private static void interior(CitadelCanvas c, Plot p, Geo g, Random rng) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double dist = g.dist(u, v);
				if (dist > g.r() - 2.0 && dist <= g.r() - 1.0) {
					for (int y = 1; y <= 3; y++) {
						BlockState st = y == 2 && p.hash(u, y, v, 3) == 0 ? facing(Blocks.CHISELED_BOOKSHELF, opposite(g.out(u, v))) : b(Blocks.BOOKSHELF);
						p.set(c, u, y, v, st);
					}
				}
			}
		}
		// a reading lectern in the middle, lanterns on a chain from the crown
		int uc = g.lo();
		int vc = (int) Math.round(g.cv());
		p.set(c, uc, 1, vc, facing(Blocks.LECTERN, Direction.NORTH));
		p.put(c, uc, 8, vc, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ garden: hedges in the corners

	private static void garden(CitadelCanvas c, Plot p, Geo g, Random rng) {
		int w = p.w();
		int d = p.d();
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				double dist = g.dist(u, v);
				boolean portico = u >= g.lo() - 3 && u <= g.hi() + 3 && v < g.fv();
				if (dist > g.r() + 1.2 && !portico && v >= g.fv() - 1) {
					int k = p.hash(u, 0, v, 4);
					p.set(c, u, 0, v, k == 0 ? b(Blocks.MOSS_BLOCK) : CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
					if (k == 2) {
						p.set(c, u, 1, v, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
					}
				}
			}
		}
	}
}
