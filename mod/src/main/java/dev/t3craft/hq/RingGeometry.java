package dev.t3craft.hq;

import java.util.SortedSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The fixed geometry of layout/ring ("Ciudad en anillos") and its growth rule. Pure functions of
 * cell coordinates (HQ frame: origin = hub floor centre at ground level, entrance south {@code +z};
 * angles are measured from north, clockwise, so east is 90°).
 *
 * <pre>
 *   r ≤ 13   hub: domed octagon on a podium (floor y = {@value #PODIUM})
 *   r ≤ 21   sunken garden patio (ground level, y = 0), crossed by the north and south bridges (y = 4)
 *   r ≤ 28   the cloister: elevated gallery (floor y = 4) all the way round, open arcade on the patio
 *   r ≤ 36   ramps down the 8 radial avenues (half-block steps), garden terraces between them
 *   r ≤ 41   inner street
 *   ring m   sectors: band of {@value #DEPTH} blocks, 8·m wedge sectors split by radial avenues;
 *            then the ring street ({@value #STREET} blocks); special rooms on the nodes where the
 *            ring-1 radials cross the ring-1 street (tower, rotundas, sunken patio, plazas), obelisks
 *            on the outer streets' nodes
 * </pre>
 *
 * <h2>Growth</h2>
 * A module index encodes {@code floor · }{@value #FLOOR_STRIDE}{@code  + sector}. Sectors are
 * numbered ring by ring (ring 1: 0..7, ring 2: 8..23, ring 3: 24..47, ring 4: 48..79); inside a ring
 * the fixed order spreads them evenly (bit-reversed angular positions: 0, 4, 2, 6, 1, 5, 3, 7), so
 * four projects already make a pinwheel. {@link #place}: a new project takes the lowest free sector
 * (ring 2 opens when ring 1 is full, and freed sectors are refilled first); a project that outgrows
 * its 6 desks adds a floor to its sector (up to {@value #MAX_FLOORS} floors), then takes a free
 * contiguous sector of the same ring, then any free sector. The open rings are those up to the ring
 * of the highest sector in use, so nothing that exists ever moves.
 */
public final class RingGeometry {
	/** Podium level: floor block of the hub, the bridges and the cloister. */
	public static final int PODIUM = 4;
	/** Hub interior (octagonal metric) and its wall. */
	static final double HUB_IN = 12.0;
	static final double HUB_OUT = 13.2;
	/** Octagon shape factor: {@code o = max(|x|, |z|, (|x|+|z|)·K)}. */
	static final double OCT_K = 0.62;
	static final double PATIO_R = 21.0;
	static final double GALLERY_R = 28.0;
	static final double RAMP_R = 36.0;
	static final double INNER_STREET_R = 41.0;
	/** Radial depth of a ring of sectors. */
	public static final int DEPTH = 26;
	/** Width of a ring street. */
	public static final int STREET = 6;
	/** Half width of a radial avenue (7 blocks wide). */
	static final double AVENUE_HALF = 3.4;
	/** Floor-to-floor height of a sector building. */
	public static final int FLOOR_H = 7;
	public static final int MAX_FLOORS = 3;
	public static final int FLOOR_STRIDE = 100;
	public static final int MAX_RINGS = 4;
	/** Sectors of rings 1..{@value #MAX_RINGS}. */
	public static final int MAX_SECTORS = 4 * MAX_RINGS * (MAX_RINGS + 1);
	/** Lawn around the outermost street. */
	static final int LAWN = 10; // covers the node rooms and aprons that overhang the outer street
	/** Ring-1 node rooms: footprint radius and the paved apron cut out of the sectors around them. */
	static final double NODE_R = 7.0;
	static final double NODE_APRON = 9.5;
	static final double OBELISK_R = 2.5;
	static final double OBELISK_APRON = 6.0;
	/** Blocks of the sector desk grid: rows at {@code ri + 5, +10, +15}, desks {@value #DESK_T} blocks either side of the centre line. */
	static final int DESK_T = 5;
	/** Radius (from the band's inner edge) where a sector's stairs start (lane origin). */
	static final int STAIR_R = 16;

	private RingGeometry() {
	}

	// ------------------------------------------------------------------ rings and sectors

	public static int sectorsIn(int ring) {
		return 8 * ring;
	}

	/** First sector id of ring {@code ring} (1-based). */
	public static int base(int ring) {
		return 4 * ring * (ring - 1);
	}

	public static int ringOf(int sector) {
		int m = 1;
		while (sector >= base(m + 1)) {
			m++;
		}
		return m;
	}

	/** Inner radius of ring {@code m}'s band of sectors. */
	public static int ringInner(int m) {
		return (int) INNER_STREET_R + (m - 1) * (DEPTH + STREET);
	}

	public static int ringOuter(int m) {
		return ringInner(m) + DEPTH;
	}

	public static int streetOuter(int m) {
		return ringOuter(m) + STREET;
	}

	/** Radius of everything a plan with {@code rings} open rings uses (lawn included). */
	public static int radius(int rings) {
		return streetOuter(rings) + LAWN;
	}

	/** Open rings for a set of module indices: up to the ring of the highest sector (at least 1). */
	public static int rings(Iterable<Integer> moduleIndices) {
		int r = 1;
		for (int i : moduleIndices) {
			int s = sectorOf(i);
			if (s >= 0) {
				r = Math.max(r, ringOf(s));
			}
		}
		return r;
	}

	/** Sector of a module index, or -1 when it is outside the city (no such sector or floor). */
	public static int sectorOf(int moduleIndex) {
		int s = moduleIndex % FLOOR_STRIDE;
		return moduleIndex / FLOOR_STRIDE < MAX_FLOORS && s < MAX_SECTORS ? s : -1;
	}

	public static int floorOf(int moduleIndex) {
		return moduleIndex / FLOOR_STRIDE;
	}

	/** Angular position (0..n-1, clockwise from north) of a sector inside its ring. */
	static int position(int sector) {
		int m = ringOf(sector);
		int n = sectorsIn(m);
		int j = sector - base(m);
		return Integer.bitCount(n) == 1 ? Integer.reverse(j) >>> (32 - Integer.numberOfTrailingZeros(n)) : j;
	}

	/** The sector at angular position {@code p} of ring {@code m} (inverse of {@link #position}). */
	static int sectorAt(int m, int p) {
		int n = sectorsIn(m);
		int j = Integer.bitCount(n) == 1 ? Integer.reverse(p) >>> (32 - Integer.numberOfTrailingZeros(n)) : p;
		return base(m) + j;
	}

	/** Centre angle of a sector (radians). */
	static double centreAngle(int sector) {
		int n = sectorsIn(ringOf(sector));
		return (position(sector) + 0.5) * 2 * Math.PI / n;
	}

	// ------------------------------------------------------------------ growth rule

	/** {@link HqLayout.Placer} of the ring style (see the class comment). */
	static int place(String project, SortedSet<Integer> own, SortedSet<Integer> used) {
		if (!own.isEmpty()) {
			int s = sectorOf(own.first());
			if (s >= 0) {
				for (int f = 0; f < MAX_FLOORS; f++) {
					int i = f * FLOOR_STRIDE + s;
					if (!used.contains(i)) {
						return i;
					}
				}
				int m = ringOf(s);
				int n = sectorsIn(m);
				for (int d : new int[] {1, -1}) {
					int t = sectorAt(m, Math.floorMod(position(s) + d, n));
					if (free(t, used)) {
						return t;
					}
				}
			}
		}
		for (int s = 0; s < MAX_SECTORS; s++) {
			if (free(s, used)) {
				return s;
			}
		}
		// the city is full: an index outside it (no building; the generator skips it)
		return Math.max(MAX_FLOORS * FLOOR_STRIDE, used.isEmpty() ? 0 : used.last() + 1);
	}

	private static boolean free(int sector, SortedSet<Integer> used) {
		for (int f = 0; f < MAX_FLOORS; f++) {
			if (used.contains(f * FLOOR_STRIDE + sector)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ zones

	/** What a ground column is. */
	enum Kind {
		HUB, HUB_WALL, BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET, AVENUE, SECTOR, STREET, NODE, APRON, LAWN, NONE
	}

	/** Node room kinds (ring-1 street nodes by radial; obelisks further out). */
	enum NodeKind {
		REVIEW_TOWER, SCHEDULE_ROTUNDA, ARCHIVE_ROTUNDA, LOUNGE_SUNKEN, PLAZA, OBELISK
	}

	/**
	 * @param ring   band or street ring (0 for the core)
	 * @param sector sector id for {@link Kind#SECTOR}, else -1
	 * @param node   node: {@code q · 1000 + k} (street ring q, radial k) for NODE / APRON, else -1
	 */
	record Zone(Kind kind, int ring, int sector, int node) {
		static final Zone NONE = new Zone(Kind.NONE, 0, -1, -1);
	}

	/** A node's centre cell. */
	record Node(int q, int k, NodeKind kind, int cx, int cz) {
		int id() {
			return q * 1000 + k;
		}

		double footprint() {
			return q == 1 ? NODE_R : OBELISK_R;
		}

		double apron() {
			return q == 1 ? NODE_APRON : OBELISK_APRON;
		}

		boolean contains(int x, int z) {
			int dx = x - cx;
			int dz = z - cz;
			if (kind == NodeKind.REVIEW_TOWER) {
				return Math.abs(dx) <= 6 && Math.abs(dz) <= 6;
			}
			return Math.hypot(dx, dz) <= (kind == NodeKind.PLAZA ? 6.0 : footprint());
		}
	}

	static double r(int x, int z) {
		return Math.hypot(x, z);
	}

	/** Angle of a cell from north, clockwise, in [0, 2π). */
	static double theta(double x, double z) {
		double a = Math.atan2(x, -z);
		return a < 0 ? a + 2 * Math.PI : a;
	}

	static double octagon(int x, int z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) * OCT_K);
	}

	/** Perpendicular distance to the nearest of {@code n} radials (angles k·2π/n) and that radial's index. */
	static double radialDistance(int x, int z, int n) {
		double a = theta(x, z);
		int k = (int) Math.round(a * n / (2 * Math.PI)) % n;
		double b = k * 2 * Math.PI / n;
		return Math.abs(x * Math.cos(b) + z * Math.sin(b));
	}

	static int nearestRadial(int x, int z, int n) {
		return (int) Math.round(theta(x, z) * n / (2 * Math.PI)) % n;
	}

	static Node node(int q, int k) {
		int n = sectorsIn(q);
		double a = k * 2 * Math.PI / n;
		double rc = ringOuter(q) + STREET / 2.0;
		NodeKind kind = NodeKind.OBELISK;
		if (q == 1) {
			kind = switch (k) {
				case 0 -> NodeKind.REVIEW_TOWER;
				case 2 -> NodeKind.SCHEDULE_ROTUNDA;
				case 4 -> NodeKind.ARCHIVE_ROTUNDA;
				case 6 -> NodeKind.LOUNGE_SUNKEN;
				default -> NodeKind.PLAZA;
			};
		}
		return new Node(q, k, kind, (int) Math.round(rc * Math.sin(a)), (int) Math.round(-rc * Math.cos(a)));
	}

	/** The node whose apron (or footprint) holds the cell, among streets 1..rings, or null. */
	static Node nodeNear(int x, int z, int rings) {
		for (int q = 1; q <= rings; q++) {
			Node nd = node(q, nearestRadial(x, z, sectorsIn(q)));
			if (nd.contains(x, z) || Math.hypot(x - nd.cx(), z - nd.cz()) <= nd.apron()) {
				return nd;
			}
		}
		return null;
	}

	/** Every node of streets 1..rings. */
	static java.util.List<Node> nodes(int rings) {
		java.util.List<Node> out = new java.util.ArrayList<>();
		for (int q = 1; q <= rings; q++) {
			for (int k = 0; k < sectorsIn(q); k++) {
				out.add(node(q, k));
			}
		}
		return out;
	}

	/**
	 * The zone of a ground column with {@code rings} open rings. A sector's cells depend only on
	 * its own ring's geometry and the nodes of the streets on either side of it (both open whenever
	 * the ring is), never on how many rings are open: opening a ring never reshapes an older one.
	 */
	static Zone zone(int x, int z, int rings) {
		double o = octagon(x, z);
		if (o <= HUB_IN) {
			return new Zone(Kind.HUB, 0, -1, -1);
		}
		if (o <= HUB_OUT) {
			return new Zone(Kind.HUB_WALL, 0, -1, -1);
		}
		double r = r(x, z);
		if (Math.abs(x) <= 3 && r <= PATIO_R + 0.5) {
			return new Zone(Kind.BRIDGE, 0, -1, -1);
		}
		if (r <= PATIO_R) {
			return new Zone(Kind.PATIO, 0, -1, -1);
		}
		if (r <= GALLERY_R) {
			return new Zone(Kind.GALLERY, 0, -1, -1);
		}
		if (r <= RAMP_R) {
			return new Zone(radialDistance(x, z, 8) <= AVENUE_HALF ? Kind.RAMP : Kind.TERRACE, 0, -1, -1);
		}
		if (r <= INNER_STREET_R) {
			return new Zone(Kind.INNER_STREET, 0, -1, -1);
		}
		if (r > radius(rings)) {
			return Zone.NONE;
		}
		Node nd = nodeNear(x, z, rings);
		if (nd != null) {
			return new Zone(nd.contains(x, z) ? Kind.NODE : Kind.APRON, nd.q(), -1, nd.id());
		}
		for (int m = 1; m <= rings; m++) {
			if (r <= ringOuter(m)) {
				int n = sectorsIn(m);
				if (radialDistance(x, z, n) <= AVENUE_HALF) {
					return new Zone(Kind.AVENUE, m, -1, -1);
				}
				int p = (int) Math.floor(theta(x, z) * n / (2 * Math.PI)) % n;
				return new Zone(Kind.SECTOR, m, sectorAt(m, p), -1);
			}
			if (r <= streetOuter(m)) {
				return new Zone(Kind.STREET, m, -1, -1);
			}
		}
		return new Zone(Kind.LAWN, rings, -1, -1);
	}

	// ------------------------------------------------------------------ sector furniture

	/** The cardinal direction nearest an angle (from north, clockwise). */
	static Direction cardinal(double angle) {
		int i = Math.floorMod((int) Math.round(angle / (Math.PI / 2)), 4);
		return switch (i) {
			case 0 -> Direction.NORTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	/** The cell at polar {@code (radius, angle)} on level {@code y}. */
	static BlockPos polar(double radius, double angle, int y) {
		return new BlockPos((int) Math.round(radius * Math.sin(angle)), y, (int) Math.round(-radius * Math.cos(angle)));
	}

	/** Floor block level of floor {@code f} of a sector building. */
	static int floorY(int f) {
		return f * FLOOR_H;
	}

	/**
	 * One desk slot of a sector floor: the desk's middle cell (the screen's bottom-middle cell is
	 * above it) and the direction from the agent to the desk. Rows at {@code ri + 5, ri + 10,
	 * ri + 15}, two desks per row {@value #DESK_T} blocks either side of the centre line, agents on
	 * the hub side of their desk looking outwards (towards the nearest cardinal direction).
	 */
	record DeskSpot(BlockPos desk, Direction facing) {
		BlockPos stand() {
			return desk.relative(facing.getOpposite());
		}
	}

	static DeskSpot desk(int sector, int floor, int slot) {
		int ri = ringInner(ringOf(sector));
		double rr = ri + 5 + 5 * (slot / 2);
		double t = (slot % 2 == 0 ? -1 : 1) * DESK_T;
		double a = centreAngle(sector) + t / rr;
		return new DeskSpot(polar(rr, a, floorY(floor) + 1), cardinal(a));
	}

	/**
	 * The switchback stairs of a sector: lane cells in the stairs' own frame (u along {@code F}, the
	 * cardinal nearest the outward radial at the sector's centre; v along {@code F}'s clockwise
	 * neighbour), around the origin cell {@link #STAIR_R} blocks into the band on the centre line.
	 * Floor f → f+1 climbs lane A (v = -2, -1) outwards when f is even, lane B (v = 0, 1) inwards
	 * when odd: 7 stair blocks (the last one in the upper slab), a landing after it.
	 */
	record Stairs(BlockPos origin, Direction f, Direction right) {
		BlockPos cell(int u, int v, int y) {
			return origin.relative(f, u).relative(right, v).atY(y);
		}

		/** Lane offsets of the run from floor {@code from}. */
		int[] lane(int from) {
			return from % 2 == 0 ? new int[] {-2, -1} : new int[] {0, 1};
		}

		/** The u of step {@code i} (0..6) of the run from floor {@code from}. */
		int stepU(int from, int i) {
			return from % 2 == 0 ? i : 6 - i;
		}

		/** Direction the run climbs towards. */
		Direction climbs(int from) {
			return from % 2 == 0 ? f : f.getOpposite();
		}
	}

	static Stairs stairs(int sector) {
		int ri = ringInner(ringOf(sector));
		double a = centreAngle(sector);
		Direction f = cardinal(a);
		return new Stairs(polar(ri + STAIR_R, a, 0), f, f.getClockWise());
	}

	// ------------------------------------------------------------------ public views (renders, docs)

	/** Bounding box (xz, ground to the highest roof) of a sector's wedge. */
	public static Box sectorBounds(int sector) {
		int m = ringOf(sector);
		double a = centreAngle(sector);
		double half = Math.PI / sectorsIn(m);
		int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
		for (int i = 0; i <= 8; i++) {
			double aa = a - half + i * half / 4;
			for (double rr : new double[] {ringInner(m), ringOuter(m)}) {
				int x = (int) Math.round(rr * Math.sin(aa));
				int z = (int) Math.round(-rr * Math.cos(aa));
				x1 = Math.min(x1, x);
				z1 = Math.min(z1, z);
				x2 = Math.max(x2, x);
				z2 = Math.max(z2, z);
			}
		}
		return Box.of(x1, 0, z1, x2, floorY(MAX_FLOORS) + 6, z2);
	}

	/** Centre cell (x, z) of a ring-1 node room: 0 review tower (N), 2 scheduled rotunda (E), 4 archive (S), 6 sunken lounge (W). */
	public static int[] nodeCentre(int k) {
		Node n = node(1, k);
		return new int[] {n.cx(), n.cz()};
	}
}
