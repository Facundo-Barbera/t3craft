package dev.t3craft.blocks;

import org.jspecify.annotations.Nullable;

/**
 * What a monitor or lamp shows, as stored in its block entity ({@code "binding"} string):
 * <ul>
 *   <li>{@code thread:<npcKey>}: one thread; {@code npcKey} is the NPC id, {@code <envId>:<threadId>}
 *       in direct mode (the same key the HQ plan, {@code NpcRegistry} and the workstations use);</li>
 *   <li>{@code board:<i>}: bulb {@code i} of the "needs attention" board (the i-th most urgent thread);</li>
 *   <li>{@code beacon}: the hub lantern's beacon, the whole HQ's aggregate state;</li>
 *   <li>empty: unbound (a block placed by hand, a vacant desk). The position source may still bind it.</li>
 * </ul>
 * Anything else parses as {@link #NONE}. Pure.
 */
public record Binding(Kind kind, String value) {
	public enum Kind {
		NONE, THREAD, BOARD, BEACON
	}

	public static final Binding NONE = new Binding(Kind.NONE, "");
	private static final String BEACON_WIRE = "beacon";
	/** The hub beacon (adapted from AgentCraft's {@code beacon} binding of the cupola lamps). */
	public static final Binding BEACON = new Binding(Kind.BEACON, BEACON_WIRE);
	private static final String THREAD = "thread:";
	private static final String BOARD = "board:";

	public Binding {
		value = value == null ? "" : value;
		if (kind == null || value.isEmpty() && kind != Kind.NONE) {
			kind = Kind.NONE;
			value = "";
		}
	}

	public static Binding thread(String npcKey) {
		return new Binding(Kind.THREAD, npcKey);
	}

	public static Binding board(int index) {
		return index < 0 ? NONE : new Binding(Kind.BOARD, Integer.toString(index));
	}

	public static Binding parse(@Nullable String s) {
		if (s == null || s.isEmpty()) {
			return NONE;
		}
		if (s.equals(BEACON_WIRE)) {
			return BEACON;
		}
		if (s.startsWith(THREAD)) {
			return thread(s.substring(THREAD.length()));
		}
		if (s.startsWith(BOARD)) {
			try {
				return board(Integer.parseInt(s.substring(BOARD.length())));
			} catch (NumberFormatException e) {
				return NONE;
			}
		}
		return NONE;
	}

	/** The stored string ({@code ""} for NONE); {@code parse(b.wire()).equals(b)}. */
	public String wire() {
		return switch (kind) {
			case NONE -> "";
			case THREAD -> THREAD + value;
			case BOARD -> BOARD + value;
			case BEACON -> BEACON_WIRE;
		};
	}

	public boolean isNone() {
		return kind == Kind.NONE;
	}

	/** Board bulb index, or -1. */
	public int boardIndex() {
		return kind == Kind.BOARD ? Integer.parseInt(value) : -1;
	}

	/**
	 * The effective binding of a block: its own block entity binding wins; an unbound block takes the
	 * one its position has in the HQ plan ({@code positional}, null when unknown). Deterministic: the
	 * same block data and plan always give the same answer.
	 */
	public static Binding resolve(@Nullable String own, @Nullable Binding positional) {
		Binding b = parse(own);
		if (!b.isNone()) {
			return b;
		}
		return positional == null ? NONE : positional;
	}

	@Override
	public String toString() {
		return isNone() ? "(unbound)" : wire();
	}
}
