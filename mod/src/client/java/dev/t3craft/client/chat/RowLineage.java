package dev.t3craft.client.chat;

import dev.t3craft.t3.Lineage;
import dev.t3craft.t3.Presentation;
import dev.t3craft.t3.ThreadShell;
import org.jspecify.annotations.Nullable;

/**
 * feat/thread-lineage: where a sidebar row sits in its thread family. Only delegated tasks
 * ({@link ThreadShell#isDelegated()}, T3's {@code delegate_task} children) carry a link: provider
 * subagents are not listed (they live inside their parent's view, as in T3 Code).
 *
 * @param parentKey   row key ({@code env:threadId}) of the parent, or null when it is in another
 *                    environment ({@code delegatedFrom}) or unknown
 * @param parentTitle parent title when known, else ""
 * @param delegated   the row is a delegated task (draws the "delegated" mark)
 */
public record RowLineage(@Nullable String parentKey, String parentTitle, boolean delegated) {
	public static final RowLineage NONE = new RowLineage(null, "", false);

	public RowLineage {
		parentTitle = parentTitle == null ? "" : parentTitle;
	}

	public boolean hasParent() {
		return parentKey != null || !parentTitle.isBlank();
	}

	/** From a shell thread and its environment's {@link Lineage}. */
	public static RowLineage of(String env, ThreadShell t, Lineage lineage) {
		if (t.isDelegated() && t.parentThreadId() != null && !t.parentThreadId().isBlank()) {
			ThreadShell p = lineage.parent(t.id());
			return new RowLineage(Presentation.npcId(env, t.parentThreadId()), p == null ? "" : p.title(), true);
		}
		ThreadShell.DelegatedFrom from = t.delegatedFrom();
		if (from != null) {
			// delegated from a thread of another linked environment: no local row to nest under
			return new RowLineage(null, from.title(), true);
		}
		return NONE;
	}
}
