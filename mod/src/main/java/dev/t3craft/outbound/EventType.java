package dev.t3craft.outbound;

import org.jspecify.annotations.Nullable;

/**
 * What happened, in the words of the outbound events (independent of the client's classes).
 * {@code wire} is the id stored in {@code outbound.json} and sent by the JSON template; it never changes.
 */
public enum EventType {
	NEEDS_ATTENTION("needs_attention", "Needs your input", 4, "bell", 0xFFAA00),
	ERROR("error", "Thread failed", 4, "x", 0xFF5555),
	DONE("done", "Thread finished", 3, "white_check_mark", 0x55FF55),
	NEW_THREAD("new_thread", "New thread", 2, "new", 0x55FFFF),
	RUN_FINISHED("run_finished", "Run finished", 3, "checkered_flag", 0x55FF55);

	public final String wire;
	public final String label;
	/** Push priority 1 (min) to 5 (max). */
	public final int pushPriority;
	/** Push tag (an emoji short code the push service understands). */
	public final String pushTag;
	/** 0xRRGGBB accent for the rich chat blocks. */
	public final int color;

	EventType(String wire, String label, int pushPriority, String pushTag, int color) {
		this.wire = wire;
		this.label = label;
		this.pushPriority = pushPriority;
		this.pushTag = pushTag;
		this.color = color;
	}

	public static @Nullable EventType fromWire(@Nullable String s) {
		if (s == null) {
			return null;
		}
		for (EventType t : values()) {
			if (t.wire.equals(s)) {
				return t;
			}
		}
		return null;
	}
}
