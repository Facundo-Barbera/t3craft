package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;

/**
 * Every size and position the hub pieces use, in one place, at the citadel's scale
 * ({@code LayoutParams.RINGCAMPUS_SCALE}, see {@code CitadelCanvas}). Horizontal values are cells from the
 * hub's centre ({@code s(n)}, or derived from the hub's zone: {@code hubX}, the nave's half width);
 * vertical ones are absolute y ({@code y0() + sy(y - 4)}, or derived from {@link CitadelHub#aisleTop()}).
 * The comment of each one gives its value at scale 1, where every method returns the original constant.
 *
 * <p>They are methods (with the old constant names, so each use only gained {@code ()}) because the scale
 * can change at a reload. Doors, screens, the light radius and the walk-spot positions are not here:
 * they are fixed by {@code CitadelHub} and never scale.
 */
final class HubDims {
	private HubDims() {
	}

	private static RingCampusGeometry.Dims d() {
		return CitadelCanvas.dims();
	}

	private static int s(int n) {
		return d().s(n);
	}

	/** An absolute height on the podium: {@code y} at scale 1. */
	private static int py(int y) {
		return CitadelCanvas.y0() + d().sy(y - 4);
	}

	// ---- horizontal: the octagon and its wall
	/** Coordinate of the wall's flat faces (the wall column): 13; and the half-length of a flat face: 8. */
	static int FACE() {
		return d().hubX() + 1;
	}

	static int FACE_HALF() {
		return s(8);
	}

	/** Inside edge of the wall on the flat faces (last interior column): 12. */
	static int INNER() {
		return d().hubX();
	}

	/** Half-width of the nave: the colonnade's lines are at |x| = NAVE_HALF (6). */
	static int NAVE_HALF() {
		return CitadelHub.naveHalf();
	}

	/** Bays: the piers repeat every PIER_PERIOD (4) cells along a face, shifted by PIER_PHASE (2). */
	static int PIER_PERIOD() {
		return s(4);
	}

	static int PIER_PHASE() {
		return s(2);
	}

	/** Half-length of the windowless stretch of the east and west faces (the screens are behind it): 5. */
	static int BLIND_HALF() {
		return s(5);
	}

	// ---- horizontal: inside
	/** Rug: first and last z (-9, 10), border at |x| = RUG_HALF + 1 (1). */
	static int RUG_Z0() {
		return -s(9);
	}

	static int RUG_Z1() {
		return s(10);
	}

	static int RUG_HALF() {
		return s(1);
	}

	/** Pews: rows at these z (7, 2 rows, step 2: they are benches and keep their count), seats at |x| from PEW_X0 (2) to PEW_X1 (4). */
	static int PEW_Z0() {
		return s(7);
	}

	static int PEW_ROWS() {
		return 2;
	}

	static int PEW_STEP() {
		return 2;
	}

	static int PEW_X0() {
		return s(2);
	}

	static int PEW_X1() {
		return s(4);
	}

	/** Reading nooks: table at |x| = NOOK_X (9), chairs one either side, planter at NOOK_X + 2, z = +-NOOK_Z (8). */
	static int NOOK_X() {
		return s(9);
	}

	static int NOOK_Z() {
		return s(8);
	}

	/** Planters at the foot of the piers: z of the two piers used (6, 10), x on the nave (5) and aisle (7) side. */
	static int PLANTER_Z0() {
		return s(6);
	}

	static int PLANTER_Z1() {
		return s(10);
	}

	static int PLANTER_NAVE_X() {
		return NAVE_HALF() - 1;
	}

	static int PLANTER_AISLE_X() {
		return NAVE_HALF() + 1;
	}

	/** Pilasters on the aisle walls: x 12, z 6. */
	static int PILASTER_X() {
		return INNER();
	}

	static int PILASTER_Z() {
		return s(6);
	}

	/** Pendant lamps in the nave: {x, z} (at scale 1 {0,±4}, {0,±8}, {±3,0}, {±3,±8}). */
	static int[][] PENDANTS() {
		int[][] base = {{0, -8}, {0, -4}, {0, 4}, {0, 8}, {-3, 0}, {3, 0}, {-3, -8}, {3, -8}, {-3, 8}, {3, 8}};
		int[][] out = new int[base.length][];
		for (int i = 0; i < base.length; i++) {
			out[i] = new int[] {s(base[i][0]), s(base[i][1])};
		}
		return out;
	}

	/** Rafters start at this |x| (8); hanging lanterns of the aisle at this |x| (9), up to this |z| (8). */
	static int RAFTER_X() {
		return NAVE_HALF() + 2;
	}

	static int AISLE_LAMP_X() {
		return s(9);
	}

	static int AISLE_LAMP_Z() {
		return s(8);
	}

	/** Lights are scanned over this half-extent (12). */
	static int LIGHT_SCAN() {
		return INNER();
	}

	/** Covering radius (steps) of the light blocks: a level-13 light one cell above the floor keeps 10 two steps away (not scaled). */
	static int LIGHT_RADIUS() {
		return 2;
	}

	// ---- horizontal: outside
	/** Buttresses on the east and west faces: x of the two steps (14, 15) and the z of each (±2, ±6). */
	static int BUTTRESS_X0() {
		return FACE() + 1;
	}

	static int BUTTRESS_X1() {
		return FACE() + 2;
	}

	static int[] BUTTRESS_Z() {
		return new int[] {-s(6), -s(2), s(2), s(6)};
	}

	/** z of the torii over each bridge (16), and the half-width of the pillars (3, the bridge's), tie beam (2), lintel (4) and roof (5). */
	static int TORII_Z() {
		return s(16);
	}

	static int TORII_PILLAR() {
		return d().bridgeHalf;
	}

	static int TORII_TIE() {
		return s(2);
	}

	static int TORII_LINTEL() {
		return s(4);
	}

	static int TORII_ROOF() {
		return s(5);
	}

	/** Scan extent for the outside pass (14), at least the buttresses' second step. */
	static int SCAN() {
		return Math.max(s(14), FACE() + 1);
	}

	// ---- vertical (absolute y)
	/** Top of the lower pier shaft below the capital (capital at CAPITAL_Y): 11, one under the aisle top. */
	static int CAPITAL_Y() {
		return CitadelHub.aisleTop() - 1;
	}

	/** Chandeliers hang down to this y (the lantern): 16. */
	static int PENDANT_LANTERN_Y() {
		return py(16);
	}

	/** Buttress tops (the stair on the last block): 11 and 8. */
	static int BUTTRESS_TOP0() {
		return py(11);
	}

	static int BUTTRESS_TOP1() {
		return py(8);
	}

	// ---- roof, lantern and towers
	/** The lantern drum over the crossing: half-width of its ring (3; the opening is DRUM_HALF - 1), top of its walls (26). */
	static int DRUM_HALF() {
		return s(3);
	}

	static int DRUM_TOP() {
		return py(26);
	}

	/** Transept over the aisles: half-width in z of each cross gable (4; ridge at TERRACE_Y + TRANSEPT_HALF). */
	static int TRANSEPT_HALF() {
		return s(4);
	}

	/** Aisle roof terrace: floor y (13, on the aisle top), and the x range of the terrace (7 to 12, aisle side of the clerestory). */
	static int TERRACE_Y() {
		return CitadelHub.aisleTop() + 1;
	}

	static int TERRACE_X0() {
		return NAVE_HALF() + 1;
	}

	static int TERRACE_X1() {
		return INNER();
	}

	/**
	 * Corner towers: half-width of the shaft (1: 3 x 3), offset of the centre outward from the vertex (9, 14),
	 * shaft tops (16, 14), eave half-width (2).
	 */
	static int TOWER_HALF() {
		return s(1);
	}

	static int TOWER_X() {
		return s(9);
	}

	static int TOWER_Z() {
		return FACE() + 1;
	}

	static int TOWER_TOP_A() {
		return py(16);
	}

	static int TOWER_TOP_B() {
		return py(14);
	}

	static int TOWER_EAVE() {
		return TOWER_HALF() + 1;
	}

	/** Stone lanterns at the bridge heads, on the rail line, this far from the hub's centre (14). */
	static int TORO_Z() {
		return s(14);
	}

	/** Niches on the east and west faces (between the buttresses): floor y (1, the ground), lintel y (4, the podium). */
	static int NICHE_FLOOR() {
		return 1;
	}

	static int NICHE_LINTEL() {
		return CitadelCanvas.y0();
	}
}
