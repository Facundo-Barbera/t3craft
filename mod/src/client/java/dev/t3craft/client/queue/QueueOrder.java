package dev.t3craft.client.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Queue ordering, same rules as T3's {@code QueuedRunOrder.queuedRunsInDeliveryOrder}: automatic
 * completion deliveries first, then {@code queuePosition ?? ordinal}, then {@code ordinal}. And the
 * moves the view offers, as the {@code beforeRunId} that {@code queued-run.reorder} expects
 * ({@code null} = to the end), computed over the movable runs like the server does.
 */
public final class QueueOrder {
	private QueueOrder() {
	}

	public static final Comparator<QueuedRun> DELIVERY = Comparator
			.comparing((QueuedRun r) -> !r.automatic())
			.thenComparingLong(r -> r.queuePosition() != null ? r.queuePosition() : r.ordinal())
			.thenComparingLong(QueuedRun::ordinal)
			.thenComparing(QueuedRun::id);

	/** Across environments and threads: by env, then thread, then delivery order. */
	public static final Comparator<QueuedRun> GLOBAL = Comparator.comparing(QueuedRun::envId)
			.thenComparing(QueuedRun::threadId)
			.thenComparing(DELIVERY);

	public static List<QueuedRun> sort(Collection<QueuedRun> runs) {
		List<QueuedRun> out = new ArrayList<>(runs);
		out.sort(DELIVERY);
		return out;
	}

	/** A reorder to send: move {@code runId} before {@code beforeRunId} (null = last). */
	public record Move(String runId, @Nullable String beforeRunId) {
	}

	/**
	 * Moving {@code runId} one place earlier among the movable runs of {@code queue} (delivery
	 * order), or null when it is already first, not movable or not queued.
	 */
	public static @Nullable Move up(List<QueuedRun> queue, String runId) {
		List<QueuedRun> m = movable(queue);
		int i = indexOf(m, runId);
		if (i <= 0) {
			return null;
		}
		return new Move(runId, m.get(i - 1).id());
	}

	/** One place later, or null when it is already last, not movable or not queued. */
	public static @Nullable Move down(List<QueuedRun> queue, String runId) {
		List<QueuedRun> m = movable(queue);
		int i = indexOf(m, runId);
		if (i < 0 || i >= m.size() - 1) {
			return null;
		}
		// Without the moving run, the one after the next sits at i + 1: insert before it, or at the end.
		return new Move(runId, i + 2 < m.size() ? m.get(i + 2).id() : null);
	}

	/** The queue as it would look after {@code move} (for tests and an optimistic view). */
	public static List<QueuedRun> apply(List<QueuedRun> queue, Move move) {
		List<QueuedRun> fixed = new ArrayList<>();
		List<QueuedRun> rest = new ArrayList<>();
		QueuedRun moving = null;
		for (QueuedRun r : sort(queue)) {
			if (r.id().equals(move.runId())) {
				moving = r;
			} else if (r.automatic()) {
				fixed.add(r);
			} else {
				rest.add(r);
			}
		}
		if (moving == null) {
			return sort(queue);
		}
		int at = move.beforeRunId() == null ? rest.size() : indexOf(rest, move.beforeRunId());
		rest.add(at < 0 ? rest.size() : at, moving);
		fixed.addAll(rest);
		return fixed;
	}

	private static List<QueuedRun> movable(List<QueuedRun> queue) {
		List<QueuedRun> out = new ArrayList<>();
		for (QueuedRun r : sort(queue)) {
			if (r.queued() && r.movable() && !r.automatic()) {
				out.add(r);
			}
		}
		return out;
	}

	private static int indexOf(List<QueuedRun> list, String runId) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(runId)) {
				return i;
			}
		}
		return -1;
	}
}
