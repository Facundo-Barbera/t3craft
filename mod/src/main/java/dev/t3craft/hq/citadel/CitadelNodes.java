package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.nodes.CloisterRoom;
import dev.t3craft.hq.citadel.nodes.LoungeRoom;
import dev.t3craft.hq.citadel.nodes.NodeDecor;
import dev.t3craft.hq.citadel.nodes.RotundaRoom;
import dev.t3craft.hq.citadel.nodes.TowerRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;

/**
 * The special rooms of the ring-1 street: the eight nodes of the citadel. Each node is a disc or
 * square of {@code Zone.Kind.NODE} columns (radius ≤ 8 round its centre {@code (cx, cz)}); {@link #node}
 * paints every column of the node cell by cell ({@link #nodeCell}, in x-then-z order), then the furniture
 * ({@link NodeDecor#furnish}), then the fixtures of the room (screen, terminal, walk spots). All coordinates
 * are absolute: the ground is {@code y = 0}, the hub's podium does not reach the nodes. Nothing here keeps
 * state: the pieces are static methods taking the {@link CitadelCanvas} first, and the order of the calls
 * decides which block wins on a shared cell, so it must not change.
 *
 * <h2>Pieces (each room is its own class in {@code citadel.nodes}, with its own guide)</h2>
 *
 * <ul>
 * <li><b>{@link #tower} — review tower</b> ({@code Kind.REVIEW_TOWER}) → {@link TowerRoom}: a slender stepped
 *     tower, 13 × 13 at the base, about 39 high with its spire: base with piers and glass bays, a middle body
 *     with slit windows, an open belfry with a hanging lantern for a bell, a stair roof. Fixtures
 *     ({@link #towerFixtures}): the "review" big screen at {@code (cx - 3, 5, cz - 5)}, 7 × 4, facing south
 *     (flush in the north wall plane), and a walk spot at {@code (cx, 1, cz - 2)}.</li>
 * <li><b>{@link #rotunda} — scheduled-tasks rotunda</b> ({@code Kind.SCHEDULE_ROTUNDA}) → {@link RotundaRoom}:
 *     a round hall of radius 7 with a clock-face floor, a drum with pilasters and tall windows, a banded dome
 *     with a glass cupola. Fixtures ({@link #rotundaFixtures}): the "schedule" big screen (base) at
 *     {@code (cx + 4, 2, cz - 2)}, 5 × 3, facing west, and a walk spot at {@code (cx - 1, 1, cz)}.</li>
 * <li><b>{@link #cloister} — archive cloister</b> ({@code Kind.ARCHIVE_CLOISTER}) → {@link CloisterRoom}: a
 *     square of 15 × 15 with a fountain court, a gallery of arches and columns under a tile roof, corner
 *     turrets and a bell gable over the north gate. Fixtures ({@link #cloisterFixtures}): the archive terminal
 *     (a fixture, {@code style.archiveTerminal}) at {@code (cx + 3, 1, cz - 6)} facing south, anchors
 *     {@code ARCHIVE_TERMINAL} (face) on it and {@code ARCHIVE} (at) one block south of it, and a walk spot at
 *     {@code (cx - 4, 1, cz)}.</li>
 * <li><b>{@link #sunken} — lounge</b> ({@code Kind.LOUNGE_SUNKEN}) → {@link LoungeRoom}: a sunken amphitheatre
 *     of half steps down to {@code y = -3} with pools, a fire pavilion, sofas and trees. Fixtures
 *     ({@link #loungeFixtures}): a walk spot at {@code (cx + 1, -2, cz)}; no screen.</li>
 * <li><b>{@link #pillar} — diagonal nodes</b> ({@code Kind.PILLAR}): a paved round whose monument is chosen by
 *     the index {@code k} of the node ({@link NodeDecor#pillarVariant}); built by {@link NodeDecor}.</li>
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

	/** Paints node {@code nd}: all of its columns, then its furniture, then the fixtures of its kind. */
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
		NodeDecor.furnish(c, nd);
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
			case PILLAR -> pillar(c, x, z, dx, dz, d, nd.k());
		}
	}

	/** A door of width 3 on the four sides: the cell is within 1 of an axis and at least {@code edge} along the other. */
	public static boolean door(int dx, int dz, int edge) {
		return Math.abs(dx) <= 1 && Math.abs(dz) >= edge || Math.abs(dz) <= 1 && Math.abs(dx) >= edge;
	}

	// ------------------------------------------------------------------ review tower

	/** Review tower: see {@link TowerRoom}. */
	public static void tower(CitadelCanvas c, int x, int z, int dx, int dz) {
		TowerRoom.cell(c, x, z, dx, dz);
	}

	/** The fixtures of the tower: the review screen in its north wall and a walk spot. */
	public static void towerFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz - 5), Direction.EAST, Direction.SOUTH, 7, 4), "review", "", false);
		c.walkSpot(new BlockPos(cx, 1, cz - 2), Direction.NORTH);
	}

	// ------------------------------------------------------------------ schedule rotunda

	/** Scheduled-tasks rotunda: see {@link RotundaRoom}. */
	public static void rotunda(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		RotundaRoom.cell(c, x, z, dx, dz, d);
	}

	/** The fixtures of the rotunda: the schedule screen (with a base) and a walk spot. */
	public static void rotundaFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx + 4, 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "", true);
		c.walkSpot(new BlockPos(cx - 1, 1, cz), Direction.EAST);
	}

	// ------------------------------------------------------------------ archive cloister

	/** Archive cloister: see {@link CloisterRoom}. */
	public static void cloister(CitadelCanvas c, int x, int z, int dx, int dz) {
		CloisterRoom.cell(c, x, z, dx, dz);
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

	/** Lounge: see {@link LoungeRoom}. */
	public static void sunken(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		LoungeRoom.cell(c, x, z, dx, dz, d);
	}

	/** The fixtures of the lounge: a walk spot at the bottom of the amphitheatre. */
	public static void loungeFixtures(CitadelCanvas c, Node nd) {
		c.walkSpot(new BlockPos(nd.cx() + 1, -2, nd.cz()), Direction.WEST);
	}

	// ------------------------------------------------------------------ diagonal nodes

	/**
	 * Diagonal nodes: the paving of a small monument. The paving and the monument depend on the index
	 * {@code k} of the node ({@link NodeDecor#pillarVariant}): stone lantern, bell pavilion, cherry tree,
	 * obelisk (the monuments themselves are built by {@link NodeDecor}).
	 */
	public static void pillar(CitadelCanvas c, int x, int z, int dx, int dz, double d, int k) {
		BlockState paving = c.accent();
		switch (NodeDecor.pillarVariant(k)) {
			case 0 -> paving = d > 1.8 ? c.floor() : paving;
			case 1 -> paving = d > 1.5 ? c.floor() : paving;
			case 2 -> paving = d <= 1.5 ? GRASS : paving;
			default -> paving = d < 1.5 ? FOOT : paving;
		}
		c.set(x, 0, z, paving);
	}
}
