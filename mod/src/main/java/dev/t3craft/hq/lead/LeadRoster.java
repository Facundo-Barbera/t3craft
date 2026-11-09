package dev.t3craft.hq.lead;

import dev.t3craft.t3.LeadSignals;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: who holds a lead office right now. Immutable; made by {@link LeadDetector}, shared
 * through {@link LeadOffices#current()}.
 *
 * @param leads    the leads, sorted by {@link Lead#slot()} (a lead keeps its slot while it holds the office)
 * @param briefing new children (NPC id) that should stop by their lead's office first, to the lead's NPC id
 * @param reviews  feat/lead-review-walk: finished children (NPC id) waiting for their parent's review, to
 *                 who reviews them; filled by the client ({@link #withReviews}), empty from the detector
 */
public record LeadRoster(List<Lead> leads, Map<String, String> briefing, Map<String, Review> reviews) {
	public static final LeadRoster EMPTY = new LeadRoster(List.of(), Map.of(), Map.of());

	/**
	 * feat/lead-review-walk: a finished child waits for its parent to review its work.
	 *
	 * @param parentNpcId the reviewer's NPC id ({@code <env>:<threadId>}): the lead, or the plain parent
	 * @param parentTitle the reviewer's thread title ("awaiting review from &lt;title&gt;")
	 * @param inOffice    true when the reviewer holds a lead office (wait there), false = by its desk
	 */
	public record Review(String parentNpcId, String parentTitle, boolean inOffice) {
	}

	/** Shape before feat/lead-review-walk (no reviews). */
	public LeadRoster(List<Lead> leads, Map<String, String> briefing) {
		this(leads, briefing, Map.of());
	}

	/** This roster with {@code r} as its reviews. */
	public LeadRoster withReviews(Map<String, Review> r) {
		return new LeadRoster(leads, briefing, r);
	}

	/**
	 * feat/lead-review-walk: every live delegated child of a lead (NPC id) to its lead's NPC id. Used to
	 * seat a lead's new children near its office ({@link LeadTeamSeats}).
	 */
	public Map<String, String> childrenOfLeads() {
		Map<String, String> out = new java.util.TreeMap<>();
		for (Lead l : leads) {
			for (LeadSignals.Child c : l.children()) {
				if (c.delegated() && !c.gone()) {
					out.put(l.childNpcId(c), l.npcId());
				}
			}
		}
		return out;
	}

	/**
	 * One lead.
	 *
	 * @param npcId        {@code <env>:<threadId>}, the id the HQ roster and the NPCs use
	 * @param slot         office slot, stable while the lead holds it
	 * @param coordinating children working now (the "Coordinating N agents" plate)
	 * @param children     the lead's direct children, oldest first
	 */
	public record Lead(String npcId, String env, String threadId, int slot, int coordinating, List<LeadSignals.Child> children) {
		public Lead {
			children = List.copyOf(children);
		}

		/** Children still alive (not archived, settled or deleted). */
		public List<LeadSignals.Child> liveChildren() {
			return children.stream().filter(c -> !c.gone()).toList();
		}

		/** NPC id of a child of this lead. */
		public String childNpcId(LeadSignals.Child c) {
			return env + ":" + c.threadId();
		}
	}

	public LeadRoster {
		leads = List.copyOf(leads);
		briefing = Map.copyOf(briefing);
		reviews = Map.copyOf(reviews);
	}

	public @Nullable Lead lead(String npcId) {
		for (Lead l : leads) {
			if (l.npcId().equals(npcId)) {
				return l;
			}
		}
		return null;
	}

	public boolean isLead(String npcId) {
		return lead(npcId) != null;
	}

	public boolean isEmpty() {
		return leads.isEmpty();
	}
}
