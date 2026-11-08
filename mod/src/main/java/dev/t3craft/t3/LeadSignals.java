package dev.t3craft.t3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: the few lineage facts the lead detector ({@code dev.t3craft.hq.lead.LeadDetector})
 * needs about the threads of <b>one</b> environment, so it never depends on how lineage is indexed.
 *
 * <p>{@link #of(Collection)} computes them from the shell list alone (children by
 * {@code parentThreadId}). When feat/thread-lineage lands, {@code dev.t3craft.t3.Lineage} can implement
 * this interface directly (children from its index, {@code isDelegated()} for {@link Child#delegated()},
 * live descendants for {@link #workingChildren}) and the detector does not change.
 */
public interface LeadSignals {
	/** One direct child of a thread. */
	record Child(String threadId, String title, String stateWire, @Nullable Long createdAt, boolean delegated, boolean working, boolean gone) {
	}

	/** Threads that have at least one child (the only possible leads), sorted. */
	Collection<String> parents();

	/** Creation time of {@code threadId} (epoch ms), 0 when unknown or not in the list. */
	long createdAt(String threadId);

	/** True when {@code threadId} is itself a child (subagent or fork) of another thread. */
	boolean isChild(String threadId);

	/** Direct children of {@code threadId} (subagents, forks and delegated tasks), oldest first. */
	List<Child> children(String threadId);

	/** Children (delegated or subagent, forks excluded) created at or after {@code sinceMs}. */
	default int recentChildren(String threadId, long sinceMs) {
		int n = 0;
		for (Child c : children(threadId)) {
			if (c.delegated() && c.createdAt() != null && c.createdAt() >= sinceMs) {
				n++;
			}
		}
		return n;
	}

	/** Children working right now (and not archived or deleted). */
	default int workingChildren(String threadId) {
		int n = 0;
		for (Child c : children(threadId)) {
			if (c.working() && !c.gone()) {
				n++;
			}
		}
		return n;
	}

	/** Children not archived, deleted or settled. */
	default int liveChildren(String threadId) {
		int n = 0;
		for (Child c : children(threadId)) {
			if (!c.gone()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Signals from the shells of one environment. A child counts as {@link Child#delegated()} unless its
	 * {@code relationshipToParent} is {@code "fork"} (forks are copies of a conversation, not work handed
	 * out). One pass over the list; safe to call off the render thread.
	 */
	static LeadSignals of(Collection<ThreadShell> shells) {
		Map<String, ThreadShell> byId = new HashMap<>();
		Map<String, List<Child>> kids = new HashMap<>();
		for (ThreadShell t : shells) {
			if (t != null && t.deletedAt() == null) {
				byId.put(t.id(), t);
			}
		}
		for (ThreadShell t : byId.values()) {
			String p = t.parentThreadId();
			if (p == null || p.isEmpty() || p.equals(t.id())) {
				continue;
			}
			ThreadState s = ThreadState.of(t);
			boolean fork = "fork".equals(t.relationshipToParent());
			boolean gone = t.archivedAt() != null || t.settledAt() != null;
			kids.computeIfAbsent(p, k -> new ArrayList<>())
					.add(new Child(t.id(), t.title(), s.wire, t.createdAt(), !fork, s == ThreadState.WORKING, gone));
		}
		for (List<Child> l : kids.values()) {
			l.sort((a, b) -> {
				long x = a.createdAt() == null ? 0 : a.createdAt();
				long y = b.createdAt() == null ? 0 : b.createdAt();
				return x != y ? Long.compare(x, y) : a.threadId().compareTo(b.threadId());
			});
		}
		TreeSet<String> parents = new TreeSet<>();
		for (String p : kids.keySet()) {
			if (byId.containsKey(p)) {
				parents.add(p);
			}
		}
		return new LeadSignals() {
			@Override
			public Collection<String> parents() {
				return parents;
			}

			@Override
			public long createdAt(String threadId) {
				ThreadShell t = byId.get(threadId);
				return t == null || t.createdAt() == null ? 0 : t.createdAt();
			}

			@Override
			public boolean isChild(String threadId) {
				ThreadShell t = byId.get(threadId);
				return t != null && t.parentThreadId() != null && !t.parentThreadId().isEmpty();
			}

			@Override
			public List<Child> children(String threadId) {
				List<Child> l = kids.get(threadId);
				return l == null ? List.of() : List.copyOf(l);
			}
		};
	}
}
