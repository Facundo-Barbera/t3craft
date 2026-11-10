package dev.t3craft.client.blocks;

import dev.t3craft.client.archive.TerminalDisplay;
import java.util.List;
import java.util.Map;

/**
 * The archive terminal's own display options (issue #43), stored as strings in the block entity's {@code options}
 * compound by its config page and read by {@link ArchiveTerminalRenderer}. Missing or unknown = the default, today's
 * look: the latest {@link TerminalDisplay#RECENT} closed threads under an amber header.
 *
 * <pre>
 * options: { recent: "1" | "2" | "3"   (closed threads listed under the header),
 *            accent: "status" | "accent" | "brass" | "working" | "agent" | "done" | "error"   (the header's colour;
 *                    "status" = the terminal's amber) }
 * </pre>
 */
public record ArchiveOptions(int recent, String accent) {
	public static final String RECENT = "recent";
	public static final String ACCENT = BlockAccent.KEY;
	/** Counts the page offers. */
	public static final List<Integer> RECENT_CHOICES = List.of(1, 2, 3);
	public static final ArchiveOptions DEFAULT = new ArchiveOptions(TerminalDisplay.RECENT, BlockAccent.OWN);

	public ArchiveOptions {
		recent = Math.max(1, Math.min(TerminalDisplay.RECENT, recent));
		accent = accent == null || !BlockAccent.CHOICES.contains(accent) ? BlockAccent.OWN : accent;
	}

	/** From a block entity's options (empty → {@link #DEFAULT}, without allocating). */
	public static ArchiveOptions of(Map<String, String> o) {
		if (o == null || o.isEmpty()) {
			return DEFAULT;
		}
		int recent = TerminalDisplay.RECENT;
		try {
			recent = Integer.parseInt(o.getOrDefault(RECENT, Integer.toString(TerminalDisplay.RECENT)).trim());
		} catch (NumberFormatException e) {
			// default
		}
		return new ArchiveOptions(recent, BlockAccent.of(o));
	}

	/** How many of the display's lines to draw: the header and two lines (title, environment) per thread. */
	public int lines() {
		return 1 + 2 * recent;
	}
}
