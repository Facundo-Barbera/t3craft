package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;

/**
 * The four towers flanking the north and south gables: a hollow 3 x 3 shaft at each outer vertex of the
 * faces (it never takes an interior cell, nor the diagonal paths and patio stairs the walkers use). Dark base, stone-brick shaft with
 * calcite corner posts and slit windows on the outer faces, a corbelled cornice and a stepped cherry cap
 * with a spire. Paints after the wall, on top of it.
 */
final class HubTowers {
	private HubTowers() {
	}

	/** One tower at each end of the north and south faces, flanking the gables and the bridges; the heights alternate. */
	static void paint(CitadelCanvas c) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int top = sx * sz > 0 ? HubDims.TOWER_TOP_A : HubDims.TOWER_TOP_B;
				tower(c, sx * HubDims.TOWER_X, sz * HubDims.TOWER_Z, sx, sz, top);
			}
		}
	}

	private static void tower(CitadelCanvas c, int cx, int cz, int sx, int sz, int topY) {
		int hw = HubDims.TOWER_HALF;
		int cornice = topY - 1;
		for (int dx = -hw; dx <= hw; dx++) {
			for (int dz = -hw; dz <= hw; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				boolean corner = dx != 0 && dz != 0;
				boolean outer = dx == sx || dz == sz;
				for (int y = 0; y <= Y0; y++) {
					c.set(x, y, z, HubBlocks.darkBase(x, y, z));
				}
				for (int y = Y0 + 1; y < cornice; y++) {
					if (corner) {
						c.set(x, y, z, y == Y0 + 1 ? CitadelCanvas.RIB : CALCITE);
					} else if (outer && (y == Y0 + 4 || y == Y0 + 5)) {
						c.set(x, y, z, HubBlocks.pane(y == Y0 + 4 ? HubBlocks.GLASS_COLORS[1] : HubBlocks.GLASS_COLORS[0]));
					} else {
						c.set(x, y, z, HubBlocks.stoneBrick(x, y, z));
					}
				}
				c.set(x, cornice, z, CALCITE);
			}
		}
		int roof = topY;
		int eave = HubDims.TOWER_EAVE;
		for (int dx = -eave; dx <= eave; dx++) {
			for (int dz = -eave; dz <= eave; dz++) {
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				int x = cx + dx;
				int z = cz + dz;
				if (m == eave) {
					c.set(x, cornice, z, HubBlocks.stoneStairs(inward(dx, dz), true));
				}
				for (int k = 0; k < eave + 1; k++) {
					int ring = eave - k;
					if (m == ring) {
						c.set(x, roof + k, z, HubBlocks.roofStairs(inward(dx, dz)));
					} else if (m < ring) {
						c.set(x, roof + k, z, PLANKS);
					}
				}
			}
		}
		c.set(cx, roof + eave + 1, cz, HubBlocks.stoneWall());
		c.set(cx, roof + eave + 2, cz, HubBlocks.stoneWall());
		c.set(cx, roof + eave + 3, cz, HubBlocks.rod());
	}

	/** Direction from a ring cell towards the centre (the stair's tall back is on that side). */
	private static Direction inward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}
}
