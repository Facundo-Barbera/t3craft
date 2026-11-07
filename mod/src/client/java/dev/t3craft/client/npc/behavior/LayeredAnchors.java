package dev.t3craft.client.npc.behavior;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code primary} first, {@code fallback} for whatever the primary leaves null. */
record LayeredAnchors(AnchorSource primary, AnchorSource fallback) implements AnchorSource {
	@Override
	public @Nullable Anchor desk(String npcId, String groupId) {
		Anchor a = primary.desk(npcId, groupId);
		return a != null ? a : fallback.desk(npcId, groupId);
	}

	@Override
	public @Nullable Anchor board() {
		Anchor a = primary.board();
		return a != null ? a : fallback.board();
	}

	@Override
	public List<Anchor> boardSlots() {
		// Slots only make sense with the board they belong to.
		return primary.board() != null ? primary.boardSlots() : fallback.boardSlots();
	}

	@Override
	public @Nullable Anchor home(String groupId) {
		Anchor a = primary.home(groupId);
		return a != null ? a : fallback.home(groupId);
	}

	@Override
	public double wanderRadius(String groupId) {
		return primary.home(groupId) != null ? primary.wanderRadius(groupId) : fallback.wanderRadius(groupId);
	}

	@Override
	public @Nullable Anchor rest(String npcId, String groupId) {
		Anchor a = primary.rest(npcId, groupId);
		return a != null ? a : fallback.rest(npcId, groupId);
	}
}
