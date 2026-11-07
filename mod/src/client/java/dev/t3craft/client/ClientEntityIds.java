package dev.t3craft.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The client-only entity id ranges (docs/feat-workstation.md, docs/feat-npc-behavior.md,
 * docs/feat-npc-helpers.md). Each owner counts down from its base and owns one million ids, so an
 * orphan sweep only ever discards entities of <b>its own</b> range (before fix/hq-stale-stations the
 * NPC registry treated every id ≤ −1,000,000 as its own and could wipe the workstations).
 *
 * <pre>
 *  −1,000,000 … −1,999,999  NpcRegistry (avatars, nametag panels, neighbourhood signs)
 *  −2,000,000 … −2,999,999  builders (crews)
 *  −3,000,000 … −3,999,999  BehaviorDirector (bubbles, icons)
 *  −4,000,000 … −4,999,999  HelperDirector (helpers)
 *  −5,000,000 … −5,999,999  workstations (screens, computer props, hitboxes)
 * </pre>
 */
public enum ClientEntityIds {
	NPC(-1_000_000),
	BUILDERS(-2_000_000),
	BEHAVIOR(-3_000_000),
	HELPERS(-4_000_000),
	WORKSTATION(-5_000_000);

	public static final int SPAN = 1_000_000;

	public final int base;

	ClientEntityIds(int base) {
		this.base = base;
	}

	public boolean contains(int id) {
		return id <= base && id > base - SPAN;
	}

	/**
	 * Ids of {@code present} in this range that {@code tracked} does not list: the orphans to
	 * discard (stations or NPCs of threads that are gone, entities of an old session). Ids of other
	 * ranges and server entities are never returned.
	 */
	public List<Integer> orphans(Collection<Integer> present, Set<Integer> tracked) {
		List<Integer> out = new ArrayList<>();
		for (int id : present) {
			if (contains(id) && !tracked.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}
}
