package dev.t3craft.hq;

import dev.t3craft.client.npc.behavior.AnchorSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * NPC behaviour anchors from a placed HQ: adapts a {@link BuildPlan} at an {@link HqSite} to the
 * {@link AnchorSource} of {@code dev.t3craft.client.npc.behavior}. Everything is transformed to world
 * coordinates once, in the constructor; lookups are map reads (cheap enough for the client tick).
 *
 * <ul>
 *   <li>{@link #desk}: {@code desk_<npcId>} (the chair, facing the monitor). The HQ thread ids are
 *   {@code NpcView.id()} when the input comes from {@link HqInput#fromNpcs}, the same ids the
 *   director passes as {@code npcId}.</li>
 *   <li>{@link #board}: {@code board_front} facing the board; {@link #boardSlots}: {@code board_slot_*}.</li>
 *   <li>{@link #home}: {@code walk_<groupId>} (the wing corridor); {@link #wanderRadius}: half a wing's
 *   minimum length.</li>
 *   <li>{@link #rest}: one of the hub benches, picked by a stable hash of the NPC id.</li>
 * </ul>
 * Unknown ids return null, so the director falls back to its ring layout for them.
 *
 * <p>Register with {@code T3CraftClient.get().npcs().behavior().setAnchorSource(new HqAnchorSource(plan, site))}
 * once the HQ is built, and {@code setAnchorSource(null)} when it is gone.
 */
public final class HqAnchorSource implements AnchorSource {
	/** Idle NPCs stay within this distance of their wing's stroll point. */
	public static final double WING_WANDER = HqGeometry.SEGMENT * HqLayout.SEGMENTS / 2.0 - 1.0;

	private final Map<String, dev.t3craft.client.npc.behavior.Anchor> byName = new HashMap<>();
	private final List<dev.t3craft.client.npc.behavior.Anchor> boardSlots;
	private final List<dev.t3craft.client.npc.behavior.Anchor> benches;

	public HqAnchorSource(BuildPlan plan, HqSite site) {
		List<dev.t3craft.client.npc.behavior.Anchor> slots = new ArrayList<>();
		List<dev.t3craft.client.npc.behavior.Anchor> seats = new ArrayList<>();
		for (Anchor a : plan.anchors().values()) {
			dev.t3craft.client.npc.behavior.Anchor w = convert(site.anchor(a));
			byName.put(a.name(), w);
		}
		for (int i = 0; byName.containsKey(AnchorNames.boardSlot(i)); i++) {
			slots.add(byName.get(AnchorNames.boardSlot(i)));
		}
		for (int i = 0; byName.containsKey(AnchorNames.bench(i)); i++) {
			seats.add(byName.get(AnchorNames.bench(i)));
		}
		this.boardSlots = List.copyOf(slots);
		this.benches = List.copyOf(seats);
	}

	/** A world-space HQ anchor as a behaviour anchor (feet position and yaw). */
	public static dev.t3craft.client.npc.behavior.Anchor convert(Anchor a) {
		return dev.t3craft.client.npc.behavior.Anchor.at(a.x(), a.y(), a.z(), a.yaw());
	}

	/** Any anchor by its {@link AnchorNames} name, in world coordinates. */
	public dev.t3craft.client.npc.behavior.@Nullable Anchor named(String name) {
		return byName.get(name);
	}

	@Override
	public dev.t3craft.client.npc.behavior.@Nullable Anchor desk(String npcId, String groupId) {
		return byName.get(AnchorNames.desk(npcId));
	}

	@Override
	public dev.t3craft.client.npc.behavior.@Nullable Anchor board() {
		return byName.get(AnchorNames.BOARD_FRONT);
	}

	@Override
	public List<dev.t3craft.client.npc.behavior.Anchor> boardSlots() {
		return boardSlots;
	}

	@Override
	public dev.t3craft.client.npc.behavior.@Nullable Anchor home(String groupId) {
		return byName.get(AnchorNames.walk(groupId));
	}

	@Override
	public double wanderRadius(String groupId) {
		return WING_WANDER;
	}

	@Override
	public dev.t3craft.client.npc.behavior.@Nullable Anchor rest(String npcId, String groupId) {
		if (benches.isEmpty() || !byName.containsKey(AnchorNames.desk(npcId))) {
			return null;
		}
		return benches.get(Math.floorMod(npcId.hashCode(), benches.size()));
	}

	/** Settled NPCs leave through the hub's main doorway. */
	@Override
	public dev.t3craft.client.npc.behavior.@Nullable Anchor exit() {
		return byName.get(AnchorNames.ENTRANCE);
	}
}
