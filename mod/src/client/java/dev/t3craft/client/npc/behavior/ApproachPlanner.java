package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides which NPCs in {@link Mode#ATTENTION} leave their spot and come to the player (inventario
 * item 16), and where they stand: about {@link #COMFORT} blocks from the player, on the side they
 * come from, fanned out so two approachers don't share a spot, facing the player.
 *
 * <p>Limits ({@link ApproachConfig}): only when the player is within {@code maxDistance} of the NPC,
 * at most {@code maxConcurrent} at once (most urgent first), a per-NPC cooldown after an approach
 * ends, and a time limit after which the NPC goes back to its spot for the rest of that attention
 * episode. An approach ends when the thread leaves ATTENTION, the player goes away (beyond
 * {@code maxDistance} + {@link #LEAVE_SLACK}) or the time runs out. No Minecraft classes; tested.
 */
public final class ApproachPlanner {
	/** One NPC as the planner sees it. */
	public record Candidate(String id, Pos pos, boolean attention, int priority) {
	}

	static final double COMFORT = 2.5;
	/** The spot is recomputed when the player moved farther than this from where it was computed. */
	static final double FOLLOW = 3.0;
	static final double LEAVE_SLACK = 8.0;
	static final double MAX_DY = 6.0;
	static final double FAN_DEGREES = 35.0;

	private static final class Active {
		final long start;
		final int slot;
		Pos playerRef;
		Anchor spot;

		Active(long start, int slot, Pos playerRef, Anchor spot) {
			this.start = start;
			this.slot = slot;
			this.playerRef = playerRef;
			this.spot = spot;
		}
	}

	private final Map<String, Active> active = new LinkedHashMap<>();
	private final Map<String, Long> cooldownUntil = new HashMap<>();
	/** Timed out in the current attention episode: don't come again until it leaves ATTENTION. */
	private final Set<String> spent = new HashSet<>();

	public boolean approaching(String id) {
		return active.containsKey(id);
	}

	public void reset() {
		active.clear();
		cooldownUntil.clear();
		spent.clear();
	}

	/** One tick: returns the approach spot per approaching NPC id (absent = use its normal spot). */
	public Map<String, Anchor> update(long tick, ApproachConfig cfg, @Nullable Pos player, List<Candidate> npcs) {
		Map<String, Candidate> byId = new HashMap<>();
		for (Candidate c : npcs) {
			byId.put(c.id(), c);
			if (!c.attention()) {
				spent.remove(c.id());
			}
		}
		cooldownUntil.values().removeIf(t -> t <= tick);
		spent.retainAll(byId.keySet());

		// End approaches that no longer apply.
		for (var it = active.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Candidate c = byId.get(e.getKey());
			boolean timeUp = tick - e.getValue().start >= cfg.maxTicks();
			if (!cfg.enabled() || c == null || !c.attention() || player == null || timeUp
					|| c.pos().horizDist(player) > cfg.maxDistance() + LEAVE_SLACK) {
				it.remove();
				cooldownUntil.put(e.getKey(), tick + cfg.cooldownTicks());
				if (timeUp && c != null && c.attention()) {
					spent.add(e.getKey());
				}
			}
		}
		if (!cfg.enabled() || player == null) {
			return Map.of();
		}

		// Start new ones, most urgent first.
		List<Candidate> eligible = new ArrayList<>();
		for (Candidate c : npcs) {
			if (c.attention() && !active.containsKey(c.id()) && !spent.contains(c.id()) && !cooldownUntil.containsKey(c.id())
					&& c.pos().horizDist(player) <= cfg.maxDistance() && Math.abs(c.pos().y() - player.y()) <= MAX_DY) {
				eligible.add(c);
			}
		}
		eligible.sort(Comparator.comparingInt(Candidate::priority).thenComparing(Candidate::id));
		for (Candidate c : eligible) {
			if (active.size() >= cfg.maxConcurrent()) {
				break;
			}
			int slot = freeSlot();
			active.put(c.id(), new Active(tick, slot, player, spot(player, c.pos(), slot)));
		}

		// Follow the player loosely (re-plan only after they moved a few blocks).
		Map<String, Anchor> out = new LinkedHashMap<>();
		for (var e : active.entrySet()) {
			Active a = e.getValue();
			if (a.playerRef.horizDist(player) > FOLLOW) {
				a.playerRef = player;
				a.spot = spot(player, byId.get(e.getKey()).pos(), a.slot);
			}
			out.put(e.getKey(), a.spot);
		}
		return out;
	}

	private int freeSlot() {
		Set<Integer> used = new HashSet<>();
		for (Active a : active.values()) {
			used.add(a.slot);
		}
		int s = 0;
		while (used.contains(s)) {
			s++;
		}
		return s;
	}

	/** Where slot {@code slot} stands: COMFORT blocks from the player towards {@code from}, fanned by slot. */
	static Anchor spot(Pos player, Pos from, int slot) {
		double dx = from.x() - player.x();
		double dz = from.z() - player.z();
		double len = Math.sqrt(dx * dx + dz * dz);
		double ang = len < 1e-3 ? 0 : Math.atan2(dz, dx);
		int k = (slot + 1) / 2;
		ang += Math.toRadians((slot % 2 == 1 ? 1 : -1) * k * FAN_DEGREES);
		Pos p = new Pos(player.x() + Math.cos(ang) * COMFORT, player.y(), player.z() + Math.sin(ang) * COMFORT);
		return new Anchor(p, p.yawTo(player));
	}
}
