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
		boolean rib = Math.floorMod(z, HubDims.PIER_PERIOD()) == 0;
		c.set(x, top, z, shell(x, top, z, ax, rib));
		if (rib) {
			c.set(x, top - 1, z, RIB);
		}
		if (ax == 0) {
			c.set(x, top + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
	}

	/**
	 * The roof's material by height: dark eave course, cherry body. (The skirt of struct/hub4 is the stepped eave, see {@link #eave}.)
	 * From scale 2 (struct/hub5) the columns of the ribs are one thin hip of light stone brick, up the slope and over the ridge: a
	 * pale line that breaks the pink field every bay (the dark oak ribs of hub4 read as a heavy grill).
	 */
	private static net.minecraft.world.level.block.state.BlockState shell(int x, int y, int z, int ax, boolean rib) {
		Direction in = towardAxis(x);
		boolean hip = rib && HubDims.TALL();
		if (ax >= HubDims.NAVE_HALF() - 1) {
			return HubBlocks.darkStairs(in, false);
		}
		if (ax >= CitadelCanvas.dims().s(2)) {
			return hip ? HubBlocks.stoneStairs(in, false) : HubBlocks.roofStairs(in);
		}
		return hip ? HubBlocks.stoneBrick(x, y, z) : PLANKS;
	}

	/**
	 * Gabled dormers (struct/hub5), from scale 2: on each slope of the nave, at the bays between the hips nearest the ends
	 * (z = +-12 and +-20 at 200), a window with a pediment. Five wide: a front of light stone brick jambs, two rows of stained
	 * panes, a chiselled lintel and a calcite tympanum, under a deepslate gable (stairs and a slab ridge) that runs back into the
	 * slope until it meets it. The box is solid above the lintel so no stair of the gable hangs from another; the window rows have
	 * only a shallow cavity (the slope closes it behind).
	 */
	private static void dormers(CitadelCanvas c) {
		if (!HubDims.TALL()) {
			return;
		}
		int front = HubDims.NAVE_HALF() - 3;
		int[] zs = {HubDims.PIER_PERIOD() * 3 / 2, HubDims.PIER_PERIOD() * 5 / 2};
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int z0 : zs) {
					dormer(c, sx, sz * z0, front);
				}
			}
		}
	}

	private static void dormer(CitadelCanvas c, int sx, int zc, int front) {
		int base = CitadelHub.vaultY(front);
		int tint = Math.floorMod(Math.floorDiv(zc, HubDims.PIER_PERIOD()) + (sx > 0 ? 1 : 0), HubBlocks.GLASS_COLORS.length);
		for (int ax = front; ax >= 0; ax--) {
			int surf = CitadelHub.vaultY(ax);
			int x = sx * ax;
			boolean face = ax == front;
			for (int dz = -2; dz <= 2; dz++) {
				int z = zc + dz;
				int a = Math.abs(dz);
				for (int y = Math.max(surf + 1, base + 1); y <= base + 6; y++) {
					int r = y - base;
					net.minecraft.world.level.block.state.BlockState s = null;
					if (a == 2) {
						// cheeks and jambs up to the lintel, then the low stair of the gable
						s = r <= 3 ? HubBlocks.stoneBrick(x, y, z) : r == 4 ? HubBlocks.stoneStairs(dz < 0 ? Direction.SOUTH : Direction.NORTH, false) : null;
					} else if (a == 1) {
						if (r <= 2) {
							s = face ? HubBlocks.pane(HubBlocks.GLASS_COLORS[tint]) : null;
						} else if (r == 3) {
							s = face ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : PLANKS;
						} else if (r == 4) {
							s = face ? CALCITE : PLANKS;
						} else if (r == 5) {
							s = HubBlocks.stoneStairs(dz < 0 ? Direction.SOUTH : Direction.NORTH, false);
						}
					} else {
						if (r <= 2) {
							s = face ? HubBlocks.pane(HubBlocks.GLASS_COLORS[tint]) : null;
						} else if (r == 3) {
							s = face ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : PLANKS;
						} else if (r <= 5) {
							s = face ? CALCITE : PLANKS;
						} else {
							s = slab(Blocks.DEEPSLATE_TILE_SLAB);
						}
					}
					if (s != null) {
						c.set(x, y, z, s);
					}
				}
			}
		}
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
		// from scale 2 (struct/hub4) glass and wood alternate cell by cell (1:1) and the wood is a thin cherry post, not a block
		boolean tall = HubDims.TALL();
		boolean mullion = tall ? Math.floorMod(along, 2) == 0 : Math.floorMod(along, 3) == 0;
		for (int y = bottom; y <= topY; y++) {
			boolean solid = corner || y <= glassFrom || y == topY;
			c.set(x, y, z, solid ? CALCITE : mullion ? (tall ? Blocks.CHERRY_FENCE.defaultBlockState() : HubBlocks.strippedLog(Direction.Axis.Y)) : Blocks.GLASS.defaultBlockState());
		}
	}

	/**
	 * The two-body lantern: the lower drum's cap is a ring of eaves with a smaller drum on top, then a
	 * stepped cherry pyramid and a spire; the beacon hangs on a chain from the lower ceiling.
	 */
	public static void cupola(CitadelCanvas c) {
		dormers(c);
		int d = HubDims.DRUM_HALF();
		int eaveY = HubDims.DRUM_TOP() + 1;
		for (int hx = -(d + 1); hx <= d + 1; hx++) {
			for (int hz = -(d + 1); hz <= d + 1; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				c.set(hx, eaveY, hz, m == d + 1 ? HubBlocks.roofStairs(inward(hx, hz)) : PLANKS);
			}
		}
		int up = d - 1;
		// the upper body: a sill row, the glass (2 rows; 4 from scale 2, struct/hub4, so it is a lantern and not a collar) and a cornice
		int glassRows = HubDims.TALL() ? 4 : 2;
		int bodyTop = eaveY + 2 + glassRows;
		for (int hx = -up; hx <= up; hx++) {
			for (int hz = -up; hz <= up; hz++) {
				int m = Math.max(Math.abs(hx), Math.abs(hz));
				if (m == up) {
					drumWall(c, hx, hz, Math.abs(hx), Math.abs(hz), eaveY + 1, eaveY + 1, bodyTop);
				}
			}
		}
		if (HubDims.TALL()) {
			// a pinnacle on each corner of the lower eave: calcite block, post and rod
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					c.set(sx * (d + 1), eaveY + 1, sz * (d + 1), CALCITE);
					c.set(sx * (d + 1), eaveY + 2, sz * (d + 1), HubBlocks.stoneWall());
					c.set(sx * (d + 1), eaveY + 3, sz * (d + 1), HubBlocks.rod());
				}
			}
		}
		int capY = bodyTop + 1;
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
		if (HubDims.TALL()) {
			// the finial: a calcite collar, a gilded ball, a post and the rod
			c.set(0, capY + d, 0, CALCITE);
			c.set(0, capY + d + 1, 0, Blocks.GOLD_BLOCK.defaultBlockState());
			c.set(0, capY + d + 2, 0, HubBlocks.stoneWall());
			c.set(0, capY + d + 3, 0, HubBlocks.rod());
		} else {
			c.set(0, capY + d, 0, HubBlocks.stoneWall());
			c.set(0, capY + d + 1, 0, HubBlocks.stoneWall());
			c.set(0, capY + d + 2, 0, HubBlocks.rod());
		}
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
		if (HubDims.TALL()) {
			// struct/hub4: a stepped skirt: the eave flares out and down in two more steps (dark, then cherry), over the terrace
			c.set(x + 2 * sx, CitadelHub.spring() - 1, z, HubBlocks.darkStairs(towardAxis(x), false));
			c.set(x + 3 * sx, CitadelHub.spring() - 2, z, HubBlocks.roofStairs(towardAxis(x)));
			// struct/hub5: the skirt is a solid wedge, not three stairs touching on their corners (the settle pass dropped two runs of
			// 16 of them at the crossing): a dark block under each of the first two steps, so every step rests on a face
			c.set(x + sx, CitadelHub.spring() - 1, z, HubBlocks.darkBase(x + sx, CitadelHub.spring() - 1, z));
			c.set(x + 2 * sx, CitadelHub.spring() - 2, z, HubBlocks.darkBase(x + 2 * sx, CitadelHub.spring() - 2, z));
			// and a dentil course under the lip (every other cell, upside-down stairs): the eave throws a broken shadow on the terrace
			if (Math.floorMod(z, 2) == 0) {
				c.set(x + 3 * sx, CitadelHub.spring() - 3, z, HubBlocks.stairsTop(Blocks.DEEPSLATE_TILE_STAIRS, towardAxis(x)));
			}
		}
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
