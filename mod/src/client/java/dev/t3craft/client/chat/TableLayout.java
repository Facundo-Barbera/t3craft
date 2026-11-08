package dev.t3craft.client.chat;

import dev.t3craft.client.chat.Markdown.Align;
import dev.t3craft.client.chat.Markdown.Span;
import dev.t3craft.client.chat.Markdown.Table;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Column widths and wrapped cells of a {@link Markdown.Table} for a given width, measured with the
 * same {@link TextWrap.Measure} as the rest of the transcript. Pure Java; {@link ConversationPanel}
 * turns the result into feed lines and draws the grid.
 *
 * <p>Geometry: 1 px borders, {@link #PAD_X} px of padding each side of a cell. Columns take their
 * natural width (widest cell on one line) when the table fits; otherwise the content width is
 * shared in proportion to the natural widths with a minimum per column and cells wrap (the row is
 * as tall as its tallest cell). When even the minimums do not fit (many columns) every cell stays
 * on one line, cut with "…", and keeps its full text for a hover tooltip.
 */
public final class TableLayout {
	/** Horizontal padding inside a cell, each side. */
	public static final int PAD_X = 4;
	/** Narrowest wrapped column (content px) unless its natural width is smaller. */
	static final int MIN_WRAP = 30;
	/** Narrowest clipped column (content px) in the many-columns fallback. */
	static final int MIN_CLIP = 12;
	private static final String ELLIPSIS = "…";

	/**
	 * One cell: its lines with piece x relative to the table's left edge (alignment applied), and
	 * the full plain text when it was cut with "…" (else null).
	 */
	public record Cell(List<TextWrap.Line> lines, @Nullable String clipped) {
	}

	/** One row: its cells (one per column) and its height in text lines. */
	public record Row(List<Cell> cells, int lines) {
	}

	/**
	 * @param edges   x of every vertical border relative to the table's left edge (n + 1 values; the
	 *                last one is the right border, so the table is {@code edges[n] + 1} px wide)
	 * @param header  header row (bold spans)
	 * @param rows    body rows
	 * @param clipped true in the many-columns fallback (cells on one line, cut with "…")
	 */
	public record Result(int[] edges, Row header, List<Row> rows, boolean clipped) {
		public int width() {
			return edges[edges.length - 1] + 1;
		}

		/** Column under {@code x} (relative to the table), or -1. */
		public int columnAt(int x) {
			for (int k = 0; k + 1 < edges.length; k++) {
				if (x >= edges[k] && x < edges[k + 1]) {
					return k;
				}
			}
			return -1;
		}
	}

	private TableLayout() {
	}

	public static Result layout(Table table, int maxWidth, TextWrap.Measure measure) {
		int n = Math.max(1, table.columns());
		List<List<Span>> header = new ArrayList<>(n);
		for (List<Span> cell : table.header()) {
			header.add(cell.stream().map(s -> new Span(s.text(), true, s.italic(), s.code(), s.url())).toList());
		}
		while (header.size() < n) {
			header.add(List.of());
		}
		int[] nat = new int[n];
		measureRow(header, nat, measure);
		for (List<List<Span>> row : table.rows()) {
			measureRow(row, nat, measure);
		}
		int avail = Math.max(0, maxWidth - (n + 1) - n * 2 * PAD_X);
		long sumNat = 0;
		for (int v : nat) {
			sumNat += v;
		}
		int[] w;
		boolean clip = false;
		if (sumNat <= avail) {
			w = nat.clone();
		} else {
			int[] minWrap = mins(nat, MIN_WRAP);
			if (sum(minWrap) <= avail) {
				w = share(nat, minWrap, avail);
			} else {
				clip = true;
				int[] minClip = mins(nat, MIN_CLIP);
				w = sum(minClip) <= avail ? share(nat, minClip, avail) : minClip;
			}
		}
		int[] edges = new int[n + 1];
		for (int k = 0; k < n; k++) {
			edges[k + 1] = edges[k] + 1 + PAD_X + w[k] + PAD_X;
		}
		List<Align> aligns = table.aligns();
		Row head = row(header, aligns, edges, w, clip, measure);
		List<Row> rows = new ArrayList<>(table.rows().size());
		for (List<List<Span>> r : table.rows()) {
			rows.add(row(r, aligns, edges, w, clip, measure));
		}
		return new Result(edges, head, List.copyOf(rows), clip);
	}

	private static void measureRow(List<List<Span>> row, int[] nat, TextWrap.Measure measure) {
		for (int k = 0; k < nat.length && k < row.size(); k++) {
			nat[k] = Math.max(nat[k], oneLine(row.get(k), measure).width());
		}
	}

	/** The cell on a single line (cells never contain newlines). */
	private static TextWrap.Line oneLine(List<Span> spans, TextWrap.Measure measure) {
		return TextWrap.wrap(spans, Integer.MAX_VALUE / 4, measure).getFirst();
	}

	private static int[] mins(int[] nat, int floor) {
		int[] m = new int[nat.length];
		for (int k = 0; k < nat.length; k++) {
			m[k] = Math.min(nat[k], floor);
		}
		return m;
	}

	private static long sum(int[] a) {
		long s = 0;
		for (int v : a) {
			s += v;
		}
		return s;
	}

	/**
	 * Shares {@code avail} px in proportion to {@code nat}, never under {@code min}: columns whose
	 * share would fall under their minimum get it and leave the rest to the others.
	 */
	static int[] share(int[] nat, int[] min, int avail) {
		int n = nat.length;
		int[] w = new int[n];
		boolean[] fixed = new boolean[n];
		int remaining = avail;
		long restNat = sum(nat);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int k = 0; k < n; k++) {
				if (!fixed[k] && (restNat <= 0 || nat[k] * (long) remaining / restNat < min[k])) {
					fixed[k] = true;
					w[k] = min[k];
					remaining -= min[k];
					restNat -= nat[k];
					changed = true;
				}
			}
		}
		int used = 0;
		int widest = -1;
		for (int k = 0; k < n; k++) {
			if (!fixed[k]) {
				w[k] = (int) (nat[k] * (long) Math.max(0, remaining) / Math.max(1, restNat));
				used += w[k];
				if (widest < 0 || nat[k] > nat[widest]) {
					widest = k;
				}
			}
		}
		if (widest >= 0 && remaining > used) {
			w[widest] += remaining - used; // rounding leftovers to the widest column
		}
		return w;
	}

	private static Row row(List<List<Span>> cells, List<Align> aligns, int[] edges, int[] w, boolean clip, TextWrap.Measure measure) {
		int n = w.length;
		List<Cell> out = new ArrayList<>(n);
		int tallest = 1;
		for (int k = 0; k < n; k++) {
			List<Span> spans = k < cells.size() ? cells.get(k) : List.of();
			Align align = k < aligns.size() ? aligns.get(k) : Align.LEFT;
			int cx = edges[k] + 1 + PAD_X;
			List<TextWrap.Line> lines;
			String full = null;
			if (clip) {
				TextWrap.Line one = oneLine(spans, measure);
				if (one.width() > w[k]) {
					full = one.plain();
					one = clipLine(one, w[k], measure);
				}
				lines = List.of(one);
			} else {
				lines = TextWrap.wrap(spans, Math.max(1, w[k]), measure);
			}
			List<TextWrap.Line> placed = new ArrayList<>(lines.size());
			for (TextWrap.Line l : lines) {
				int shift = switch (align) {
					case LEFT -> 0;
					case CENTER -> Math.max(0, (w[k] - l.width()) / 2);
					case RIGHT -> Math.max(0, w[k] - l.width());
				};
				List<TextWrap.Piece> ps = new ArrayList<>(l.pieces().size());
				for (TextWrap.Piece p : l.pieces()) {
					ps.add(new TextWrap.Piece(p.span(), cx + shift + p.x(), p.width()));
				}
				placed.add(new TextWrap.Line(List.copyOf(ps), l.width()));
			}
			tallest = Math.max(tallest, placed.size());
			out.add(new Cell(List.copyOf(placed), full));
		}
		return new Row(List.copyOf(out), tallest);
	}

	/** {@code line} cut to {@code room} px with a trailing "…" (in the style of the last piece kept). */
	static TextWrap.Line clipLine(TextWrap.Line line, int room, TextWrap.Measure measure) {
		List<TextWrap.Piece> kept = new ArrayList<>();
		Span last = Span.plain(ELLIPSIS);
		int ew = measure.width(last);
		int limit = room - ew;
		for (TextWrap.Piece p : line.pieces()) {
			last = p.span().withText(ELLIPSIS);
			ew = measure.width(last);
			limit = room - ew;
			if (p.x() + p.width() <= limit) {
				kept.add(p);
				continue;
			}
			String text = p.span().text();
			int take = 0;
			int i = 0;
			while (i < text.length()) {
				int next = i + Character.charCount(text.codePointAt(i));
				if (p.x() + measure.width(p.span().withText(text.substring(0, next))) > limit) {
					break;
				}
				take = next;
				i = next;
			}
			if (take > 0) {
				Span part = p.span().withText(text.substring(0, take).stripTrailing());
				if (!part.text().isEmpty()) {
					kept.add(new TextWrap.Piece(part, p.x(), measure.width(part)));
				}
			}
			break;
		}
		int x = kept.isEmpty() ? 0 : kept.getLast().x() + kept.getLast().width();
		if (x + ew > room && !kept.isEmpty()) {
			x = Math.max(0, room - ew);
		}
		kept.add(new TextWrap.Piece(last, x, ew));
		return new TextWrap.Line(List.copyOf(kept), x + ew);
	}
}
