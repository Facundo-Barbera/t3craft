package dev.t3craft.plan;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * One task of the project plan (docs/features/project-plan.md). Immutable: {@link PlanStore} replaces it on every
 * change. The three review marks are kept apart on purpose and never derived from one another:
 * <ul>
 *   <li><b>implemented</b>: {@link #stage} reached {@link Stage#IN_REVIEW} because the linked thread finished its turn
 *   (observed from the shell, {@link PlanReview});</li>
 *   <li><b>checked by the agent</b>: {@link #agentChecked}, free text of what the agent says it checked; empty means
 *   "nothing declared" (never assume it compiled);</li>
 *   <li><b>tested by the user</b>: {@link #userTested}, only ever set by the player's own click.</li>
 * </ul>
 *
 * @param id           stable id: {@code t-<hex>} for local tasks, the proposal's own id for imported ones
 * @param dependsOn    ids of other tasks (missing ids are kept and shown as unknown)
 * @param decision     the decision the player still owes, or empty
 * @param origin       written here, or imported from a coordinator's proposal
 * @param source       the thread a proposal came from (null for local tasks)
 * @param link         the thread doing the work (null until launched or linked)
 * @param feedback     the last observations sent back to the thread, or null
 * @param edited       the player changed a proposal after importing it (a re-import then never refreshes it)
 * @param incoming     a newer proposal for this id that was not applied (local wins), or null
 * @param extra        fields of the file this version does not know, kept on write (additive format)
 */
public record PlanTask(
		String id,
		String title,
		String outcome,
		List<String> dependsOn,
		String decision,
		String notes,
		Stage stage,
		Origin origin,
		@Nullable ThreadRef source,
		@Nullable Link link,
		String agentChecked,
		boolean userTested,
		@Nullable String feedback,
		boolean edited,
		@Nullable Incoming incoming,
		long createdAt,
		long updatedAt,
		JsonObject extra) {

	public PlanTask {
		title = title == null ? "" : title;
		outcome = outcome == null ? "" : outcome;
		dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
		decision = decision == null ? "" : decision;
		notes = notes == null ? "" : notes;
		agentChecked = agentChecked == null ? "" : agentChecked;
		extra = extra == null ? new JsonObject() : extra.deepCopy();
	}

	/** Where a task stands. Only {@link #PROPOSED} tasks are not part of the plan yet. */
	public enum Stage {
		/** Imported from a coordinator; waits for the player to accept it into the plan. */
		PROPOSED,
		/** Being prepared, not linked to a thread. */
		DRAFT,
		/** Linked to a thread that is (or will be) working on it. */
		IN_PROGRESS,
		/** The linked thread finished its turn: waits for the player's decision. */
		IN_REVIEW,
		/** The player accepted the delivery. Nothing else was launched or authorised by it. */
		ACCEPTED;

		public final String wire = name().toLowerCase(Locale.ROOT);

		public static Stage fromWire(@Nullable String s, Stage def) {
			for (Stage v : values()) {
				if (v.wire.equals(s)) {
					return v;
				}
			}
			return def;
		}
	}

	public enum Origin {
		LOCAL, PROPOSAL;

		public final String wire = name().toLowerCase(Locale.ROOT);

		public static Origin fromWire(@Nullable String s) {
			return PROPOSAL.wire.equals(s) ? PROPOSAL : LOCAL;
		}
	}

	/** A thread of an environment. */
	public record ThreadRef(String envId, String threadId) {
		public String key() {
			return envId + ":" + threadId;
		}
	}

	/**
	 * The thread working on the task.
	 *
	 * @param launched true when the plan page launched it (false: an existing thread was linked)
	 * @param baseline the thread's completion mark ({@link PlanReview#completionMark}, server time) when the task was
	 *                 linked or last sent back; only a later completion counts as "implemented". Null: any completion.
	 */
	public record Link(String envId, String threadId, boolean launched, long linkedAt, @Nullable Long baseline) {
		public ThreadRef ref() {
			return new ThreadRef(envId, threadId);
		}
	}

	/** A re-imported proposal that differs from the local task and was not applied. */
	public record Incoming(String title, String outcome, String decision, @Nullable ThreadRef from, long at) {
	}

	/** Part of the plan (accepted into it): everything but a pending proposal. */
	public boolean inPlan() {
		return stage != Stage.PROPOSED;
	}

	public boolean hasDecision() {
		return !decision.isBlank();
	}

	PlanTask with(Stage s, long now) {
		return new PlanTask(id, title, outcome, dependsOn, decision, notes, s, origin, source, link, agentChecked, userTested, feedback,
				edited, incoming, createdAt, now, extra);
	}

	PlanTask withText(String t, String o, List<String> deps, String d, String n, boolean markEdited, long now) {
		return new PlanTask(id, t, o, deps, d, n, stage, origin, source, link, agentChecked, userTested, feedback,
				edited || markEdited, incoming, createdAt, now, extra);
	}

	PlanTask withLink(@Nullable Link l, Stage s, long now) {
		return new PlanTask(id, title, outcome, dependsOn, decision, notes, s, origin, source, l, agentChecked, userTested, feedback,
				edited, incoming, createdAt, now, extra);
	}

	PlanTask withMarks(String checked, boolean tested, long now) {
		return new PlanTask(id, title, outcome, dependsOn, decision, notes, stage, origin, source, link, checked, tested, feedback,
				edited, incoming, createdAt, now, extra);
	}

	PlanTask withFeedback(@Nullable String f, @Nullable Link l, Stage s, long now) {
		return new PlanTask(id, title, outcome, dependsOn, decision, notes, s, origin, source, l, agentChecked, userTested, f,
				edited, incoming, createdAt, now, extra);
	}

	PlanTask withIncoming(@Nullable Incoming in, long now) {
		return new PlanTask(id, title, outcome, dependsOn, decision, notes, stage, origin, source, link, agentChecked, userTested, feedback,
				edited, in, createdAt, now, extra);
	}
}
