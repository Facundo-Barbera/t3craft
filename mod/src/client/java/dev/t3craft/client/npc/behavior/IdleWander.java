package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.level.BlockGetter;
import org.jspecify.annotations.Nullable;

/**
 * feat/npc-idle-wander: calm NPCs stroll around the HQ so the city looks alive. Owned by the
 * {@link BehaviorDirector}; client thread only; no static state.
 *
 * <p><b>Who.</b> An NPC whose thread is pinned in T3 (or any, with {@code includeUnpinned}), calm
 * (IDLE or COMPLETED, not an error, not celebrating, not departing) for {@link WanderConfig#idleTicks}
 * plus a per-thread jitter. At most {@code maxWalkers} stroll at once; at most {@code routesPerTick}
 * new stroll routes are asked per tick. Fewer than {@link #MIN_SPOTS} stroll spots in the layout: no
 * one strolls (they keep their normal spot).
 *
 * <p><b>What.</b> A stroll is a session of a few hops: each hop goes to a stroll spot (walk points,
 * hub, patios, in front of walls and the archive; within {@code hopDistance}), a seat (sits a while)
 * or next to the desk of a thread that is working (a short visit, looking at it), then stays there a
 * while looking at the nearest big screen / board, or a nearby NPC, or around. After the last hop the
 * NPC walks back to its own spot (home or rest) and cools down before the next session. Pauses are
 * long and random but deterministic per thread (seeded by the id), so strollers do not move in step.
 * The moment its thread works or needs input the NPC drops the stroll (the brain walks it to its desk,
 * running when it needs attention).
 *
 * <p><b>Cost.</b> Routes come from {@link WanderRoutes} (snapshot on this thread within a budget, A*
 * on a worker, cached); picking is a scan of the precomputed spot lists. Timed as {@code npc.wander}.
 */
final class IdleWander {
	static final int MIN_SPOTS = 2;
	/** Hops per session (plus the walk back). */
	static final int HOPS_MIN = 2;
	static final int HOPS_MAX = 5;
	/** Stay on a stroll spot, a seat and next to a desk (ticks, min + random). */
	static final int DWELL_MIN = 240;
	static final int DWELL_RANGE = 400;
	static final int SEAT_MIN = 500;
	static final int SEAT_RANGE = 700;
	static final int VISIT_MIN = 100;
	static final int VISIT_RANGE = 120;
	/** Between sessions, back on its own spot (ticks, min + random). */
	static final int COOLDOWN_MIN = 1_200;
	static final int COOLDOWN_RANGE = 2_400;
	/** Added once to the calm time, per thread, so a group that went calm together does not start together. */
	static final int START_JITTER = 1_200;
	/** A hop that has not arrived in this long is given up (the next one, or home). */
	static final int WALK_TIMEOUT = 900;
	/** Retry after no spot was in reach or every candidate failed. */
	static final int NOTHING_BACKOFF = 600;
	/** A spot found unreachable is not picked again for this long. */
	static final int UNREACHABLE_TICKS = 2_400;
	/** Nearer than this (blocks) a candidate is "where I stand already". */
	static final double MIN_HOP = 3.0;
	static final double MAX_RISE = 5.0;
	/** Two strollers never pick spots closer than this. */
	static final double TAKEN = 1.5;
	static final double SIGHT_RANGE = 16.0;
	static final double NPC_RANGE = 6.0;
	static final double HEAD = 1.6;
	/** Side offset (blocks) of a visitor from the worker's desk spot. */
	static final double VISIT_SIDE = 1.4;
	static final int PINNED_EVERY = 40;

	/** What the brain gets this tick. */
	record Plan(Anchor spot, boolean sit, @Nullable Pos look) {
	}

	/** A thread that is working, at its desk: someone to visit. */
	record Worker(String id, Anchor desk) {
	}

	private enum Stage {
		CALM, HOP, DWELL, HOME
	}

	private static final class State {
		final SplittableRandom rnd;
		final int jitter;
		int calmSince;
		int nextAt;
		Stage stage = Stage.CALM;
		@Nullable Anchor spot;
		boolean sit;
		@Nullable Pos look;
		/** The NPC looked at (visit, or a neighbour), followed while dwelling. */
		@Nullable String lookAt;
		int stageAt;
		int dwell;
		int hopsLeft;
		@Nullable Pos last;

		State(String id, int now) {
			this.rnd = new SplittableRandom((id.hashCode() * 0x9E3779B97F4A7C15L) ^ 0x5DEECE66DL);
			this.jitter = rnd.nextInt(START_JITTER);
			this.calmSince = now;
		}

		boolean active() {
			return stage != Stage.CALM;
		}
	}

	private final Map<String, State> states = new HashMap<>();
	private final Map<Long, Integer> unreachable = new HashMap<>();
	private final WanderRoutes routes = new WanderRoutes();
	private WanderConfig cfg = WanderConfig.OFF;
	private List<Anchor> spots = List.of();
	private List<Anchor> seats = List.of();
	private List<Pos> sights = List.of();
	private List<Worker> workers = List.of();
	private Function<String, @Nullable Pos> positions = id -> null;
	private Set<String> pinned = Set.of();
	private boolean pinnedRead;
	private int tick;
	private int active;
	private int startsLeft;
	private long spentNs;

	/** Start of the director's tick: config, layout lists, working threads, route results and new snapshots. */
	void begin(int now, WanderConfig config, AnchorSource layout, List<Worker> working, Function<String, @Nullable Pos> where,
			Supplier<Set<String>> pinnedIds, BlockGetter level) {
		long t0 = System.nanoTime();
		tick = now;
		cfg = config;
		spots = layout.strollSpots();
		seats = layout.seats();
		sights = layout.sights();
		workers = working;
		positions = where;
		if (cfg.enabled() && !cfg.includeUnpinned() && (now % PINNED_EVERY == 0 || !pinnedRead)) {
			pinnedRead = true;
			try {
				pinned = Set.copyOf(pinnedIds.get());
			} catch (RuntimeException e) {
				pinned = Set.of();
			}
		}
		active = 0;
		for (State s : states.values()) {
			if (s.active()) {
				active++;
			}
		}
		startsLeft = cfg.routesPerTick();
		if (tick % 200 == 0 && !unreachable.isEmpty()) {
			unreachable.values().removeIf(until -> until <= tick);
		}
		if (cfg.enabled() || active > 0) {
			routes.tick(level, cfg.routesPerTick());
		}
		spentNs = System.nanoTime() - t0;
	}

	/** Time spent this tick ({@link #begin} plus every {@link #update}). */
	long spentNs() {
		return spentNs;
	}

	/**
	 * One NPC, before its brain's tick: what it should stroll to (null = nothing, normal behaviour).
	 * {@code own} is where it goes back to after a session (its home in IDLE, rest spot when COMPLETED).
	 */
	@Nullable Plan update(String id, Mode mode, boolean error, NpcBrain brain, Anchor own) {
		long t0 = System.nanoTime();
		try {
			return decide(id, mode, error, brain, own);
		} finally {
			spentNs += System.nanoTime() - t0;
		}
	}

	private @Nullable Plan decide(String id, Mode mode, boolean error, NpcBrain brain, Anchor own) {
		State s = states.computeIfAbsent(id, k -> new State(k, tick));
		boolean calm = (mode == Mode.IDLE || mode == Mode.COMPLETED) && !error && brain.phase() != NpcBrain.Phase.CELEBRATING;
		if (!calm) {
			s.calmSince = tick;
		}
		boolean eligible = calm && cfg.enabled() && cfg.maxWalkers() > 0 && spots.size() >= MIN_SPOTS && (cfg.includeUnpinned() || pinned.contains(id));
		if (!eligible) {
			stop(id, s, 0);
			return null;
		}
		boolean failed = brain.takeStrollFailed();
		if (s.active() && failed) {
			if (s.spot != null) {
				unreachable.put(cellKey(s.spot.pos()), tick + UNREACHABLE_TICKS);
			}
			if (s.stage == Stage.HOME || startsLeft <= 0) {
				stop(id, s, cooldown(s)); // never re-send the spot that just failed
				return null;
			}
			next(id, s, brain, own);
		}
		switch (s.stage) {
			case CALM -> {
				if (tick - s.calmSince < cfg.idleTicks() + s.jitter || tick < s.nextAt || active >= cfg.maxWalkers() || startsLeft <= 0) {
					return null;
				}
				s.hopsLeft = HOPS_MIN + s.rnd.nextInt(HOPS_MAX - HOPS_MIN + 1);
				if (!pick(id, s, brain.pos())) {
					s.nextAt = tick + NOTHING_BACKOFF + s.rnd.nextInt(NOTHING_BACKOFF);
					return null;
				}
				active++;
			}
			case HOP -> {
				if (brain.strollArrived()) {
					s.stage = Stage.DWELL;
					s.stageAt = tick;
					lookOnArrival(id, s, brain.pos());
				} else if (tick - s.stageAt > WALK_TIMEOUT) {
					next(id, s, brain, own);
				}
			}
			case DWELL -> {
				if (s.lookAt != null) {
					Pos p = positions.apply(s.lookAt);
					if (p != null) {
						s.look = p.add(0, HEAD, 0);
					}
				}
				if (tick - s.stageAt >= s.dwell) {
					next(id, s, brain, own);
				}
			}
			case HOME -> {
				if (brain.strollArrived() || tick - s.stageAt > WALK_TIMEOUT) {
					stop(id, s, cooldown(s));
					return null;
				}
			}
		}
		Anchor spot = s.spot;
		return s.active() && spot != null ? new Plan(spot, s.sit && s.stage == Stage.DWELL, s.stage == Stage.DWELL ? s.look : null) : null;
	}

	/** The hop is over: the next one when hops are left (and a route may be asked this tick), else walk back. */
	private void next(String id, State s, NpcBrain brain, Anchor own) {
		if (startsLeft <= 0) {
			s.stage = Stage.DWELL; // no new route this tick: stay a moment longer
			s.stageAt = tick - Math.max(0, s.dwell - 20);
			return;
		}
		if (s.hopsLeft > 0 && pick(id, s, brain.pos())) {
			return;
		}
		if (own.pos().horizDist(brain.pos()) < MIN_HOP) {
			stop(id, s, cooldown(s));
			return;
		}
		startsLeft--;
		s.stage = Stage.HOME;
		s.stageAt = tick;
		s.spot = own;
		s.sit = false;
		s.look = null;
		s.lookAt = null;
	}

	/** Ends the session (if any): back to normal behaviour; next session after {@code cooldown} ticks. */
	private void stop(String id, State s, int cooldown) {
		if (s.active()) {
			active--;
			routes.forget(id);
			s.nextAt = tick + cooldown;
		}
		s.stage = Stage.CALM;
		s.spot = null;
		s.sit = false;
		s.look = null;
		s.lookAt = null;
	}

	private int cooldown(State s) {
		return COOLDOWN_MIN + s.rnd.nextInt(COOLDOWN_RANGE);
	}

	/** Picks the next hop (visit, seat or spot) within reach; false when nothing fits. Uses one route start. */
	private boolean pick(String id, State s, Pos at) {
		if (s.spot != null) {
			s.last = s.spot.pos(); // not straight back to where it just was
		}
		int roll = s.rnd.nextInt(100);
		boolean ok = roll < 20 && visit(id, s, at) || roll < 40 && seat(id, s, at) || spot(id, s, at, spots, false);
		if (!ok) {
			return false;
		}
		startsLeft--;
		s.hopsLeft--;
		s.stage = Stage.HOP;
		s.stageAt = tick;
		return true;
	}

	private boolean visit(String id, State s, Pos at) {
		List<Worker> near = new ArrayList<>();
		for (Worker w : workers) {
			if (!w.id().equals(id) && reach(at, w.desk().pos())) {
				near.add(w);
			}
		}
		if (near.isEmpty()) {
			return false;
		}
		Worker w = near.get(s.rnd.nextInt(near.size()));
		Anchor desk = w.desk();
		double rad = Math.toRadians(desk.yaw());
		double side = s.rnd.nextBoolean() ? VISIT_SIDE : -VISIT_SIDE;
		// Minecraft yaw: forward = (-sin, cos); its side is (cos, sin).
		Pos p = desk.pos().add(Math.cos(rad) * side, 0, Math.sin(rad) * side);
		if (taken(id, p) || blocked(p)) {
			return false;
		}
		s.spot = new Anchor(p, p.yawTo(desk.pos()));
		s.sit = false;
		s.look = null;
		s.lookAt = w.id();
		s.dwell = VISIT_MIN + s.rnd.nextInt(VISIT_RANGE);
		return true;
	}

	private boolean seat(String id, State s, Pos at) {
		if (seats.isEmpty() || !spot(id, s, at, seats, true)) {
			return false;
		}
		s.dwell = SEAT_MIN + s.rnd.nextInt(SEAT_RANGE);
		return true;
	}

	private boolean spot(String id, State s, Pos at, List<Anchor> from, boolean sit) {
		List<Anchor> near = new ArrayList<>();
		for (Anchor a : from) {
			Pos p = a.pos();
			if (reach(at, p) && (s.last == null || p.distSqr(s.last) > 1) && !taken(id, p) && !blocked(p)) {
				near.add(a);
			}
		}
		if (near.isEmpty()) {
			return false;
		}
		s.spot = near.get(s.rnd.nextInt(near.size()));
		s.sit = sit;
		s.look = null;
		s.lookAt = null;
		if (!sit) {
			s.dwell = DWELL_MIN + s.rnd.nextInt(DWELL_RANGE);
		}
		return true;
	}

	/** Within one hop: not where it stands, not farther than {@code hopDistance}, not floors apart. */
	private boolean reach(Pos at, Pos p) {
		double d = at.horizDist(p);
		return d >= MIN_HOP && d <= cfg.hopDistance() && Math.abs(p.y() - at.y()) <= MAX_RISE;
	}

	private boolean taken(String id, Pos p) {
		for (Map.Entry<String, State> e : states.entrySet()) {
			Anchor o = e.getValue().spot;
			if (o != null && !e.getKey().equals(id) && e.getValue().active() && o.pos().horizDist(p) < TAKEN && Math.abs(o.pos().y() - p.y()) < 2) {
				return true;
			}
		}
		return false;
	}

	private boolean blocked(Pos p) {
		Integer until = unreachable.get(cellKey(p));
		return until != null && until > tick;
	}

	/** On arrival: the visited NPC, else the nearest sight (screen, board), else a nearby NPC, else nothing (looks around). */
	private void lookOnArrival(String id, State s, Pos at) {
		if (s.lookAt != null) {
			Pos p = positions.apply(s.lookAt);
			s.look = p == null ? null : p.add(0, HEAD, 0);
			return;
		}
		if (s.sit) {
			s.look = null;
			return;
		}
		Pos best = null;
		double bestD = SIGHT_RANGE * SIGHT_RANGE;
		for (Pos p : sights) {
			double d = p.distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		if (best != null && s.rnd.nextInt(4) != 0) {
			s.look = best;
			return;
		}
		String friend = null;
		double friendD = NPC_RANGE * NPC_RANGE;
		for (String other : states.keySet()) {
			if (other.equals(id)) {
				continue;
			}
			Pos p = positions.apply(other);
			if (p != null) {
				double d = p.distSqr(at);
				if (d < friendD && d > 0.5) {
					friendD = d;
					friend = other;
				}
			}
		}
		if (friend != null) {
			s.lookAt = friend;
			Pos p = positions.apply(friend);
			s.look = p == null ? null : p.add(0, HEAD, 0);
		} else {
			s.look = best;
		}
	}

	/** The navigator a stroller's brain uses this tick: routes from {@link WanderRoutes}, moves and collision from {@code live}. */
	NpcBrain.Navigator navigator(String id, NpcBrain.Navigator live) {
		return new NpcBrain.Navigator() {
			@Override
			public @Nullable List<Pos> path(Pos from, Pos to, @Nullable Pos avoid) {
				Route r = route(from, to, avoid);
				return r.status() == Route.Status.FOUND ? r.points() : null;
			}

			@Override
			public Route route(Pos from, Pos to, @Nullable Pos avoid) {
				return routes.route(id, from, to);
			}

			@Override
			public Pos move(Pos from, Pos to) {
				return live.move(from, to);
			}

			@Override
			public boolean embedded(Pos p) {
				return live.embedded(p);
			}

			@Override
			public @Nullable Pos nearestFree(Pos p) {
				return live.nearestFree(p);
			}

			@Override
			public void blocked(Pos at, Pos toward, Pos goal) {
				routes.invalidate(); // the stretch changed under the stroller: search again
			}
		};
	}

	/** An NPC is gone (removed, departing): drop its stroll. */
	void forget(String id) {
		State s = states.remove(id);
		if (s != null && s.active()) {
			active--;
		}
		routes.forget(id);
	}

	/** The base changed: cached routes and unreachable spots no longer hold. */
	void layoutChanged() {
		routes.invalidate();
		unreachable.clear();
	}

	void reset() {
		states.clear();
		unreachable.clear();
		routes.clear();
		active = 0;
	}

	private static long cellKey(Pos p) {
		return GridPathfinder.pack(p.blockX(), p.blockY(), p.blockZ());
	}
}
