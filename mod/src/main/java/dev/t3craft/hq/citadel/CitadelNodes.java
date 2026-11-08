package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

/**
 * The special rooms of the ring-1 street: the eight nodes of the citadel. Each node is a disc or
 * square of {@code Zone.Kind.NODE} columns (radius ≤ 8 round its centre {@code (cx, cz)}); {@link #node}
 * paints every column of the node cell by cell ({@link #nodeCell}, in x-then-z order), then adds the
 * fixtures of the room (screen, terminal, walk spots). All coordinates are absolute: the ground is
 * {@code y = 0}, the hub's podium does not reach the nodes. Nothing here keeps state: the pieces are
 * static methods taking the {@link CitadelCanvas} first, and the order of the calls decides which block
 * wins on a shared cell, so it must not change.
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li><b>{@link #tower} — review tower</b> ({@code Kind.REVIEW_TOWER}; args {@code x, z, dx, dz} = cell
 *     and offset from the centre). Square 13 × 13, 20 high; ring {@code m = max(|dx|,|dz|)}: floor
 *     ({@link #towerFloor}), wall at {@code m = 6} with corner and mid-side posts, a glass checker, a
 *     panel band every 5 y and doors of width 3 on the four sides ({@link #towerWall}), a mezzanine
 *     band at {@code m = 5, y = 10} ({@link #towerMezzanine}), a stepped pyramid roof of ribs
 *     ({@link #towerRoof}) with a log spire and a light on top. Blocks: {@code kit.accent()} /
 *     {@code kit.floor()}, {@code LOG}, {@code kit.wallPanel(true)}, {@code GLASS}, {@code CALCITE},
 *     {@code FOOT}, {@code RIB}, {@code LIGHT}. Fixtures ({@link #towerFixtures}): the "review" big
 *     screen on the west wall at {@code (cx - 3, 5, cz - 5)}, 7 × 4, facing east, and a walk spot at
 *     {@code (cx, 1, cz - 2)}.</li>
 * <li><b>{@link #rotunda} — scheduled-tasks rotunda</b> ({@code Kind.SCHEDULE_ROTUNDA}; args
 *     {@code x, z, dx, dz, d} with {@code d = hypot(dx, dz)}). Round hall of radius 7: floor with a
 *     ring of lights at {@code d ≈ 5} (the clock) and an accent ring at {@code d ≈ 4}
 *     ({@link #rotundaFloor}); wall at {@code d > 6}, 8 high, with glass slits at y 3..6 and doors of
 *     width 3 ({@link #rotundaWall}); a dome of planks, glass at the oculus, a light on top
 *     ({@link #rotundaDome}). Blocks: {@code kit.accent()} / {@code kit.floor()}, {@code LIGHT},
 *     {@code FOOT}, {@code GLASS}, {@code kit.wallPanel(true)}, {@code PLANKS}. Fixtures
 *     ({@link #rotundaFixtures}): the "schedule" big screen (base) at {@code (cx + 4, 2, cz - 2)}, 5 × 3,
 *     facing west, and a walk spot at {@code (cx - 1, 1, cz)}.</li>
 * <li><b>{@link #cloister} — archive cloister</b> ({@code Kind.ARCHIVE_CLOISTER}; args
 *     {@code x, z, dx, dz}). Square 15 × 15 (ring {@code mx = max(|dx|,|dz|)}): a pool at the centre,
 *     garden court, accent and floor rings, a foot ring outside ({@link #cloisterFloor}); white walls
 *     with four gates at {@code mx = 7}, an arcaded gallery with corner posts under a plank and brick
 *     slab roof at {@code mx >= 3} ({@link #cloisterWalls}). Blocks: {@code kit.accent()} /
 *     {@code kit.floor()}, {@code kit.gardenPlanter()}, {@code kit.wallPanel(true)}, {@code CALCITE},
 *     {@code GRASS}, {@code WATER}, {@code FOOT}, {@code LOG}, {@code PLANKS}, brick slab. Fixtures
 *     ({@link #cloisterFixtures}): the archive terminal (a fixture, {@code style.archiveTerminal}) at
 *     {@code (cx + 3, 1, cz - 6)} facing south, anchors {@code ARCHIVE_TERMINAL} (face) on it and
 *     {@code ARCHIVE} (at) one block south of it, and a walk spot at {@code (cx - 4, 1, cz)}.</li>
 * <li><b>{@link #sunken} — lounge</b> ({@code Kind.LOUNGE_SUNKEN}; args {@code x, z, dx, dz, d}). A
 *     sunken round patio: half steps ({@code k = round(7 - d)}, 0..6) go down to {@code y = -3}, moss and
 *     deepslate-tile steps with an accent rim, a log-and-light column at the centre and azalea bushes
 *     on the diagonals at {@code d > 6.2}. It first removes the ground cell the zone painted. Blocks:
 *     {@code MOSS}, {@code FOOT}, deepslate-tile slab, {@code kit.accent()}, {@code LOG}, {@code LIGHT},
 *     azalea leaves. Fixtures ({@link #loungeFixtures}): a walk spot at {@code (cx + 1, -2, cz)}; no
 *     screen.</li>
 * <li><b>{@link #pillar} — diagonal nodes</b> ({@code Kind.PILLAR}; args {@code x, z, d}). A paved round
 *     ({@code kit.accent()}) with a log pillar 4 high at the centre and a light on top. No fixtures.</li>
 * <li>{@link #door} — the 3-wide door test shared by the tower and the rotunda.</li>
 * </ul>
 *
 * <h2>Groups and anchors</h2>
 *
 * The nodes make no groups themselves: the finish pass groups a node column by its zone, as
 * {@code node_<k>}, except node 4 (the archive cloister), which goes in the {@code archive} group. The
 * anchors a node produces are the big-screen anchors ({@code AnchorNames.bigScreen}, through
 * {@link CitadelHub#bigScreen}), the walk spots ({@code AnchorNames.walkHub(n)}, numbered in the order
 * the nodes place them) and, for the cloister, {@code ARCHIVE_TERMINAL} and {@code ARCHIVE}.
 */
public final class CitadelNodes {
	private CitadelNodes() {
	}

	/** Paints node {@code nd}: all of its columns, then the fixtures of its kind. */
	public static void node(CitadelCanvas c, Node nd) {
		int R = 8;
		for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
			for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
				Zone zn = c.zoneAt(x, z);
				if (zn.kind() == Kind.NODE && zn.node() == nd.k()) {
					nodeCell(c, nd, x, z);
				}
			}
		}
		switch (nd.kind()) {
			case REVIEW_TOWER -> towerFixtures(c, nd);
			case SCHEDULE_ROTUNDA -> rotundaFixtures(c, nd);
			case ARCHIVE_CLOISTER -> cloisterFixtures(c, nd);
			case LOUNGE_SUNKEN -> loungeFixtures(c, nd);
			default -> {
			}
		}
	}

	/** Paints one column of a node, by the kind of the node. */
	public static void nodeCell(CitadelCanvas c, Node nd, int x, int z) {
		int dx = x - nd.cx();
		int dz = z - nd.cz();
		double d = Math.hypot(dx, dz);
		switch (nd.kind()) {
			case REVIEW_TOWER -> tower(c, x, z, dx, dz);
			case SCHEDULE_ROTUNDA -> rotunda(c, x, z, dx, dz, d);
			case ARCHIVE_CLOISTER -> cloister(c, x, z, dx, dz);
			case LOUNGE_SUNKEN -> sunken(c, x, z, dx, dz, d);
			case PILLAR -> pillar(c, x, z, d);
		}
	}

	/** A door of width 3 on the four sides: the cell is within 1 of an axis and at least {@code edge} along the other. */
	public static boolean door(int dx, int dz, int edge) {
		return Math.abs(dx) <= 1 && Math.abs(dz) >= edge || Math.abs(dz) <= 1 && Math.abs(dx) >= edge;
	}

	// ------------------------------------------------------------------ review tower

	/** Review tower: square, 13 × 13, 20 high, an inner mezzanine band, a pyramid roof and a light on its spire. */
	public static void tower(CitadelCanvas c, int x, int z, int dx, int dz) {
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		int top = 20;
		towerFloor(c, x, z, m);
		towerWall(c, x, z, dx, dz, m, top);
		towerMezzanine(c, x, z, m);
		towerRoof(c, x, z, m, top);
	}

	/** The floor of the tower: an accent square of 5 × 5 in the middle, the floor elsewhere. */
	public static void towerFloor(CitadelCanvas c, int x, int z, int m) {
		c.set(x, 0, z, m <= 2 ? c.accent() : c.floor());
	}

	/** The wall of the tower (ring 6): posts, glass checker, a panel band every 5 y, doors on the four sides. */
	public static void towerWall(CitadelCanvas c, int x, int z, int dx, int dz, int m, int top) {
		if (m == 6) {
			boolean corner = Math.abs(dx) == 6 && Math.abs(dz) == 6 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
			for (int y = 1; y <= top; y++) {
				BlockState s = corner ? LOG : (y % 5 == 0 ? c.kit.wallPanel(true) : (y >= 3 && Math.floorMod(dx + dz, 2) == 0 ? GLASS : CALCITE));
				c.set(x, y, z, s);
			}
			if (door(dx, dz, 6)) {
				c.fill(x, 1, 4, z, AIR);
			}
		}
	}

	/** The mezzanine band of the tower: a foot ring at y = 10 inside the wall. */
	public static void towerMezzanine(CitadelCanvas c, int x, int z, int m) {
		if (m == 5) {
			c.set(x, 10, z, FOOT);
		}
	}

	/** The pyramid roof of the tower (a rib per ring, clearing the cells below it) and its log spire with a light. */
	public static void towerRoof(CitadelCanvas c, int x, int z, int m, int top) {
		int lvl = top + 1 + (6 - m);
		for (int y = top + 1; y <= lvl; y++) {
			c.set(x, y, z, y == lvl ? RIB : AIR);
		}
		if (m == 0) {
			c.set(x, lvl + 1, z, LOG);
			c.set(x, lvl + 2, z, LIGHT);
		}
	}

	/** The fixtures of the tower: the review screen on its west wall and a walk spot. */
	public static void towerFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz - 5), Direction.EAST, Direction.SOUTH, 7, 4), "review", "", false);
		c.walkSpot(new BlockPos(cx, 1, cz - 2), Direction.NORTH);
	}

	// ------------------------------------------------------------------ schedule rotunda

	/** Scheduled-tasks rotunda: a round hall (radius 7) under a dome, a clock ring of lights set in the floor. */
	public static void rotunda(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int wallTop = 8;
		rotundaFloor(c, x, z, dx, dz, d);
		rotundaWall(c, x, z, dx, dz, d, wallTop);
		rotundaDome(c, x, z, d, wallTop);
	}

	/** The floor of the rotunda: the clock ring of lights (d ≈ 5), an accent ring (d ≈ 4), the floor elsewhere. */
	public static void rotundaFloor(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		boolean clock = Math.abs(d - 5) < 0.5 && Math.floorMod(dx * 7 + dz * 3, 4) == 0;
		c.set(x, 0, z, clock ? LIGHT : Math.abs(d - 4) < 0.5 ? c.accent() : c.floor());
	}

	/** The round wall of the rotunda (d > 6): foot blocks, glass slits at y 3..6, a panel cornice, doors on the four sides. */
	public static void rotundaWall(CitadelCanvas c, int x, int z, int dx, int dz, double d, int wallTop) {
		if (d > 6) {
			for (int y = 1; y <= wallTop; y++) {
				c.set(x, y, z, y == wallTop ? c.kit.wallPanel(true) : (y >= 3 && y <= 6 && Math.floorMod(dx * 3 + dz, 3) == 0 ? GLASS : FOOT));
			}
			if (door(dx, dz, 6)) {
				c.fill(x, 1, 4, z, AIR);
			}
		}
	}

	/** The dome of the rotunda: planks (glass at the oculus), one shell thick, with a light on top. */
	public static void rotundaDome(CitadelCanvas c, int x, int z, double d, int wallTop) {
		double R = 7.2;
		int top = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)));
		double dn = Math.min(R, d + 1);
		int low = wallTop + (int) Math.round(Math.sqrt(Math.max(0, R * R - dn * dn))) + 1;
		for (int y = Math.max(wallTop + 1, Math.min(low, top)); y <= top; y++) {
			c.set(x, y, z, d < 1 ? GLASS : PLANKS);
		}
		if (d < 0.5) {
			c.set(x, top + 1, z, LIGHT);
		}
	}

	/** The fixtures of the rotunda: the schedule screen (with a base) and a walk spot. */
	public static void rotundaFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx + 4, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "", true);
		c.walkSpot(new BlockPos(cx - 1, 1, cz), Direction.EAST);
	}

	// ------------------------------------------------------------------ archive cloister

	/**
	 * Archive cloister (after design/mediterranean): a square of 15 × 15, white walls with four gates,
	 * an arcaded gallery round a garden court with a pool; the archive terminal stands in the north
	 * gallery.
	 */
	public static void cloister(CitadelCanvas c, int x, int z, int dx, int dz) {
		int mx = Math.max(Math.abs(dx), Math.abs(dz));
		int mn = Math.min(Math.abs(dx), Math.abs(dz));
		cloisterFloor(c, x, z, dx, dz, mx);
		cloisterWalls(c, x, z, dx, dz, mx, mn);
	}

	/** The floor of the cloister: pool, garden court with planters at the corners, accent and floor rings, foot outside. */
	public static void cloisterFloor(CitadelCanvas c, int x, int z, int dx, int dz, int mx) {
		if (mx == 0) {
			c.set(x, -1, z, FOOT);
			c.set(x, 0, z, WATER);
		} else if (mx <= 2) {
			c.set(x, 0, z, mx == 1 ? c.accent() : GRASS);
			if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
				c.set(x, 1, z, c.kit.gardenPlanter());
			}
		} else if (mx == 3) {
			c.set(x, 0, z, c.accent());
		} else if (mx <= 6) {
			c.set(x, 0, z, c.floor());
		} else {
			c.set(x, 0, z, FOOT);
		}
	}

	/** The outer wall with four gates (ring 7) and the arcaded gallery with its roof (rings 3..6). */
	public static void cloisterWalls(CitadelCanvas c, int x, int z, int dx, int dz, int mx, int mn) {
		if (mx == 7) {
			for (int y = 1; y <= 5; y++) {
				c.set(x, y, z, y == 3 && mn % 4 == 2 ? c.kit.wallPanel(true) : CALCITE);
			}
			c.set(x, 6, z, PLANKS);
			c.set(x, 7, z, slab(Blocks.BRICK_SLAB));
			if (mn <= 1) {
				c.fill(x, 1, 3, z, AIR);
			}
		} else if (mx >= 3) {
			if (mx == 3 && mn >= 2) {
				c.fill(x, 1, 4, z, LOG);
			}
			c.set(x, 5, z, PLANKS);
			c.set(x, 6, z, slab(Blocks.BRICK_SLAB));
		}
	}

	/** The fixtures of the cloister: the archive terminal and its anchors, and a walk spot. */
	public static void cloisterFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		BlockPos t = new BlockPos(cx + 3, 1, cz - 6);
		c.fixture(t, c.style.archiveTerminal(Direction.SOUTH));
		c.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
		c.anchor(Anchor.at(AnchorNames.ARCHIVE, t.south(), Direction.NORTH));
		c.walkSpot(new BlockPos(cx - 4, 1, cz), Direction.EAST);
	}

	// ------------------------------------------------------------------ lounge

	/** Lounge: a sunken patio, a round amphitheatre of half steps down to y = -3 round a light column. */
	public static void sunken(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int k = (int) Math.max(0, Math.min(6, Math.round(7 - d)));
		long g = BlockPos.asLong(x, 0, z);
		c.cells.remove(g);
		c.layers.remove(g);
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		c.set(x, solid, z, k % 2 == 0 ? (k == 6 ? c.accent() : MOSS) : FOOT);
		if (k % 2 == 1) {
			c.set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			c.set(x, y, z, AIR);
		}
		if (dx == 0 && dz == 0) {
			c.set(x, -2, z, LOG);
			c.set(x, -1, z, LOG);
			c.set(x, 0, z, LIGHT);
		}
		if (d > 6.2 && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && Math.abs(dx) >= 4) {
			c.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			c.set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}

	/** The fixtures of the lounge: a walk spot at the bottom of the amphitheatre. */
	public static void loungeFixtures(CitadelCanvas c, Node nd) {
		c.walkSpot(new BlockPos(nd.cx() + 1, -2, nd.cz()), Direction.WEST);
	}

	// ------------------------------------------------------------------ diagonal nodes

	/** Diagonal nodes: a light pillar in a paved round. */
	public static void pillar(CitadelCanvas c, int x, int z, double d) {
		c.set(x, 0, z, c.accent());
		if (d < 0.5) {
			c.fill(x, 1, 4, z, LOG);
			c.set(x, 5, z, LIGHT);
		}
	}
}
