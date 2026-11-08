package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Rotation;

/**
 * Fixed geometry of the <b>campus</b> layout ({@link CampusStyle}): a hexagonal lattice of plots
 * around a central plaza, relative to the HQ origin (hub floor centre, north = -z).
 *
 * <pre>
 *            barrio   barrio                 hex cell (q, r): centre x = 72 q + 36 r, z = 63 r
 *        barrio  [archive]  barrio           vertices of a cell: (±36, ±21), (0, ±42)
 *     [sched.]   ( HUB  )   [review]         edges between vertices = the path network
 *     [lounge]   plaza      [errors]         the six vertices around the hub hold the pavilions
 *        barrio   [gate]   barrio            (S = entrance gate with the spawn)
 * </pre>
 *
 * <ul>
 *   <li><b>Cells.</b> The centre cell is the hub (building + plaza). Every other cell is a
 *   <b>parcel</b>, numbered in a fixed spiral order ({@link #parcel(int)}): ring by ring, and in each
 *   ring the six corners first in the order E, W, NE, SW, NW, SE, then the cells between them with
 *   the same alternation, so a half-full ring stays balanced. A parcel's {@link #AREA} square
 *   ({@code 49 × 49}) holds one project's barrio.</li>
 *   <li><b>Vertices.</b> Three cells meet at each vertex. Paths run along the cell edges (vertex to
 *   vertex), with a small plaza ({@link #PLAZA}) at every vertex they reach. The six vertices of the
 *   hub cell hold the special pavilions ({@link #PAVILION} plots).</li>
 *   <li><b>Gaps.</b> Between a parcel's square and the edge paths there is a band of 9 to 12 cells
 *   (gardens, trees, ponds with bridges, pergolas) that no plot ever uses.</li>
 * </ul>
 */
final class CampusGeometry {
	/** x step of the lattice per q. */
	static final int PITCH = 72;
	/** z step of the lattice per r. */
	static final int ROW = 63;
	/** Vertex offsets from a cell centre: (±VX, ±VZ) and (0, ±2·VZ). */
	static final int VX = 36;
	static final int VZ = 21;
	/** Half size of a parcel's (and the hub's) square area. */
	static final int AREA = 24;
	/** Half size of a pavilion plot at a hub vertex. */
	static final int PAVILION = 10;
	/** Half size of a junction plaza at a vertex. */
	static final int PLAZA = 4;
	/** Half width of a path (3 wide). */
	static final int PATH = 1;
	/** Highest y any campus block uses (the scheduled tasks tower). */
	static final int TOP = 30;
	/** Module index = parcel × SLOTS + building ({@link CampusLayout}). */
	static final int SLOTS = 8;

	private CampusGeometry() {
	}

	/** A lattice cell in axial coordinates. */
	record Hex(int q, int r) {
		int x() {
			return PITCH * q + VX * r;
		}

		int z() {
			return ROW * r;
		}

		int ring() {
			return (Math.abs(q) + Math.abs(r) + Math.abs(q + r)) / 2;
		}

		/** Vertex {@code i} (0..5, clockwise from the east-north one): (36,-21), (0,-42), (-36,-21), (-36,21), (0,42), (36,21). */
		Vertex vertex(int i) {
			int[][] o = {{VX, -VZ}, {0, -2 * VZ}, {-VX, -VZ}, {-VX, VZ}, {0, 2 * VZ}, {VX, VZ}};
			return new Vertex(x() + o[i][0], z() + o[i][1]);
		}

		Area area() {
			return new Area(x() - AREA, z() - AREA, x() + AREA, z() + AREA);
		}
	}

	/** A lattice vertex (world xz). */
	record Vertex(int x, int z) implements Comparable<Vertex> {
		@Override
		public int compareTo(Vertex o) {
			return x != o.x ? Integer.compare(x, o.x) : Integer.compare(z, o.z);
		}
	}

	/** An edge between two vertices, normalised ({@code a < b}). */
	record Edge(Vertex a, Vertex b) {
		static Edge of(Vertex p, Vertex q) {
			return p.compareTo(q) <= 0 ? new Edge(p, q) : new Edge(q, p);
		}

		boolean vertical() {
			return a.x() == b.x();
		}

		int midX() {
			return (a.x() + b.x()) / 2;
		}

		int midZ() {
			return (a.z() + b.z()) / 2;
		}

		/** Stable small hash (decorations: pond, pergola, trees). */
		int hash() {
			int h = a.x() * 73856093 ^ a.z() * 19349663 ^ b.x() * 83492791 ^ b.z() * 2654435;
			return Math.floorMod(h ^ (h >>> 13), 1 << 16);
		}
	}

	/** An inclusive xz rectangle. */
	record Area(int minX, int minZ, int maxX, int maxZ) {
		boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		Area grow(int d) {
			return new Area(minX - d, minZ - d, maxX + d, maxZ + d);
		}

		Area union(Area o) {
			return new Area(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
		}
	}

	static final Hex HUB = new Hex(0, 0);

	/** The special pavilions, on the six vertices of the hub cell. */
	enum Pavilion {
		REVIEW(0, "review"),
		ARCHIVE(1, "archive"),
		SCHEDULED(2, "scheduled"),
		LOUNGE(3, "lounge"),
		GATE(4, "gate"),
		ERRORS(5, "errors");

		final int vertex;
		final String key;

		Pavilion(int vertex, String key) {
			this.vertex = vertex;
			this.key = key;
		}

		Vertex at() {
			return HUB.vertex(vertex);
		}

		Area plot() {
			Vertex v = at();
			return new Area(v.x() - PAVILION, v.z() - PAVILION, v.x() + PAVILION, v.z() + PAVILION);
		}

		static Pavilion at(Vertex v) {
			for (Pavilion p : values()) {
				if (p.at().equals(v)) {
					return p;
				}
			}
			return null;
		}
	}

	// ------------------------------------------------------------------ the spiral

	private static final int[][] CORNER_DIR = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
	/** Walking directions between corners: E→NE, NE→NW, NW→W, W→SW, SW→SE, SE→E. */
	private static final int[][] WALK = {{0, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 0}, {1, -1}};
	/** Corner order inside a ring: E, W, NE, SW, NW, SE (opposite sides alternate). */
	private static final int[] CORNERS = {0, 3, 1, 4, 2, 5};
	private static final List<Hex> SPIRAL = new ArrayList<>();

	/** Cells of ring {@code k} counter-clockwise from the east corner: index {@code side * k + step}. */
	static List<Hex> ring(int k) {
		List<Hex> out = new ArrayList<>(6 * k);
		for (int side = 0; side < 6; side++) {
			int q = CORNER_DIR[side][0] * k;
			int r = CORNER_DIR[side][1] * k;
			for (int step = 0; step < k; step++) {
				out.add(new Hex(q + WALK[side][0] * step, r + WALK[side][1] * step));
			}
		}
		return out;
	}

	/** Ring {@code k} in parcel order (corners alternating, then the in-between cells the same way). */
	static List<Hex> ringOrder(int k) {
		List<Hex> cells = ring(k);
		List<Hex> out = new ArrayList<>(cells.size());
		for (int step = 0; step < k; step++) {
			for (int side : CORNERS) {
				out.add(cells.get(side * k + step));
			}
		}
		return out;
	}

	/** The cell of parcel {@code p} ({@code p >= 0}). */
	static synchronized Hex parcel(int p) {
		if (p < 0) {
			throw new IllegalArgumentException("parcel " + p);
		}
		int k = SPIRAL.isEmpty() ? 1 : SPIRAL.get(SPIRAL.size() - 1).ring() + 1;
		while (SPIRAL.size() <= p) {
			SPIRAL.addAll(ringOrder(k++));
		}
		return SPIRAL.get(p);
	}

	/** Parcels in rings 1..k: {@code 3k(k+1)}. */
	static int parcelsWithin(int k) {
		return 3 * k * (k + 1);
	}

	/** The ring parcel {@code p} lies in. */
	static int ringOf(int p) {
		return parcel(p).ring();
	}

	// ------------------------------------------------------------------ barrio frames

	/** Rotation of barrio {@code p}'s plan (its commons turns around the patio from parcel to parcel). */
	static Rotation barrioRotation(int p) {
		return Rotation.values()[Math.floorMod(p * 3 + 1, 4)];
	}

	/** Every vertex a cell touches. */
	static List<Vertex> vertices(Hex h) {
		List<Vertex> out = new ArrayList<>(6);
		for (int i = 0; i < 6; i++) {
			out.add(h.vertex(i));
		}
		return out;
	}

	static List<Edge> edges(Hex h) {
		List<Edge> out = new ArrayList<>(6);
		for (int i = 0; i < 6; i++) {
			out.add(Edge.of(h.vertex(i), h.vertex((i + 1) % 6)));
		}
		return out;
	}
}
