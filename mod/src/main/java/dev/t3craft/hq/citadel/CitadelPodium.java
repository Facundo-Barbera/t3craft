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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
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
	private static final BlockState ANDESITE = b(Blocks.POLISHED_ANDESITE);

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
		return cheek(p, x, z, d) ? cheekTop(h) : h / 2;
	}

	/**
	 * design/citadel-terraces4: the block (y) that ends a cheek of the flights at {@code h} half blocks of climb: the cheek rises a
	 * half block per cell, 1.5 blocks over the tread, as a full block on the odd cells and a slab on the even ones
	 * (it used to rise a hard block every second cell).
	 */
	static int cheekTop(int h) {
		return (h + 3) / 2 + (h % 2 == 0 ? 1 : 0);
	}

	/** Cells (from the street) where a cheek ends in a newel: the foot, both ends of the landing and the top of the flights. */
	private static boolean newel(Profile p, int d) {
		int landEnd = p.fa() + p.land();
		return d == 1 || d == p.fa() || d == landEnd + 1 || d == landEnd + 2 * y0() - p.fa();
	}

	/** The newels that carry a lantern: two at the foot, two where the second flight starts and two at the top (the cheeks are two). */
	private static boolean lit(Profile p, int d) {
		int landEnd = p.fa() + p.land();
		return d == 1 || d == landEnd + 1 || d == landEnd + 2 * y0() - p.fa();
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
			// one column wide: the wall palette under a cap that climbs with the flight a half block per cell (full block on the odd
			// cells, slab on the even ones); a newel (pier, calcite cap) stops each flight at its foot, at both ends of the landing
			// and at the top, and the ones at the foot, the start of the second flight and the top carry a lantern
			if (newel(p, d)) {
				c.set(x, top, z, PIER);
				c.set(x, top + 1, z, CALCITE);
				if (lit(p, d)) {
					c.set(x, top + 2, z, b(Blocks.LANTERN));
				}
			} else if (h % 2 == 0) {
				c.set(x, top, z, CitadelRelief.slabOf(copingBlock(x, z)));
			} else {
				c.set(x, top, z, copingBlock(x, z));
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
	static BlockState wall(int x, int y, int z, int depth) {
		// design/citadel-terraces4: a gradient with no black band: a light course under the coping, a mid grey-green middle and a
		// dark (deepslate brick, not tile) footing; polished deepslate and tiles only in small amounts, deep down
		int h = hash(x, z, 311 + y) % 20;
		if (depth == 1) {
			return h < 7 ? TUFF : h < 12 ? STONE : h < 15 ? STONE_MOSSY : h < 18 ? ANDESITE : BRICK;
		}
		if (depth == 2) {
			return h < 6 ? BRICK : h < 11 ? TUFF : h < 14 ? STONE : h < 16 ? STONE_MOSSY : h < 18 ? BRICK_CRACKED : POLISHED;
		}
		return h < 8 ? BRICK : h < 12 ? BRICK_CRACKED : h < 16 ? POLISHED : TILES;
	}

	/** The face of the gallery's plinth (wall B) at {@code depth} courses under its rim, in the palette of the other walls. */
	static BlockState plinthFace(int x, int y, int z, int depth) {
		return wall(x, y, z, depth);
	}

	/**
	 * The light cap of a wall, a stair cheek or the edge of the gallery: runs of 3 cells of one material (calcite, stone bricks,
	 * polished andesite, tuff bricks, mossy bricks) so the top line is never a continuous white stripe.
	 */
	static BlockState copingBlock(int x, int z) {
		int j = hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 351) % 20;
		return j < 7 ? CALCITE : j < 12 ? STONE : j < 15 ? ANDESITE : j < 18 ? TUFF : STONE_MOSSY;
	}

	/** The cap of the cornice of a wall, one material for each bay of {@link #BAY} cells of arc. */
	private static BlockState capBlock(long bay, int top) {
		int j = hash((int) bay, top, 361) % 20;
		return j < 6 ? CALCITE : j < 11 ? STONE : j < 14 ? ANDESITE : j < 17 ? TUFF : STONE_MOSSY;
	}

	/** The stairs of the corbel under a cap (the cap's family, a step darker). */
	private static Block corbelStairs(long bay, int top) {
		int j = hash((int) bay, top, 362) % 10;
		return j < 4 ? Blocks.TUFF_BRICK_STAIRS : j < 8 ? Blocks.STONE_BRICK_STAIRS : Blocks.MOSSY_STONE_BRICK_STAIRS;
	}

	/** The gallery's outer edge (two rows beyond the deck, see {@code CitadelPaving#promenade}) and the stripe inside it, with no black row. */
	static BlockState deckEdge(CitadelCanvas c, int x, int z, double r, BlockState base) {
		if (profile() == null) {
			return base;
		}
		double rim = CitadelPaving.promenadeRim();
		double w = CitadelPaving.promenadeWidth();
		if (RingCampusGeometry.radialDistance(x, z, 8) <= c.g.avenueHalf) {
			return base; // the mouths of the avenues keep their runner
		}
		if (r > rim) {
			if (base == CALCITE) {
				return copingBlock(x, z);
			}
			if (base == FOOT) {
				return hash(x, z, 371) % 3 == 0 ? TUFF : ANDESITE;
			}
			return base;
		}
		if (r > rim - w / 6 && base == c.accent()) {
			return hash(x, z, 372) % 4 == 0 ? TUFF : STONE;
		}
		return base;
	}

	// ------------------------------------------------------------------ the tiers

	/**
	 * The apron at the foot of wall A: lawn with low flowers beside the street, then a paved walk of 3 cells that
	 * runs along the wall. Nothing stands on it.
	 */
	private static void apron(CitadelCanvas c, Profile p, int x, int z, int d) {
		if (d == p.fa()) {
			verge(c, x, z);
			return;
		}
		if (d > p.fa() - 4) {
			// the paved walk, 3 cells wide, with its accent line on the wall side
			c.set(x, 0, z, d == p.fa() - 1 ? c.accent() : c.floor());
			return;
		}
		c.set(x, 0, z, hash(x, z, 321) % 9 == 0 ? MOSS : GRASS);
		int h = hash(x, z, 322) % 100;
		if (d == p.fa() - 4 && h < 38 && RingCampusGeometry.radialDistance(x, z, 8) >= CitadelCanvas.dims().avenueHalf + 2.0) {
			// design/citadel-terraces3: a broken line of azaleas on the lawn side of the paved walk, green in front of wall A
			c.set(x, 1, z, leaves(h % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES));
		} else if (h < 22) {
			Flora.plantOn(c, x, 0, z, Flora.bloom(1 + Math.floorDiv(x + z, 6), h >> 1));
		} else if (h < 34) {
			Flora.plantOn(c, x, 0, z, b(Blocks.SHORT_GRASS));
		}
	}

	/**
	 * design/citadel-terraces4: the verge at the foot of wall A, the row under its cornice (the buttresses stand here, not on the
	 * walk): grass with low ferns and flowers, an azalea now and then, never anything that closes the walk.
	 */
	private static void verge(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, hash(x, z, 323) % 5 == 0 ? MOSS : GRASS);
		int h = hash(x, z, 324) % 100;
		if (h < 14) {
			c.set(x, 1, z, leaves(h % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES));
		} else if (h < 40) {
			Flora.plantOn(c, x, 0, z, b(Blocks.FERN));
		} else if (h < 70) {
			Flora.plantOn(c, x, 0, z, Flora.bloom(1 + Math.floorDiv(x + z, 6), h >> 1));
		}
	}

	/** The ring walk of the middle tier: a coping on the edge of wall A, the kit's floor between. */
	private static void ringWalk(CitadelCanvas c, Profile p, int x, int z, int d, int top, int lowestNeighbour) {
		// design/citadel-terraces4: the edge over wall A is a line of the kit's accent; the cap of the cornice (below) is the light line
		c.set(x, top, z, d == p.fa() + 1 || d == p.fa() + p.land() ? c.accent() : c.floor());
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

	// ------------------------------------------------------------------ cornice and buttresses

	/** Cells of arc of a bay of the cornice: 2 of buttress, then a run of corbel and cap in one material. */
	private static final int BAY = 8;

	/** A cell at the foot of a high wall (wall A: the verge of the apron; wall B: the last bed cell, against the gallery's plinth). */
	private record Foot(int x, int z, Direction toWall, int floor, int top, long u) {
	}

	/**
	 * design/citadel-terraces4: the cornice and the buttresses of the walls of the tiers, on the finished flat plan (the beds and the
	 * paving are already there).
	 *
	 * <p>Along the foot of each wall (the verge of wall A, the last bed cell against wall B) the wall is crowned by a cornice that
	 * overhangs the foot by one block: inverted stairs (the corbel, the high side against the wall) one block under a cap at the
	 * level of the walk above. The cap is one material for each bay of {@link #BAY} cells (calcite, stone bricks, polished andesite,
	 * tuff bricks, mossy bricks), so the top is not a continuous white line. Two cells of each bay are a buttress instead, one cell
	 * deep, the whole height of the wall, capped in quartz: it stands on the verge, never on the walk. Nothing walks under the
	 * cornice (the foot is planted) and the walk above is not narrower.
	 */
	static void buttresses(CitadelCanvas c) {
		Profile p = profile();
		if (p == null) {
			return;
		}
		double clear = c.g.avenueHalf + 1.0;
		double clearPier = c.g.avenueHalf + 3.0;
		List<Foot> feet = new ArrayList<>();
		for (long key : c.zones.keySet()) {
			if (c.zones.get(key).kind() != Kind.TERRACE) {
				continue;
			}
			int x = CitadelCanvas.colX(key);
			int z = CitadelCanvas.colZ(key);
			double r = RingCampusGeometry.r(x, z);
			if (RingCampusGeometry.radialDistance(x, z, 8) <= clear) {
				continue;
			}
			int d = terraceD(x, z, r);
			int here = terraceTop(p, d);
			Direction toWall = null;
			int wallTop = 0;
			for (Direction dir : DIRS) {
				int nx = x + dir.getStepX();
				int nz = z + dir.getStepZ();
				Kind k = c.zoneAt(nx, nz).kind();
				int t = k == Kind.TERRACE || k == Kind.GALLERY ? top(c, p, nx, nz) : 0;
				boolean wallB = k == Kind.GALLERY && here == p.h1();
				boolean wallA = k == Kind.TERRACE && here == 0 && d == p.fa();
				if ((wallA || wallB) && t >= here + 2) {
					toWall = dir;
					wallTop = t;
					break;
				}
			}
			if (toWall == null) {
				// the corner cell of a wall that turns: touches the wall only by a corner, between two foot cells
				for (int dx = -1; dx <= 1 && toWall == null; dx += 2) {
					for (int dz = -1; dz <= 1 && toWall == null; dz += 2) {
						Kind k = c.zoneAt(x + dx, z + dz).kind();
						if (k != Kind.TERRACE && k != Kind.GALLERY) {
							continue;
						}
						int t = top(c, p, x + dx, z + dz);
						boolean wallB = k == Kind.GALLERY && here == p.h1();
						boolean wallA = k == Kind.TERRACE && here == 0 && d == p.fa();
						if ((wallA || wallB) && t >= here + 2 && top(c, p, x + dx, z) < here + 2 && top(c, p, x, z + dz) < here + 2
								&& c.zoneAt(x + dx, z).kind() != Kind.RAMP && c.zoneAt(x, z + dz).kind() != Kind.RAMP) {
							toWall = dx > 0 ? Direction.EAST : Direction.WEST;
							wallTop = t;
						}
					}
				}
			}
			if (toWall != null && wallTop - here == 3) {
				feet.add(new Foot(x, z, toWall, here, wallTop, Math.round(RingCampusGeometry.theta(x, z) * r)));
			}
		}
		for (Foot f : feet) {
			long bay = Math.floorDiv(f.u(), BAY);
			int k = (int) Math.floorMod(f.u(), (long) BAY);
			boolean pier = k < 2 && RingCampusGeometry.radialDistance(f.x(), f.z(), 8) > clearPier;
			if (pier) {
				// the buttress: one cell deep, the height of the wall, a quartz cap level with the walk
				for (int y = f.floor() + 1; y < f.top(); y++) {
					c.set(f.x(), y, f.z(), PIER);
				}
				c.set(f.x(), f.top(), f.z(), b(Blocks.SMOOTH_QUARTZ));
			} else {
				c.set(f.x(), f.top() - 1, f.z(), stairs(corbelStairs(bay, f.top()), f.toWall()).setValue(StairBlock.HALF, Half.TOP));
				c.set(f.x(), f.top(), f.z(), capBlock(bay, f.top()));
			}
		}
	}

	/** The coping of the gallery's outer rim: calcite on its outermost row. */
	static BlockState rim(double r) {
		return r > CitadelCanvas.dims().galleryR - 1.0 ? CALCITE : FOOT;
	}
}
