package dev.t3craft.client.npc;

import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * Pure rules for the text over an NPC (compact mode): the one-line name tag, the aim test, the
 * detail panel's visibility with hysteresis, and the bubble height so it never crosses the panel.
 *
 * <p>Per NPC there is exactly one far label (the avatar's vanilla name tag) and one near panel
 * (its {@code TextDisplay}); the speech bubble and attention icon stack above whatever the panel
 * currently shows.
 */
public final class NpcLabels {
	/** Panel lines keep showing until the player is this much further than the detail distance. */
	public static final double HYSTERESIS = 1.0;
	/** Ticks the panel stays after every keep condition is gone (no flicker at the edge). */
	public static final int GRACE_TICKS = 10;
	/** Ticks a click keeps the panel open. */
	public static final int CLICK_TICKS = 200;
	/** A click reveal closes when the player walks further than this. */
	public static final double CLICK_MAX_DIST = 16.0;
	/** Aim: max miss distance (blocks, perpendicular to the view ray) to open / to keep open. */
	public static final double AIM_SHOW = 0.5;
	public static final double AIM_KEEP = 0.9;
	/** Aim: max range along the view ray. */
	public static final double AIM_RANGE = 16.0;

	/** World text pill: activity line length (code points, the dot not counted). */
	public static final int PILL_MAX = 40;
	/** The pill's status dot. */
	public static final String DOT = "\u25CF";

	private NpcLabels() {
	}

	/** The {@link ThreadState} of a wire state; unknown → idle ({@code "attention"} → needs attention). */
	public static ThreadState state(@Nullable String state) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(state)) {
				return s;
			}
		}
		return "attention".equals(state) ? ThreadState.NEEDS_ATTENTION : ThreadState.IDLE;
	}

	/**
	 * The pill's one line: the activity's first line, whitespace collapsed and cut to
	 * {@link #PILL_MAX}; the title when there is no activity.
	 */
	public static String pillLine(@Nullable String activity, @Nullable String title) {
		String a = activity == null ? "" : activity.strip();
		int nl = a.indexOf('\n');
		if (nl >= 0) {
			a = a.substring(0, nl).strip();
		}
		if (a.isEmpty()) {
			a = title == null ? "" : title.strip();
		}
		return truncate(a.replaceAll("\\s+", " "), PILL_MAX);
	}

	/** {@code "⚙ Fix the grid…"}: state glyph, then the title cut to {@code max} code points. */
	public static String compactLine(String state, @Nullable String title, int max) {
		return glyph(state) + " " + truncate(title == null ? "" : title.strip().replaceAll("\\s+", " "), max);
	}

	/** Glyph for a wire state ({@code needs_attention}, {@code working}…); unknown → idle's. */
	public static String glyph(@Nullable String state) {
		for (ThreadState s : ThreadState.values()) {
			if (s.wire.equals(state)) {
				return s.glyph;
			}
		}
		if ("attention".equals(state)) {
			return ThreadState.NEEDS_ATTENTION.glyph;
		}
		return ThreadState.IDLE.glyph;
	}

	/** At most {@code max} code points; longer strings end in {@code …} (counted in the max). */
	public static String truncate(String s, int max) {
		int n = s.codePointCount(0, s.length());
		if (n <= max) {
			return s;
		}
		if (max <= 1) {
			return "…";
		}
		int end = s.offsetByCodePoints(0, max - 1);
		return s.substring(0, end).stripTrailing() + "…";
	}

	/**
	 * How far (blocks) the view ray from {@code eye} along {@code look} passes from {@code target},
	 * or {@link Double#POSITIVE_INFINITY} if the target is behind the eye or beyond {@code range}.
	 */
	public static double aimMiss(double ex, double ey, double ez, double lx, double ly, double lz,
			double tx, double ty, double tz, double range) {
		double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (len == 0) {
			return Double.POSITIVE_INFINITY;
		}
		double dx = tx - ex;
		double dy = ty - ey;
		double dz = tz - ez;
		double along = (dx * lx + dy * ly + dz * lz) / len;
		if (along <= 0 || along > range) {
			return Double.POSITIVE_INFINITY;
		}
		double perp2 = dx * dx + dy * dy + dz * dz - along * along;
		return Math.sqrt(Math.max(0, perp2));
	}

	/** Base height (above the feet) for the speech bubble: above the panel when it shows lines. */
	public static double bubbleBase(double panelY, double bubbleY, double lineH, int panelLines) {
		return panelLines <= 0 ? bubbleY : Math.max(bubbleY, panelY + panelLines * lineH + 0.1);
	}

	/** Lines in a panel text ({@code \n}-separated), 0 for empty. */
	public static int lines(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		int n = 1;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	/** Detail panel visibility for one NPC, updated once per tick. */
	public static final class Visibility {
		private boolean shown;
		private int grace;
		private int pinned;

		/** Left-click: open for {@link #CLICK_TICKS}; a second click while open closes it. */
		public void toggle() {
			if (pinned > 0) {
				pinned = 0;
				shown = false;
				grace = 0;
			} else {
				pinned = CLICK_TICKS;
			}
		}

		/** Open for {@link #CLICK_TICKS} (no toggle). */
		public void reveal() {
			pinned = CLICK_TICKS;
		}

		/**
		 * @param dist player distance to the NPC (blocks)
		 * @param aim  {@link #aimMiss} result
		 * @param near configured detail distance
		 * @return whether the panel shows this tick
		 */
		public boolean update(double dist, double aim, double near) {
			if (pinned > 0) {
				pinned--;
				if (dist > CLICK_MAX_DIST) {
					pinned = 0;
				}
			}
			boolean open = pinned > 0 || dist <= near || aim <= AIM_SHOW;
			boolean keep = open || dist <= near + HYSTERESIS || aim <= AIM_KEEP;
			if (open || (shown && keep)) {
				shown = true;
				grace = GRACE_TICKS;
			} else if (shown && grace > 0) {
				grace--;
			} else {
				shown = false;
			}
			return shown;
		}

		public boolean shown() {
			return shown;
		}

		/** Opened by a click (or {@link #reveal}) and still within its {@link #CLICK_TICKS}. */
		public boolean pinned() {
			return pinned > 0;
		}
	}
}
