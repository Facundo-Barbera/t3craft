// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatTheme;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Splits the wall's cards into columns. Pure.
 *
 * <ul>
 *   <li>{@link WallMode#STATUS}: always the four kanban columns, in this order: <b>Working</b>
 *       (working), <b>Needs you</b> (needs you + approval pending), <b>Error</b>, <b>Done</b> (done +
 *       idle). Colours of the chat kit ({@link ChatTheme}).</li>
 *   <li>{@link WallMode#PROJECT}: one column per project (and environment, so two environments' "api"
 *       stay apart), {@link WallMode#ENVIRONMENT}: one per environment (badge colour). Columns in a
 *       stable label order so they never jump; past {@code maxColumns} the rest merge into "Other".</li>
 * </ul>
 * Cards of a column follow {@link WallCard#ORDER} (the chat's order). Settled threads are not cards.
 *
 * <p>Column model adapted from AgentCraft's {@code TaskBoard.Col} (fixed kanban columns with a
 * status family each).
 */
public final class WallColumns {
	public static final String WORKING = "working";
	public static final String NEEDS_YOU = "needs_you";
	public static final String ERROR = "error";
	public static final String DONE = "done";
	public static final String OTHER = "#other";

	/** A column: stable id, header label, header colour (ARGB) and its cards in order. */
	public record Column(String id, String label, int color, List<WallCard> cards) {
		public Column {
			cards = List.copyOf(cards);
		}

		public int count() {
			return cards.size();
		}
	}

	private WallColumns() {
	}

	/** Status column id of an attention level. */
	public static String statusColumn(Attention a) {
		return switch (a) {
			case WORKING -> WORKING;
			case NEEDS_YOU, PENDING -> NEEDS_YOU;
			case ERROR -> ERROR;
			case DONE, IDLE -> DONE;
		};
	}

	public static List<Column> group(Collection<WallCard> cards, WallMode mode, ChatTheme theme, int maxColumns) {
		return switch (mode) {
			case STATUS -> byStatus(cards, theme);
			case PROJECT -> byKey(cards, theme, maxColumns, true);
			case ENVIRONMENT -> byKey(cards, theme, maxColumns, false);
		};
	}

	private static List<Column> byStatus(Collection<WallCard> cards, ChatTheme theme) {
		Map<String, List<WallCard>> by = new LinkedHashMap<>();
		for (String id : List.of(WORKING, NEEDS_YOU, ERROR, DONE)) {
			by.put(id, new ArrayList<>());
		}
		for (WallCard c : cards) {
			by.get(statusColumn(c.attention())).add(c);
		}
		return List.of(
				column(WORKING, "Working", theme.working(), by.get(WORKING)),
				column(NEEDS_YOU, "Needs you", theme.needsYou(), by.get(NEEDS_YOU)),
				column(ERROR, "Error", theme.error(), by.get(ERROR)),
				column(DONE, "Done", theme.done(), by.get(DONE)));
	}

	private record Bucket(String id, String label, List<WallCard> cards) {
	}

	private static List<Column> byKey(Collection<WallCard> cards, ChatTheme theme, int maxColumns, boolean project) {
		Map<String, Bucket> by = new LinkedHashMap<>();
		for (WallCard c : cards) {
			String id = project ? c.envId() + "\u0000" + c.project() : c.envId();
			String label = project ? (c.project().isEmpty() ? "(no project)" : c.project()) : c.envLabel();
			by.computeIfAbsent(id, k -> new Bucket(k, label, new ArrayList<>())).cards().add(c);
		}
		List<Bucket> buckets = new ArrayList<>(by.values());
		buckets.sort(Comparator.comparing((Bucket b) -> b.label().toLowerCase(Locale.ROOT)).thenComparing(Bucket::id));
		int max = Math.max(1, maxColumns);
		List<Column> out = new ArrayList<>();
		List<WallCard> rest = new ArrayList<>();
		for (int i = 0; i < buckets.size(); i++) {
			Bucket b = buckets.get(i);
			if (buckets.size() > max && i >= max - 1) {
				rest.addAll(b.cards());
				continue;
			}
			int color = project ? theme.color(mostUrgent(b.cards())) : b.cards().get(0).badge().background();
			out.add(column(b.id(), b.label(), color, b.cards()));
		}
		if (!rest.isEmpty()) {
			out.add(column(OTHER, "Other", theme.idle(), rest));
		}
		return List.copyOf(out);
	}

	static Attention mostUrgent(List<WallCard> cards) {
		Attention best = Attention.IDLE;
		for (WallCard c : cards) {
			if (c.attention().ordinal() < best.ordinal()) {
				best = c.attention();
			}
		}
		return best;
	}

	private static Column column(String id, String label, int color, List<WallCard> cards) {
		List<WallCard> sorted = new ArrayList<>(cards);
		sorted.sort(WallCard.ORDER);
		return new Column(id, label, color, sorted);
	}
}
