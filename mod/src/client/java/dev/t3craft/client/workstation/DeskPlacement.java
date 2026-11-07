package dev.t3craft.client.workstation;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.client.npc.behavior.RingLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Provisional placement until the HQ (feat/hq-design) gives real desks: the computer goes one block
 * in front of the NPC's desk spot, so the NPC "types" facing its monitor. The desk spot is the same
 * one the behaviour director walks the NPC to when it has no physical base: the {@link RingLayout}
 * built from the registry's layout origin and the visible threads (same members, same order).
 *
 * <p>Settled (archived) threads get no station here: their desk is vacated. Pure apart from the
 * origin supplier; unit-tested.
 */
public final class DeskPlacement implements WorkstationPlacementSource {
	/** Blocks between the desk spot (NPC feet) and the computer. */
	public static final double DISTANCE = 1.0;

	private final Supplier<@Nullable Pos> origin;
	private @Nullable RingLayout ring;
	private @Nullable List<RingLayout.Member> members;
	private @Nullable Pos ringOrigin;

	public DeskPlacement(Supplier<@Nullable Pos> origin) {
		this.origin = origin;
	}

	@Override
	public void prepare(List<WorkstationThread> threads) {
		Pos o = origin.get();
		if (o == null) {
			ring = null;
			members = null;
			ringOrigin = null;
			return;
		}
		// Same member list as BehaviorDirector.ring(): every NPC the registry shows, sorted by id.
		List<RingLayout.Member> ms = new ArrayList<>(threads.size());
		for (WorkstationThread t : threads) {
			ms.add(new RingLayout.Member(t.key(), t.groupId(), t.priority()));
		}
		ms.sort(Comparator.comparing(RingLayout.Member::id));
		if (ring == null || !ms.equals(members) || !o.equals(ringOrigin)) {
			ring = RingLayout.of(o, ms);
			members = ms;
			ringOrigin = o;
		}
	}

	@Override
	public @Nullable StationPlacement placement(WorkstationThread t) {
		if (t.archived() || ring == null) {
			return null;
		}
		Anchor desk = ring.desk(t.key(), t.groupId());
		if (desk == null) {
			return null;
		}
		Pos p = desk.pos();
		return StationPlacement.inFrontOf(p.x(), p.y(), p.z(), desk.yaw(), DISTANCE);
	}

	@Override
	public String toString() {
		return "DeskPlacement[origin=" + Objects.toString(ringOrigin) + "]";
	}
}
