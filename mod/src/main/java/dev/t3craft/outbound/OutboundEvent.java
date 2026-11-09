package dev.t3craft.outbound;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One thing worth telling the outside world. Own model: it knows nothing about the client's classes,
 * so another process (issue #1) can build the same events.
 *
 * @param environment id of the T3 environment ({@code my-vps})
 * @param threadId id of the thread inside the environment
 * @param thread display title of the thread
 * @param project project / group the thread belongs to
 * @param state thread state after the change ({@link EventType#wire}-style word, free text)
 * @param text short description ("Needs your input", the activity, the failure…)
 * @param atMs epoch millis when it happened
 */
public record OutboundEvent(EventType type, @Nullable String environment, @Nullable String threadId, @Nullable String thread,
		@Nullable String project, @Nullable String state, String text, long atMs) {

	public OutboundEvent {
		Objects.requireNonNull(type, "type");
		text = text == null ? "" : text;
	}

	/** Same thread = same key: the emitter keeps only the latest event per key while it debounces. */
	public String key() {
		return (environment == null ? "" : environment) + ":" + (threadId == null ? "" : threadId);
	}

	/** The content-free event used by destinations in "pending only" mode. */
	static OutboundEvent pending(long atMs) {
		return new OutboundEvent(EventType.NEEDS_ATTENTION, null, null, null, null, EventType.NEEDS_ATTENTION.wire, "", atMs);
	}
}
