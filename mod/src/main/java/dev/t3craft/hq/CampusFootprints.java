package dev.t3craft.hq;

import dev.t3craft.hq.CampusGeometry.Edge;
import dev.t3craft.hq.CampusGeometry.Hex;
import dev.t3craft.hq.CampusGeometry.Pavilion;
import dev.t3craft.hq.CampusGeometry.Vertex;
import java.util.ArrayList;
import java.util.List;

/**
 * Public view of the campus ground plan ({@link CampusStyle}) for whoever reserves terrain for it,
 * in HQ coordinates (origin = hub floor centre, north = -z), like {@link HqFootprints} for the spine
 * styles. Areas never overlap:
 *
 * <ul>
 *   <li>{@link #hub()}: the hub and its plaza (49 × 49);</li>
 *   <li>{@link #pavilion(String)}: the plot of a special pavilion (21 × 21) on a vertex round the
 *   hub: {@code review}, {@code archive}, {@code scheduled}, {@code lounge}, {@code gate},
 *   {@code errors};</li>
 *   <li>{@link #parcel(int)}: parcel {@code p}'s square (49 × 49): one project's barrio, or a park;</li>
 *   <li>everything else is the network (paths, plazas, ponds, trees) inside {@link #envelope}.</li>
 * </ul>
 *
 * <p><b>Survival (proposal, not wired):</b> a site would reserve {@link #envelope(int)} for the
 * number of parcels it allows (it is far beyond the 64-block site limit of the spine styles: 249 × 231 for ring 1, 393 × 357 for rings 1–2, 537 × 483 for rings 1–3), build {@code base} = hub + pavilions + the ring-1 network
 * first, and then one site expansion per barrio parcel (its {@link #parcel} square, then the network
 * cells of that parcel's {@code grounds_<p>} group). Until then the campus is a demo / creative
 * layout and {@code HqSiteFrame} keeps the spine styles' limits.
 */
public final class CampusFootprints {
	private CampusFootprints() {
	}

	public static int bottom() {
		return 0;
	}

	public static int top() {
		return CampusGeometry.TOP;
	}

	private static HqFootprints.Area area(CampusGeometry.Area a) {
		return new HqFootprints.Area(a.minX(), a.minZ(), a.maxX(), a.maxZ());
	}

	public static HqFootprints.Area hub() {
		return area(CampusGeometry.HUB.area());
	}

	public static HqFootprints.Area pavilion(String key) {
		for (Pavilion p : Pavilion.values()) {
			if (p.key.equals(key)) {
				return area(p.plot());
			}
		}
		throw new IllegalArgumentException("no pavilion " + key);
	}

	public static List<String> pavilions() {
		List<String> out = new ArrayList<>();
		for (Pavilion p : Pavilion.values()) {
			out.add(p.key);
		}
		return out;
	}

	public static HqFootprints.Area parcel(int p) {
		return area(CampusGeometry.parcel(p).area());
	}

	/** Parcel of a module index ({@code index / 8}). */
	public static int parcelOf(int moduleIndex) {
		return CampusLayout.parcelOf(moduleIndex);
	}

	/** Ring of parcel {@code p} (1 = round the hub). */
	public static int ring(int p) {
		return CampusGeometry.ringOf(p);
	}

	/** Parcels in rings 1..k. */
	public static int parcelsWithin(int k) {
		return CampusGeometry.parcelsWithin(k);
	}

	/** Reach of a cell's network (paths on its edges, trees beside them) from its centre. */
	static final int REACH = 52;

	/** Everything a campus whose barrios use parcels below {@code parcels} may use (all rings those parcels touch). */
	public static HqFootprints.Area envelope(int parcels) {
		int ring = 1;
		for (int p = 0; p < parcels; p++) {
			ring = Math.max(ring, CampusGeometry.ringOf(p));
		}
		CampusGeometry.Area a = new CampusGeometry.Area(-REACH, -REACH, REACH, REACH);
		for (int p = 0; p < CampusGeometry.parcelsWithin(ring); p++) {
			Hex h = CampusGeometry.parcel(p);
			a = a.union(new CampusGeometry.Area(h.x() - REACH, h.z() - REACH, h.x() + REACH, h.z() + REACH));
		}
		return area(a);
	}

	// ------------------------------------------------------------------ reserved ground

	private static Hex nearest(int x, int z) {
		int r = Math.round(z / (float) CampusGeometry.ROW);
		int q = Math.round((x - CampusGeometry.VX * r) / (float) CampusGeometry.PITCH);
		Hex best = null;
		long bd = Long.MAX_VALUE;
		for (int dr = -1; dr <= 1; dr++) {
			for (int dq = -1; dq <= 1; dq++) {
				Hex h = new Hex(q + dq, r + dr);
				long d = (long) (h.x() - x) * (h.x() - x) + (long) (h.z() - z) * (h.z() - z);
				if (d < bd) {
					bd = d;
					best = h;
				}
			}
		}
		return best;
	}

	private static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double t = ((px - ax) * vx + (pz - az) * vz) / (vx * vx + vz * vz);
		t = Math.max(0, Math.min(1, t));
		return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
	}

	/**
	 * True when (x, z) is within {@code margin} of ground the campus may ever need for a plot, a path,
	 * a spur, a plaza or a pond, whatever the roster: decoration stays off it, so a barrio added
	 * later never meets a tree.
	 */
	static boolean reserved(int x, int z, int margin) {
		Hex c = nearest(x, z);
		double band = CampusGeometry.PATH + 0.5 + margin;
		for (Pavilion p : Pavilion.values()) {
			if (p.plot().grow(margin).contains(x, z)) {
				return true;
			}
		}
		int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, -1}, {-1, 1}};
		for (int[] o : around) {
			Hex h = new Hex(c.q() + o[0], c.r() + o[1]);
			if (h.area().grow(margin).contains(x, z)) {
				return true;
			}
			for (Vertex v : CampusGeometry.vertices(h)) {
				int r = CampusGeometry.PLAZA + margin;
				if (Math.abs(x - v.x()) <= r && Math.abs(z - v.z()) <= r) {
					return true;
				}
			}
			for (Edge e : CampusGeometry.edges(h)) {
				if (segDist(x, z, e.a().x(), e.a().z(), e.b().x(), e.b().z()) <= band) {
					return true;
				}
				if (CampusGenerator.pond(e) && Math.hypot(x - e.midX(), z - e.midZ()) <= 7.5 + margin) {
					return true;
				}
			}
			int a = CampusGeometry.AREA + 1;
			int[][] spurs = {{0, -a, 0, -2 * CampusGeometry.VZ}, {0, a, 0, 2 * CampusGeometry.VZ}, {a, 0, CampusGeometry.VX, 0},
					{-a, 0, -CampusGeometry.VX, 0}};
			for (int[] s : spurs) {
				if (segDist(x, z, h.x() + s[0], h.z() + s[1], h.x() + s[2], h.z() + s[3]) <= band) {
					return true;
				}
			}
		}
		return false;
	}
}
