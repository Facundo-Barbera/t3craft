package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelHub.arcadeZ;

/**
 * The roofs of the basilica. The nave has a pointed vault inside (a stepped arch with blackstone ribs
 * every 4 z) and a pitched cherry roof outside: one stair per column up the slope, a flat three-wide
 * ridge with a dark crest, an eave that overhangs the clerestory, and a lantern drum over the crossing
 * (calcite ring, stained glass, a stepped cherry cap with a spire) from which the beacon hangs on a
 * chain. The aisles carry a roof terrace of cherry and bamboo slabs with planters and lantern posts,
 * behind the parapet of {@link HubFacade}.
 */
public final class HubRoof {
	private HubRoof() {
	}

	/** Direction from a column towards the nave's axis. */
	private static Direction towardAxis(int x) {
		return x > 0 ? Direction.WEST : Direction.EAST;
	}

	/** One column of the nave's roof ({@code |x| < naveHalf}, 6 at scale 1): shell, ribs, crest; the lantern's ring and opening near the centre. */
	public static void vault(CitadelCanvas c, int x, int z, int ax) {
		int top = CitadelHub.vaultY(ax);
		int hz = Math.abs(z);
		int d = HubDims.DRUM_HALF();
		if (ax < d && hz < d) {
			return; // the opening into the lantern
		}
		if (Math.max(ax, hz) == d) {
			drumWall(c, x, z, ax, hz, top, HubDims.DRUM_TOP() - CitadelHub.hy(4), HubDims.DRUM_TOP());
			return;
		}
		c.set(x, top, z, shell(x, ax));
		if (Math.floorMod(z, HubDims.PIER_PERIOD()) == 0) {
			c.set(x, top - 1, z, RIB);
		}
		if (ax == 0) {
			c.set(x, top + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/** The roof's material by height: dark eave course, cherry body. */
	private static net.minecraft.world.level.block.state.BlockState shell(int x, int ax) {
		Direction in = towardAxis(x);
		if (ax >= HubDims.NAVE_HALF() - 1) {
			return HubBlocks.darkStairs(in, false);
		}
		if (ax >= CitadelCanvas.dims().s(2)) {
			return HubBlocks.roofStairs(in);
		}
		return PLANKS;
	}

	/**
	 * A cell of a drum's ring: calcite corners, base and cornice, clear glass between (rows above {@code glassFrom}) split by
	 * a stripped cherry mullion every third cell. (struct/hub3: it was pink on two sides and light blue on the other two, which read as a
	 * grey-blue patch from outside and did not match the rest of the roof; clear glass lets the light in and shows the
	 * frame, the palette of the roof.)
	 */
	private static void drumWall(CitadelCanvas c, int x, int z, int ax, int hz, int bottom, int glassFrom, int topY) {
		int half = Math.max(ax, hz);
		boolean corner = ax == half && hz == half;
		int along = ax == half ? z : x;
		boolean mullion = Math.floorMod(along, 3) == 0;
		for (int y = bottom; y <= topY; y++) {
			boolean solid = corner || y <= glassFrom || y == topY;
			c.set(x, y, z, solid ? CALCITE : mullion ? HubBlocks.strippedLog(Direction.Axis.Y) : Blocks.GLASS.defaultBlockState());
		}
	}

	/**
	 * The two-body lantern: the lower drum's cap is a ring of eaves with a smaller drum on top, then a
	 * stepped cherry pyramid and a spire; the beacon hangs on a chain from the lower ceiling.
	 */
	public static void cupola(CitadelCanvas c) {
		int d = HubDims.DRUM_HALF();
		int eaveY = HubDims.DRUM_TOP() + 1;
		for (int hx = -(d + 1); hx <= d + 1; hx++) {
			for (int hz = -(d + 1); hz <= d + 1; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				c.set(hx, eaveY, hz, m == d + 1 ? HubBlocks.roofStairs(inward(hx, hz)) : PLANKS);
			}
		}
		int up = d - 1;
		for (int hx = -up; hx <= up; hx++) {
			for (int hz = -up; hz <= up; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				if (m == up) {
					drumWall(c, hx, hz, Math.abs(hx), Math.abs(hz), eaveY + 1, eaveY + 1, eaveY + 4);
				}
			}
		}
		int capY = eaveY + 5;
		for (int hx = -d; hx <= d; hx++) {
			for (int hz = -d; hz <= d; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				for (int k = 0; k < d; k++) {
					int ring = d - k;
					if (m == ring) {
						c.set(hx, capY + k, hz, HubBlocks.roofStairs(inward(hx, hz)));
					} else if (m < ring) {
						c.set(hx, capY + k, hz, PLANKS);
					}
				}
			}
		}
		c.set(0, capY + d, 0, HubBlocks.stoneWall());
		c.set(0, capY + d + 1, 0, HubBlocks.stoneWall());
		c.set(0, capY + d + 2, 0, HubBlocks.rod());
		int beacon = CitadelHub.vaultY(0) + 1;
		for (int cy = beacon + 1; cy < eaveY; cy++) {
			c.set(0, cy, 0, HubBlocks.chain());
		}
	}

	private static Direction inward(int hx, int hz) {
		if (Math.abs(hx) >= Math.abs(hz)) {
			return hx > 0 ? Direction.WEST : Direction.EAST;
		}
		return hz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	/** The eave: a stair overhanging the aisle beyond the clerestory's top, in line with the nave's slope. */
	public static void eave(CitadelCanvas c, int x, int z) {
		if (Math.abs(z) <= HubDims.TRANSEPT_HALF()) {
			return;
		}
		int sx = x > 0 ? 1 : -1;
		c.set(x + sx, CitadelHub.spring(), z, HubBlocks.darkStairs(towardAxis(x), false));
	}

	/**
	 * One column of an aisle's roof: within {@link HubDims#TRANSEPT_HALF} of the axis the cross gable of the
	 * transept (stairs up to a ridge along x), elsewhere a terrace of stone-brick slabs with bamboo stripes
	 * on the pier lines, planters and lantern posts.
	 */
	public static void terrace(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		int y = HubDims.TERRACE_Y();
		int hz = Math.abs(z);
		int t = HubDims.TRANSEPT_HALF();
		if (hz <= t) {
			if (hz == 0) {
				c.set(x, y + t, z, HubBlocks.stoneBrick(x, y, z));
				c.set(x, y + t + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
			} else {
				c.set(x, y + t - hz, z, hz == t ? HubBlocks.darkStairs(z > 0 ? Direction.NORTH : Direction.SOUTH, false) : HubBlocks.roofStairs(z > 0 ? Direction.NORTH : Direction.SOUTH));
			}
			return;
		}
		c.set(x, y, z, arcadeZ(z) ? slab(Blocks.CHERRY_SLAB) : HubBlocks.stoneSlab(false));
		if (ax == HubDims.TERRACE_X0() + 1 && arcadeZ(z)) {
			c.set(x, y + 1, z, HubBlocks.bush());
		}
		if (ax == HubDims.TERRACE_X0() + 2 && arcadeZ(z) && Math.floorMod(z + HubDims.PIER_PHASE(), 2 * HubDims.PIER_PERIOD()) == 0) {
			c.set(x, y + 1, z, HubBlocks.stoneWall());
			c.set(x, y + 2, z, Blocks.LANTERN.defaultBlockState());
		}
	}
}
