package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

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
 * everything else is a shared block of the canvas ({@code FOOT} deepslate tiles, {@code BAMBOO}
 * mosaic, {@code GRASS}, {@code MOSS}, {@code WATER}, {@code LOG}, {@code LIGHT} froglight) or a
 * vanilla leaf. {@code LIGHT} appears wherever {@link CitadelCanvas#lattice} says so, so that the
 * street lights are built into the floor. No piece here produces anchors or fixtures; the construction
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
 *     the patio and the bridges' ground: paved ring ({@code r > 17.5}, accent or light), pool with a
 *     {@code FOOT} bed and {@code WATER} ({@code r > 15.5}, away from the eight radial paths), the
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
 *     the head of a patio stair, where there is no railing): the elevated ring at {@code Y0}: a
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

	// ------------------------------------------------------------------ ground

	/** The half-block height of a ramp column at radius {@code r}: from 0 (outside) up to twice the podium. */
	private static int rampHalves(double r) {
		return (int) Math.max(0, Math.min(2 * Y0, Math.round(RingCampusGeometry.RAMP_R - r)));
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
			case INNER_STREET, STREET -> street(c, x, z, r);
			case AVENUE -> avenue(c, x, z, zn);
			case LOT -> lot(c, x, z);
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

	/** A ramp column: bamboo mosaic stepped in half blocks down from the podium. */
	private static void ramp(CitadelCanvas c, int x, int z, double r) {
		stepped(c, x, z, rampHalves(r), BAMBOO, slab(Blocks.BAMBOO_MOSAIC_SLAB));
	}

	/** A terrace column: a stone step with grass (one in eight moss), a bush every so often and the vegetation of {@link Gardens#terrace}. */
	private static void terrace(CitadelCanvas c, int x, int z, double r) {
		int h = rampHalves(r);
		stepped(c, x, z, h - h % 2, FOOT, FOOT);
		int top = h / 2;
		c.set(x, top, z, (x * 7 + z * 13 & 7) == 0 ? MOSS : GRASS);
		if (Math.floorMod(x * 31 + z * 17, dev.t3craft.hq.pack.LayoutParams.RINGCAMPUS_TERRACE_BUSH.get()) == 0 && top + 1 < 6) {
			c.set(x, top + 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		} else {
			Gardens.terrace(c, x, z, top, h);
		}
	}

	/** A street column (inner street and ring streets): light, or bands of {@code FOOT} and accent every third radius. */
	private static void street(CitadelCanvas c, int x, int z, double r) {
		c.set(x, 0, z, lattice(x, z) ? LIGHT : ((int) Math.floor(r) % 3 == 0 ? FOOT : c.accent()));
	}

	/** An avenue column: light, {@code FOOT} away from the avenue's axis, accent along it. */
	private static void avenue(CitadelCanvas c, int x, int z, Zone zn) {
		c.set(x, 0, z, lattice(x, z) ? LIGHT
				: RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(zn.ring())) > 2.4 ? FOOT : c.accent());
	}

	/** A lot column: light, or grass (the barrio then paves its own court over it). */
	private static void lot(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, lattice(x, z) ? LIGHT : GRASS);
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
		if (r > 17.5) {
			c.set(x, 0, z, lattice(x, z) ? LIGHT : c.accent());
		} else if (r > 15.5 && RingCampusGeometry.radialDistance(x, z, 8) > 1.2) {
			c.set(x, -1, z, FOOT);
			c.set(x, 0, z, WATER);
		} else if (r > 15.5) {
			c.set(x, 0, z, FOOT);
		} else {
			c.set(x, 0, z, lattice(x, z) ? LIGHT : (x + z & 3) == 0 ? MOSS : GRASS);
		}
	}

	// ------------------------------------------------------------------ core: bridges, promenade, patio

	/** A column of a bridge: deck, railings and arches. */
	public static void bridge(CitadelCanvas c, int x, int z) {
		if (c.at(x, Y0, z) != null) {
			return; // patio stairs
		}
		bridgeDeck(c, x, z);
		bridgeRail(c, x, z);
		bridgeArches(c, x, z);
	}

	/** The deck: accent (or light) in the middle five columns, {@code FOOT} on the edges. */
	private static void bridgeDeck(CitadelCanvas c, int x, int z) {
		c.set(x, Y0, z, Math.abs(x) <= 2 ? (lattice(x, z) ? LIGHT : c.accent()) : FOOT);
	}

	/** The railing on both edges of the deck. */
	private static void bridgeRail(CitadelCanvas c, int x, int z) {
		if (Math.abs(x) == 3) {
			c.set(x, Y0 + 1, z, c.kit.railing(x > 0 ? Direction.EAST : Direction.WEST));
		}
	}

	/** Arches under the deck: a lintel course and piers. */
	private static void bridgeArches(CitadelCanvas c, int x, int z) {
		c.set(x, Y0 - 1, z, Math.abs(z) % 4 == 1 || Math.abs(x) == 3 ? FOOT : AIR);
		if (Math.abs(z) % 4 == 1 && Math.abs(x) == 3) {
			c.fill(x, 1, Y0 - 2, z, FOOT);
		}
	}

	/**
	 * The elevated ring: a deck at the podium level over a solid plinth, a railing on the patio side,
	 * light posts (log and a light block) round the outer edge between the ramps.
	 */
	public static void promenade(CitadelCanvas c, int x, int z, double r, boolean stairHead) {
		if (c.at(x, Y0, z) != null) {
			return; // the top step of a patio stair
		}
		promenadePlinth(c, x, z, r);
		promenadeDeck(c, x, z, r);
		boolean inner = r <= RingCampusGeometry.PATIO_R + 1.2;
		promenadeRail(c, x, z, inner, stairHead);
		promenadePost(c, x, z, r);
	}

	/** The solid plinth under the deck, with a wall panel every so often on the inner edge. */
	private static void promenadePlinth(CitadelCanvas c, int x, int z, double r) {
		c.fill(x, 1, Y0 - 1, z, r <= RingCampusGeometry.PATIO_R + 1.2 && Math.floorMod(x * 3 + z * 7, 9) == 0 ? c.kit.wallPanel(true) : FOOT);
	}

	/** The deck: light, {@code FOOT} on the outer rim, the kit's floor elsewhere. */
	private static void promenadeDeck(CitadelCanvas c, int x, int z, double r) {
		c.set(x, Y0, z, lattice(x, z) ? LIGHT : (r > RingCampusGeometry.GALLERY_R - 1 ? FOOT : c.floor()));
	}

	/** The railing on the patio side (none on the heads of the patio stairs nor near the axis). */
	private static void promenadeRail(CitadelCanvas c, int x, int z, boolean inner, boolean stairHead) {
		if (inner && Math.abs(x) > 3 && !stairHead) {
			Direction edge = RingCampusGeometry.cardinal(RingCampusGeometry.theta(x, z)).getOpposite();
			c.set(x, Y0 + 1, z, c.kit.railing(edge));
		}
	}

	/** The light posts ({@code LOG} twice, then {@code LIGHT}) round the outer edge, between the ramps. */
	private static void promenadePost(CitadelCanvas c, int x, int z, double r) {
		if (r > RingCampusGeometry.GALLERY_R - 1 && RingCampusGeometry.radialDistance(x, z, 16) < 0.5
				&& RingCampusGeometry.radialDistance(x, z, 8) > 5) {
			c.fill(x, Y0 + 1, Y0 + 2, z, LOG);
			c.set(x, Y0 + 3, z, LIGHT);
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
	 */
	public static void emptyLot(CitadelCanvas c, int lot) {
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
