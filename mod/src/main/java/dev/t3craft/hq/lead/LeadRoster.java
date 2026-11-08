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
 */
public record LeadRoster(List<Lead> leads, Map<String, String> briefing) {
	public static final LeadRoster EMPTY = new LeadRoster(List.of(), Map.of());

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
