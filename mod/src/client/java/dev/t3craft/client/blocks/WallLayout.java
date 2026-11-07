// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.EnvBadge;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where everything goes on the wall, in board pixels (x right, y down from the wall's top-left
 * corner as seen from the front). Columns share the width equally; each column has a header
 * (label + count) and a stack of fixed-height cards. When a column holds more cards than fit, the
 * last slot becomes a "+N more" chip (clicking it opens the first hidden thread). Pure: text widths
 * come from a {@link Measure} (the font in game, a fixed width in tests).
 *
 * <p>Lanes, headers with counts and the "+N more" overflow chip adapted from AgentCraft's
 * {@code TaskBoard} (which also sizes cards to their titles; here cards have one size so the wall
 * reads evenly from across the hub).
 */
public final class WallLayout {
	public static final int PAD = 4;
	public static final int GAP = 4;
	public static final int HEADER_H = 14;
	public static final int CARD_H = 24;
	public static final int CARD_GAP = 3;
	public static final int CHIP_H = 12;
	/** Inner padding of a card's text. */
	public static final int CARD_PAD = 4;
	/** Room on a card's left for the attention stripe. */
	public static final int STRIPE = 3;
	/** Badge size on a card's footer line. */
	public static final int BADGE = 9;
	public static final String ELLIPSIS = "…";

	/** Text width in board pixels. */
	@FunctionalInterface
	public interface Measure {
		int width(String text);
	}

	/** A column's lane: header label (clipped) and count text. */
	public record Lane(int column, String id, float x, float w, String label, String count, int color) {
	}

	/** A card's slot and its texts (already clipped to fit). */
	public record Slot(String key, int column, float x, float y, float w, float h, String title, String footer,
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

	public record Layout(float width, float height, WallMode mode, List<Lane> lanes, List<Slot> cards, List<More> more, int total) {
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

	/** How many card rows fit under a header in a wall {@code height} px tall. */
	public static int rows(float height) {
		float avail = height - 2 * PAD - HEADER_H - GAP;
		return avail < CARD_H ? 0 : (int) ((avail + CARD_GAP) / (CARD_H + CARD_GAP));
	}

	/** Most columns that stay readable on a wall {@code width} px wide (project/environment modes). */
	public static int maxColumns(float width) {
		return Math.max(1, (int) ((width - 2 * PAD + GAP) / (64 + GAP)));
	}

	public static Layout build(List<WallColumns.Column> columns, WallMode mode, float width, float height, Measure m) {
		int n = Math.max(1, columns.size());
		float laneW = (width - 2 * PAD - GAP * (n - 1)) / n;
		int rows = rows(height);
		List<Lane> lanes = new ArrayList<>(columns.size());
		List<Slot> slots = new ArrayList<>();
		List<More> more = new ArrayList<>();
		int total = 0;
		for (int i = 0; i < columns.size(); i++) {
			WallColumns.Column c = columns.get(i);
			float x = PAD + i * (laneW + GAP);
			String count = Integer.toString(c.count());
			int labelRoom = (int) laneW - 2 * CARD_PAD - m.width(count) - 3;
			lanes.add(new Lane(i, c.id(), x, laneW, clip(c.label(), labelRoom, m), count, c.color()));
			total += c.count();
			int shown = c.count() <= rows ? c.count() : Math.max(0, rows - 1);
			float y = PAD + HEADER_H + GAP;
			float textRoom = laneW - STRIPE - 2 * CARD_PAD;
			for (int k = 0; k < shown; k++) {
				WallCard card = c.cards().get(k);
				String footer = footer(card, mode);
				slots.add(new Slot(card.key(), i, x, y, laneW, CARD_H,
						clip(card.title(), (int) textRoom, m),
						clip(footer, (int) (textRoom - BADGE - 3), m),
						card.attention(), card.badge()));
				y += CARD_H + CARD_GAP;
			}
			int hidden = c.count() - shown;
			if (hidden > 0 && rows > 0) {
				String text = "+" + hidden + " more";
				float chipW = Math.min(laneW, m.width(text) + 10);
				more.add(new More(i, x + (laneW - chipW) / 2f, y + (CARD_H - CHIP_H) / 2f, chipW, CHIP_H, hidden,
						c.cards().get(shown).key(), text));
			}
		}
		return new Layout(width, height, mode, List.copyOf(lanes), List.copyOf(slots), List.copyOf(more), total);
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
