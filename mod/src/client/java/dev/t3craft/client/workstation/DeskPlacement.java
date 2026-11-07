package dev.t3craft.client.workstation;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.AnchorSource;
import dev.t3craft.client.npc.behavior.Pos;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Provisional placement until the HQ (feat/hq-design) gives real desks: the computer goes one block
 * in front of the NPC's desk spot, so the NPC "types" facing its monitor. The desk spot is the same
 * one the behaviour director walks the NPC to when it has no physical base: the registry's
 * {@link dev.t3craft.client.npc.behavior.GridLayout} (all NPCs face −z there, so the computers line
 * up in rows).
 *
 * <p>Settled (archived) threads get no station here: their desk is vacated. Pure apart from the
 * layout supplier; unit-tested.
 */
public final class DeskPlacement implements WorkstationPlacementSource {
	/** Blocks between the desk spot (NPC feet) and the computer. */
	public static final double DISTANCE = 1.0;

	private final Supplier<@Nullable AnchorSource> layout;
	private @Nullable AnchorSource current;

	public DeskPlacement(Supplier<@Nullable AnchorSource> layout) {
		this.layout = layout;
	}

	@Override
	public void prepare(List<WorkstationThread> threads) {
		current = layout.get();
	}

	@Override
	public @Nullable StationPlacement placement(WorkstationThread t) {
		AnchorSource l = current;
		if (t.archived() || l == null) {
			return null;
		}
		Anchor desk = l.desk(t.key(), t.groupId());
		if (desk == null) {
			return null;
		}
		Pos p = desk.pos();
		return StationPlacement.inFrontOf(p.x(), p.y(), p.z(), desk.yaw(), DISTANCE);
	}

	@Override
	public String toString() {
		return "DeskPlacement[" + Objects.toString(current) + "]";
	}
}
