package dev.t3craft.client.blocks;

import java.util.Map;

/**
 * The merge station's own display options (issue #43), stored as strings in the block entity's {@code options} compound
 * by its config page and read by {@link MergeStationRenderer}. Missing or unknown = the default, today's look.
 *
 * <pre>
 * options: { accent:    "status" | "accent" | "brass" | "working" | "agent" | "done" | "error"   (the headline's colour;
 *                       "status" = the colour of the pull requests' state: green open, amber draft, violet merged),
 *            showTitle: "true" | "false"   (the third line, the title of the first pending pull request) }
 * </pre>
 * The lamps and the frame of the model follow the block state ({@code pr}) and are not options.
 */
public record MergeOptions(String accent, boolean showTitle) {
	public static final String ACCENT = BlockAccent.KEY;
	public static final String SHOW_TITLE = "showTitle";
	public static final MergeOptions DEFAULT = new MergeOptions(BlockAccent.OWN, true);

	public MergeOptions {
		accent = accent == null || !BlockAccent.CHOICES.contains(accent) ? BlockAccent.OWN : accent;
	}

	/** From a block entity's options (empty → {@link #DEFAULT}, without allocating). */
	public static MergeOptions of(Map<String, String> o) {
		if (o == null || o.isEmpty()) {
			return DEFAULT;
		}
		return new MergeOptions(BlockAccent.of(o), !"false".equals(o.get(SHOW_TITLE)));
	}
}
