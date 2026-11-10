package dev.t3craft.plan;

import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The one thing of the plan that needs the player next, for the line at the top of the plan page. In order: a
 * delivery waiting for review, a linked thread that asks for the player or failed, a decision the player owes, a
 * proposal not accepted yet. Pure: reads the tasks and the shell rows the client already has.
 */
public final class PlanAttention {
	private PlanAttention() {
	}

	public enum Reason {
		REVIEW, THREAD_NEEDS_YOU, THREAD_ERROR, DECISION, PROPOSAL;

		public final String wire = name().toLowerCase(Locale.ROOT);
	}

	public record Next(PlanTask task, Reason reason) {
	}

	public static @Nullable Next next(List<PlanTask> tasks, Function<PlanTask.ThreadRef, @Nullable ThreadShell> thread) {
		for (PlanTask t : tasks) {
			if (t.stage() == PlanTask.Stage.IN_REVIEW) {
				return new Next(t, Reason.REVIEW);
			}
		}
		for (PlanTask t : tasks) {
			if (t.stage() == PlanTask.Stage.IN_PROGRESS && t.link() != null) {
				ThreadShell s = thread.apply(t.link().ref());
				ThreadState st = s == null ? null : ThreadState.of(s);
				if (st == ThreadState.NEEDS_ATTENTION) {
					return new Next(t, Reason.THREAD_NEEDS_YOU);
				}
				if (st == ThreadState.ERROR) {
					return new Next(t, Reason.THREAD_ERROR);
				}
			}
		}
		for (PlanTask t : tasks) {
			if (t.inPlan() && t.stage() != PlanTask.Stage.ACCEPTED && t.hasDecision()) {
				return new Next(t, Reason.DECISION);
			}
		}
		for (PlanTask t : tasks) {
			if (t.stage() == PlanTask.Stage.PROPOSED) {
				return new Next(t, Reason.PROPOSAL);
			}
		}
		return null;
	}
}
