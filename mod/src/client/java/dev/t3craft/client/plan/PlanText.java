package dev.t3craft.client.plan;

import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The plan's player-facing strings built from tasks and shell rows (translation keys {@code t3craft.plan.*}). */
final class PlanText {
	private PlanText() {
	}

	static String stage(PlanTask.Stage s) {
		return T3Draw.tr("t3craft.plan.stage." + s.wire);
	}

	/** A task's title, or its id when it has none. */
	static String title(PlanTask t) {
		return t.title().isBlank() ? t.id() : t.title();
	}

	/** "Depends on: A, B (accepted), unknown-id (?)", or null when it depends on nothing. */
	static @Nullable String deps(PlanTask t, List<PlanTask> all) {
		if (t.dependsOn().isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (String id : t.dependsOn()) {
			PlanTask d = all.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
			if (d == null) {
				names.add(id + " (?)");
			} else {
				names.add(title(d) + (d.stage() == PlanTask.Stage.ACCEPTED ? " " + T3Draw.tr("t3craft.plan.dep.accepted") : ""));
			}
		}
		return T3Draw.tr("t3craft.plan.deps", String.join(", ", names));
	}

	/** The thread's live state in words, from the shell row (unknown when not visible). */
	static String state(@Nullable ThreadShell s) {
		return s == null ? T3Draw.tr("t3craft.plan.thread.unseen") : T3Draw.tr("t3craft.plan.thread.state." + ThreadState.of(s).wire);
	}

	/** "Thread: env · title — state". */
	static String threadLine(PlanTask.ThreadRef ref, @Nullable ThreadShell s) {
		String title = s == null || s.title().isBlank() ? ref.threadId() : s.title();
		return T3Draw.tr("t3craft.plan.thread", OperateClient.label(ref.envId()), title, state(s));
	}

	/** The first message of a thread launched for {@code t}; the player can edit it before launching. */
	static String prompt(PlanTask t, List<PlanTask> all) {
		StringBuilder b = new StringBuilder();
		b.append(T3Draw.tr("t3craft.plan.prompt.task", title(t))).append("\n\n");
		if (!t.outcome().isBlank()) {
			b.append(T3Draw.tr("t3craft.plan.prompt.outcome")).append('\n').append(t.outcome()).append("\n\n");
		}
		String deps = deps(t, all);
		if (deps != null) {
			b.append(deps).append("\n\n");
		}
		if (t.hasDecision()) {
			b.append(T3Draw.tr("t3craft.plan.prompt.decision", t.decision())).append("\n\n");
		}
		if (!t.notes().isBlank()) {
			b.append(T3Draw.tr("t3craft.plan.prompt.notes")).append('\n').append(t.notes()).append("\n\n");
		}
		b.append(T3Draw.tr("t3craft.plan.prompt.report"));
		return b.toString();
	}
}
