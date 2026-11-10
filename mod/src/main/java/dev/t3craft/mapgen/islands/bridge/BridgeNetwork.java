package dev.t3craft.mapgen.islands.bridge;

import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.islands.Bridge;
import dev.t3craft.mapgen.islands.BridgePlanner;
import dev.t3craft.mapgen.islands.Island;
import dev.t3craft.mapgen.islands.IslandField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which islands get a bridge to which (second cut, #32): up to eight directions per island, never two bridges
 * crossing or touching. Deterministic and symmetric (whether {@code a} links to {@code b} does not depend on
 * which of them is asked, nor on the order islands are visited), with plans cached. Not thread-safe: the
 * server thread owns it.
 *
 * <ul>
 *   <li><b>Axis links</b>: the four cell neighbours, as in the first cut (same bridge ids, so worlds saved with
 *       the first cut keep their bridges). Their decks stay inside the two cells, so two axis bridges never
 *       cross. A deck longer than {@value #MAX_AXIS_DECK} steps (two small islands at the far ends of their
 *       cells, with extra large ones in the mix) is not built, unless it is the only way out of an island: an
 *       island with no other link (no short axis bridge, no diagonal) keeps its shortest axis bridge, whatever
 *       its length, so no island is cut off.</li>
 *   <li><b>Diagonal links</b>: in each 2x2 block of cells {@code (bx..bx+1, bz..bz+1)} with {@code bx + bz}
 *       even (a checkerboard, so blocks sharing a side never both have one) and a hash roll under
 *       {@value #DIAGONAL_PERCENT} %, one of its two diagonals (picked by hash) is a candidate. It is kept when
 *       its plan exists (no third island in the way), its deck is at most {@value #MAX_DIAGONAL_DECK} steps,
 *       it keeps {@value #CLEARANCE} block of clearance from every axis bridge that can reach its block, and
 *       from the candidates of the four corner blocks (both sides of a conflict drop it, so the result does not
 *       depend on who is asked first).</li>
 * </ul>
 *
 * <p>Working out the links of an island the first time plans dozens of bridges (its own, the diagonal
 * candidates of its four blocks and every axis bridge that could clash with them). {@link #links(Island, int)}
 * does that in instalments: it plans at most the given number of bridges per call and answers null until it has
 * them all, keeping what it planned, so the caller can spread the work over ticks.
 */
public final class BridgeNetwork {
	/** Chance that a 2x2 block of the checkerboard gets a diagonal candidate. */
	public static final int DIAGONAL_PERCENT = 60;
	/** Longest diagonal deck, in steps. */
	public static final int MAX_DIAGONAL_DECK = 80;
	/** Longest axis deck, in steps, unless it is an island's only way out. */
	public static final int MAX_AXIS_DECK = 64;
	/** Free columns kept between two bridges' blocks (Chebyshev). */
	public static final int CLEARANCE = 1;

	private static final int UNLIMITED = Integer.MAX_VALUE;

	/** Thrown (preallocated, no stack) when a budgeted call has planned all it may; never leaves this class. */
	private static final class Pending extends RuntimeException {
		private static final long serialVersionUID = 1L;

		Pending() {
			super("bridge plans pending", null, false, false);
		}
	}

	private static final Pending PENDING = new Pending();

	private final IslandField field;
	private final Map<String, Optional<Bridge>> plans = new HashMap<>();
	/** Island id to the id of the neighbour whose (long) axis bridge is its only way out, when it has no other link. */
	private final Map<String, Optional<String>> lifelines = new HashMap<>();
	/** Plans left in the current budgeted call; {@link #UNLIMITED} outside one. */
	private int budget = UNLIMITED;
	private final Map<String, Set<Long>> footprints = new HashMap<>();
	private final Map<Long, Optional<Diagonal>> candidates = new HashMap<>();
	private final Map<Long, Optional<Diagonal>> diagonals = new HashMap<>();

	/** A diagonal bridge and the columns it keeps clear (its own, widened by {@link #CLEARANCE}). */
	private record Diagonal(Island p, Island q, Bridge bridge, Set<Long> reach) {
	}

	public BridgeNetwork(IslandField field) {
		this.field = field;
	}

	/** The (cached) plan of the bridge between two islands, linked or not; null when there is none. */
	public @Nullable Bridge bridge(Island a, Island b) {
		String id = BridgePlanner.idOf(a, b);
		Optional<Bridge> o = plans.get(id);
		if (o == null) {
			if (budget != UNLIMITED) {
				if (budget <= 0) {
					throw PENDING;
				}
				budget--;
			}
			o = Optional.ofNullable(BridgePlanner.plan(field, a, b));
			plans.put(id, o);
		}
		return o.orElse(null);
	}

	/**
	 * {@link #links(Island)} planning at most {@code maxPlans} new bridges: null when that was not enough (call
	 * again, the plans made so far are kept), the links once every plan they need exists.
	 */
	public @Nullable List<Island> links(Island i, int maxPlans) {
		budget = maxPlans;
		try {
			return links(i);
		} catch (Pending e) {
			return null;
		} finally {
			budget = UNLIMITED;
		}
	}

	/** The islands {@code i} is linked to: its axis neighbours (when a bridge fits) and its diagonal links. */
	public List<Island> links(Island i) {
		List<Island> out = new ArrayList<>(8);
		int gx = i.gx();
		int gz = i.gz();
		for (Island n : field.neighbours(i)) {
			if (axisLinked(i, n)) {
				out.add(n);
			}
		}
		for (int bx = gx - 1; bx <= gx; bx++) {
			for (int bz = gz - 1; bz <= gz; bz++) {
				Diagonal d = diagonal(bx, bz);
				if (d != null && d.p().id().equals(i.id())) {
					out.add(d.q());
				} else if (d != null && d.q().id().equals(i.id())) {
					out.add(d.p());
				}
			}
		}
		return out;
	}

	/** Whether the axis neighbours are linked: a bridge fits and is not too long, or it is the way out of one of them. Symmetric. */
	private boolean axisLinked(Island a, Island b) {
		Bridge br = bridge(a, b);
		if (br == null) {
			return false;
		}
		return br.deck() <= MAX_AXIS_DECK || lifeline(a, b) || lifeline(b, a);
	}

	/** Whether {@code y} is the neighbour whose long bridge keeps {@code x} from being cut off. */
	private boolean lifeline(Island x, Island y) {
		Optional<String> keep = lifelines.get(x.id());
		if (keep == null) {
			keep = Optional.ofNullable(onlyWayOut(x));
			lifelines.put(x.id(), keep);
		}
		return keep.isPresent() && keep.get().equals(y.id());
	}

	/**
	 * The neighbour {@code x} must keep its (long) axis bridge to, or null: {@code x} has no axis bridge within
	 * {@value #MAX_AXIS_DECK} steps and no diagonal bridge, but does have a longer axis one; the shortest wins
	 * (the lower id on a tie).
	 */
	private @Nullable String onlyWayOut(Island x) {
		Island best = null;
		int bestDeck = UNLIMITED;
		for (Island n : field.neighbours(x)) {
			Bridge b = bridge(x, n);
			if (b == null) {
				continue;
			}
			if (b.deck() <= MAX_AXIS_DECK) {
				return null;
			}
			if (best == null || b.deck() < bestDeck || (b.deck() == bestDeck && n.id().compareTo(best.id()) < 0)) {
				best = n;
				bestDeck = b.deck();
			}
		}
		if (best == null || hasDiagonal(x)) {
			return null;
		}
		return best.id();
	}

	private boolean hasDiagonal(Island x) {
		for (int bx = x.gx() - 1; bx <= x.gx(); bx++) {
			for (int bz = x.gz() - 1; bz <= x.gz(); bz++) {
				Diagonal d = diagonal(bx, bz);
				if (d != null && (d.p().id().equals(x.id()) || d.q().id().equals(x.id()))) {
					return true;
				}
			}
		}
		return false;
	}

	/** The diagonal bridge of block {@code (bx, bz)}, or null. */
	private @Nullable Diagonal diagonal(int bx, int bz) {
		long key = Bridge.column(bx, bz);
		Optional<Diagonal> o = diagonals.get(key);
		if (o == null) {
			Diagonal c = candidate(bx, bz);
			if (c != null) {
				for (int dx = -1; dx <= 1 && c != null; dx += 2) {
					for (int dz = -1; dz <= 1 && c != null; dz += 2) {
						Diagonal other = candidate(bx + dx, bz + dz);
						if (other != null && touches(c.reach(), footprint(other.bridge()))) {
							c = null;
						}
					}
				}
			}
			o = Optional.ofNullable(c);
			diagonals.put(key, o);
		}
		return o.orElse(null);
	}

	/** The diagonal candidate of block {@code (bx, bz)} before comparing with neighbouring blocks, or null. */
	private @Nullable Diagonal candidate(int bx, int bz) {
		long key = Bridge.column(bx, bz);
		Optional<Diagonal> o = candidates.get(key);
		if (o == null) {
			o = Optional.ofNullable(computeCandidate(bx, bz));
			candidates.put(key, o);
		}
		return o.orElse(null);
	}

	private @Nullable Diagonal computeCandidate(int bx, int bz) {
		if (Math.floorMod(bx + bz, 2) != 0) {
			return null;
		}
		long seed = field.seed();
		if (Noise.hash(seed, bx, bz, 44, 100) >= DIAGONAL_PERCENT) {
			return null;
		}
		boolean main = Noise.hash(seed, bx, bz, 45, 2) == 0;
		Island p = main ? field.island(bx, bz) : field.island(bx + 1, bz);
		Island q = main ? field.island(bx + 1, bz + 1) : field.island(bx, bz + 1);
		Bridge b = bridge(p, q);
		if (b == null || b.deck() > MAX_DIAGONAL_DECK) {
			return null;
		}
		Set<Long> reach = widen(footprint(b), CLEARANCE);
		// Every axis bridge whose two cells can reach this block's square: cells bx-1..bx+2, bz-1..bz+2.
		for (int x = bx - 1; x <= bx + 2; x++) {
			for (int z = bz - 1; z <= bz + 2; z++) {
				if (x <= bx + 1 && clash(reach, field.island(x, z), field.island(x + 1, z))) {
					return null;
				}
				if (z <= bz + 1 && clash(reach, field.island(x, z), field.island(x, z + 1))) {
					return null;
				}
			}
		}
		return new Diagonal(p, q, b, reach);
	}

	private boolean clash(Set<Long> reach, Island a, Island b) {
		Bridge ax = bridge(a, b);
		return ax != null && touches(reach, footprint(ax));
	}

	private Set<Long> footprint(Bridge b) {
		Set<Long> f = footprints.get(b.id());
		if (f == null) {
			f = b.columns();
			footprints.put(b.id(), f);
		}
		return f;
	}

	private static boolean touches(Set<Long> reach, Set<Long> columns) {
		for (long c : columns) {
			if (reach.contains(c)) {
				return true;
			}
		}
		return false;
	}

	private static Set<Long> widen(Set<Long> columns, int by) {
		Set<Long> out = new HashSet<>();
		for (long c : columns) {
			int x = (int) (c >> 32);
			int z = (int) c;
			for (int dx = -by; dx <= by; dx++) {
				for (int dz = -by; dz <= by; dz++) {
					out.add(Bridge.column(x + dx, z + dz));
				}
			}
		}
		return out;
	}
}
