package dev.t3craft.hq.citadel.hub;

/**
 * Every size and position the hub pieces use, in one place so the coordinator can wrap them with the
 * scale helpers ({@code canvas.s(int)} horizontally, {@code canvas.sy(int)} vertically) when the citadel
 * grows. Horizontal values are cells from the hub's centre; vertical ones are absolute y. Doors, screens
 * and the walk-spot positions are not here: they are fixed by {@code CitadelHub} and never scale.
 */
final class HubDims {
	private HubDims() {
	}

	// ---- horizontal: the octagon and its wall
	/** Coordinate of the wall's flat faces (the wall column), and the half-length of a flat face. */
	static final int FACE = 13;
	static final int FACE_HALF = 8;
	/** Inside edge of the wall on the flat faces (last interior column). */
	static final int INNER = 12;
	/** Half-width of the nave: the colonnade's lines are at |x| = NAVE_HALF. */
	static final int NAVE_HALF = 6;
	/** Bays: the piers repeat every PIER_PERIOD cells along a face, shifted by PIER_PHASE. */
	static final int PIER_PERIOD = 4;
	static final int PIER_PHASE = 2;
	/** Half-length of the windowless stretch of the east and west faces (the screens are behind it). */
	static final int BLIND_HALF = 5;

	// ---- horizontal: inside
	/** Rug: first and last z, border at |x| = RUG_HALF + 1. */
	static final int RUG_Z0 = -9;
	static final int RUG_Z1 = 10;
	static final int RUG_HALF = 1;
	/** Pews: rows at these z, seats at |x| from PEW_X0 to PEW_X1. */
	static final int PEW_Z0 = 7;
	static final int PEW_ROWS = 2;
	static final int PEW_STEP = 2;
	static final int PEW_X0 = 2;
	static final int PEW_X1 = 4;
	/** Reading nooks: table at |x| = NOOK_X, chairs one either side, planter at NOOK_X + 2, z = +-NOOK_Z. */
	static final int NOOK_X = 9;
	static final int NOOK_Z = 8;
	/** Planters at the foot of the piers: z of the two piers used, x on the nave and aisle side. */
	static final int PLANTER_Z0 = 6;
	static final int PLANTER_Z1 = 10;
	static final int PLANTER_NAVE_X = 5;
	static final int PLANTER_AISLE_X = 7;
	/** Pilasters on the aisle walls. */
	static final int PILASTER_X = 12;
	static final int PILASTER_Z = 6;
	/** Pendant lamps in the nave: {x, z}. */
	static final int[][] PENDANTS = {{0, -8}, {0, -4}, {0, 4}, {0, 8}, {-3, 0}, {3, 0}, {-3, -8}, {3, -8}, {-3, 8}, {3, 8}};
	/** Rafters start at this |x|; hanging lanterns of the aisle at this |x|, up to this |z|. */
	static final int RAFTER_X = 8;
	static final int AISLE_LAMP_X = 9;
	static final int AISLE_LAMP_Z = 8;
	/** Lights are scanned over this half-extent. */
	static final int LIGHT_SCAN = 12;
	/** Covering radius (steps) of the light blocks. */
	static final int LIGHT_RADIUS = 3;

	// ---- horizontal: outside
	/** Buttresses on the east and west faces: x of the two steps and the z of each. */
	static final int BUTTRESS_X0 = 14;
	static final int BUTTRESS_X1 = 15;
	static final int[] BUTTRESS_Z = {-6, -2, 2, 6};
	/** z of the torii over each bridge, and the half-width of the pillars, tie beam, lintel and roof. */
	static final int TORII_Z = 16;
	static final int TORII_PILLAR = 3;
	static final int TORII_TIE = 2;
	static final int TORII_LINTEL = 4;
	static final int TORII_ROOF = 5;
	/** Scan extent for the outside pass. */
	static final int SCAN = 14;

	// ---- vertical (absolute y)
	/** Top of the lower pier shaft below the capital (capital at CAPITAL_Y). */
	static final int CAPITAL_Y = 11;
	/** Chandeliers hang down to this y (the lantern). */
	static final int PENDANT_LANTERN_Y = 16;
	/** Buttress tops (the stair on the last block). */
	static final int BUTTRESS_TOP0 = 11;
	static final int BUTTRESS_TOP1 = 8;

	// ---- roof, lantern and towers
	/** The lantern drum over the crossing: half-width of its ring (the opening is DRUM_HALF - 1), top of its walls. */
	static final int DRUM_HALF = 3;
	static final int DRUM_TOP = 26;
	/** Transept over the aisles: half-width in z of each cross gable (ridge at TERRACE_Y + TRANSEPT_HALF). */
	static final int TRANSEPT_HALF = 4;
	/** Aisle roof terrace: floor y, and the x range of the terrace (aisle side of the clerestory). */
	static final int TERRACE_Y = 13;
	static final int TERRACE_X0 = 7;
	static final int TERRACE_X1 = 12;
	/** Corner towers: half-width of the shaft (3 x 3 => 1), offset of the centre outward from the vertex, shaft top, eave half-width. */
	static final int TOWER_HALF = 1;
	static final int TOWER_X = 9;
	static final int TOWER_Z = 14;
	static final int TOWER_TOP_A = 16;
	static final int TOWER_TOP_B = 14;
	static final int TOWER_EAVE = 2;
	/** Stone lanterns at the bridge heads, on the rail line, this far from the hub's centre. */
	static final int TORO_Z = 14;
	/** Niches on the east and west faces (between the buttresses): floor y, lintel y. */
	static final int NICHE_FLOOR = 1;
	static final int NICHE_LINTEL = 4;
}
