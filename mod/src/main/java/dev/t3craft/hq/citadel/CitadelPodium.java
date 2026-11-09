package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Dims;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.citadel.nature.Flora;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * design/citadel-terraces2: the climb from the inner street to the gallery of the hub, the "acropolis podium".
 *
 * <p>Between the gallery (r = galleryR, deck at {@code y0()}) and the inner street (r = rampR, ground) the old plan
 * had a plain grass slope with eight slab ramps. It is now a base in two tiers, so that the podium reads from the
 * street as a dark plinth with a light cornice, and every axis has a stair that is climbed on foot:
 *
 * <pre>
 *   r = rampR                                                                  r = galleryR
 *   street | apron (lawn, then a paved walk) |wall A| ring walk + beds        |wall B = gallery plinth| deck
 *   0      | 0                               | h1   | h1                       | y0()
 * </pre>
 *
 * <ul>
 * <li><b>Walls</b> (terrace columns between the avenues): a polished-deepslate base, a course of deepslate bricks
 *     and a calcite coping on the edge. Never more than half the podium high ({@code y0() / 2 + 1} at most, 3 at
 *     scale 2), so there is no tall slab of wall; a pilaster (buttress) every {@code s(5)} cells of arc.</li>
 * <li><b>Stairs</b> (the eight avenues, 13 wide at scale 2, about 9 between their kerbs): two flights of
 *     half-block steps (one cell per half block, so one cell of run per half block of rise) with a flat landing
 *     between them ({@code length - 2 * y0() - 1} cells, 3 at scale 2), flanked by a parapet that follows the
 *     flight. The landings are the crossings of a ring walk on the middle tier, so the tiers are connected.</li>
 * <li><b>Free foot</b>: the foot of wall A is a paved walk (3 cells) after a strip of lawn, the foot of wall B
 *     (the gallery plinth) a bed of low flowers; nothing taller than a flower stands within 2 cells of a wall except
 *     the pilasters.</li>
 * </ul>
 *
 * Everything is painted in the flat frame (ground at y = 0); {@link CitadelRelief} lifts it with the core. Where the
 * scale leaves no room for two flights and a landing ({@link #profile} is null, scale 1) the old slope is kept.
 */
public final class CitadelPodium {
	private CitadelPodium() {
	}

	private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	// [palette] dark heavy base, a brick course, a light coping: the three bands every wall of the podium reads as
	private static final BlockState BASE = b(Blocks.POLISHED_DEEPSLATE);
	private static final BlockState BRICK = b(Blocks.DEEPSLATE_BRICKS);
	private static final BlockState BRICK_CRACKED = b(Blocks.CRACKED_DEEPSLATE_BRICKS);
	private static final BlockState TILES = b(Blocks.DEEPSLATE_TILES);
	private static final BlockState PIER = b(Blocks.POLISHED_BLACKSTONE_BRICKS);

	/** The shape of the climb, in cells and blocks (see the class comment). */
	record Profile(int h1, int h2, int fa, int land, int length) {
	}

	/** The profile at the scale in use, or null when there is no room for it. */
	static @Nullable Profile profile() {
		Dims g = CitadelCanvas.dims();
		int h = y0();
		int len = (int) Math.round(g.rampR - g.galleryR);
		int h1 = h / 2;
		int h2 = h - h1;
		int land = len - 2 * h - 1;
		if (h1 < 1 || land < 2) {
			return null;
		}
		return new Profile(h1, h2, 2 * h1, land, len);
	}

	/** Cells from the street side: 0 at {@code r = rampR}, increasing towards the gallery. */
	private static int dOut(double r) {
		return (int) Math.round(CitadelCanvas.dims().rampR - r);
	}

	/** Half blocks of the stair at {@code d} cells from the street. */
	private static int halves(Profile p, int d) {
		if (d <= 0) {
			return 0;
		}
		if (d <= p.fa()) {
			return d;
		}
		int landEnd = p.fa() + p.land();
		if (d <= landEnd) {
			return p.fa();
		}
		return Math.min(2 * y0(), p.fa() + d - landEnd);
	}

	private static boolean landing(Profile p, int d) {
		return d > p.fa() && d <= p.fa() + p.land();
	}

	/** The top block (y) of the terrace column at {@code d} cells from the street. */
	private static int terraceTop(Profile p, int d) {
		return d <= p.fa() ? 0 : p.h1();
	}

	/** The top block (y) of a stair column, the parapet of the flights included. */
	private static int rampTop(Profile p, int x, int z, int d) {
		int h = halves(p, d);
		boolean cheek = CitadelPaving.rampBand(x, z) == 3 && !landing(p, d);
		return cheek ? (h + 1) / 2 + 1 : h / 2;
	}

	/** The top block (y) of any column near the podium, as this class paints it (0 for the street, the patio and beyond). */
	private static int top(CitadelCanvas c, Profile p, int x, int z) {
		RingCampusGeometry.Zone zn = c.zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL, GALLERY, BRIDGE -> y0();
			case TERRACE -> terraceTop(p, dOut(RingCampusGeometry.r(x, z)));
			case RAMP -> rampTop(p, x, z, dOut(RingCampusGeometry.r(x, z)));
			default -> 0;
		};
	}

	private static int lowestNeighbour(CitadelCanvas c, Profile p, int x, int z) {
		int low = Integer.MAX_VALUE;
		for (Direction dir : DIRS) {
			low = Math.min(low, top(c, p, x + dir.getStepX(), z + dir.getStepZ()));
		}
		return low;
	}

	private static int hash(int x, int z, int salt) {
		return Flora.hash(x, z, salt);
	}

	// ------------------------------------------------------------------ the columns

	/** A terrace column (between two avenues). Returns false when the old slope must be kept. */
	static boolean terrace(CitadelCanvas c, int x, int z, double r) {
		Profile p = profile();
		if (p == null) {
			return false;
		}
		int d = dOut(r);
		int top = terraceTop(p, d);
		int low = lowestNeighbour(c, p, x, z);
		solid(c, x, z, top, low);
		if (d <= p.fa()) {
			apron(c, p, x, z, d);
		} else if (d <= p.fa() + p.land()) {
			ringWalk(c, p, x, z, d, top, low);
		} else {
			bed(c, x, z, top);
		}
		return true;
	}

	/** A stair column (an avenue's mouth into the podium). Returns false when the old ramp must be kept. */
	static boolean ramp(CitadelCanvas c, int x, int z, double r) {
		Profile p = profile();
		if (p == null) {
			return false;
		}
		int d = dOut(r);
		int h = halves(p, d);
		int band = CitadelPaving.rampBand(x, z);
		boolean landing = landing(p, d);
		boolean cheek = band == 3 && !landing;
		int top = rampTop(p, x, z, d);
		int low = lowestNeighbour(c, p, x, z);
		solid(c, x, z, top, low);
		if (cheek) {
			c.set(x, top, z, CALCITE);
		} else if (landing) {
			c.set(x, top, z, CitadelPaving.rampFull(Math.min(band, 2)));
		} else {
			c.set(x, top, z, CitadelPaving.rampFull(band));
			if (h % 2 == 1) {
				c.set(x, top + 1, z, CitadelPaving.rampHalf(band));
			}
		}
		return true;
	}

	/** The solid of a column from the ground to {@code top - 1}: the wall palette where a side shows, deepslate where it does not. */
	private static void solid(CitadelCanvas c, int x, int z, int top, int lowestNeighbour) {
		for (int y = 0; y < top; y++) {
			c.set(x, y, z, y > lowestNeighbour ? wall(x, y, z, top - y) : FOOT);
		}
	}

	/** One block of a wall seen from the low side; {@code depth} 1 is the course under the coping. */
	private static BlockState wall(int x, int y, int z, int depth) {
		if (depth == 1) {
			int h = hash(x, z, 311 + y) % 10;
			return h < 2 ? BRICK_CRACKED : h < 4 ? TILES : BRICK;
		}
		return BASE;
	}

	// ------------------------------------------------------------------ the tiers

	/**
	 * The apron at the foot of wall A: lawn with low flowers beside the street, then a paved walk of 3 cells that
	 * runs along the wall. Nothing stands on it.
	 */
	private static void apron(CitadelCanvas c, Profile p, int x, int z, int d) {
		if (d > p.fa() - 3) {
			c.set(x, 0, z, d == p.fa() ? c.accent() : c.floor());
			return;
		}
		c.set(x, 0, z, hash(x, z, 321) % 9 == 0 ? MOSS : GRASS);
		int h = hash(x, z, 322) % 100;
		if (h < 22) {
			Flora.plantOn(c, x, 0, z, Flora.bloom(1 + Math.floorDiv(x + z, 6), h >> 1));
		} else if (h < 34) {
			Flora.plantOn(c, x, 0, z, b(Blocks.SHORT_GRASS));
		}
	}

	/** The ring walk of the middle tier: a coping on the edge of wall A, the kit's floor between. */
	private static void ringWalk(CitadelCanvas c, Profile p, int x, int z, int d, int top, int lowestNeighbour) {
		if (d == p.fa() + 1) {
			c.set(x, top, z, top - lowestNeighbour >= 2 ? CALCITE : c.accent());
		} else if (d == p.fa() + p.land()) {
			c.set(x, top, z, c.accent());
		} else {
			c.set(x, top, z, c.floor());
		}
	}

	/** The beds at the foot of the gallery's plinth: low flowers and a bush now and then, never a tree. */
	private static void bed(CitadelCanvas c, int x, int z, int top) {
		c.set(x, top, z, hash(x, z, 331) % 8 == 0 ? MOSS : GRASS);
		int h = hash(x, z, 332) % 100;
		int pal = 2 + Math.floorDiv(x, 5) + Math.floorDiv(z, 5);
		if (h < 40) {
			Flora.plantOn(c, x, top, z, Flora.bloom(pal, h >> 1));
		} else if (h < 48) {
			c.set(x, top + 1, z, leaves(h % 2 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
		} else if (h < 60) {
			Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
		}
	}

	// ------------------------------------------------------------------ buttresses

	/**
	 * A pilaster every {@code s(5)} cells of arc on the foot of each wall: a pier of polished blackstone bricks capped
	 * by a stair against the wall, one block under the coping. Not within 2 cells of a stair, so the foot stays free.
	 * Runs on the finished flat plan (the beds and the paving are already there).
	 */
	static void buttresses(CitadelCanvas c) {
		Profile p = profile();
		if (p == null) {
			return;
		}
		int every = Math.max(4, c.s(5));
		double clear = c.g.avenueHalf + 2.0;
		for (long key : c.zones.keySet()) {
			if (c.zones.get(key).kind() != Kind.TERRACE) {
				continue;
			}
			int x = CitadelCanvas.colX(key);
			int z = CitadelCanvas.colZ(key);
			double r = RingCampusGeometry.r(x, z);
			int d = dOut(r);
			if (d != p.fa() && !wallBFoot(c, x, z) || RingCampusGeometry.radialDistance(x, z, 8) <= clear) {
				continue;
			}
			if (Math.floorMod(Math.round(RingCampusGeometry.theta(x, z) * r), every) != 0) {
				continue;
			}
			int here = terraceTop(p, d);
			for (Direction dir : DIRS) {
				int hn = top(c, p, x + dir.getStepX(), z + dir.getStepZ());
				if (hn >= here + 2) {
					pier(c, x, z, here, hn, dir);
					break;
				}
			}
		}
	}

	/** A bed column against the gallery's plinth. */
	private static boolean wallBFoot(CitadelCanvas c, int x, int z) {
		for (Direction dir : DIRS) {
			if (c.zoneAt(x + dir.getStepX(), z + dir.getStepZ()).kind() == Kind.GALLERY) {
				return true;
			}
		}
		return false;
	}

	/** The pier at the foot of a wall of top {@code wallTop}, standing on a surface at {@code floor}, against the wall to {@code towards}. */
	private static void pier(CitadelCanvas c, int x, int z, int floor, int wallTop, Direction towards) {
		int capY = wallTop - 1;
		for (int y = floor + 1; y < capY; y++) {
			c.set(x, y, z, PIER);
		}
		c.set(x, capY, z, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, towards));
		for (int y = capY + 1; y <= capY + 3; y++) {
			c.set(x, y, z, CitadelCanvas.AIR);
		}
	}

	/** The coping of the gallery's outer rim: calcite on its outermost row. */
	static BlockState rim(double r) {
		return r > CitadelCanvas.dims().galleryR - 1.0 ? CALCITE : FOOT;
	}
}
