package dev.t3craft.client.portable.held;

import dev.t3craft.client.portable.Conversation;
import dev.t3craft.client.portable.EnvBadge;
import dev.t3craft.client.portable.PortableEnv;
import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the held T3 Terminal shows, as rows of coloured spans (pure, tested). Two pages:
 * <ul>
 *   <li><b>Chat</b>: the thread last opened in the terminal (if still listed and enabled): badge +
 *   title, state, then the newest conversation lines, bottom-aligned like a chat.</li>
 *   <li><b>List</b>: every thread, attention first, each with its environment badge.</li>
 * </ul>
 * Honest states: not paired, disconnected (with each environment's status), nothing to show,
 * loading/unavailable/failed conversation. Equal inputs give equal rows, so the caller re-rasterises
 * only when this changes. No clock text, so an idle view never changes by itself.
 */
public final class HeldScreenModel {
	public static final int BG = 0x101418;
	public static final int HEADER_BG = 0x1F3A40;
	public static final int TEXT = 0xE6E6E6;
	public static final int DIM = 0x8A949E;
	public static final int ACCENT = 0x55FFFF;
	public static final int AMBER = 0xFFAA00;
	public static final int RED = 0xFF5555;

	public enum Kind {
		HEADER, TEXT, RULE
	}

	/** {@code bg} 0 = transparent. */
	public record Span(String text, int color, int bg) {
		static Span of(String text, int color) {
			return new Span(text, color, 0);
		}
	}

	public record Row(Kind kind, List<Span> spans) {
		public Row {
			spans = List.copyOf(spans);
		}

		static Row text(Span... s) {
			return new Row(Kind.TEXT, List.of(s));
		}
	}

	@FunctionalInterface
	public interface Measure {
		int width(String s);
	}

	public enum Page {
		CHAT, LIST
	}

	/**
	 * @param selectedNpc thread opened last in the terminal, or null
	 * @param widthPx     text area width
	 * @param maxRows     rows that fit
	 */
	public record Input(PortableState state, Conversation conversation, @Nullable String selectedNpc, boolean showChat,
			Map<String, EnvBadge> badges, int widthPx, int maxRows, Measure measure) {
	}

	private HeldScreenModel() {
	}

	public static Page page(Input in) {
		return chatThread(in) != null ? Page.CHAT : Page.LIST;
	}

	static @Nullable PortableThread chatThread(Input in) {
		if (!in.showChat() || in.selectedNpc() == null || in.state().connection() != PortableState.Connection.CONNECTED) {
			return null;
		}
		for (PortableThread t : in.state().threads()) {
			if (t.id().equals(in.selectedNpc())) {
				return t;
			}
		}
		return null;
	}

	public static List<Row> build(Input in) {
		List<Row> rows = new ArrayList<>();
		PortableState s = in.state();
		int attention = s.needsAttention();
		rows.add(new Row(Kind.HEADER, List.of(
				new Span("T3 TERMINAL", ACCENT, 0),
				new Span(attention > 0 ? "  !" + attention + " need you" : s.connection() == PortableState.Connection.CONNECTED ? "  " + s.threads().size() + " threads" : "", attention > 0 ? AMBER : DIM, 0))));

		switch (s.connection()) {
			case NOT_CONFIGURED -> {
				wrap(rows, in, "No T3 environment paired.", TEXT);
				wrap(rows, in, "Pair one with /t3craft pair (read-only).", DIM);
				return cap(rows, in.maxRows());
			}
			case DISCONNECTED -> {
				wrap(rows, in, "Not connected to T3.", RED);
				for (PortableEnv e : s.environments()) {
					EnvBadge b = badge(in, e.id());
					rows.add(Row.text(new Span(" " + b.letters() + " ", 0x101418, b.color()), Span.of(" " + e.label() + ": " + e.status(), DIM)));
				}
				wrap(rows, in, "Reconnecting by itself.", DIM);
				return cap(rows, in.maxRows());
			}
			case CONNECTED -> {
			}
		}

		PortableThread chat = chatThread(in);
		if (chat != null) {
			chatPage(rows, in, chat);
		} else {
			listPage(rows, in);
		}
		return cap(rows, in.maxRows());
	}

	private static void listPage(List<Row> rows, Input in) {
		List<PortableThread> threads = in.state().threads();
		if (threads.isEmpty()) {
			wrap(rows, in, "No threads to show (only active, waiting or recent ones are listed).", DIM);
			return;
		}
		int room = in.maxRows() - rows.size();
		int shown = threads.size() > room ? Math.max(0, room - 1) : threads.size();
		for (int i = 0; i < shown; i++) {
			rows.add(threadRow(in, threads.get(i)));
		}
		if (shown < threads.size()) {
			rows.add(Row.text(Span.of("+" + (threads.size() - shown) + " more", DIM)));
		}
	}

	static Row threadRow(Input in, PortableThread t) {
		EnvBadge b = badge(in, t.env());
		String badgeText = " " + b.letters() + " ";
		String glyph = " " + glyph(t.state()) + " ";
		int used = in.measure().width(badgeText) + in.measure().width(glyph);
		String title = clip(in, t.title(), in.widthPx() - used);
		return Row.text(new Span(badgeText, 0x101418, b.color()), Span.of(glyph, t.color()), Span.of(title, t.settled() ? DIM : TEXT));
	}

	private static void chatPage(List<Row> rows, Input in, PortableThread t) {
		rows.add(threadRow(in, t));
		String status = word(t.state()) + (t.activity() == null ? "" : " - " + t.activity());
		rows.add(Row.text(Span.of(clip(in, status, in.widthPx()), t.color())));
		rows.add(new Row(Kind.RULE, List.of()));
		Conversation c = in.conversation();
		boolean mine = t.id().equals(c.npcId());
		List<Row> body = new ArrayList<>();
		if (!mine || c.status() == Conversation.Status.LOADING || c.status() == Conversation.Status.NONE) {
			wrapInto(body, in, "Loading conversation...", DIM);
		} else if (c.status() == Conversation.Status.UNAVAILABLE) {
			wrapInto(body, in, c.detail(), AMBER);
		} else {
			for (Conversation.Line l : c.lines()) {
				lineRows(body, in, l);
			}
			if (c.lines().isEmpty()) {
				wrapInto(body, in, "No messages yet.", DIM);
			}
			if (c.status() == Conversation.Status.FAILED) {
				wrapInto(body, in, "! " + c.detail(), AMBER);
			}
		}
		int room = in.maxRows() - rows.size();
		// newest at the bottom, like a chat: keep the tail
		rows.addAll(body.size() > room ? body.subList(body.size() - room, body.size()) : body);
	}

	private static void lineRows(List<Row> out, Input in, Conversation.Line l) {
		String prefix = l.role().label + ": ";
		int prefixW = in.measure().width(prefix);
		String text = l.text().replaceAll("\\s+", " ").trim() + (l.streaming() ? " ..." : "");
		List<String> parts = wrapText(in.measure(), text, in.widthPx() - prefixW, in.widthPx());
		int color = l.role() == Conversation.Role.TOOL ? DIM : TEXT;
		for (int i = 0; i < parts.size(); i++) {
			out.add(i == 0
					? Row.text(Span.of(prefix, l.role().color), Span.of(parts.get(i), color))
					: Row.text(Span.of(parts.get(i), color)));
		}
	}

	private static void wrap(List<Row> rows, Input in, String text, int color) {
		wrapInto(rows, in, text, color);
	}

	private static void wrapInto(List<Row> rows, Input in, String text, int color) {
		for (String part : wrapText(in.measure(), text, in.widthPx(), in.widthPx())) {
			rows.add(Row.text(Span.of(part, color)));
		}
	}

	/** Greedy word wrap; the first line may be narrower ({@code firstW}). Over-long words are cut. */
	static List<String> wrapText(Measure m, String text, int firstW, int restW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		int limit = firstW;
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (m.width(candidate) <= limit) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (!line.isEmpty()) {
				out.add(line.toString());
				line.setLength(0);
				limit = restW;
			}
			String w = word;
			while (m.width(w) > limit && w.length() > 1) {
				int cut = w.length() - 1;
				while (cut > 1 && m.width(w.substring(0, cut)) > limit) {
					cut--;
				}
				out.add(w.substring(0, cut));
				w = w.substring(cut);
				limit = restW;
			}
			line.append(w);
		}
		if (!line.isEmpty() || out.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	private static String clip(Input in, String s, int maxW) {
		if (in.measure().width(s) <= maxW) {
			return s;
		}
		String t = s;
		while (!t.isEmpty() && in.measure().width(t + "...") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t.stripTrailing() + "...";
	}

	private static List<Row> cap(List<Row> rows, int max) {
		return rows.size() > max ? new ArrayList<>(rows.subList(0, max)) : rows;
	}

	static EnvBadge badge(Input in, String env) {
		EnvBadge b = in.badges().get(env);
		return b != null ? b : EnvBadge.fallback(env, in.badges().values());
	}

	/** ASCII state glyphs (the bitmap sheet has no ⚙/✔). */
	static String glyph(String state) {
		return switch (state) {
			case "needs_attention" -> "!";
			case "error" -> "x";
			case "working" -> "*";
			case "done" -> "v";
			default -> "-";
		};
	}

	static String word(String state) {
		return switch (state) {
			case "needs_attention" -> "Needs you";
			case "error" -> "Error";
			case "working" -> "Working";
			case "done" -> "Done";
			default -> "Idle";
		};
	}
}
