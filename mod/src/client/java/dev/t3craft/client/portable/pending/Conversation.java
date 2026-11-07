package dev.t3craft.client.portable;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the terminal knows about the open thread's conversation, as plain data (drawn by the screen
 * panel and by the held-item screen). Only what T3 returned; when it cannot be read, {@link #status}
 * and {@link #detail} say why and {@link #lines} stays empty.
 */
public record Conversation(@Nullable String npcId, Status status, String detail, List<Line> lines, boolean hasOlder, long fetchedAtMs) {
	public enum Status {
		/** No thread selected. */
		NONE,
		LOADING,
		LIVE,
		/** This source cannot serve history (bridge mode, not paired, no thread id). Not an error. */
		UNAVAILABLE,
		/** The read failed (offline, HTTP error); last good lines, if any, are kept. */
		FAILED
	}

	public enum Role {
		YOU("You", 0x55FFFF),
		AGENT("Agent", 0x55FF55),
		PLAN("Plan", 0xC9A227),
		TOOL("Tool", 0x8A949E),
		NEEDS_YOU("Needs you", 0xFFAA00),
		ERROR("Error", 0xFF5555),
		SYSTEM("System", 0xAAAAAA);

		public final String label;
		public final int color;

		Role(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	/** One conversation row. {@code atMs} is null when T3 sent no time. */
	public record Line(String id, Role role, String text, @Nullable Long atMs, boolean streaming) {
	}

	public Conversation {
		lines = List.copyOf(lines);
	}

	public static final Conversation NONE = new Conversation(null, Status.NONE, "", List.of(), false, 0);

	public static Conversation loading(String npcId) {
		return new Conversation(npcId, Status.LOADING, "", List.of(), false, 0);
	}

	public static Conversation unavailable(@Nullable String npcId, String reason) {
		return new Conversation(npcId, Status.UNAVAILABLE, reason, List.of(), false, 0);
	}

	public Conversation failed(String reason) {
		return new Conversation(npcId, Status.FAILED, reason, lines, hasOlder, fetchedAtMs);
	}
}
