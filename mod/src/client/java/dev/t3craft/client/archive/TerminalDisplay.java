package dev.t3craft.client.archive;

import dev.t3craft.client.workstation.TextFit;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What the archive terminal's screen shows (the block's renderer draws it). Pure, cached by the
 * store's version: a steady archive costs one int comparison per frame, and the lines are rebuilt
 * only when {@link ArchiveStore#version()} moves.
 *
 * <pre>
 *   ARCHIVE · 42 threads          header (amber)
 *   Fix login redirect l…         newest closed thread: title (white)
 *   ● vps · ✔ 2026-11-03          environment (its HQ colour) · kind + close date
 *   Bump deps                     second
 *   ● mini · ▣ 2026-11-02
 *   …                             third
 * </pre>
 * Empty archive: the header and "no closed threads".
 */
public final class TerminalDisplay {
	/** Threads listed under the header. */
	public static final int RECENT = 3;
	/** Characters per line (the screen is ~0.85 block wide at the renderer's scale). */
	public static final int WIDTH = 22;
	public static final int HEADER = 0xFFB040;
	public static final int TITLE = 0xF0F0F0;
	public static final int DIM = 0x8A9AA8;

	public record Line(String text, int color) {
	}

	/** One rendered screen: the store version it was built from and its lines. */
	public record Screen(int version, List<Line> lines) {
	}

	private @Nullable Screen cached;
	private int builds;

	/**
	 * The screen for store version {@code version}: the cached one if it was built from the same
	 * version, else built from {@code entries} (newest close first). {@code header}: the localized
	 * "ARCHIVE · %s threads" for a count; {@code empty}: the empty-archive line.
	 */
	public Screen show(int version, Supplier<List<ArchiveEntry>> entries, ZoneId zone, IntFunction<String> header, String empty) {
		Screen c = cached;
		if (c != null && c.version() == version) {
			return c;
		}
		c = new Screen(version, lines(entries.get(), zone, header, empty));
		cached = c;
		builds++;
		return c;
	}

	/** Drop the cache (language change, hot reload). */
	public void invalidate() {
		cached = null;
	}

	/** How many times the lines were built (tests: proves the cache). */
	int builds() {
		return builds;
	}

	public static List<Line> lines(List<ArchiveEntry> entries, ZoneId zone, IntFunction<String> header, String empty) {
		List<Line> out = new ArrayList<>();
		out.add(new Line(TextFit.clip(header.apply(entries.size()), WIDTH), HEADER));
		if (entries.isEmpty()) {
			out.add(new Line(TextFit.clip(empty, WIDTH), DIM));
			return List.copyOf(out);
		}
		for (int i = 0; i < Math.min(RECENT, entries.size()); i++) {
			ArchiveEntry e = entries.get(i);
			out.add(new Line(TextFit.clip(e.title(), WIDTH), TITLE));
			out.add(new Line(TextFit.clip("● " + e.envLabel() + " · " + e.kind().glyph + " " + ArchiveLabel.date(e.closedAtMs(), zone), WIDTH),
					ArchiveLabel.envColor(e.env())));
		}
		return List.copyOf(out);
	}
}
