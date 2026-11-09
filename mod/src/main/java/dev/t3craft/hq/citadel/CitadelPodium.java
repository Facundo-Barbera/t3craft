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
	// design/citadel-terraces3: the face of a wall climbs from dark to light (deepslate, then tuff, then stone bricks) instead of
	// a black body under a white coping, and the pilasters are a mid stone with a light cap
	private static final BlockState POLISHED = BASE;
	private static final BlockState TUFF = b(Blocks.TUFF_BRICKS);
	private static final BlockState STONE = b(Blocks.STONE_BRICKS);
	private static final BlockState STONE_MOSSY = b(Blocks.MOSSY_STONE_BRICKS);
	private static final BlockState PIER = STONE;

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
		return cheek(p, x, z, d) ? (h + 1) / 2 + 1 : h / 2;
	}

	/** Cells (from the street) where a cheek ends in a newel: the foot, both ends of the landing and the top of the flights. */
	private static boolean newel(Profile p, int d) {
		int landEnd = p.fa() + p.land();
		return d == 1 || d == p.fa() || d == landEnd + 1 || d == landEnd + 2 * y0() - p.fa();
	}

	/** The one-column parapet on each side of a flight (none on the landing, where the ring walk comes in). */
	private static boolean cheek(Profile p, int x, int z, int d) {
		return !landing(p, d) && RingCampusGeometry.radialDistance(x, z, 8) > CitadelCanvas.dims().avenueHalf - 1.0;
	}

	/**
	 * Cells from the street of a terrace column, bent by up to about 2.5 cells along the circle (lobes of 20 to 35 cells
	 * of arc) so the walls of the tiers are not perfect circles; the flights of the avenues are not bent.
	 */
	private static int terraceD(int x, int z, double r) {
		double a = RingCampusGeometry.theta(x, z);
		// design/citadel-terraces3: five long lobes and a faint ripple, so the wall keeps runs of 8 or more cells before a step
		double warp = 1.3 * Math.sin(5 * a + 0.3) + 0.35 * Math.sin(11 * a + 1.7);
		return (int) Math.round(CitadelCanvas.dims().rampR - r + warp);
	}

	/** The top block (y) of any column near the podium, as this class paints it (0 for the street, the patio and beyond). */
	private static int top(CitadelCanvas c, Profile p, int x, int z) {
		RingCampusGeometry.Zone zn = c.zoneAt(x, z);
		return switch (zn.kind()) {
			case HUB, HUB_WALL, GALLERY, BRIDGE -> y0();
			case TERRACE -> terraceTop(p, terraceD(x, z, RingCampusGeometry.r(x, z)));
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

	private static int lowestDiagonal(CitadelCanvas c, Profile p, int x, int z) {
		int low = Integer.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				low = Math.min(low, top(c, p, x + dx, z + dz));
			}
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
		int d = terraceD(x, z, r);
		int top = terraceTop(p, d);
		int low = lowestNeighbour(c, p, x, z);
		solid(c, x, z, top, low);
		if (d <= p.fa()) {
			apron(c, p, x, z, d);
		} else if (d <= p.fa() + p.land()) {
			ringWalk(c, p, x, z, d, top, low);
		} else {
			bed(c, p, x, z, d, top);
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
		// only the central runner is bamboo (the yellow); the rest of the flight is smooth stone
		int band = CitadelPaving.rampBand(x, z) == 0 ? 0 : 2;
		boolean landing = landing(p, d);
		boolean cheek = cheek(p, x, z, d);
		int top = rampTop(p, x, z, d);
		int low = lowestNeighbour(c, p, x, z);
		solid(c, x, z, top, low);
		if (cheek) {
			// one column wide: the wall palette under a coping that follows the flight; a newel (pier, calcite cap and lantern)
			// stops each flight at its foot, at both ends of the landing and at the top, so the cheeks end in a finial
			if (newel(p, d)) {
				c.set(x, top, z, PIER);
				c.set(x, top + 1, z, CALCITE);
				c.set(x, top + 2, z, b(Blocks.LANTERN));
			} else {
				c.set(x, top, z, coping(x, z));
			}
		} else if (landing) {
			c.set(x, top, z, CitadelPaving.rampFull(band));
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
		// a gradient: a light course under the coping, a mixed middle, a dark footing (3 courses at most)
		int h = hash(x, z, 311 + y) % 20;
		if (depth == 1) {
			return h < 8 ? TUFF : h < 13 ? STONE : h < 16 ? STONE_MOSSY : BRICK;
		}
		if (depth == 2) {
			return h < 5 ? POLISHED : h < 10 ? BRICK : h < 14 ? TUFF : h < 17 ? BRICK_CRACKED : TILES;
		}
		return h < 8 ? POLISHED : h < 14 ? BRICK : h < 17 ? TILES : BRICK_CRACKED;
	}

	/** The face of the gallery's plinth (wall B) at {@code depth} courses under its rim, in the palette of the other walls. */
	static BlockState plinthFace(int x, int y, int z, int depth) {
		return wall(x, y, z, depth);
	}

	/** The coping of a wall: mostly calcite, with stone bricks now and then so the line is not a flat white stripe. */
	private static BlockState coping(int x, int z) {
		return hash(x, z, 351) % 5 < 2 ? STONE : CALCITE;
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
		if (d == p.fa() - 3 && h < 38 && RingCampusGeometry.radialDistance(x, z, 8) >= CitadelCanvas.dims().avenueHalf + 2.0) {
			// design/citadel-terraces3: a broken line of azaleas on the lawn side of the paved walk, green in front of wall A
			c.set(x, 1, z, leaves(h % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES));
		} else if (h < 22) {
			Flora.plantOn(c, x, 0, z, Flora.bloom(1 + Math.floorDiv(x + z, 6), h >> 1));
		} else if (h < 34) {
			Flora.plantOn(c, x, 0, z, b(Blocks.SHORT_GRASS));
		}
	}

	/** The ring walk of the middle tier: a coping on the edge of wall A, the kit's floor between. */
	private static void ringWalk(CitadelCanvas c, Profile p, int x, int z, int d, int top, int lowestNeighbour) {
		if (d == p.fa() + 1) {
			// the coping is a continuous line: a cell that only touches the drop at a corner gets it too
			c.set(x, top, z, top - Math.min(lowestNeighbour, lowestDiagonal(c, p, x, z)) >= 2 ? coping(x, z) : c.accent());
		} else if (d == p.fa() + p.land()) {
			c.set(x, top, z, c.accent());
		} else {
			c.set(x, top, z, c.floor());
		}
	}

	/** The beds at the foot of the gallery's plinth: low flowers and a bush now and then, never a tree. */
	private static void bed(CitadelCanvas c, Profile p, int x, int z, int d, int top) {
		// design/citadel-terraces3: three bands from the ring walk to the plinth. The verge beside the walk is low flowers
		// (nothing to step round), then ferns and flowers, then a shrub band against the wall (azaleas one block high with
		// ferns between: green in front of the wall, never a hedge that closes it); no shrubs next to a stair
		int e = d - (p.fa() + p.land()); // 1 on the cell beside the ring walk
		boolean clear = RingCampusGeometry.radialDistance(x, z, 8) < CitadelCanvas.dims().avenueHalf + 2.0;
		c.set(x, top, z, hash(x, z, 331) % (e >= 3 ? 4 : 8) == 0 ? MOSS : GRASS);
		int h = hash(x, z, 332) % 100;
		int pal = 2 + Math.floorDiv(x, 5) + Math.floorDiv(z, 5);
		if (e <= 2 || clear) {
			if (h < 45) {
				Flora.plantOn(c, x, top, z, Flora.bloom(pal, h >> 1));
			} else if (h < 60) {
				Flora.plantOn(c, x, top, z, b(Blocks.SHORT_GRASS));
			}
		} else if (h < 40) {
			c.set(x, top + 1, z, leaves(h % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES));
		} else if (h < 62) {
			Flora.plantOn(c, x, top, z, b(Blocks.FERN));
		} else if (h < 84) {
			Flora.plantOn(c, x, top, z, Flora.bloom(pal, h >> 1));
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
			if (RingCampusGeometry.radialDistance(x, z, 8) <= clear
					|| Math.floorMod(Math.round(RingCampusGeometry.theta(x, z) * r), every) != 0) {
				continue;
			}
			int here = terraceTop(p, terraceD(x, z, r));
			for (Direction dir : DIRS) {
				int hn = top(c, p, x + dir.getStepX(), z + dir.getStepZ());
				if (hn < here + 2) {
					continue;
				}
				// design/citadel-terraces3: only in the middle of a run of the wall (a cell each side and two cells along), so no
				// pilaster stands alone at the end of a wall or beside a stair, and two cells wide
				int tx = dir.getStepZ() != 0 ? 1 : 0;
				int tz = dir.getStepX() != 0 ? 1 : 0;
				if (against(c, p, x - tx, z - tz, dir, here, hn, clear) && against(c, p, x + tx, z + tz, dir, here, hn, clear)
						&& against(c, p, x + 2 * tx, z + 2 * tz, dir, here, hn, clear)) {
					pier(c, x, z, here, hn, dir);
					pier(c, x + tx, z + tz, here, hn, dir);
				}
				break;
			}
		}
	}

	/** True when the cell stands at the foot of the same wall (same two levels), on a terrace and clear of the stairs. */
	private static boolean against(CitadelCanvas c, Profile p, int x, int z, Direction dir, int here, int hn, double clear) {
		return c.zoneAt(x, z).kind() == Kind.TERRACE && top(c, p, x, z) == here && top(c, p, x + dir.getStepX(), z + dir.getStepZ()) == hn
				&& RingCampusGeometry.radialDistance(x, z, 8) > clear;
	}

	/**
	 * The pier at the foot of a wall of top {@code wallTop}, standing on a surface at {@code floor}, against the wall to
	 * {@code towards}: it rises the whole height of the wall and ends in a quartz stair that meets the coping, so it reads as
	 * a buttress of the wall and not as a stump under it.
	 */
	private static void pier(CitadelCanvas c, int x, int z, int floor, int wallTop, Direction towards) {
		int capY = wallTop;
		for (int y = floor + 1; y < capY; y++) {
			c.set(x, y, z, PIER);
		}
		c.set(x, capY, z, stairs(Blocks.SMOOTH_QUARTZ_STAIRS, towards));
		for (int y = capY + 1; y <= capY + 3; y++) {
			c.set(x, y, z, CitadelCanvas.AIR);
		}
	}

	/** The coping of the gallery's outer rim: calcite on its outermost row. */
	static BlockState rim(double r) {
		return r > CitadelCanvas.dims().galleryR - 1.0 ? CALCITE : FOOT;
	}
}
