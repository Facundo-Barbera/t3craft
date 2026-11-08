package dev.t3craft.hq.lead;

import dev.t3craft.t3.LeadSignals;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * feat/lead-office: picks the leads (coordinator threads) that get an office in the HQ.
 *
 * <h2>Rule</h2>
 * A top-level thread <b>qualifies</b> when it is at least {@link LeadPolicy#minAgeMs()} old and either
 * handed out {@link LeadPolicy#recentChildren()} or more children (delegated tasks or subagents, not
 * forks) within {@link LeadPolicy#recentWindowMs()}, or has {@link LeadPolicy#workingChildren()} or more
 * children working now. Only threads with a desk in the HQ ({@code eligible}) are considered.
 *
 * <h2>Hysteresis and stability</h2>
 * <ul>
 *   <li>A lead keeps its office while it qualifies, for {@link LeadPolicy#holdMs()} after it last
 *   qualified, and never less than {@link LeadPolicy#minHoldMs()} after it got the office.</li>
 *   <li>Leads are never displaced by a more active thread: they leave by the rule above only. Free
 *   offices go to the qualifying threads with the most activity (score, then NPC id).</li>
 *   <li>A lead keeps its slot; a new lead takes the lowest free slot. The HQ keeps a lead's module
 *   where it is for as long as the lead holds it, so offices never jump.</li>
 * </ul>
 * Not thread-safe: one instance, called from one thread (the client tick, throttled). Cost: one pass
 * over the shells of each environment.
 */
public final class LeadDetector {
	/** A child created within this window is "new": it stops by the lead's office ({@link LeadRoster#briefing()}). */
	public static final long BRIEFING_WINDOW_MS = 120_000;

	private final Map<String, Integer> seats = new TreeMap<>();
	private final Map<String, Long> since = new HashMap<>();
	private final Map<String, Long> lastQualified = new HashMap<>();
	private LeadRoster last = LeadRoster.EMPTY;

	/** What the detector measured for one candidate. */
	record Measure(String npcId, String env, String threadId, boolean qualifies, int score, int working, List<LeadSignals.Child> children) {
	}

	/**
	 * One update.
	 *
	 * @param byEnv    lineage signals per environment id
	 * @param eligible NPC ids ({@code env:thread}) that hold a desk in the HQ now
	 * @param nowMs    wall clock
	 */
	public LeadRoster update(Map<String, LeadSignals> byEnv, Predicate<String> eligible, LeadPolicy policy, long nowMs) {
		if (!policy.enabled() || policy.maxOffices() <= 0) {
			clear();
			return last;
		}
		Map<String, Measure> m = measure(byEnv, eligible, policy, nowMs);
		for (Measure x : m.values()) {
			if (x.qualifies()) {
				lastQualified.put(x.npcId(), nowMs);
			}
		}
		// 1. incumbents that stay (hysteresis)
		List<String> keep = new ArrayList<>();
		for (String id : seats.keySet()) {
			Measure x = m.get(id);
			if (x == null) {
				continue; // no desk any more, or no children at all: the office goes
			}
			long q = lastQualified.getOrDefault(id, 0L);
			long s = since.getOrDefault(id, nowMs);
			if (x.qualifies() || nowMs - q < policy.holdMs() || nowMs - s < policy.minHoldMs()) {
				keep.add(id);
			}
		}
		Comparator<String> byActivity = Comparator.<String>comparingInt(id -> -m.get(id).score()).thenComparing(id -> id);
		if (keep.size() > policy.maxOffices()) {
			keep.sort(byActivity);
			keep = new ArrayList<>(keep.subList(0, policy.maxOffices()));
		}
		seats.keySet().retainAll(new HashSet<>(keep));
		since.keySet().retainAll(seats.keySet());
		// 2. free offices to the most active newcomers, lowest free slot first
		List<String> fresh = new ArrayList<>();
		for (Measure x : m.values()) {
			if (x.qualifies() && !seats.containsKey(x.npcId())) {
				fresh.add(x.npcId());
			}
		}
		fresh.sort(byActivity);
		for (String id : fresh) {
			if (seats.size() >= policy.maxOffices()) {
				break;
			}
			seats.put(id, lowestFreeSlot());
			since.put(id, nowMs);
		}
		// forget old qualifications of threads without an office
		lastQualified.entrySet().removeIf(e -> !seats.containsKey(e.getKey()) && nowMs - e.getValue() > policy.holdMs());
		last = roster(m, nowMs, policy);
		return last;
	}

	/** The last roster made. */
	public LeadRoster last() {
		return last;
	}

	public void clear() {
		seats.clear();
		since.clear();
		lastQualified.clear();
		last = LeadRoster.EMPTY;
	}

	private int lowestFreeSlot() {
		Set<Integer> used = new HashSet<>(seats.values());
		int s = 0;
		while (used.contains(s)) {
			s++;
		}
		return s;
	}

	static Map<String, Measure> measure(Map<String, LeadSignals> byEnv, Predicate<String> eligible, LeadPolicy p, long nowMs) {
		Map<String, Measure> out = new TreeMap<>();
		for (Map.Entry<String, LeadSignals> e : new TreeMap<>(byEnv).entrySet()) {
			String env = e.getKey();
			LeadSignals s = e.getValue();
			if (s == null) {
				continue;
			}
			Collection<String> parents = s.parents();
			for (String t : parents) {
				String npc = env + ":" + t;
				if (s.isChild(t) || !eligible.test(npc)) {
					continue;
				}
				long created = s.createdAt(t);
				boolean oldEnough = created > 0 && nowMs - created >= p.minAgeMs();
				int recent = s.recentChildren(t, nowMs - p.recentWindowMs());
				int working = s.workingChildren(t);
				boolean q = oldEnough && (recent >= p.recentChildren() || working >= p.workingChildren());
				int score = 3 * working + 2 * recent + s.liveChildren(t);
				out.put(npc, new Measure(npc, env, t, q, score, working, s.children(t)));
			}
		}
		return out;
	}

	private LeadRoster roster(Map<String, Measure> m, long nowMs, LeadPolicy p) {
		List<LeadRoster.Lead> leads = new ArrayList<>();
		Map<String, String> brief = new TreeMap<>();
		for (Map.Entry<String, Integer> e : seats.entrySet()) {
			Measure x = m.get(e.getKey());
			if (x == null) {
				continue;
			}
			leads.add(new LeadRoster.Lead(x.npcId(), x.env(), x.threadId(), e.getValue(), x.working(), x.children()));
			if (p.briefing()) {
				for (LeadSignals.Child c : x.children()) {
					if (!c.gone() && c.delegated() && c.createdAt() != null && nowMs - c.createdAt() <= BRIEFING_WINDOW_MS) {
						brief.put(x.env() + ":" + c.threadId(), x.npcId());
					}
				}
			}
		}
		leads.sort(Comparator.comparingInt(LeadRoster.Lead::slot));
		return new LeadRoster(leads, brief);
	}
}
