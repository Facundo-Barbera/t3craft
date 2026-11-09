package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The octagonal plaza of a crossing (struct/streets-plazas2): geometry and ground. A pure function of the
 * cell, like {@link CitadelPaving}: {@link #at} says whether a cell belongs to a plaza and {@link #ground} what
 * block goes at {@code y = 0}. What stands on it (kerb slabs, benches, planters, the focal lantern) is
 * {@link CitadelStreetscape#crossings}'s, which runs after the catalog's placement and only on free cells.
 *
 * <h2>Shape</h2>
 *
 * A regular-looking octagon (flat sides on the axes of the avenue and of the street, 45 degree chamfers) 22 cells
 * across at scale 2, in the frame of the avenue ({@code u} along it, {@code v} across). Its outer flat side lies
 * exactly on the outer edge of the street and the centre is shifted that much towards the inside, because the ring
 * outside the street is a terrace lower than the street: every cell of a plaza must be on the same terrace
 * ({@link CitadelRelief#halves}), so the plaza never has a step in the middle. The inner street (one terrace with
 * the core, 10 wide) gets a flatter octagon that stays inside the street.
 *
 * <h2>Floor</h2>
 *
 * Concentric octagonal rings, one material each, measured with an octagonal metric rounded to whole rings
 * ({@link Plaza#band}: ring {@code k} is every cell whose metric rounds to {@code k}, so every ring is 1 cell
 * thick and its diagonals are closed staircases, never loose dots): 3x3 core of chiselled brick (the focal
 * point's base), two rings of calcite, one thin dark ring of polished deepslate, one of stone brick, one
 * continuous ring of cherry planks (the accent), an outer band of two tones in a chequer (the only variation;
 * the avenue's runner flows through it to the accent ring), and the kerb: a ring of deepslate tile with a tile
 * slab on it, half a block over the floor, which a walker steps over. In the four quadrants between the roads
 * the outer band is a planter ({@code k = 8..10}: a low wall, then two rows of azalea) with a bench
 * ({@code k = 7}) in front of it facing the focal point.
 */
final class CitadelPlaza {
	static final BlockState CORE = CitadelPaving.CHISELED;
	static final BlockState DISC = CitadelPaving.CALCITE;
	static final BlockState DARK = CitadelPaving.POLISHED;
	static final BlockState BODY = CitadelPaving.BRICKS;
	static final BlockState ACCENT = b(Blocks.CHERRY_PLANKS);
	static final BlockState TONE_A = CitadelPaving.BRICKS;
	static final BlockState TONE_B = CitadelPaving.JOINT;
	static final BlockState KERB = FOOT;
	static final BlockState KERB_TOP = CitadelPaving.KERB_SLAB;
	static final BlockState BED = b(Blocks.PODZOL);

	/** The kerb ring: the octagon's metric is scaled so that it is 11 here, whatever the scale of the citadel. */
	static final int KERB_K = 11;
	/** Ring of the accent (cherry planks). */
	static final int ACCENT_K = 6;
	/** Ring of the benches; the planters are the three rings outside it, then the kerb. */
	static final int BENCH_K = 7;
	static final int BED_K = 8;
	/** A cell is in a quadrant (not on the arm of a road) when both its distances to the axes are at least this many units. */
	static final double QUAD_MIN = 4.5;
	/** The diagonal side of the octagon: {@code |u| + |v| <= DIAG * metric}. */
	static final double DIAG = 1.5;

	private CitadelPlaza() {
	}

	/** Without room (the streets of a small scale are under 10 wide) there is no plaza: the crossing keeps the plain roads. */
	static boolean enabled() {
		return CitadelCanvas.dims().street >= 10;
	}

	/** One plaza: its centre cell, the frame of its avenue and the scale of its metric. */
	record Plaza(int m, int cx, int cz, double ux, double uz, double vx, double vz, double ku, double kv, boolean planters, int ref) {
		double u(int x, int z) {
			return (x - cx) * ux + (z - cz) * uz;
		}

		double v(int x, int z) {
			return (x - cx) * vx + (z - cz) * vz;
		}

		/** Octagonal distance to the centre, in units where the kerb is {@link #KERB_K}. */
		double metric(int x, int z) {
			double a = Math.abs(v(x, z)) * kv;
			double bb = Math.abs(u(x, z)) * ku;
			return Math.max(Math.max(a, bb), (a + bb) / DIAG);
		}

		/** The ring of a cell. */
		int band(int x, int z) {
			return (int) Math.round(metric(x, z));
		}

		/** Whether the cell is in a quadrant between the roads (where the benches and planters go). */
		/** Whether the avenue's runner reaches in (the ring plazas, not the inner street's). */
		boolean runner() {
			return planters;
		}

		boolean quadrant(int x, int z) {
			return planters && Math.abs(v(x, z)) * kv >= QUAD_MIN && Math.abs(u(x, z)) * ku >= QUAD_MIN;
		}

		/** The cell at {@code lat} units across and {@code along} units along the avenue from the centre. */
		BlockPos cell(double lat, double along) {
			return new BlockPos((int) Math.round(cx + lat / kv * vx + along / ku * ux), 0, (int) Math.round(cz + lat / kv * vz + along / ku * uz));
		}

		/** Whether the cell belongs to this plaza (inside the kerb, on its terrace, on a road or a lot). */
		boolean contains(CitadelCanvas c, int x, int z) {
			return metric(x, z) < KERB_K + 0.5 && allows(c, x, z);
		}

		private boolean allows(CitadelCanvas c, int x, int z) {
			Zone zn = c.zoneAt(x, z);
			switch (zn.kind()) {
				case STREET, INNER_STREET, AVENUE, LOT -> {
				}
				default -> {
					return false;
				}
			}
			return CitadelRelief.halves(c, x, z) == ref;
		}
	}

	/** Where a ring street's plazas stand: the street's centre radius, how far the plaza reaches from the plaza centre, and the shift inwards. */
	private record Spec(double radius, double reach, double ku, double kv, boolean planters, int ref) {
	}

	private static Spec spec(int m) {
		var d = CitadelCanvas.dims();
		if (m == 0) {
			double lo = d.rampR;
			double hi = RingCampusGeometry.ringInner(1);
			double w = hi - lo;
			double hu = w / 2;
			double hv = w * 0.9;
			return new Spec((lo + hi) / 2, hu + 2, KERB_K / hu, KERB_K / hv, false, 2 * RingCampusGeometry.level(0));
		}
		double w = d.street;
		double h = w * 11.0 / 12.0;
		// the kerb's outer cell is the last cell of the street (half a cell in, so the rounded ring stays in the street's zone)
		return new Spec(RingCampusGeometry.streetOuter(m) - h - 0.5, h + 2, KERB_K / h, KERB_K / h, true, 2 * RingCampusGeometry.level(m));
	}

	/** The radials a street has plazas at: the inner street's eight (but the spawn's, which has its own plaza), a ring street's two sets. */
	private static int[] counts(CitadelCanvas c, int m) {
		if (m == 0) {
			return new int[] {8};
		}
		return m < c.rings ? new int[] {RingCampusGeometry.lotsIn(m), RingCampusGeometry.lotsIn(m + 1)} : new int[] {RingCampusGeometry.lotsIn(m)};
	}

	private static Plaza make(int m, int k, int n, Spec s) {
		double ang = k * 2 * Math.PI / n;
		BlockPos centre = RingCampusGeometry.polar(s.radius(), ang, 0);
		return new Plaza(m, centre.getX(), centre.getZ(), Math.sin(ang), -Math.cos(ang), Math.cos(ang), Math.sin(ang), s.ku(), s.kv(), s.planters(), s.ref());
	}

	/** The plaza a ground cell belongs to, or {@code null}. */
	static @Nullable Plaza at(CitadelCanvas c, int x, int z) {
		if (!enabled()) {
			return null;
		}
		double r = RingCampusGeometry.r(x, z);
		double a = RingCampusGeometry.theta(x, z);
		Plaza best = null;
		double bestMetric = Double.MAX_VALUE;
		for (int m = 0; m <= c.rings; m++) {
			if (m == 1) {
				continue;
			}
			Spec s = spec(m);
			if (Math.abs(r - s.radius()) > s.reach() + 1) {
				continue;
			}
			for (int n : counts(c, m)) {
				int k = (int) Math.round(a * n / (2 * Math.PI)) % n;
				if (m == 0 && k == n / 2) {
					continue; // the spawn's plaza has its own design
				}
				Plaza p = make(m, k, n, s);
				double mt = p.metric(x, z);
				if (mt < bestMetric && p.contains(c, x, z)) {
					best = p;
					bestMetric = mt;
				}
			}
		}
		return best;
	}

	/** Every plaza of the canvas, once each. */
	static List<Plaza> all(CitadelCanvas c) {
		List<Plaza> out = new ArrayList<>();
		if (!enabled()) {
			return out;
		}
		Set<Long> done = new HashSet<>();
		for (int m = 0; m <= c.rings; m++) {
			if (m == 1) {
				continue;
			}
			Spec s = spec(m);
			for (int n : counts(c, m)) {
				for (int k = 0; k < n; k++) {
					if (m == 0 && k == n / 2) {
						continue;
					}
					Plaza p = make(m, k, n, s);
					if (done.add(CitadelCanvas.col(p.cx(), p.cz()))) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	/** The block at {@code y = 0} of a cell of plaza {@code p}. */
	static BlockState ground(@Nullable CitadelCanvas c, Plaza p, int x, int z) {
		int k = p.band(x, z);
		if (k <= 1) {
			return CORE;
		}
		if (k <= 3) {
			return DISC;
		}
		if (k == 4) {
			return DARK;
		}
		if (k == 5) {
			return BODY;
		}
		if (k == ACCENT_K) {
			return ACCENT;
		}
		if (k >= KERB_K) {
			return KERB;
		}
		if (c != null && p.runner() && k > ACCENT_K && Math.abs(p.v(x, z)) <= CitadelPaving.RUNNER_D() && p.u(x, z) < 0) {
			return c.floor(); // the avenue's runner flows into the plaza, up to the accent ring
		}
		if (k >= BED_K && p.quadrant(x, z)) {
			return BED;
		}
		return Math.floorMod(x + z, 2) == 0 ? TONE_A : TONE_B; // the one band with variation: a chequer of two tones
	}
}
