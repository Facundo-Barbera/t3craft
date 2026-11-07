// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.hud;

import dev.t3craft.client.config.T3CraftSettings.Hud;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Pure model of the HUD status bar (layout after AgentCraft's {@code GoalBar}: a row of counts plus
 * a "n waiting · KEY" badge hanging under it). No Minecraft classes: state → segments, texts, widths
 * and position, so it is unit-tested and rebuilt only when its inputs change.
 */
public record StatusBarModel(List<Segment> segments, @Nullable Segment badge, int width, int height) {
	public static final int PAD = 4;
	public static final int GAP = 8;
	public static final int ROW_H = 12;
	public static final int BADGE_GAP = 2;
	/** Text colour of a zero count. */
	public static final int DIM = 0x777777;
	/** Badge text: needs-you orange. */
	public static final int BADGE_COLOR = ThreadState.NEEDS_ATTENTION.color;

	/** Hidden: nothing to draw. */
	public static final StatusBarModel HIDDEN = new StatusBarModel(List.of(), null, 0, 0);

	public record Segment(String text, int color, int width) {
	}

	/** Thread counts across every environment. */
	public record Counts(int working, int needsYou, int error) {
		public static final Counts ZERO = new Counts(0, 0, 0);

		public boolean allZero() {
			return working == 0 && needsYou == 0 && error == 0;
		}

		/** Counts NPC states by their wire name ({@link ThreadState#wire}); others are ignored. */
		public static Counts of(Iterable<String> states) {
			int w = 0;
			int n = 0;
			int e = 0;
			for (String s : states) {
				if (ThreadState.WORKING.wire.equals(s)) {
					w++;
				} else if (ThreadState.NEEDS_ATTENTION.wire.equals(s)) {
					n++;
				} else if (ThreadState.ERROR.wire.equals(s)) {
					e++;
				}
			}
			return new Counts(w, n, e);
		}
	}

	public boolean hidden() {
		return segments.isEmpty();
	}

	/**
	 * @param keyLabel label of the key that opens the UI, or null when unbound (badge without key)
	 * @param textWidth font width in GUI px
	 */
	public static StatusBarModel build(Counts c, Hud cfg, @Nullable String keyLabel, ToIntFunction<String> textWidth) {
		if (!cfg.statusBar() || (cfg.hideWhenIdle() && c.allZero())) {
			return HIDDEN;
		}
		List<Segment> segs = new ArrayList<>(3);
		segs.add(segment(ThreadState.WORKING, c.working(), textWidth));
		segs.add(segment(ThreadState.NEEDS_ATTENTION, c.needsYou(), textWidth));
		segs.add(segment(ThreadState.ERROR, c.error(), textWidth));
		int w = PAD * 2 + GAP * (segs.size() - 1);
		for (Segment s : segs) {
			w += s.width();
		}
		Segment badge = null;
		int h = ROW_H;
		if (c.needsYou() > 0) {
			String t = c.needsYou() + " waiting" + (keyLabel == null || keyLabel.isBlank() ? "" : " · " + keyLabel);
			badge = new Segment(t, BADGE_COLOR, textWidth.applyAsInt(t));
			h += BADGE_GAP + ROW_H;
		}
		return new StatusBarModel(List.copyOf(segs), badge, w, h);
	}

	private static Segment segment(ThreadState s, int n, ToIntFunction<String> textWidth) {
		String t = s.glyph + " " + n + " " + s.word;
		return new Segment(t, n == 0 ? DIM : s.color, textWidth.applyAsInt(t));
	}

	// ---- placement ----------------------------------------------------------------------------

	/** Hotbar width (vanilla), centred at the bottom; hearts / armor / food / XP sit over it. */
	static final int HOTBAR_W = 182;
	/** Height of hotbar + XP + hearts + armor rows (vanilla, from the bottom edge). */
	static final int SURVIVAL_BARS_H = 49;
	/** Above the action-bar / held-item-name line, which vanilla draws at about height − 68. */
	static final int CENTER_BOTTOM_CLEAR = 72;
	static final int MARGIN = 4;

	/**
	 * Top-left corner of the bar ({@code [x, y]}) for a GUI {@code guiW × guiH}. {@code topBusy}:
	 * bottom edge of what {@link PeekHud} drew at the top (the bar stacks under it). Bottom placement
	 * never covers the hotbar, hearts or XP bar: left/right use the free corner beside the hotbar
	 * when the bar fits there, else go above the survival bars; centre always goes above the
	 * action-bar line.
	 */
	public int[] place(Hud cfg, int guiW, int guiH, int topBusy) {
		int barW = Math.max(width, badge == null ? 0 : badge.width() + PAD * 2);
		int x = switch (cfg.align()) {
			case "left" -> MARGIN;
			case "right" -> guiW - MARGIN - barW;
			default -> (guiW - barW) / 2;
		};
		if (!cfg.bottom()) {
			return new int[] {x, Math.max(MARGIN, topBusy + 2)};
		}
		int y;
		boolean side = !"center".equals(cfg.align());
		int besideHotbar = (guiW - HOTBAR_W) / 2 - MARGIN * 2;
		if (side && barW <= besideHotbar) {
			y = guiH - MARGIN - height;
		} else if (side) {
			y = guiH - SURVIVAL_BARS_H - MARGIN - height;
		} else {
			y = guiH - CENTER_BOTTOM_CLEAR - height;
		}
		return new int[] {x, Math.max(MARGIN, y)};
	}
}
