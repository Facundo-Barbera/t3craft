package dev.t3craft.client.npc.helpers;

import dev.t3craft.t3.ThreadState;

/**
 * One running subagent shown as a "helper" next to its parent's NPC.
 *
 * @param id        helper NPC id ({@code env:threadId}, same scheme as {@code Presentation.npcId})
 * @param parentId  NPC id of the direct parent thread ({@code env:parentThreadId}); may itself be a
 *                  subagent, {@link HelperRoster#group} resolves it to the nearest visible ancestor
 * @param title     thread title (unclipped; the renderer clips)
 * @param state     the subagent's own state (its colour and glyph)
 * @param startedAt epoch millis used for stable ordering (oldest helper keeps its slot)
 */
public record HelperView(String id, String parentId, String title, ThreadState state, long startedAt) {
	public int color() {
		return state.color;
	}

	/** Stable visual variant in {@code [0, n)} derived from the id (same helper, same look). */
	public int variant(int n) {
		return n <= 0 ? 0 : Math.floorMod(id.hashCode() * 31 + 7, n);
	}
}
