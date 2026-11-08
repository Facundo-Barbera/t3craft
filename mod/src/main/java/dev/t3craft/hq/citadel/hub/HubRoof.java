package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
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

	/** One column of the nave's roof ({@code |x| < 6}): shell, ribs, crest; the lantern's ring and opening near the centre. */
	public static void vault(CitadelCanvas c, int x, int z, int ax) {
		int top = CitadelHub.vaultY(ax);
		int hz = Math.abs(z);
		int d = HubDims.DRUM_HALF;
		if (ax < d && hz < d) {
			return; // the opening into the lantern
		}
		if (Math.max(ax, hz) == d) {
			drumWall(c, x, z, ax, hz, top);
			return;
		}
		c.set(x, top, z, ax <= 1 ? PLANKS : HubBlocks.roofStairs(towardAxis(x)));
		if (Math.floorMod(z, HubDims.PIER_PERIOD) == 0) {
			c.set(x, top - 1, z, RIB);
		}
		if (ax == 0) {
			c.set(x, top + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/** A cell of the lantern's ring: calcite corners and base, stained glass, calcite cornice. */
	private static void drumWall(CitadelCanvas c, int x, int z, int ax, int hz, int top) {
		boolean corner = ax == HubDims.DRUM_HALF && hz == HubDims.DRUM_HALF;
		DyeColor tint = ax == HubDims.DRUM_HALF ? (x > 0 ? DyeColor.PINK : DyeColor.LIGHT_BLUE) : (z > 0 ? DyeColor.MAGENTA : DyeColor.YELLOW);
		for (int y = top; y <= HubDims.DRUM_TOP; y++) {
			boolean solid = corner || y <= HubDims.DRUM_TOP - 4 || y == HubDims.DRUM_TOP;
			c.set(x, y, z, solid ? CALCITE : HubBlocks.glass(tint));
		}
	}

	/** The cap of the lantern (a stepped cherry pyramid with eaves and a spire) and the chain the beacon hangs from. */
	public static void cupola(CitadelCanvas c) {
		int y = HubDims.DRUM_TOP + 1;
		for (int hx = -3; hx <= 3; hx++) {
			for (int hz = -3; hz <= 3; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				for (int k = 0; k < 3; k++) {
					int ring = 3 - k;
					if (m == ring) {
						c.set(hx, y + k, hz, HubBlocks.roofStairs(inward(hx, hz)));
					} else if (m < ring) {
						c.set(hx, y + k, hz, PLANKS);
					}
				}
			}
		}
		c.set(0, y + 3, 0, HubBlocks.stoneWall());
		c.set(0, y + 4, 0, HubBlocks.stoneWall());
		c.set(0, y + 5, 0, HubBlocks.rod());
		int beacon = CitadelHub.vaultY(0) + 1;
		for (int cy = beacon + 1; cy < y; cy++) {
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
		int sx = x > 0 ? 1 : -1;
		c.set(x + sx, CitadelHub.SPRING, z, HubBlocks.roofStairs(towardAxis(x)));
	}

	/** One column of an aisle's roof terrace: cherry slabs, bamboo along the pier lines, planters and lantern posts. */
	public static void terrace(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		int y = HubDims.TERRACE_Y;
		c.set(x, y, z, arcadeZ(z) ? slab(Blocks.BAMBOO_MOSAIC_SLAB) : slab(Blocks.CHERRY_SLAB));
		if (ax == HubDims.TERRACE_X1 - 1 && arcadeZ(z)) {
			c.set(x, y + 1, z, HubBlocks.bush());
		}
		if (ax == HubDims.TERRACE_X0 + 2 && arcadeZ(z) && Math.floorMod(z + 2, 8) == 0) {
			c.set(x, y + 1, z, HubBlocks.stoneWall());
			c.set(x, y + 2, z, Blocks.LANTERN.defaultBlockState());
		}
	}
}
