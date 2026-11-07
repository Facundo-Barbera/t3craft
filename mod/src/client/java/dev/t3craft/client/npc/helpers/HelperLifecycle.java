package dev.t3craft.client.npc.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure bookkeeping of which helpers are on screen, turning each new grouping into spawn / update /
 * despawn events with the reason a helper left:
 * <ul>
 *   <li>{@link Reason#PARENT_GONE}: its parent NPC is no longer shown (released with the parent);</li>
 *   <li>{@link Reason#FINISHED}: the subagent stopped running (done, failed, settled, gone);</li>
 *   <li>{@link Reason#HIDDEN}: still running but pushed over the per-parent cap (now in "+n"), or
 *       helpers were switched off.</li>
 * </ul>
 */
public final class HelperLifecycle {
	public enum Reason { FINISHED, PARENT_GONE, HIDDEN }

	/** A helper on screen: its view, the parent it follows and its slot in that parent's fan. */
	public record Placed(HelperView view, String parentId, int index, int count) {
	}

	public record Despawn(Placed last, Reason reason) {
	}

	public record Diff(List<Placed> spawned, List<Placed> updated, List<Despawn> despawned) {
		public boolean isEmpty() {
			return spawned.isEmpty() && updated.isEmpty() && despawned.isEmpty();
		}
	}

	private final Map<String, Placed> shown = new LinkedHashMap<>();

	/**
	 * @param families       the new grouping ({@link HelperRoster#group})
	 * @param runningIds     every running subagent, capped or not ({@link HelperRoster.Collected#activeIds})
	 * @param visibleParents NPC ids that currently have an NPC
	 */
	public Diff update(Map<String, HelperRoster.Family> families, Set<String> runningIds, Collection<String> visibleParents) {
		Map<String, Placed> next = new LinkedHashMap<>();
		for (HelperRoster.Family f : families.values()) {
			int n = f.shown().size();
			for (int i = 0; i < n; i++) {
				HelperView v = f.shown().get(i);
				next.put(v.id(), new Placed(v, f.parentId(), i, n));
			}
		}
		List<Placed> spawned = new ArrayList<>();
		List<Placed> updated = new ArrayList<>();
		List<Despawn> despawned = new ArrayList<>();
		for (Placed old : shown.values()) {
			if (next.containsKey(old.view().id())) {
				continue;
			}
			Reason r = !visibleParents.contains(old.parentId()) ? Reason.PARENT_GONE
					: !runningIds.contains(old.view().id()) ? Reason.FINISHED
					: Reason.HIDDEN;
			despawned.add(new Despawn(old, r));
		}
		for (Placed p : next.values()) {
			Placed old = shown.get(p.view().id());
			if (old == null) {
				spawned.add(p);
			} else if (!old.equals(p)) {
				updated.add(p);
			}
		}
		shown.clear();
		shown.putAll(next);
		return new Diff(spawned, updated, despawned);
	}

	public Map<String, Placed> shown() {
		return java.util.Collections.unmodifiableMap(shown);
	}

	/** Forget everything (level change, reload): no events, the entities are already gone. */
	public void reset() {
		shown.clear();
	}
}
