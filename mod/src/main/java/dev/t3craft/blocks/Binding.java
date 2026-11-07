package dev.t3craft.blocks;

import org.jspecify.annotations.Nullable;

/**
 * What a monitor or lamp shows, as stored in its block entity ({@code "binding"} string):
 * <ul>
 *   <li>{@code thread:<npcKey>}: one thread; {@code npcKey} is the NPC id, {@code <envId>:<threadId>}
 *       in direct mode (the same key the HQ plan, {@code NpcRegistry} and the workstations use);</li>
 *   <li>{@code board:<i>}: bulb {@code i} of the "needs attention" board (the i-th most urgent thread);</li>
 *   <li>{@code module:<key>,<key>...}: a module's bulb over its doorway: the most urgent of the threads
 *       sitting at that module's desks (sorted keys; off when the module is empty);</li>
 *   <li>{@code beacon}: the hub lantern's beacon, the whole HQ's aggregate state;</li>
 *   <li>{@code project:<projectKey>}: a project's feed (a large screen): its active threads and their
 *       last event; {@code projectKey} is the NPC group id ({@code <envId>:<projectId>});</li>
 *   <li>{@code env:<envId>}: the same feed for every thread of one environment;</li>
 *   <li>empty: unbound (a block placed by hand, a vacant desk). The position source may still bind it.</li>
 * </ul>
 * Anything else parses as {@link #NONE}. Pure.
 */
public record Binding(Kind kind, String value) {
	public enum Kind {
		NONE, THREAD, BOARD, MODULE, BEACON, PROJECT, ENV
	}

	public static final Binding NONE = new Binding(Kind.NONE, "");
	private static final String BEACON_WIRE = "beacon";
	/** The hub beacon (adapted from AgentCraft's {@code beacon} binding of the cupola lamps). */
	public static final Binding BEACON = new Binding(Kind.BEACON, BEACON_WIRE);
	private static final String THREAD = "thread:";
	private static final String BOARD = "board:";
	private static final String MODULE = "module:";
	private static final String PROJECT = "project:";
	private static final String ENV = "env:";

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

	/** A project feed ({@code projectKey} = NPC group id, {@code <envId>:<projectId>}). */
	public static Binding project(String projectKey) {
		return new Binding(Kind.PROJECT, projectKey);
	}

	/** An environment feed. */
	public static Binding env(String envId) {
		return new Binding(Kind.ENV, envId);
	}

	/** True for the feed kinds ({@link Kind#PROJECT}, {@link Kind#ENV}). */
	public boolean isFeed() {
		return kind == Kind.PROJECT || kind == Kind.ENV;
	}

	/** A module bulb over {@code threads} (order and duplicates do not matter; empty → NONE). */
	public static Binding module(java.util.Collection<String> threads) {
		java.util.TreeSet<String> keys = new java.util.TreeSet<>();
		for (String t : threads) {
			if (t != null && !t.isEmpty() && t.indexOf(',') < 0) {
				keys.add(t);
			}
		}
		return keys.isEmpty() ? NONE : new Binding(Kind.MODULE, String.join(",", keys));
	}

	/** Thread keys of a module bulb (empty for other kinds). */
	public java.util.List<String> moduleThreads() {
		return kind == Kind.MODULE ? java.util.List.of(value.split(",")) : java.util.List.of();
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
		if (s.startsWith(PROJECT)) {
			return project(s.substring(PROJECT.length()));
		}
		if (s.startsWith(ENV)) {
			return env(s.substring(ENV.length()));
		}
		if (s.startsWith(MODULE)) {
			return module(java.util.List.of(s.substring(MODULE.length()).split(",")));
		}
		return NONE;
	}

	/** The stored string ({@code ""} for NONE); {@code parse(b.wire()).equals(b)}. */
	public String wire() {
		return switch (kind) {
			case NONE -> "";
			case THREAD -> THREAD + value;
			case BOARD -> BOARD + value;
			case MODULE -> MODULE + value;
			case BEACON -> BEACON_WIRE;
			case PROJECT -> PROJECT + value;
			case ENV -> ENV + value;
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
