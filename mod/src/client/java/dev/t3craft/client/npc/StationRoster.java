package dev.t3craft.client.npc;

import dev.t3craft.hq.StationPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Applies {@link StationPolicy} to what the sources send (fix/hq-stale-stations): the registry
 * spawns, and every roster reader (workstations, demo HQ, portable, HQ preview) sees, only the
 * <b>stationed</b> threads; the <b>released</b> ones (settled, archived, done and inactive) lose their
 * NPC, nametag, desk and screens and are only listed for the archive ({@link #released()}, with
 * {@code settled = true}). Pure: the caller passes the clock.
 *
 * <p>Inactivity comes from {@code lastActivity} (direct mode: the thread's last message/run, from
 * its shell); a thread with no known timestamp (bridge mode) counts from when it was first seen done
 * ({@link StationPolicy.DoneClock}).
 */
public final class StationRoster {
	private final Map<String, NpcInfo> incoming = new LinkedHashMap<>();
	private final Map<String, Long> lastActivity = new LinkedHashMap<>();
	private final StationPolicy.DoneClock clock = new StationPolicy.DoneClock();
	private @Nullable StationPolicy policy;
	private List<NpcInfo> stationed = List.of();
	private List<NpcInfo> released = List.of();

	/** Overrides the shared policy for this roster (tests); null = {@link StationPolicy#active()}. */
	public void setPolicy(@Nullable StationPolicy policy) {
		this.policy = policy;
	}

	public StationPolicy policy() {
		StationPolicy p = policy;
		return p == null ? StationPolicy.active() : p;
	}

	/** Every NPC the source shows now; {@code activity}: NPC id → last activity epoch ms (missing = unknown). */
	public void replaceAll(List<NpcInfo> npcs, @Nullable Map<String, Long> activity) {
		incoming.clear();
		lastActivity.clear();
		for (NpcInfo n : npcs) {
			incoming.put(n.id(), n);
		}
		if (activity != null) {
			lastActivity.putAll(activity);
		}
	}

	public void upsert(NpcInfo npc) {
		incoming.put(npc.id(), npc);
	}

	public boolean remove(String id) {
		lastActivity.remove(id);
		return incoming.remove(id) != null;
	}

	public void clear() {
		incoming.clear();
		lastActivity.clear();
		clock.clear();
		stationed = List.of();
		released = List.of();
	}

	/**
	 * Re-applies the policy at {@code nowMs}. Returns true when the stationed set changed (ids or
	 * content), i.e. the world must be reconciled.
	 */
	public boolean regate(long nowMs) {
		StationPolicy.Split<NpcInfo> s = policy().split(incoming.values(), NpcInfo::id, this::facts, clock, nowMs);
		List<NpcInfo> archived = s.released().stream().map(StationRoster::asSettled).toList();
		boolean changed = !s.stationed().equals(stationed);
		stationed = s.stationed();
		released = archived;
		return changed;
	}

	/** Everything the source shows (stationed or not), in source order. */
	public List<NpcInfo> all() {
		return List.copyOf(incoming.values());
	}

	/** Threads that hold a station (NPC, nametag, desk, screens), in source order. */
	public List<NpcInfo> stationed() {
		return stationed;
	}

	/** Threads that released their station, marked settled: they only go to the archive. */
	public List<NpcInfo> released() {
		return released;
	}

	StationPolicy.Facts facts(NpcInfo n) {
		Long t = lastActivity.get(n.id());
		return new StationPolicy.Facts(n.settled(), false, n.state(), t == null ? 0 : t);
	}

	static NpcInfo asSettled(NpcInfo n) {
		return n.settled() ? n : new NpcInfo(n.id(), n.title(), n.label(), n.envLabel(), n.sublabel(), n.color(), n.activity(), n.priority(),
				true, n.state(), n.groupId(), n.groupLabel(), n.skin(), n.particle(), n.model(), n.provider(), n.branch(), n.worktree());
	}
}
