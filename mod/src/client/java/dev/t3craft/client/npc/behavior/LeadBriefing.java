package dev.t3craft.client.npc.behavior;

import dev.t3craft.hq.lead.LeadOffices;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: a lead's new children "get their instructions" first. A child the lead roster lists
 * in {@link dev.t3craft.hq.lead.LeadRoster#briefing()} (delegated a moment ago) has its desk target
 * swapped for a spot just behind its lead (facing the lead and its screen) until it has stood there for
 * {@link #LISTEN_MS}, or for at most {@link #MAX_MS}; then it goes to its own desk.
 *
 * <p>Costs nothing extra: it only changes the anchor the director hands the brain, so the walk uses the
 * same async routes and per-tick budget as any desk change (a brand-new NPC simply appears in the
 * office instead of at its desk, then walks over). Each child is briefed once.
 */
final class LeadBriefing {
	static final long LISTEN_MS = 4_000;
	static final long MAX_MS = 30_000;
	/** How far behind the lead the visitor stands (blocks). */
	static final double BEHIND = 1.6;
	static final double ARRIVED = 1.5;

	private final Map<String, long[]> visits = new HashMap<>();
	private final Set<String> done = new HashSet<>();

	/**
	 * The anchor {@code npcId} should go to now: {@code desk}, or the briefing spot in its lead's office.
	 *
	 * @param at where the NPC is now
	 */
	@Nullable Anchor redirect(String npcId, @Nullable Anchor desk, AnchorSource layout, Pos at, long nowMs) {
		Map<String, String> brief = LeadOffices.current().briefing();
		String lead = brief.get(npcId);
		if (lead == null || desk == null || done.contains(npcId) || lead.equals(npcId)) {
			return desk;
		}
		Anchor leadDesk = layout.desk(lead, "");
		if (leadDesk == null) {
			return desk;
		}
		double rad = Math.toRadians(leadDesk.yaw());
		double dx = -Math.sin(rad);
		double dz = Math.cos(rad);
		Anchor spot = new Anchor(leadDesk.pos().add(-dx * BEHIND, 0, -dz * BEHIND), leadDesk.yaw());
		long[] v = visits.computeIfAbsent(npcId, k -> new long[] {nowMs, 0});
		if (v[1] == 0 && at.horizDist(spot.pos()) <= ARRIVED && Math.abs(at.y() - spot.pos().y()) < 1.5) {
			v[1] = nowMs;
		}
		if ((v[1] != 0 && nowMs - v[1] >= LISTEN_MS) || nowMs - v[0] >= MAX_MS) {
			done.add(npcId);
			visits.remove(npcId);
			return desk;
		}
		return spot;
	}

	/** Forgets children that left the briefing list (call now and then). */
	void prune() {
		Map<String, String> brief = LeadOffices.current().briefing();
		visits.keySet().retainAll(brief.keySet());
		done.retainAll(brief.keySet());
	}

	void clear() {
		visits.clear();
		done.clear();
	}
}
