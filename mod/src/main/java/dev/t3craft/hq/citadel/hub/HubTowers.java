package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;

/**
 * The eight corner towers of the wall: a hollow 3 x 3 shaft on the vertices of the octagon, set
 * {@link HubDims#TOWER_OUT} outward so it never takes an interior cell. Dark base, stone-brick shaft with
 * calcite corner posts and slit windows on the outer faces, a corbelled cornice and a stepped cherry cap
 * with a spire. Paints after the wall, on top of it.
 */
final class HubTowers {
	private HubTowers() {
	}

	/** The eight vertices {x, z} of the octagon. */
	private static int[][] vertices() {
		int f = HubDims.FACE;
		int h = HubDims.FACE_HALF;
		int[][] v = new int[8][];
		int i = 0;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				v[i++] = new int[] {sx * f, sz * h};
				v[i++] = new int[] {sx * h, sz * f};
			}
		}
		return v;
	}

	static void paint(CitadelCanvas c) {
		for (int[] v : vertices()) {
			int sx = Integer.signum(v[0]);
			int sz = Integer.signum(v[1]);
			tower(c, v[0] + sx * HubDims.TOWER_OUT, v[1] + sz * HubDims.TOWER_OUT, sx, sz);
		}
	}

	private static void tower(CitadelCanvas c, int cx, int cz, int sx, int sz) {
		int hw = HubDims.TOWER_HALF;
		int cornice = HubDims.TOWER_TOP - 1;
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
		int roof = HubDims.TOWER_TOP;
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
