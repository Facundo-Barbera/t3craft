package dev.t3craft.client.npc.behavior;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where NPCs go, supplied by whoever owns the physical base (HQ builder, schematic, ...). Every
 * method may return null: the {@link BehaviorDirector} then falls back to the {@link GridLayout}
 * (a provisional office of desk rows, one block per neighbourhood, next to where the player first
 * stood), so NPCs behave with or without a base.
 *
 * <p><b>Integration point.</b> A base implementation registers itself with
 * {@code T3CraftClient.get().npcs().behavior().setAnchorSource(src)} (and {@code setAnchorSource(null)} when the
 * base is gone). Calls happen on the client thread, a few times per second at most per NPC: return
 * cached values, don't scan the world here.
 */
public interface AnchorSource {
	/** Nothing physical: everything comes from the fallback grid layout. */
	AnchorSource NONE = new AnchorSource() {
	};

	/** The NPC's desk/workstation: where it stands (or sits) and "types" while working. */
	default @Nullable Anchor desk(String npcId, String groupId) {
		return null;
	}

	/**
	 * The central board where NPCs that need attention gather. The director spreads several NPCs
	 * over {@link #boardSlots()} when given, else on an arc in front of this anchor (facing its yaw).
	 */
	default @Nullable Anchor board() {
		return null;
	}

	/** Explicit standing spots in front of the board, nearest first (optional). */
	default List<Anchor> boardSlots() {
		return List.of();
	}

	/** Centre of a neighbourhood (environment + project): idle NPCs wander around it. */
	default @Nullable Anchor home(String groupId) {
		return null;
	}

	/** How far (blocks) idle NPCs may wander from {@link #home}. */
	default double wanderRadius(String groupId) {
		return 4.0;
	}

	/** Where a completed NPC rests (bench, couch). Null = at its desk, else near home. */
	default @Nullable Anchor rest(String npcId, String groupId) {
		return null;
	}

	/** Where a settled NPC walks to before fading out ({@link Departure}), e.g. the HQ entrance. Null = its {@link #home}. */
	default @Nullable Anchor exit() {
		return null;
	}
}
