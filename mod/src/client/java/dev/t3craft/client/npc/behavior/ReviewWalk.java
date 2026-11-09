package dev.t3craft.client.npc.behavior;

import dev.t3craft.hq.lead.LeadRoster;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-review-walk: finished children wait for their lead, not for the player. A delegated child
 * listed in {@link LeadRoster#reviews()} (done, or idle after its turn) walks to its reviewer and waits
 * there "awaiting review", facing it:
 * <ul>
 *   <li>the reviewer holds a lead office: the office's visitor chairs first (sits), then spots in front
 *   of its door ({@link AnchorSource#reviewSeats}), then rows behind the lead's desk;</li>
 *   <li>otherwise (no office: not a lead, or the HQ had no room): rows behind the parent's desk.</li>
 * </ul>
 * Several children form a queue per reviewer in arrival order: the first one takes the best spot, and
 * when one leaves the others move up. A spot found unreachable is skipped for {@link #FAIL_TICKS}.
 *
 * <p>Costs nothing new: the wait is handed to the brain as a stroll ({@link NpcBrain#stroll}, the same
 * stand/sit/look as {@link IdleWander}) and the walk uses the director's shared {@link Navigation} (its
 * route cache and per-tick node budget, answers PENDING while searching). Spots are reassigned at most
 * every {@link #REASSIGN_EVERY} ticks or when the roster or the base changes; the seats come cached from
 * the anchor source. Timed as {@code npc.review}. Client thread only; owned by the director (no static
 * state, hot-swap safe).
 */
final class ReviewWalk {
	static final int REASSIGN_EVERY = 20;
	/** An unreachable spot is not handed out again for this long (ticks). */
	static final int FAIL_TICKS = 1_200;
	/** Rows behind the reviewer's desk spot: first row distance, row step and side step (blocks). */
	static final double BEHIND = 1.8;
	static final double ROW = 1.3;
	static final double SIDE = 1.2;
	static final double HEAD = 1.6;

	private final Map<String, LinkedHashSet<String>> queues = new TreeMap<>();
	private final Map<String, IdleWander.Plan> assigned = new HashMap<>();
	private final Map<Long, Integer> failed = new HashMap<>();
	private @Nullable AnchorSource lastLayout;
	private @Nullable Map<String, LeadRoster.Review> lastReviews;
	private int lastAssign = Integer.MIN_VALUE / 2;
	private boolean dirty;
	private int tick;
	private long spentNs;

	/**
	 * Start of the director's tick.
	 *
	 * @param reviews children waiting for a review (from the lead roster; empty when the feature is off)
	 * @param present NPC ids with a body this tick
	 * @param where   where an NPC stands now (its brain), null when unknown
	 */
	void begin(int now, AnchorSource layout, Map<String, LeadRoster.Review> reviews, Set<String> present, Function<String, @Nullable Pos> where) {
		long t0 = System.nanoTime();
		tick = now;
		try {
			if (reviews.isEmpty() && queues.isEmpty()) {
				assigned.clear();
				return;
			}
			if (reviews != lastReviews) {
				lastReviews = reviews;
				dirty |= syncQueues(reviews, present);
			} else if (now % REASSIGN_EVERY == 0) {
				dirty |= syncQueues(reviews, present);
			}
			if (!layout.equals(lastLayout)) { // the director's LayeredAnchors is a record: equal while the base is the same
				lastLayout = layout;
				dirty = true;
			}
			if (now % 200 == 0 && !failed.isEmpty()) {
				failed.values().removeIf(until -> until <= now);
			}
			if (dirty || now - lastAssign >= REASSIGN_EVERY) {
				assign(layout, reviews, where);
				dirty = false;
				lastAssign = now;
			}
		} finally {
			spentNs = System.nanoTime() - t0;
		}
	}

	/** Queues follow the reviews: new waiters join the end (in id order), the others leave. True when something changed. */
	private boolean syncQueues(Map<String, LeadRoster.Review> reviews, Set<String> present) {
		boolean changed = false;
		for (Iterator<Map.Entry<String, LinkedHashSet<String>>> it = queues.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, LinkedHashSet<String>> e = it.next();
			String reviewer = e.getKey();
			changed |= e.getValue().removeIf(child -> {
				LeadRoster.Review r = reviews.get(child);
				return r == null || !present.contains(child) || !r.parentNpcId().equals(reviewer);
			});
			if (e.getValue().isEmpty()) {
				it.remove();
			}
		}
		for (Map.Entry<String, LeadRoster.Review> e : new TreeMap<>(reviews).entrySet()) {
			if (present.contains(e.getKey()) && !e.getKey().equals(e.getValue().parentNpcId())) {
				changed |= queues.computeIfAbsent(e.getValue().parentNpcId(), k -> new LinkedHashSet<>()).add(e.getKey());
			}
		}
		return changed;
	}

	private void assign(AnchorSource layout, Map<String, LeadRoster.Review> reviews, Function<String, @Nullable Pos> where) {
		assigned.clear();
		for (Map.Entry<String, LinkedHashSet<String>> e : queues.entrySet()) {
			String reviewer = e.getKey();
			List<String> queue = new ArrayList<>(e.getValue());
			if (queue.isEmpty()) {
				continue;
			}
			LeadRoster.Review any = reviews.get(queue.get(0));
			Anchor desk = layout.desk(reviewer, "");
			List<AnchorSource.ReviewSeat> spots = new ArrayList<>();
			if (any != null && any.inOffice()) {
				for (AnchorSource.ReviewSeat s : layout.reviewSeats(reviewer)) {
					if (!failed(s.spot().pos())) {
						spots.add(s);
					}
				}
			}
			if (desk != null) {
				for (int row = 0; spots.size() < queue.size() && row < 4 + queue.size(); row++) {
					for (int col : new int[] {0, 1, -1}) {
						Anchor a = behind(desk, row, col);
						if (!failed(a.pos())) {
							spots.add(new AnchorSource.ReviewSeat(a, false));
						}
					}
				}
			}
			Pos lead = where.apply(reviewer);
			Pos look = lead != null ? lead.add(0, HEAD, 0) : desk != null ? desk.pos().add(0, HEAD, 0) : null;
			for (int i = 0; i < queue.size() && i < spots.size(); i++) {
				AnchorSource.ReviewSeat s = spots.get(i);
				assigned.put(queue.get(i), new IdleWander.Plan(s.spot(), s.sit(), look));
			}
		}
	}

	/** Row {@code row}, column {@code col} (0 centre, ±1 sides) behind the reviewer standing at {@code desk}, facing it. */
	static Anchor behind(Anchor desk, int row, int col) {
		double rad = Math.toRadians(desk.yaw());
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		double back = BEHIND + row * ROW;
		Pos p = desk.pos().add(-fx * back + fz * col * SIDE, 0, -fz * back - fx * col * SIDE);
		return new Anchor(p, p.yawTo(desk.pos()));
	}

	private boolean failed(Pos p) {
		Integer until = failed.get(cellKey(p));
		return until != null && until > tick;
	}

	/**
	 * What {@code id} should do now: wait at its review spot (stand or sit, looking at the reviewer), or
	 * null (not waiting for a review: normal behaviour). Reads the brain's stroll failure: an unreachable
	 * spot is dropped and another one handed out at the next assignment.
	 */
	@Nullable IdleWander.Plan plan(String id, NpcBrain brain) {
		long t0 = System.nanoTime();
		try {
			IdleWander.Plan p = assigned.get(id);
			if (p == null) {
				return null;
			}
			if (brain.takeStrollFailed()) {
				failed.put(cellKey(p.spot().pos()), tick + FAIL_TICKS);
				assigned.remove(id);
				dirty = true;
				return null;
			}
			return p;
		} finally {
			spentNs += System.nanoTime() - t0;
		}
	}

	/** Time spent this tick ({@link #begin} plus every {@link #plan}). */
	long spentNs() {
		return spentNs;
	}

	void forget(String id) {
		if (assigned.remove(id) != null) {
			dirty = true;
		}
		for (LinkedHashSet<String> q : queues.values()) {
			q.remove(id);
		}
	}

	void reset() {
		queues.clear();
		assigned.clear();
		failed.clear();
		lastLayout = null;
		lastReviews = null;
		dirty = false;
	}

	private static long cellKey(Pos p) {
		return GridPathfinder.pack(p.blockX(), p.blockY(), p.blockZ());
	}
}
