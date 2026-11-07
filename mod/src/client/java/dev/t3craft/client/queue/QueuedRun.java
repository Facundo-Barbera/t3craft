package dev.t3craft.client.queue;

import com.google.gson.JsonObject;
import dev.t3craft.t3.Json;
import org.jspecify.annotations.Nullable;

/**
 * One run of T3's {@code OrchestrationV2Run} as the queue view needs it, decoded tolerantly (older
 * servers omit {@code queuePosition}/{@code queueHeld}; unknown fields are ignored).
 *
 * @param envId         environment id
 * @param threadId      thread the run belongs to
 * @param id            run id ({@code RunId})
 * @param status        run status ({@code queued}, {@code running}…)
 * @param ordinal       creation order within the thread (fallback order when there is no position)
 * @param queuePosition 1-based delivery position, null when the server does not send it
 * @param held          {@code queueHeld}: the queue is paused (after an interrupt with holdQueue)
 * @param requestedAt   epoch millis, or null
 * @param userMessageId the user message the run answers, or null
 * @param automatic     delivers a delegated completion: always first, never a reorder target
 * @param movable       may be reordered (not automatic, not a notification)
 * @param preview       first line of the queued message, or null when unknown
 */
public record QueuedRun(String envId, String threadId, String id, String status, long ordinal, @Nullable Long queuePosition,
		boolean held, @Nullable Long requestedAt, @Nullable String userMessageId, boolean automatic, boolean movable, @Nullable String preview) {
	public static final String QUEUED = "queued";
	/** Longest preview kept (characters). */
	public static final int PREVIEW_MAX = 120;

	public boolean queued() {
		return QUEUED.equals(status);
	}

	/** Same run with the message-derived fields filled in. */
	public QueuedRun withMessage(boolean automatic, boolean movable, @Nullable String preview) {
		return new QueuedRun(envId, threadId, id, status, ordinal, queuePosition, held, requestedAt, userMessageId, automatic, movable, preview);
	}

	/** Returns null when {@code o} has no run id. A missing thread id falls back to {@code threadId}. */
	public static @Nullable QueuedRun fromJson(String envId, String threadId, @Nullable JsonObject o) {
		String id = Json.str(o, "id");
		if (o == null || id == null || id.isEmpty()) {
			return null;
		}
		String thread = Json.str(o, "threadId");
		String status = Json.str(o, "status");
		long pos = Json.num(o, "queuePosition", -1);
		return new QueuedRun(envId,
				thread == null || thread.isEmpty() ? threadId : thread,
				id,
				status == null ? "" : status,
				Json.num(o, "ordinal", 0),
				pos > 0 ? pos : null,
				Json.bool(o, "queueHeld"),
				Json.time(o, "requestedAt"),
				Json.str(o, "userMessageId"),
				false,
				true,
				null);
	}

	/** First non-blank line of a message, trimmed to {@link #PREVIEW_MAX}; null when blank. */
	public static @Nullable String preview(@Nullable String text) {
		if (text == null) {
			return null;
		}
		for (String line : text.split("\\R")) {
			String t = line.strip();
			if (!t.isEmpty()) {
				return t.length() > PREVIEW_MAX ? t.substring(0, PREVIEW_MAX - 1) + "…" : t;
			}
		}
		return null;
	}
}
