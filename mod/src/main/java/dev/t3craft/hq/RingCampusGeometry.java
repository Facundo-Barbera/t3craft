package dev.t3craft.hq;

import java.util.SortedSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The fixed geometry of the hybrid layout {@value #ID} ("Ciudadela en anillos con barrios") and its
 * growth rule. Pure functions of cell coordinates (HQ frame: origin = hub floor centre at ground
 * level, entrance south {@code +z}; angles from north, clockwise, so east is 90°).
 *
 * Sizes below are at scale 1.0; the plan uses them times {@link #scale()} ({@link Dims}: lengths ·k,
 * heights ·√k, default k = 1.0, pack parameter {@code scale}).
 *
 * <pre>
 *   o ≤ 13      hub: a basilica on a podium (floor y = 4), nave vault 12 → 17 high
 *   r ≤ 21      sunken garden patio (ground level), crossed by the north and south bridges (y = 4)
 *   r ≤ 27      the elevated ring: an open promenade (floor y = 4) all the way round
 *   r ≤ 35      ramps down the 8 radial avenues (half-block steps), garden terraces between them
 *   r ≤ 40      inner street
 *   ring m      a band of barrio lots (8, 12, 16 per ring) split by radial avenues, then the ring
 *               street; special rooms on the four cardinal nodes of the ring-1 street (review tower,
 *               scheduled-tasks rotunda, archive cloister, sunken lounge), light pillars on the
 *               diagonal ones
 * </pre>
 *
 * <h2>Barrios</h2>
 * Each lot holds one project's barrio: a paved courtyard round a fountain or a cherry tree, a
 * gatehouse on the side facing the hub (the project's sign hangs under its arch) and up to three
 * work halls (6 standing desks each) round the courtyard, every hall able to take an upper floor.
 * The cluster sits in an axis-aligned frame snapped to the lot's outward cardinal, inside a disc
 * that always fits its wedge (checked by the tests for every lot of every ring). Ring 2 and 3
 * barrios are larger (wider and deeper halls, bigger courtyard and gatehouse).
 *
 * <h2>Growth</h2>
 * A module index encodes {@code lot · }{@value #LOT_STRIDE}{@code  + b}: {@code b} 0..2 are the
 * ground floors of halls 0..2, {@code b} 3..5 their upper floors. Lots are numbered ring by ring in
 * fill order (ring 1: 0..7, ring 2: 8..19, ring 3: 20..35); inside a ring the order spreads them
 * evenly round the circle. {@link #place}: a new project takes the lowest free lot (so ring 1 fills,
 * then ring 2 opens, and freed lots are refilled first); a project that outgrows its desks densifies
 * its own barrio in the order hall 0, hall 1, upper floor on hall 0, hall 2, upper floors on halls 1
 * and 2 (36 desks), never another lot. The open rings are those up to the ring of the highest lot in
 * use, so nothing that exists ever moves.
 */
public final class RingCampusGeometry {
	/** Layout and style id. */
	public static final String ID = "hybrid-ringcampus";

	// Base values at scale 1.0 (the original citadel). Every size the plan uses comes from {@link Dims}.
	private static final int PODIUM = 4;
	private static final double HUB_IN = 12.0;
	/** Thickness of the hub's wall ring (not scaled: a wall stays one block thick). */
	private static final double HUB_WALL = 1.2;
	static final double OCT_K = 0.62;
	private static final double PATIO_R = 21.0;
	private static final double GALLERY_R = 27.0;
	private static final double RAMP_R = 35.0;
	private static final double INNER_STREET_R = 40.0;
	private static final int STREET = 6;
	private static final double AVENUE_HALF = 3.4;
	/** Lawn round the outermost street (covers the ring-1 node rooms when only ring 1 is open). */
	private static final int LAWN = 8;
	public static final int MAX_RINGS = 3;
	public static final int LOT_STRIDE = 8;
	/** Modules per lot: three halls, each with an upper floor. */
	public static final int LOT_MODULES = 6;
	/** Halls of a barrio. */
	public static final int HALLS = 3;
	/** Floor-to-floor height of a hall at scale 1 ({@link Dims#floorH} is the one in use). */
	private static final int FLOOR_H = 6;

	/** Lots of ring m (index m; 0 unused). */
	private static final int[] LOTS = {0, 8, 12, 16};
	/** Inner radius of ring 1's band of lots (the next bands start after the previous ring's street). */
	private static final int INNER_1 = 40;
	/** Radial depth of ring m's band. */
	private static final int[] DEPTH = {0, 39, 44, 46};
	/** Radius of ring m's lot centres, from the band's inner radius. */
	private static final int[] CENTRE_OFF = {0, 19, 22, 23};
	/** Half side of ring m's courtyard. */
	private static final int[] COURT = {0, 5, 6, 7};
	/** Interior width and depth of ring m's halls. */
	private static final int[] HALL_W = {0, 9, 11, 11};
	private static final int[] HALL_D = {0, 10, 12, 12};
	/** Gatehouse width and depth. */
	private static final int[] GATE_W = {0, 7, 9, 9};
	private static final int[] GATE_D = {0, 5, 7, 7};
	/** Fill order inside each ring: lot j of the ring sits at angular position ORDER[m][j]. */
	private static final int[][] ORDER = {
			{},
			{0, 4, 2, 6, 1, 5, 3, 7},
			{0, 6, 3, 9, 1, 7, 4, 10, 2, 8, 5, 11},
			{0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15}};
	public static final int MAX_LOTS = 8 + 12 + 16;
	/** Order in which a project takes the modules of its barrio (see the class comment). */
	static final int[] MODULE_ORDER = {0, 1, 3, 2, 4, 5};

	// ------------------------------------------------------------------ scale

	/** A length {@code base} at scale {@code k}, rounded half away from zero (so ±base stay symmetric). */
	public static int s(double k, int base) {
		return base < 0 ? -s(k, -base) : (int) Math.round(base * k);
	}

	/** A real length (radius, threshold) at scale {@code k}. */
	public static double sd(double k, double base) {
		return base * k;
	}

	/** A height at scale {@code k}: heights grow by {@code √k} so floors and ceilings stay playable. */
	public static int sy(double k, int base) {
		return base < 0 ? -sy(k, -base) : (int) Math.round(base * Math.sqrt(k));
	}

	/** An odd width (a room with a centre line) at scale {@code k}: the odd number nearest {@code base · k}. */
	public static int sOdd(double k, int base) {
		return 2 * (int) Math.round((base * k - 1) / 2) + 1;
	}

	/** The scale in use: {@code LayoutParams.RINGCAMPUS_SCALE} (pack-editable, 1.0 to 2.5, default 1.0). */
	public static double scale() {
		return dev.t3craft.hq.pack.LayoutParams.RINGCAMPUS_SCALE.value();
	}

	private static volatile @Nullable Dims cached;

	/** The dimensions at the scale in use (cached per scale value). */
	public static Dims dims() {
		double k = scale();
		Dims d = cached;
		if (d == null || d.k != k) {
			d = new Dims(k);
			cached = d;
		}
		return d;
	}

	/**
	 * Every size of the citadel at one scale {@code k}. Lengths are {@code base · k} (rounded), heights
	 * {@code base · √k}; at {@code k = 1} every value is the original one. Not scaled: wall thickness, the
	 * one-block steps of ramps and stairs, door heights, desks and their 3×2 screens.
	 */
	public static final class Dims {
		public final double k;
		/** Podium level: floor block of the hub, the bridges and the elevated ring. */
		public final int podium;
		public final double hubIn;
		public final double hubOut;
		public final double patioR;
		public final double galleryR;
		public final double rampR;
		public final double innerStreetR;
		public final int street;
		public final double avenueHalf;
		public final int lawn;
		/** Half width of the north and south bridges (railings on {@code |x| = bridgeHalf}). */
		public final int bridgeHalf;
		/** Floor-to-floor height of a hall. */
		public final int floorH;
		/** Radial half band round the ring-1 street where the node rooms are looked for. */
		public final int nodeBand;
		/** Desk slots: offset of slots 0/1 from the centre line, depth of slots 2/3 and 4/5 from the back wall. */
		public final int deskSide;
		public final int deskMid;
		public final int deskFront;
		final int[] inner = new int[MAX_RINGS + 1];
		final int[] depth = new int[MAX_RINGS + 1];
		final int[] centre = new int[MAX_RINGS + 1];
		final int[] court = new int[MAX_RINGS + 1];
		final int[] hallW = new int[MAX_RINGS + 1];
		final int[] hallD = new int[MAX_RINGS + 1];
		final int[] gateW = new int[MAX_RINGS + 1];
		final int[] gateD = new int[MAX_RINGS + 1];

		Dims(double k) {
			this.k = k;
			podium = RingCampusGeometry.sy(k, PODIUM);
			hubIn = RingCampusGeometry.sd(k, HUB_IN);
			hubOut = hubIn + HUB_WALL;
			patioR = RingCampusGeometry.sd(k, PATIO_R);
			galleryR = RingCampusGeometry.sd(k, GALLERY_R);
			rampR = RingCampusGeometry.sd(k, RAMP_R);
			innerStreetR = RingCampusGeometry.sd(k, INNER_STREET_R);
			street = RingCampusGeometry.s(k, STREET);
			avenueHalf = RingCampusGeometry.sd(k, AVENUE_HALF);
			lawn = RingCampusGeometry.s(k, LAWN);
			bridgeHalf = RingCampusGeometry.s(k, 3);
			floorH = RingCampusGeometry.sy(k, FLOOR_H);
			nodeBand = RingCampusGeometry.s(k, 11);
			deskSide = RingCampusGeometry.s(k, 2);
			deskMid = RingCampusGeometry.s(k, 3);
			deskFront = RingCampusGeometry.s(k, 7);
			int in = RingCampusGeometry.s(k, INNER_1);
			for (int m = 1; m <= MAX_RINGS; m++) {
				inner[m] = in;
				depth[m] = RingCampusGeometry.s(k, DEPTH[m]);
				centre[m] = in + RingCampusGeometry.s(k, CENTRE_OFF[m]);
				court[m] = RingCampusGeometry.s(k, COURT[m]);
				hallW[m] = RingCampusGeometry.sOdd(k, HALL_W[m]);
				hallD[m] = RingCampusGeometry.s(k, HALL_D[m]);
				gateW[m] = RingCampusGeometry.sOdd(k, GATE_W[m]);
				gateD[m] = RingCampusGeometry.s(k, GATE_D[m]);
				in += depth[m] + street;
			}
		}

		/** A length at this scale ({@link RingCampusGeometry#s}). */
		public int s(int base) {
			return RingCampusGeometry.s(k, base);
		}

		/** A real length at this scale. */
		public double sd(double base) {
			return RingCampusGeometry.sd(k, base);
		}

		/** A height at this scale ({@code √k}). */
		public int sy(int base) {
			return RingCampusGeometry.sy(k, base);
		}

		/** The last interior column of the hub along an axis ({@code x = ±hubX} or {@code z = ±hubX}). */
		public int hubX() {
			return (int) Math.floor(hubIn);
		}

		/** Where NPCs appear: the inner street, south, in front of the main ramp. */
		public BlockPos spawn() {
			return new BlockPos(0, 1, (int) Math.round((rampR + innerStreetR) / 2));
		}
	}

	private RingCampusGeometry() {
	}

	// ------------------------------------------------------------------ rings and lots

	public static int lotsIn(int ring) {
		return LOTS[ring];
	}

	/** First lot id of ring {@code ring} (1-based). */
	public static int base(int ring) {
		return 2 * (ring - 1) * (ring + 2);
	}

	public static int ringOfLot(int lot) {
		int m = 1;
		while (m < MAX_RINGS && lot >= base(m + 1)) {
			m++;
		}
		return m;
	}

	public static int ringInner(int m) {
		return dims().inner[m];
	}

	public static int ringOuter(int m) {
		Dims d = dims();
		return d.inner[m] + d.depth[m];
	}

	public static int streetOuter(int m) {
		return ringOuter(m) + dims().street;
	}

	/** Radius of everything a plan with {@code rings} open rings uses (lawn included). */
	public static int radius(int rings) {
		return streetOuter(rings) + dims().lawn;
	}

	/** Where NPCs appear (see {@link Dims#spawn}). */
	public static BlockPos spawn() {
		return dims().spawn();
	}

	/** Lot of a module index, or -1 when it is outside the citadel. */
	public static int lotOf(int moduleIndex) {
		int lot = moduleIndex / LOT_STRIDE;
		return moduleIndex >= 0 && lot < MAX_LOTS && moduleIndex % LOT_STRIDE < LOT_MODULES ? lot : -1;
	}

	/** Hall (0..2) of a module index. */
	public static int hallOf(int moduleIndex) {
		return moduleIndex % LOT_STRIDE % HALLS;
	}

	/** Floor (0 ground, 1 upper) of a module index. */
	public static int floorOf(int moduleIndex) {
		return moduleIndex % LOT_STRIDE / HALLS;
	}

	public static int moduleIndex(int lot, int hall, int floor) {
		return lot * LOT_STRIDE + floor * HALLS + hall;
	}

	/** Open rings for a set of module indices: up to the ring of the highest lot in use (at least 1). */
	public static int rings(Iterable<Integer> moduleIndices) {
		int r = 1;
		for (int i : moduleIndices) {
			int lot = lotOf(i);
			if (lot >= 0) {
				r = Math.max(r, ringOfLot(lot));
			}
		}
		return r;
	}

	/** Angular position (0..n-1, clockwise from north) of a lot inside its ring. */
	static int position(int lot) {
		int m = ringOfLot(lot);
		return ORDER[m][lot - base(m)];
	}

	/** The lot at angular position {@code p} of ring {@code m} (inverse of {@link #position}). */
	static int lotAt(int m, int p) {
		int[] o = ORDER[m];
		for (int j = 0; j < o.length; j++) {
			if (o[j] == p) {
				return base(m) + j;
			}
		}
		throw new IllegalArgumentException("position " + p + " of ring " + m);
	}

	/** Centre angle of a lot (radians). */
	static double centreAngle(int lot) {
		return (position(lot) + 0.5) * 2 * Math.PI / lotsIn(ringOfLot(lot));
	}

	// ------------------------------------------------------------------ growth rule

	/** {@link HqLayout.Placer} of the hybrid (see the class comment). */
	static int place(String project, SortedSet<Integer> own, SortedSet<Integer> used) {
		if (!own.isEmpty()) {
			int lot = lotOf(own.first());
			if (lot >= 0) {
				for (int b : MODULE_ORDER) {
					int i = lot * LOT_STRIDE + b;
					if (!used.contains(i)) {
						return i;
					}
				}
			}
			return overflow(used);
		}
		for (int lot = 0; lot < MAX_LOTS; lot++) {
			if (used.subSet(lot * LOT_STRIDE, (lot + 1) * LOT_STRIDE).isEmpty()) {
				return lot * LOT_STRIDE;
			}
		}
		return overflow(used);
	}

	/** An index outside the citadel (no building; the generator skips it). */
	private static int overflow(SortedSet<Integer> used) {
		return Math.max(MAX_LOTS * LOT_STRIDE, used.isEmpty() ? 0 : used.last() + 1);
	}

	// ------------------------------------------------------------------ zones

	public enum Kind {
		HUB, HUB_WALL, BRIDGE, PATIO, GALLERY, RAMP, TERRACE, INNER_STREET, AVENUE, LOT, STREET, NODE, LAWN, NONE
	}

	public enum NodeKind {
		REVIEW_TOWER, SCHEDULE_ROTUNDA, ARCHIVE_CLOISTER, LOUNGE_SUNKEN, PILLAR
	}

	/**
	 * @param ring band or street ring (0 for the core)
	 * @param lot  lot id for {@link Kind#LOT}, else -1
	 * @param node radial index k (0..7) of a ring-1 street node for {@link Kind#NODE}, else -1
	 */
	public record Zone(Kind kind, int ring, int lot, int node) {
		public static final Zone NONE = new Zone(Kind.NONE, 0, -1, -1);
	}

	/** Radius of the ring-1 street's centre line, where the node rooms stand. */
	public static double nodeRadius() {
		return ringOuter(1) + dims().street / 2.0;
	}

	/**
	 * A ring-1 node room: centre {@code (cx, cz)} and {@code size}, the half side of a square room
	 * (tower 6, cloister 7 at scale 1) or the radius of a round one (rotunda and lounge 7, pillar 2.5).
	 */
	public record Node(int k, NodeKind kind, int cx, int cz, double size) {
		/** Half side of a square room, the ring of its wall ({@code size} rounded). */
		public int half() {
			return (int) Math.round(size);
		}

		public boolean contains(int x, int z) {
			int dx = Math.abs(x - cx);
			int dz = Math.abs(z - cz);
			return switch (kind) {
				case REVIEW_TOWER, ARCHIVE_CLOISTER -> dx <= half() && dz <= half();
				case SCHEDULE_ROTUNDA, LOUNGE_SUNKEN, PILLAR -> Math.hypot(dx, dz) <= size;
			};
		}
	}

	public static Node node(int k) {
		double a = k * Math.PI / 4;
		double rc = nodeRadius();
		NodeKind kind = switch (k) {
			case 0 -> NodeKind.REVIEW_TOWER;
			case 2 -> NodeKind.SCHEDULE_ROTUNDA;
			case 4 -> NodeKind.ARCHIVE_CLOISTER;
			case 6 -> NodeKind.LOUNGE_SUNKEN;
			default -> NodeKind.PILLAR;
		};
		double base = switch (kind) {
			case REVIEW_TOWER -> 6;
			case ARCHIVE_CLOISTER, SCHEDULE_ROTUNDA, LOUNGE_SUNKEN -> 7;
			case PILLAR -> 2.5;
		};
		return new Node(k, kind, (int) Math.round(rc * Math.sin(a)), (int) Math.round(-rc * Math.cos(a)), dims().sd(base));
	}

	public static double r(int x, int z) {
		return Math.hypot(x, z);
	}

	/** Angle of a cell from north, clockwise, in [0, 2π). */
	public static double theta(double x, double z) {
		double a = Math.atan2(x, -z);
		return a < 0 ? a + 2 * Math.PI : a;
	}

	public static double octagon(int x, int z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) * OCT_K);
	}

	/** Perpendicular distance to the nearest of {@code n} radials (angles k·2π/n). */
	public static double radialDistance(int x, int z, int n) {
		double a = theta(x, z);
		int k = (int) Math.round(a * n / (2 * Math.PI)) % n;
		double b = k * 2 * Math.PI / n;
		return Math.abs(x * Math.cos(b) + z * Math.sin(b));
	}

	/**
	 * The zone of a ground column with {@code rings} open rings. A lot's cells depend only on its own
	 * ring and the ring-1 nodes, never on how many rings are open.
	 */
	public static Zone zone(int x, int z, int rings) {
		Dims d = dims();
		double o = octagon(x, z);
		if (o <= d.hubIn) {
			return new Zone(Kind.HUB, 0, -1, -1);
		}
		if (o <= d.hubOut) {
			return new Zone(Kind.HUB_WALL, 0, -1, -1);
		}
		double r = r(x, z);
		if (Math.abs(x) <= d.bridgeHalf && r <= d.patioR + 0.5) {
			return new Zone(Kind.BRIDGE, 0, -1, -1);
		}
		if (r <= d.patioR) {
			return new Zone(Kind.PATIO, 0, -1, -1);
		}
		if (r <= d.galleryR) {
			return new Zone(Kind.GALLERY, 0, -1, -1);
		}
		if (r <= d.rampR) {
			return new Zone(radialDistance(x, z, 8) <= d.avenueHalf ? Kind.RAMP : Kind.TERRACE, 0, -1, -1);
		}
		if (r <= d.innerStreetR) {
			return new Zone(Kind.INNER_STREET, 0, -1, -1);
		}
		if (r > radius(rings)) {
			return Zone.NONE;
		}
		if (Math.abs(r - nodeRadius()) <= d.nodeBand) {
			int k = (int) Math.round(theta(x, z) * 4 / Math.PI) % 8;
			if (node(k).contains(x, z)) {
				return new Zone(Kind.NODE, 1, -1, k);
			}
		}
		for (int m = 1; m <= rings; m++) {
			if (r <= ringOuter(m)) {
				int n = lotsIn(m);
				if (radialDistance(x, z, n) <= d.avenueHalf) {
					return new Zone(Kind.AVENUE, m, -1, -1);
				}
				int p = (int) Math.floor(theta(x, z) * n / (2 * Math.PI)) % n;
				return new Zone(Kind.LOT, m, lotAt(m, p), -1);
			}
			if (r <= streetOuter(m)) {
				return new Zone(Kind.STREET, m, -1, -1);
			}
		}
		return new Zone(Kind.LAWN, rings, -1, -1);
	}

	// ------------------------------------------------------------------ barrio frames

	/** The cardinal direction nearest an angle (from north, clockwise). */
	public static Direction cardinal(double angle) {
		int i = Math.floorMod((int) Math.round(angle / (Math.PI / 2)), 4);
		return switch (i) {
			case 0 -> Direction.NORTH;
			case 1 -> Direction.EAST;
			case 2 -> Direction.SOUTH;
			default -> Direction.WEST;
		};
	}

	public static BlockPos polar(double radius, double angle, int y) {
		return new BlockPos((int) Math.round(radius * Math.sin(angle)), y, (int) Math.round(-radius * Math.cos(angle)));
	}

	/**
	 * A barrio's frame: centre cell, {@code out} (cardinal nearest the outward radial) and
	 * {@code across} ({@code out}'s clockwise neighbour), the courtyard's half side and the sizes of
	 * its halls and gatehouse.
	 */
	public record Lot(int id, int ring, BlockPos centre, Direction out, Direction across, int court, int hallW, int hallD, int gateW, int gateD) {
		/** Coordinate of a cell along {@code out}. */
		public int a(int x, int z) {
			return (x - centre.getX()) * out.getStepX() + (z - centre.getZ()) * out.getStepZ();
		}

		/** Coordinate of a cell along {@code across}. */
		public int b(int x, int z) {
			return (x - centre.getX()) * across.getStepX() + (z - centre.getZ()) * across.getStepZ();
		}

		public BlockPos at(int a, int b, int y) {
			return centre.relative(out, a).relative(across, b).atY(y);
		}

		/** Half side of the paved cluster square (courtyard, halls, gatehouse). */
		public int reach() {
			return court + 2 + hallD;
		}

		/** Hall {@code h} (0 across-left, 1 across-right, 2 outer). */
		public Room hall(int h) {
			Direction back = switch (h) {
				case 0 -> across.getOpposite();
				case 1 -> across;
				default -> out;
			};
			Direction side = back.getClockWise();
			int c = (hallW - 1) / 2;
			BlockPos origin = centre.relative(back, court + 2).relative(side, -c);
			return new Room(origin, back, side, hallW, hallD);
		}

		/** The gatehouse, its depth running towards the hub side ({@code -out}). */
		public Room gate() {
			Direction back = out.getOpposite();
			Direction side = back.getClockWise();
			int c = (gateW - 1) / 2;
			return new Room(centre.relative(back, court + 1).relative(side, -c), back, side, gateW, gateD);
		}
	}

	/**
	 * A rectangular building in its own frame: interior cells {@code u ∈ [0, d)}, {@code v ∈ [0, w)}
	 * from {@code origin} along {@code back} (door wall at {@code u = -1}, back wall at {@code u = d})
	 * and {@code side} (side walls at {@code v = -1} and {@code v = w}).
	 */
	public record Room(BlockPos origin, Direction back, Direction side, int w, int d) {
		public BlockPos cell(int u, int v, int y) {
			return origin.relative(back, u).relative(side, v).atY(y);
		}

		public int c() {
			return (w - 1) / 2;
		}

		public boolean wall(int u, int v) {
			return u == -1 || u == d || v == -1 || v == w;
		}

		public boolean corner(int u, int v) {
			return (u == -1 || u == d) && (v == -1 || v == w);
		}
	}

	public static Lot lot(int id) {
		int m = ringOfLot(id);
		double a = centreAngle(id);
		Direction out = cardinal(a);
		Dims d = dims();
		return new Lot(id, m, polar(d.centre[m], a, 0), out, out.getClockWise(), d.court[m], d.hallW[m], d.hallD[m], d.gateW[m], d.gateD[m]);
	}

	/**
	 * One desk slot of a hall floor: the desk's middle cell and the direction from the agent to the
	 * desk. Slots 0/1: against the back wall; 2/3: against the side walls near the back; 4/5: against
	 * the side walls near the front. The centre line ({@code v = c}) stays free for the stairs.
	 */
	public record DeskSpot(BlockPos desk, Direction facing) {
		public BlockPos stand() {
			return desk.relative(facing.getOpposite());
		}
	}

	public static DeskSpot desk(Room h, int floor, int slot) {
		Dims d = dims();
		int y = floor * d.floorH + 1;
		int c = h.c();
		return switch (slot) {
			case 0 -> new DeskSpot(h.cell(h.d() - 1, c - d.deskSide, y), h.back());
			case 1 -> new DeskSpot(h.cell(h.d() - 1, c + d.deskSide, y), h.back());
			case 2 -> new DeskSpot(h.cell(h.d() - d.deskMid, 0, y), h.side().getOpposite());
			case 3 -> new DeskSpot(h.cell(h.d() - d.deskMid, h.w() - 1, y), h.side());
			case 4 -> new DeskSpot(h.cell(h.d() - d.deskFront, 0, y), h.side().getOpposite());
			default -> new DeskSpot(h.cell(h.d() - d.deskFront, h.w() - 1, y), h.side());
		};
	}

	/** Steps of the straight stairs of a two-floor hall: {@code i = 0..floorH-1} at {@code (u = 1 + i, v = c, y = 1 + i)}, climbing towards the back. */
	public static int stairSteps() {
		return dims().floorH;
	}

	/** Floor-to-floor height of a hall at the scale in use. */
	public static int floorH() {
		return dims().floorH;
	}

	/** Podium level at the scale in use. */
	public static int podium() {
		return dims().podium;
	}

	// ------------------------------------------------------------------ public views (renders, docs)

	/** Bounding box (xz, ground to the highest roof) of a lot's cluster. */
	public static Box lotBounds(int lot) {
		Lot l = lot(lot);
		int e = l.reach() + 1;
		BlockPos c = l.centre();
		Dims d = dims();
		return Box.of(c.getX() - e, 0, c.getZ() - e, c.getX() + e, 2 * d.floorH + d.s(9), c.getZ() + e);
	}

	/** Centre cell (x, z) of a ring-1 node: 0 review tower (N), 2 scheduled rotunda (E), 4 archive cloister (S), 6 sunken lounge (W). */
	public static int[] nodeCentre(int k) {
		Node n = node(k);
		return new int[] {n.cx(), n.cz()};
	}
}
