package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.nature.Gardens;
import dev.t3craft.hq.citadel.nature.Plots;
import dev.t3craft.hq.citadel.nature.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground of the citadel: the ring-by-ring base layer (street, avenue, lot, node, terrace, ramp,
 * lawn, patio), the core's structures over it (bridges, elevated promenade, patio planting) and the
 * lone trees and empty lots. Every method is static and takes the {@link CitadelCanvas} first; none
 * keeps state. Colours come from the kit through {@code c.floor()} (floor), {@code c.accent()} (floor
 * accent), {@code c.kit.railing(..)}, {@code c.kit.wallPanel(..)} and {@code c.kit.gardenPlanter()};
 * everything else is a shared block of the canvas ({@code FOOT} deepslate tiles, {@code GRASS},
 * {@code MOSS}, {@code WATER}, {@code LOG}) or a vanilla leaf. The paving itself (which block goes on
 * which road cell: avenues, streets, crossings, ramp, patio ring, bridge and promenade decks) is
 * {@link CitadelPaving}'s; no floor cell is a light any more (the light is {@code citadel/1-light}'s). No piece here produces anchors or fixtures; the construction
 * group of a ground cell comes from its zone ({@code CitadelFinish.groupOf}): {@code cloister} for
 * bridge, patio, gallery, ramp, terrace and inner street, {@code ring_<m>} for avenues and streets,
 * {@code lot_<id>} for a barrio's lot, {@code node_<k>} for nodes and {@code grounds} for the lawn.
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li>{@link #ground(CitadelCanvas, int, int, Zone)}: the base layer of one column, chosen by its zone
 *     kind. Run once per zone column, in the order of the zone map, before any other piece. Writes the
 *     cell at {@code y = 0} (plus the fill below and the bushes above). Its named variants:
 *     {@link #footing}, {@link #ramp}, {@link #terrace}, {@link #street}, {@link #avenue},
 *     {@link #lot}, {@link #nodeFloor}, {@link #lawn} and {@link #patioGround}.</li>
 * <li>{@link #patioGround(CitadelCanvas, int, int, double)} ({@code r} is the radius of the column):
 *     the patio and the bridges' ground: paved ring ({@code r > PATIO_RING_IN}, {@link CitadelPaving#patioRing}), pool with a
 *     {@code FOOT} bed and {@code WATER} ({@code r > POOL_IN}, away from the eight radial paths), the
 *     paths' {@code FOOT}, and the inner lawn ({@code GRASS}, one in four {@code MOSS}).</li>
 * <li>{@link #stepped(CitadelCanvas, int, int, int, BlockState, BlockState)}: a column of {@code halves}
 *     half blocks: a full block fill up to {@code halves / 2} and, if odd, the half block over it.
 *     Used by the ramp ({@code BAMBOO} and bamboo slab) and the terrace ({@code FOOT}).</li>
 * <li>{@link #bridge(CitadelCanvas, int, int)}: the 7-wide deck of a bridge over the pool at the
 *     podium's height: accent or light in the middle five columns, {@code FOOT} at the edges, railings
 *     on {@code |x| = 3}, an arched underside (lintel course and piers every 4 cells). Skips the cells
 *     the patio stairs already took. Variants: {@link #bridgeDeck}, {@link #bridgeRail},
 *     {@link #bridgeArches}.</li>
 * <li>{@link #promenade(CitadelCanvas, int, int, double, boolean)} ({@code stairHead}: the column is
 *     the head of a patio stair, where there is no railing): the elevated ring at {@code y0()}: a
 *     {@code FOOT} plinth (with a wall panel every so often on the inner edge), the deck, a railing
 *     facing the patio, and the light posts ({@code LOG} and {@code LIGHT}) round the outer edge,
 *     between the ramps. Skips the top steps of the patio stairs. Variants: {@link #promenadePlinth},
 *     {@link #promenadeDeck}, {@link #promenadeRail}, {@link #promenadePost}.</li>
 * <li>{@link #patio(CitadelCanvas, int, int, double)}: the planting of the patio over its ground: eight
 *     cherry trees on a ring of radius 19.3 ({@link #patioCherries}) and the flower macizos of the lawn,
 *     azaleas, planters and the lily pads of the pool ({@link #patioBeds}); the living part is
 *     {@link Gardens}. Skips the stairs.</li>
 * <li>{@link #tree(CitadelCanvas, int, int)}: a tree on a ground cell, one of six species with roots
 *     ({@link Trees}). Also used by the barrios (courtyard centre and empty hall slots).</li>
 * <li>{@link #emptyLot(CitadelCanvas, int)}: a lot with no project: a plot waiting for its barrio in
 *     a meadow ({@link Plots}). The terrace's vegetation is {@link Gardens#terrace}.</li>
 * </ul>
 *
 * <p>Nothing here may change a value or the order of the calls: the plan must stay identical block by
 * block.
 */
public final class CitadelGrounds {
	private CitadelGrounds() {
	}

	/** Radius where the patio's paved ring starts (the pool ends): 5/6 of the patio, 17.5 at the unscaled size. */
	static double PATIO_RING_IN() {
		return CitadelCanvas.dims().patioR * (17.5 / 21.0);
	}

	/** Radius where the patio's pool starts (inner lawn inside): 15.5 unscaled. */
	static double POOL_IN() {
		return CitadelCanvas.dims().patioR * (15.5 / 21.0);
	}

	/** Radius of the ring of the patio's cherry trees (19.3 unscaled). */
	static double PATIO_TREE_R() {
		return CitadelCanvas.dims().patioR * (19.3 / 21.0);
	}

	/** Radius of the grass pit round a patio tree (1.5 unscaled). */
	static double PIT_R() {
		return CitadelCanvas.dims().sd(1.5);
	}

	// ------------------------------------------------------------------ ground

	/** The half-block height of a ramp column at radius {@code r}: from 0 (outside) up to twice the podium. */
	private static int rampHalves(double r) {
		return (int) Math.max(0, Math.min(2 * y0(), Math.round(CitadelCanvas.dims().rampR - r)));
	}

	/** A column of {@code halves} half blocks: {@code full} up to {@code halves / 2}, then {@code half} if odd. */
	public static void stepped(CitadelCanvas c, int x, int z, int halves, BlockState full, BlockState half) {
		int solidTop = halves / 2;
		c.fill(x, 0, solidTop, z, full);
		if (halves % 2 == 1) {
			c.set(x, solidTop + 1, z, half);
		}
	}

	/** The base layer of the column {@code (x, z)} of zone {@code zn}. */
	public static void ground(CitadelCanvas c, int x, int z, Zone zn) {
		double r = RingCampusGeometry.r(x, z);
		switch (zn.kind()) {
			case HUB, HUB_WALL, GALLERY -> footing(c, x, z);
			case BRIDGE, PATIO -> patioGround(c, x, z, r);
			case RAMP -> ramp(c, x, z, r);
			case TERRACE -> terrace(c, x, z, r);
			case INNER_STREET, STREET -> street(c, x, z, r, zn);
			case AVENUE -> avenue(c, x, z, zn);
			case LOT -> lot(c, x, z, zn);
			case NODE -> nodeFloor(c, x, z);
			case LAWN -> lawn(c, x, z);
			default -> {
			}
		}
	}

	/** Under the hub, its wall and the gallery: bare {@code FOOT}. */
	private static void footing(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, FOOT);
	}

	/** A ramp column: half-block steps down from the podium, with a bamboo runner and kerbs ({@link CitadelPaving#rampBand}). */
	private static void ramp(CitadelCanvas c, int x, int z, double r) {
		if (CitadelPodium.ramp(c, x, z, r)) {
			return; // design/citadel-terraces2: the stair of the podium (two flights and a landing)
		}
		int band = CitadelPaving.rampBand(x, z);
		stepped(c, x, z, rampHalves(r), CitadelPaving.rampFull(band), CitadelPaving.rampHalf(band));
	}

	/** A terrace column: a stone step with grass (one in eight moss), a bush every so often and the vegetation of {@link Gardens#terrace}. */
	private static void terrace(CitadelCanvas c, int x, int z, double r) {
		if (CitadelPodium.terrace(c, x, z, r)) {
			return; // design/citadel-terraces2: the two tiers of the podium
		}
		int h = rampHalves(r);
		stepped(c, x, z, h - h % 2, FOOT, FOOT);
		int top = h / 2;
		c.set(x, top, z, (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS);
		if (Math.floorMod(x * 31 + z * 17, dev.t3craft.hq.pack.LayoutParams.RINGCAMPUS_TERRACE_BUSH.get()) == 0 && top + 1 < y0() + 2) {
			c.set(x, top + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		} else {
			Gardens.terrace(c, x, z, top, h);
		}
	}

	/** A street column (inner street and ring streets): paving ({@link CitadelPaving#street}) and furniture ({@link CitadelStreetscape#street}). */
	private static void street(CitadelCanvas c, int x, int z, double r, Zone zn) {
		c.set(x, 0, z, CitadelPaving.street(c, x, z, r, zn));
		CitadelStreetscape.street(c, x, z, r, zn);
	}

	/** An avenue column: paving ({@link CitadelPaving#avenue}), boulevard and gate ({@link CitadelStreetscape#avenue}). */
	private static void avenue(CitadelCanvas c, int x, int z, Zone zn) {
		c.set(x, 0, z, CitadelPaving.avenue(c, x, z, zn));
		CitadelStreetscape.avenue(c, x, z, zn);
	}

	/** A lot column: grass with a mossy verge against the roads (the barrio then paves its own court over it). */
	private static void lot(CitadelCanvas c, int x, int z, Zone zn) {
		c.set(x, 0, z, CitadelPaving.lotGround(c, x, z, zn));
		BlockState cover = CitadelPaving.vergeCover(c, x, z, zn);
		if (cover != null) {
			c.set(x, 1, z, cover);
		}
	}

	/** A node column: the kit's floor (the node's piece paints its room over it). */
	private static void nodeFloor(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, c.floor());
	}

	/** The lawn outside the citadel: grass. */
	private static void lawn(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, GRASS);
	}

	/** The ground of a patio or bridge column at radius {@code r}: paving, pool, paths and lawn. */
	public static void patioGround(CitadelCanvas c, int x, int z, double r) {
		double ringIn = PATIO_RING_IN();
		double poolIn = POOL_IN();
		if (r > ringIn) {
			c.set(x, 0, z, treePit(x, z) ? GRASS : CitadelPaving.patioRing(c, x, z, r, ringIn));
		} else if (r > poolIn && RingCampusGeometry.radialDistance(x, z, 8) > CitadelPaving.CAUSEWAY_D()) {
			c.set(x, -1, z, FOOT);
			c.set(x, 0, z, WATER);
		} else if (r > poolIn) {
			c.set(x, 0, z, CitadelPaving.causeway(x, z));
		} else {
			c.set(x, 0, z, (x + z & 3) == 0 ? MOSS : GRASS);
		}
	}

	/** A cell of the planting pit round one of the patio's eight cherry trees (see {@link #patioCherries}): grass in the paving. */
	private static boolean treePit(int x, int z) {
		double tr = PATIO_TREE_R();
		double pit = PIT_R();
		for (int k = 0; k < 8; k++) {
			BlockPos t = RingCampusGeometry.polar(tr, (k + 0.5) * Math.PI / 4, 0);
			if (Math.hypot(x - t.getX(), z - t.getZ()) <= pit) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ core: bridges, promenade, patio

	/** A column of a bridge: deck, railings and arches. */
	public static void bridge(CitadelCanvas c, int x, int z) {
		if (c.at(x, y0(), z) != null) {
			return; // patio stairs
		}
		bridgeDeck(c, x, z);
		bridgeRail(c, x, z);
		bridgeArches(c, x, z);
	}

	/** The deck: a runner, tile and joints in the middle five columns, {@code FOOT} on the edges. */
	private static void bridgeDeck(CitadelCanvas c, int x, int z) {
		c.set(x, y0(), z, CitadelPaving.bridgeDeck(c, x, z));
	}

	/** The railing on both edges of the deck. */
	private static void bridgeRail(CitadelCanvas c, int x, int z) {
		if (Math.abs(x) == c.g.bridgeHalf) {
			c.set(x, y0() + 1, z, c.kit.railing(x > 0 ? Direction.EAST : Direction.WEST));
		}
	}

	/** Arches under the deck: a lintel course and piers. */
	private static void bridgeArches(CitadelCanvas c, int x, int z) {
		boolean pier = Math.abs(z) % c.s(4) == 1;
		boolean edge = Math.abs(x) == c.g.bridgeHalf;
		c.set(x, y0() - 1, z, pier || edge ? FOOT : AIR);
		if (pier && edge) {
			c.fill(x, 1, y0() - 2, z, FOOT);
		}
	}

	/**
	 * The elevated ring: a deck at the podium level over a solid plinth, a railing on the patio side,
	 * light posts (log and a light block) round the outer edge between the ramps.
	 */
	public static void promenade(CitadelCanvas c, int x, int z, double r, boolean stairHead) {
		if (c.at(x, y0(), z) != null) {
			return; // the top step of a patio stair
		}
		promenadePlinth(c, x, z, r);
		promenadeDeck(c, x, z, r);
		boolean inner = r <= CitadelPaving.promenadeInner();
		promenadeRail(c, x, z, inner, stairHead);
		promenadePost(c, x, z, r);
	}

	/** The solid plinth under the deck, with a wall panel every so often on the inner edge. */
	private static void promenadePlinth(CitadelCanvas c, int x, int z, double r) {
		boolean inner = r <= CitadelPaving.promenadeInner();
		boolean panel = inner && Math.floorMod(x * 3 + z * 7, 9) == 0;
		c.fill(x, 1, y0() - 1, z, panel ? c.kit.wallPanel(true) : FOOT);
		// design/citadel-terraces2: the hub's own pedestal, on both faces of the gallery: dark plinth, a band of
		// chiseled deepslate two courses under the deck and (towards the patio) a light quartz course under it
		boolean outer = r > CitadelCanvas.dims().galleryR - 1.5;
		if (!panel && y0() >= 4 && outer && CitadelPodium.profile() != null) {
			// design/citadel-terraces3: the outer face is wall B of the podium: the same graded face as walls A (dark footing, mixed
			// middle, a lighter course under the calcite rim) instead of a black band under a white stripe
			for (int y = Math.max(1, y0() - 3); y < y0(); y++) {
				c.set(x, y, z, CitadelPodium.plinthFace(x, y, z, y0() - y));
			}
		} else if (!panel && y0() >= 4 && (inner || outer)) {
			c.set(x, y0() - 2, z, CitadelCanvas.b(Blocks.CHISELED_DEEPSLATE));
			if (inner) {
				c.set(x, y0() - 1, z, CitadelCanvas.b(Blocks.SMOOTH_QUARTZ));
			}
		}
	}

	/** The deck: rim, tile stripes and the kit's floor with joints ({@link CitadelPaving#promenade}). */
	private static void promenadeDeck(CitadelCanvas c, int x, int z, double r) {
		c.set(x, y0(), z, CitadelPaving.promenade(c, x, z, r));
	}

	/** The railing on the patio side (none on the heads of the patio stairs nor near the axis). */
	private static void promenadeRail(CitadelCanvas c, int x, int z, boolean inner, boolean stairHead) {
		if (inner && Math.abs(x) > c.g.bridgeHalf && !stairHead) {
			Direction edge = RingCampusGeometry.cardinal(RingCampusGeometry.theta(x, z)).getOpposite();
			c.set(x, y0() + 1, z, c.kit.railing(edge));
		}
	}

	/** The light posts ({@code LOG} twice, then {@code LIGHT}) round the outer edge, between the ramps. */
	private static void promenadePost(CitadelCanvas c, int x, int z, double r) {
		if (r > CitadelPaving.promenadeRim() && RingCampusGeometry.radialDistance(x, z, 16) < 0.5
				&& RingCampusGeometry.radialDistance(x, z, 8) > c.sd(5)) {
			c.fill(x, y0() + 1, y0() + 2, z, LOG);
			c.set(x, y0() + 3, z, LIGHT);
		}
	}

	/** The planting of a patio column: the cherry trees, then the flowerbeds. */
	public static void patio(CitadelCanvas c, int x, int z, double r) {
		if (c.at(x, 1, z) != null) {
			return; // stairs
		}
		patioCherries(c, x, z);
		patioBeds(c, x, z, r);
	}

	/** Eight cherry trees on a ring of radius 19.3 (round and slender, with roots and petals): see {@link Gardens#patioCherries}. */
	private static void patioCherries(CitadelCanvas c, int x, int z) {
		Gardens.patioCherries(c, x, z);
	}

	/** Flower macizos on the inner lawn, planters and azaleas, lily pads on the pool: see {@link Gardens#patioBeds}. */
	private static void patioBeds(CitadelCanvas c, int x, int z, double r) {
		Gardens.patioBeds(c, x, z, r);
	}

	// ------------------------------------------------------------------ empty lots and trees

	/**
	 * A lot with no project: a plot waiting for its barrio (vegetable garden, formal garden, building
	 * site, pond meadow or cherry grove, by lot id) in a meadow with a few lone trees; see {@link Plots}.
	 * With the catalog placement on ({@code catalog.enabled}, {@link CitadelPlacement#enabled}) it paints
	 * nothing: {@link CitadelPlacement#city} composes the lot from the catalog later, once the streets are placed.
	 */
	public static void emptyLot(CitadelCanvas c, int lot) {
		if (CitadelPlacement.enabled()) {
			return;
		}
		Plots.lot(c, lot);
	}

	/**
	 * A tree on a ground cell: one of six species (three cherries, an azalea tree, a birch, a small
	 * oak) picked by the cell, with roots and a crown without floating leaves; see {@link Trees}.
	 */
	public static void tree(CitadelCanvas c, int x, int z) {
		Trees.tree(c, x, z);
	}
}
