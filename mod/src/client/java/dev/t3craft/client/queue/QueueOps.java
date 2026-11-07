package dev.t3craft.client.queue;

import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Queue management on top of {@link OperateGate} (Minecraft-free, used by the command and, through
 * {@code OperateUi}, by {@code QueueScreen}). Resolves a run in a known queue, builds the action
 * with {@link QueueActions} and leaves every rule to the gate: read-only without an operate token,
 * cancel needs {@code confirmed}, everything audited.
 */
public final class QueueOps {
	public enum Direction {
		UP, DOWN
	}

	private final OperateGate gate;
	private final QueueActions actions;

	public QueueOps(OperateGate gate, QueueActions actions) {
		this.gate = gate;
		this.actions = actions;
	}

	public OperateGate gate() {
		return gate;
	}

	/** Finds a queued run by id or by unique id prefix (as typed in a command); null if none or ambiguous. */
	public static @Nullable QueuedRun find(List<QueuedRun> queue, String idOrPrefix) {
		QueuedRun hit = null;
		for (QueuedRun r : queue) {
			if (r.id().equals(idOrPrefix)) {
				return r;
			}
			if (!idOrPrefix.isEmpty() && r.id().startsWith(idOrPrefix)) {
				if (hit != null) {
					return null;
				}
				hit = r;
			}
		}
		return hit;
	}

	/** The cancel action for {@code run}; send it with {@code OperateUi.run} (confirmation) or {@link #submit}. */
	public OperateAction cancelAction(QueuedRun run, @Nullable String threadTitle) {
		return actions.cancel(run, threadTitle);
	}

	/** The reorder action, or null when the run cannot move that way. */
	public @Nullable OperateAction moveAction(List<QueuedRun> queue, QueuedRun run, Direction dir, @Nullable String threadTitle) {
		QueueOrder.Move m = dir == Direction.UP ? QueueOrder.up(queue, run.id()) : QueueOrder.down(queue, run.id());
		return m == null ? null : actions.reorder(run.envId(), run.threadId(), threadTitle, m);
	}

	public CompletableFuture<OperateResult> submit(OperateAction action, OperateGate.Origin origin, boolean confirmed) {
		return gate.submit(action, origin, confirmed);
	}

	/** Cancel from a command: {@code confirmed} only when the player typed {@code confirm}. */
	public CompletableFuture<OperateResult> cancel(QueuedRun run, @Nullable String threadTitle, OperateGate.Origin origin, boolean confirmed) {
		return submit(cancelAction(run, threadTitle), origin, confirmed);
	}

	/** Reorder (not destructive). Rejected locally, without sending, when it cannot move. */
	public CompletableFuture<OperateResult> move(List<QueuedRun> queue, QueuedRun run, Direction dir, @Nullable String threadTitle,
			OperateGate.Origin origin) {
		OperateAction a = moveAction(queue, run, dir, threadTitle);
		if (a == null) {
			return CompletableFuture.completedFuture(OperateResult.of(OperateResult.Outcome.REJECTED,
					dir == Direction.UP ? "already first (or not movable)" : "already last (or not movable)"));
		}
		return submit(a, origin, false);
	}
}
