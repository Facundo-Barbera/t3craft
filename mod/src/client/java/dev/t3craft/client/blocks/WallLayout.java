// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.Ago;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.EnvBadge;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where everything goes on the wall, in board pixels (x right, y down from the wall's top-left
 * corner as seen from the front). Columns share the width equally; each column is a dark lane with
 * a coloured header (label + count) and a stack of loose, fixed-height cards inset from the lane
 * with a gap between them: stripe of the state colour, title, then a footer line with the
 * environment badge, project and the thread's age. When a column holds more cards than fit, the
 * last slot becomes a "+N more" chip (clicking it opens the first hidden thread). Pure: text widths
 * come from a {@link Measure} (the font in game, a fixed width in tests).
 *
 * <p><b>Reading distance (6-10 blocks).</b> A block is 64 board px, so the font's 8 px is only 1/8 of
 * a block: too small from across the hub. Headers and titles are therefore drawn scaled
 * ({@link Metrics}: 1.75x / 1.25x on a wall whose lanes are wide enough, smaller scales on narrow walls)
 * and the layout measures them at that scale; the footer stays at 1x (project, age: secondary).
 *
 * <p>Lanes, headers with counts and the "+N more" overflow chip adapted from AgentCraft's
 * {@code TaskBoard} (which also sizes cards to their titles; here cards have one size so the wall
 * reads evenly from across the hub).
 */
public final class WallLayout {
	/** Space between the board's edge and the lanes (frame 6 px + 2 px of backing). */
	public static final int EDGE = 8;
	/** Frame band painted around the board (inside the brass keyline). */
	public static final int FRAME = 6;
	/** Gap between lanes. */
	public static final int GAP = 6;
	public static final int CARD_GAP = 6;
	/** Room between a lane's side and its cards. */
	public static final int CARD_INSET = 5;
	/** Space under the header before the first card. */
	public static final int HEAD_GAP = 6;
	public static final int CHIP_H = 14;
	/** Inner padding of a card's text. */
	public static final int CARD_PAD = 4;
	/** Room on a card's left for the state stripe. */
	public static final int STRIPE = 5;
	/** Badge size on a card's footer line. */
	public static final int BADGE = 9;
	/** Height of the footer text (the font's 8 px). */
	public static final int FOOT_H = 8;
	public static final String ELLIPSIS = "…";

	/** Lane width (px) from which titles are drawn at 1.25x and headers at 1.75x. */
	static final int WIDE_LANE = 120;
	/** Lane width from which headers are drawn at 1.5x (titles stay 1.25x). */
	static final int MID_LANE = 88;

	/**
	 * Text scales and the card / header sizes that go with them (board px). {@code cardH}: a card with a one-line title;
	 * a two-line title adds {@link #lineStep()}.
	 */
	public record Metrics(float titleScale, float headScale, int headerH, int cardH) {
		static Metrics of(float laneW) {
			if (laneW >= WIDE_LANE) {
				return new Metrics(1.25f, 1.75f, 20, 30);
			}
			if (laneW >= MID_LANE) {
				return new Metrics(1.25f, 1.5f, 18, 30);
			}
			return new Metrics(1f, 1f, 13, 27);
		}

		/** Extra height of a second title line. */
		public int lineStep() {
			return (int) Math.ceil(8 * titleScale);
		}

		/** Height of a card whose title takes {@code lines} (1 or 2) lines. */
		public int cardH(int lines) {
			return cardH + (lines - 1) * lineStep();
		}
	}

	/** Text width in board pixels. */
	@FunctionalInterface
	public interface Measure {
		int width(String text);
	}

	/** A column's lane: header label (clipped) and count text. */
	public record Lane(int column, String id, float x, float w, String label, String count, int color) {
	}

	/** A card's slot and its texts (already clipped to fit): the title on one or two lines ({@code title2} is "" when it fits on one). {@code age}: compact age ("5m"), "" when unknown. */
	public record Slot(String key, int column, float x, float y, float w, float h, String title, String title2, String footer, String age,
			Attention attention, EnvBadge badge) {
		boolean contains(float px, float py) {
			return px >= x && px <= x + w && py >= y && py <= y + h;
		}
	}

	/** The "+N more" chip of a column: {@code hidden} cards behind it, the first of which a click opens. */
	public record More(int column, float x, float y, float w, float h, int hidden, String firstKey, String text) {
		boolean contains(float px, float py) {
			return px >= x && px <= x + w && py >= y && py <= y + h;
		}
	}

	public enum HitKind {
		CARD, MORE
	}

	/** What a point of the wall hits: a card (its key) or a "+N more" chip (the first hidden key). */
	public record Hit(HitKind kind, String key, int column) {
	}

	public record Layout(float width, float height, WallMode mode, Metrics metrics, List<Lane> lanes, List<Slot> cards, List<More> more, int total) {
		/** The card or chip under {@code (x, y)}, or null. */
		public @Nullable Hit hit(float x, float y) {
			for (Slot s : cards) {
				if (s.contains(x, y)) {
					return new Hit(HitKind.CARD, s.key(), s.column());
				}
			}
			for (More m : more) {
				if (m.contains(x, y)) {
					return new Hit(HitKind.MORE, m.firstKey(), m.column());
				}
			}
			return null;
		}

		public @Nullable Slot card(String key) {
			for (Slot s : cards) {
				if (s.key().equals(key)) {
					return s;
				}
			}
			return null;
		}
	}

	private WallLayout() {
	}

	/** Lane width of {@code columns} columns on a wall {@code width} px wide. */
	static float laneWidth(float width, int columns) {
		int n = Math.max(1, columns);
		return (width - 2 * EDGE - GAP * (n - 1)) / n;
	}

	/** Most columns that stay readable on a wall {@code width} px wide (project/environment modes). */
	public static int maxColumns(float width) {
		return Math.max(1, (int) ((width - 2 * EDGE + GAP) / (72 + GAP)));
	}

	public static Layout build(List<WallColumns.Column> columns, WallMode mode, float width, float height, Measure m, long nowMs) {
		int n = Math.max(1, columns.size());
		float laneW = laneWidth(width, n);
		Metrics mt = Metrics.of(laneW);
		List<Lane> lanes = new ArrayList<>(columns.size());
		List<Slot> slots = new ArrayList<>();
		List<More> more = new ArrayList<>();
		int total = 0;
		float cardW = laneW - 2 * CARD_INSET;
		float bottom = height - EDGE - 4;
		for (int i = 0; i < columns.size(); i++) {
			WallColumns.Column c = columns.get(i);
			float x = EDGE + i * (laneW + GAP);
			String count = Integer.toString(c.count());
			// the count sits in a pill at the header's right; the label gets what is left, at the header scale
			float pillW = pillWidth(count, m, mt);
			int labelRoom = (int) ((laneW - 2 * CARD_PAD - pillW - 4) / mt.headScale());
			lanes.add(new Lane(i, c.id(), x, laneW, clip(c.label(), labelRoom, m), count, c.color()));
			total += c.count();
			float titleRoom = (cardW - STRIPE - 2 * CARD_PAD) / mt.titleScale();
			// titles on one or two lines, so heights differ: how many cards fit decides the "+N more" chip
			List<String[]> titles = new ArrayList<>(c.count());
			float all = 0;
			for (WallCard card : c.cards()) {
				String[] t = wrap(card.title(), (int) titleRoom, m);
				titles.add(t);
				all += mt.cardH(t[1].isEmpty() ? 1 : 2) + CARD_GAP;
			}
			float y = EDGE + mt.headerH() + HEAD_GAP;
			boolean fitsAll = y + all - CARD_GAP <= bottom;
			float limit = fitsAll ? bottom : bottom - (CHIP_H + CARD_GAP);
			int shown = 0;
			for (int k = 0; k < c.count(); k++) {
				WallCard card = c.cards().get(k);
				String[] t = titles.get(k);
				float h = mt.cardH(t[1].isEmpty() ? 1 : 2);
				if (y + h > limit) {
					break;
				}
				String age = Ago.format(nowMs, card.activityMs());
				String footer = footer(card, mode);
				float footRoom = cardW - STRIPE - 2 * CARD_PAD - BADGE - 3 - (age.isEmpty() ? 0 : m.width(age) + 5);
				slots.add(new Slot(card.key(), i, x + CARD_INSET, y, cardW, h, t[0], t[1],
						clip(footer, (int) footRoom, m), age, card.attention(), card.badge()));
				y += h + CARD_GAP;
				shown++;
			}
			int hidden = c.count() - shown;
			if (hidden > 0 && y + CHIP_H <= bottom) {
				String text = "+" + hidden + " more";
				float chipW = Math.min(cardW, m.width(text) + 12);
				more.add(new More(i, x + (laneW - chipW) / 2f, y, chipW, CHIP_H, hidden, c.cards().get(shown).key(), text));
			}
		}
		return new Layout(width, height, mode, mt, List.copyOf(lanes), List.copyOf(slots), List.copyOf(more), total);
	}

	/**
	 * A title on at most two lines of {@code maxW} px: the first breaks at the last space that fits (when there is
	 * none near the end, the title stays on one line, clipped with an ellipsis), the second is clipped with an ellipsis.
	 * {"title", ""} when it fits on one.
	 */
	static String[] wrap(String text, int maxW, Measure m) {
		if (text == null || text.isEmpty() || maxW <= 0) {
			return new String[] {"", ""};
		}
		if (m.width(text) <= maxW) {
			return new String[] {text, ""};
		}
		int end = text.length();
		while (end > 0 && m.width(text.substring(0, end)) > maxW) {
			end = text.offsetByCodePoints(end, -1);
		}
		if (end == 0) {
			return new String[] {clip(text, maxW, m), ""};
		}
		int brk = text.lastIndexOf(' ', end);
		if (brk <= 0 || brk < end / 2) {
			return new String[] {clip(text, maxW, m), ""}; // no good space: one line with an ellipsis, never a word split in two
		}
		String first = text.substring(0, brk).stripTrailing();
		String rest = text.substring(brk).stripLeading();
		return new String[] {first, clip(rest, maxW, m)};
	}

	/** Width of a header's count pill: the number at the header scale plus padding. */
	static float pillWidth(String count, Measure m, Metrics mt) {
		return Math.max(mt.headerH() - 6, m.width(count) * mt.headScale() + 8);
	}

	/** Second line of a card: project (status mode) or state word + project, plus queued runs. */
	static String footer(WallCard c, WallMode mode) {
		String base = switch (mode) {
			case STATUS -> c.project().isEmpty() ? c.envLabel() : c.project();
			case PROJECT -> c.attention().word;
			case ENVIRONMENT -> c.project().isEmpty() ? c.attention().word : c.attention().word + " · " + c.project();
		};
		return c.queued() > 0 ? base + "  ⏳" + c.queued() : base;
	}

	/** {@code text} cut to {@code maxW} px with an ellipsis; "" when not even the ellipsis fits. */
	public static String clip(String text, int maxW, Measure m) {
		if (text == null || text.isEmpty() || maxW <= 0) {
			return "";
		}
		if (m.width(text) <= maxW) {
			return text;
		}
		int room = maxW - m.width(ELLIPSIS);
		if (room <= 0) {
			return "";
		}
		int end = text.length();
		while (end > 0 && m.width(text.substring(0, end)) > room) {
			end = text.offsetByCodePoints(end, -1);
		}
		return end == 0 ? ELLIPSIS : text.substring(0, end).stripTrailing() + ELLIPSIS;
	}
}
