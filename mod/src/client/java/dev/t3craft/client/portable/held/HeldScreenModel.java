package dev.t3craft.client.portable.held;

import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.portable.PortableEnv;
import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * What the held T3 Terminal shows on its own screen, as rows of coloured spans (pure, tested). It is
 * the unified thread list in miniature: every environment's threads in one list, attention first,
 * each row with its environment chip, state glyph and title; the most urgent thread also shows its
 * latest message (what T3 already reports as the thread's activity). No conversation and no list
 * widget of its own: round 2 embeds feat/chat's component in the full screen; this only summarises.
 *
 * <p>Honest states: not paired, disconnected (each environment's status), nothing to show. Equal
 * inputs give equal rows, and there is no clock text, so an idle terminal never changes by itself:
 * {@link HeldScreenCache} re-rasterises only when the rows change.
 */
public final class HeldScreenModel {
	// Portable palette (PortableScreen): keeps the terminal's look on the item.
	public static final int BG = 0x101418;
	public static final int HEADER_BG = 0x1F3A40;
	public static final int SELECTED = 0x27414A;
	public static final int BORDER = 0x3A6B73;
	public static final int TEXT = 0xE6E6E6;
	public static final int DIM = 0x8A949E;
	public static final int ACCENT = 0x55FFFF;
	public static final int AMBER = 0xFFAA00;
	public static final int RED = 0xFF5555;
	public static final int GREEN = 0x55FF55;
	/** Text colour on an environment chip (kept for callers; chips now use {@code EnvBadge.foreground()}). */
	public static final int CHIP_TEXT = 0x101418;

	/** Extra px around a span that has a background (1 each side); the rasteriser draws it so. */
	public static final int CHIP_PAD = 2;

	/** Lines of the top thread's latest message. */
	static final int MESSAGE_LINES = 2;

	public enum Kind {
		HEADER, TEXT, RULE
	}

	/** {@code bg} 0 = transparent. */
	public record Span(String text, int color, int bg) {
		static Span of(String text, int color) {
			return new Span(text, color, 0);
		}
	}

	/** {@code bg} 0 = no row highlight. */
	public record Row(Kind kind, List<Span> spans, int bg) {
		public Row {
			spans = List.copyOf(spans);
		}

		static Row text(Span... s) {
			return new Row(Kind.TEXT, List.of(s), 0);
		}

		static Row rule() {
			return new Row(Kind.RULE, List.of(), 0);
		}
	}

	@FunctionalInterface
	public interface Measure {
		int width(String s);
	}

	/**
	 * @param badges  environment id → chip
	 * @param widthPx text area width
	 * @param maxRows rows that fit, header included
	 */
	public record Input(PortableState state, Function<String, EnvBadge> badges, int widthPx, int maxRows, Measure measure) {
	}

	private HeldScreenModel() {
	}

	/**
	 * The list order: needs attention first (thread priority, the same tiers the world and the
	 * unified list use), then live before settled; ties keep the source order, which already ranks
	 * by recent activity.
	 */
	public static List<PortableThread> order(List<PortableThread> threads) {
		List<PortableThread> out = new ArrayList<>(threads);
		out.sort(PortableState.ORDER);
		return out;
	}

	public static List<Row> build(Input in) {
		List<Row> rows = new ArrayList<>();
		PortableState s = in.state();
		int attention = s.needsAttention();
		String right = attention > 0 ? " !" + attention + " need you"
				: s.connection() == PortableState.Connection.CONNECTED ? " " + s.threads().size() + (s.threads().size() == 1 ? " thread" : " threads") : "";
		rows.add(new Row(Kind.HEADER, List.of(Span.of("T3", ACCENT), Span.of(right, attention > 0 ? AMBER : DIM)), HEADER_BG));

		switch (s.connection()) {
			case NOT_CONFIGURED -> {
				wrap(rows, in, "No T3 environment paired.", TEXT);
				wrap(rows, in, "/t3craft pair", DIM);
				return cap(rows, in.maxRows());
			}
			case DISCONNECTED -> {
				wrap(rows, in, "Not connected.", RED);
				for (PortableEnv e : s.environments()) {
					EnvBadge b = in.badges().apply(e.id());
					rows.add(Row.text(chip(b), Span.of(" " + clip(in, e.status(), in.widthPx() - CHIP_PAD - in.measure().width(chipText(b) + " ")),
							statusColor(e.status()))));
				}
				return cap(rows, in.maxRows());
			}
			case CONNECTED -> {
			}
		}

		List<PortableThread> threads = order(s.threads());
		if (threads.isEmpty()) {
			wrap(rows, in, "No threads to show.", DIM);
			return cap(rows, in.maxRows());
		}

		PortableThread top = threads.get(0);
		rows.add(threadRow(in, top, SELECTED));
		String msg = top.activity() == null ? null : top.activity().replaceAll("\\s+", " ").trim();
		if (msg != null && !msg.isEmpty()) {
			int msgW = in.widthPx() - in.measure().width(" "); // indented one space under the title
			List<String> lines = wrapText(in.measure(), msg, msgW, msgW);
			int color = top.needsAttention() ? AMBER : "error".equals(top.state()) ? RED : DIM;
			for (int i = 0; i < Math.min(MESSAGE_LINES, lines.size()); i++) {
				String line = lines.get(i);
				if (i == MESSAGE_LINES - 1 && lines.size() > MESSAGE_LINES) {
					line = clip(in, line + " " + lines.get(i + 1), msgW);
				}
				rows.add(new Row(Kind.TEXT, List.of(Span.of(" " + line, color)), SELECTED));
			}
		}
		if (threads.size() > 1) {
			rows.add(Row.rule());
		}

		int room = in.maxRows() - rows.size();
		List<PortableThread> rest = threads.subList(1, threads.size());
		int shown = rest.size() > room ? Math.max(0, room - 1) : rest.size();
		for (int i = 0; i < shown; i++) {
			rows.add(threadRow(in, rest.get(i), 0));
		}
		if (shown < rest.size()) {
			rows.add(Row.text(Span.of("+" + (rest.size() - shown) + " more", DIM)));
		}
		return cap(rows, in.maxRows());
	}

	static Row threadRow(Input in, PortableThread t, int bg) {
		EnvBadge b = in.badges().apply(t.env());
		String glyph = " " + glyph(t.state()) + " ";
		int used = in.measure().width(chipText(b)) + CHIP_PAD + in.measure().width(glyph);
		String title = clip(in, t.title(), in.widthPx() - used);
		return new Row(Kind.TEXT, List.of(chip(b), Span.of(glyph, t.color() & 0xFFFFFF), Span.of(title, t.settled() ? DIM : TEXT)), bg);
	}

	private static Span chip(EnvBadge b) {
		return new Span(chipText(b), b.foreground() & 0xFFFFFF, b.background() & 0xFFFFFF);
	}

	private static String chipText(EnvBadge b) {
		return b.initial();
	}

	static int statusColor(String status) {
		return switch (status) {
			case "ok" -> GREEN;
			case "connecting" -> 0xFFFF55;
			default -> RED;
		};
	}

	private static void wrap(List<Row> rows, Input in, String text, int color) {
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

	static String clip(Input in, String s, int maxW) {
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
}
