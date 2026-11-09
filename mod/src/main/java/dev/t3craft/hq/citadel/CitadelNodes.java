package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.RingCampusGeometry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.nodes.ArchiveBay;
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
 *     of half steps down to {@code y = -3} with flower beds, an open hearth, sofas and trees. Fixtures
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

	/**
	 * Paints node {@code nd}: all of its columns, then its furniture, then the fixtures of its kind.
	 *
	 * <p>Scale ({@code k > 1}): the rooms are designed at one size (the shell classes and {@link NodeDecor}
	 * work in design offsets {@code (dx, dz)} from the centre). They are painted at that size over the
	 * node's centre, then magnified horizontally onto the node's real zone ({@link #magnify}): every real
	 * column copies the design column {@code (round(dx / k), round(dz / k))}, so walls, piers, windows,
	 * doors, stairs and furniture grow by {@code k} in plan and keep their heights (a stair step stays one
	 * block high and becomes {@code k} deep, a door stays 3 high and becomes {@code 3k} wide). Fixtures
	 * (screens, terminal, walk spots) keep their size and move to the scaled position.
	 */
	public static void node(CitadelCanvas c, Node nd) {
		double k = c.k;
		if (k <= 1.0) {
			int R = Math.max(8, (int) Math.ceil(nd.size()) + 1);
			for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
				for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
					Zone zn = c.zoneAt(x, z);
					if (zn.kind() == Kind.NODE && zn.node() == nd.k()) {
						nodeCell(c, nd, x, z);
					}
				}
			}
			NodeDecor.furnish(c, nd);
		} else {
			Node design = new Node(nd.k(), nd.kind(), nd.cx(), nd.cz(), nd.size() / k);
			int R = Math.max(8, (int) Math.ceil(design.size()) + 1);
			for (int x = nd.cx() - R; x <= nd.cx() + R; x++) {
				for (int z = nd.cz() - R; z <= nd.cz() + R; z++) {
					if (design.contains(x, z)) {
						nodeCell(c, nd, x, z);
					}
				}
			}
			NodeDecor.furnish(c, nd);
			magnify(c, nd, design, R, k);
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

	private static final int Y_MIN = -8;
	private static final int Y_MAX = 90;

	/** A design offset of a real offset {@code n}: {@code round(|n| / k)} with the sign of {@code n}. */
	static int design(int n, double k) {
		int a = (int) Math.floor(Math.abs(n) / k + 0.5);
		return n < 0 ? -a : a;
	}

	/** The real offset at the middle of the span of design offset {@code u}: {@code round(u · k)}. */
	static int real(int u, double k) {
		return RingCampusGeometry.s(k, u);
	}

	/** The real offset of the face of design offset {@code u} nearest the centre (a wall's inner face). */
	static int realInner(int u, double k) {
		int a = Math.abs(u);
		int n = (int) Math.max(0, Math.ceil((a - 0.5) * k - 1e-9));
		while (design(n, k) < a) {
			n++;
		}
		while (n > 0 && design(n - 1, k) >= a) {
			n--;
		}
		return u < 0 ? -n : n;
	}

	/** Copies each real column of the node from the design column it magnifies (see {@link #node}). */
	private static void magnify(CitadelCanvas c, Node nd, Node design, int R, double k) {
		int cx = nd.cx();
		int cz = nd.cz();
		int span = (int) Math.ceil(nd.size()) + 2;
		int dspan = Math.max(R, (int) Math.ceil(design.size()) + 1);
		// 1. snapshot of the design columns
		Map<Long, Column> snap = new HashMap<>();
		for (int dx = -dspan; dx <= dspan; dx++) {
			for (int dz = -dspan; dz <= dspan; dz++) {
				if (design.contains(cx + dx, cz + dz)) {
					snap.put(CitadelCanvas.col(dx, dz), Column.of(c, cx + dx, cz + dz));
				}
			}
		}
		// 2. every real column of the node takes the design column it magnifies
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				Zone zn = c.zoneAt(cx + dx, cz + dz);
				if (zn.kind() != Kind.NODE || zn.node() != nd.k()) {
					continue;
				}
				int u = design(dx, k);
				int w = design(dz, k);
				Column src = snap.get(CitadelCanvas.col(u, w));
				while (src == null && (u != 0 || w != 0)) {
					u -= Integer.signum(u);
					w -= Integer.signum(w);
					src = snap.get(CitadelCanvas.col(u, w));
				}
				if (src != null) {
					src.paste(c, cx + dx, cz + dz);
				}
			}
		}
	}

	/** The cells of one column, from {@link #Y_MIN} to {@link #Y_MAX}. */
	private record Column(Map<Integer, BlockState> states, Map<Integer, Integer> layers, Map<Integer, CompoundTag> data) {
		static Column of(CitadelCanvas c, int x, int z) {
			Map<Integer, BlockState> st = new HashMap<>();
			Map<Integer, Integer> ly = new HashMap<>();
			Map<Integer, CompoundTag> dt = new HashMap<>();
			for (int y = Y_MIN; y <= Y_MAX; y++) {
				long key = BlockPos.asLong(x, y, z);
				BlockState s = c.cells.get(key);
				if (s != null) {
					st.put(y, s);
					ly.put(y, c.layers.getOrDefault(key, CitadelCanvas.SOLID));
					CompoundTag t = c.data.get(key);
					if (t != null) {
						dt.put(y, t);
					}
				}
			}
			return new Column(st, ly, dt);
		}

		void paste(CitadelCanvas c, int x, int z) {
			for (int y = Y_MIN; y <= Y_MAX; y++) {
				long key = BlockPos.asLong(x, y, z);
				c.cells.remove(key);
				c.layers.remove(key);
				c.data.remove(key);
			}
			states.forEach((y, s) -> {
				c.put(x, y, z, s, layers.get(y));
				CompoundTag t = data.get(y);
				if (t != null) {
					c.data.put(BlockPos.asLong(x, y, z), t);
				}
			});
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
		// the screen keeps its 7 x 4; it stays flush in the (thicker) north wall, on the wall's inner face
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx - 3, 5, cz + realInner(-5, c.k)), Direction.EAST, Direction.SOUTH, 7, 4), "review", "", false);
		c.walkSpot(new BlockPos(cx, 1, cz + real(-2, c.k)), Direction.NORTH);
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
		CitadelHub.bigScreen(c, new HqGeometry.Surface(new BlockPos(cx + real(4, c.k), 2, cz - 2), Direction.SOUTH, Direction.WEST, 5, 3), "schedule", "", true);
		c.walkSpot(new BlockPos(cx + real(-1, c.k), 1, cz), Direction.EAST);
	}

	// ------------------------------------------------------------------ archive cloister

	/** Archive cloister: see {@link CloisterRoom}. */
	public static void cloister(CitadelCanvas c, int x, int z, int dx, int dz) {
		CloisterRoom.cell(c, x, z, dx, dz);
	}

	/**
	 * The fixtures of the cloister: the archive terminal, its anchors and a walk spot. The consultation bay round the
	 * terminal (niche, plaque, lantern, rug) is painted here, in real offsets after the magnification
	 * ({@link ArchiveBay}).
	 */
	public static void cloisterFixtures(CitadelCanvas c, Node nd) {
		int cx = nd.cx();
		int cz = nd.cz();
		ArchiveBay.build(c, nd);
		BlockPos t = new BlockPos(cx + ArchiveBay.terminalX(c.k), 1, cz + ArchiveBay.terminalZ(c.k));
		c.fixture(t, c.style.archiveTerminal(Direction.SOUTH));
		c.anchor(Anchor.face(AnchorNames.ARCHIVE_TERMINAL, t, Direction.SOUTH));
		c.anchor(Anchor.at(AnchorNames.ARCHIVE, t.south(), Direction.NORTH));
		c.walkSpot(new BlockPos(cx + real(-4, c.k), 1, cz), Direction.EAST);
	}

	// ------------------------------------------------------------------ lounge

	/** Lounge: see {@link LoungeRoom}. */
	public static void sunken(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		LoungeRoom.cell(c, x, z, dx, dz, d);
	}

	/** The fixtures of the lounge: a walk spot at the bottom of the amphitheatre. */
	public static void loungeFixtures(CitadelCanvas c, Node nd) {
		c.walkSpot(new BlockPos(nd.cx() + real(1, c.k), -2, nd.cz()), Direction.WEST);
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
