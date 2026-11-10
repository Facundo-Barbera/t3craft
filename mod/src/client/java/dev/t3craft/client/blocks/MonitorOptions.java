package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.ChatTheme;
import java.util.List;
import java.util.Map;

/**
 * A monitor's own display options (issue #33), stored as strings in the block entity's {@code options} compound
 * (written by the monitor's config page) and read by {@link MonitorRenderer}. A missing or unknown value is the
 * default, and the defaults are today's look: normal text, the status colour as accent, as many rows as fit,
 * the age in the status chip.
 *
 * <pre>
 * options: { textSize: "small" | "normal" | "large",
 *            accent: "status" | "accent" | "brass" | "working" | "agent" | "done" | "error",
 *            rows: "0" (as many as fit) | "1".."40",
 *            showAge: "true" | "false" }
 * </pre>
 * The accents name tokens of the UI theme ({@code gui/ui-style.json}, {@link ChatTheme#warmStudio()}).
 */
public record MonitorOptions(TextSize textSize, String accent, int rows, boolean showAge) {
	public static final String TEXT_SIZE = "textSize";
	public static final String ACCENT = "accent";
	public static final String ROWS = "rows";
	public static final String SHOW_AGE = "showAge";
	public static final String ACCENT_STATUS = "status";
	public static final int MAX_ROWS = 40;
	/** Accent choices: the status colour, then theme tokens. */
	public static final List<String> ACCENTS = List.of(ACCENT_STATUS, "accent", "brass", "working", "agent", "done", "error");
	/** Row choices offered by the page (0 = as many as fit). */
	public static final List<Integer> ROW_CHOICES = List.of(0, 3, 5, 8, 12, 16, 20);

	/** Text size of the NEAR screen: a factor on its pixels per block (more pixels = smaller text, more rows). */
	public enum TextSize {
		SMALL("small", "Small", 1.25f),
		NORMAL("normal", "Normal", 1f),
		LARGE("large", "Large", 0.75f);

		public final String id;
		public final String label;
		public final float density;

		TextSize(String id, String label, float density) {
			this.id = id;
			this.label = label;
			this.density = density;
		}

		static TextSize parse(String s) {
			for (TextSize t : values()) {
				if (t.id.equals(s)) {
					return t;
				}
			}
			return NORMAL;
		}
	}

	public static final MonitorOptions DEFAULT = new MonitorOptions(TextSize.NORMAL, ACCENT_STATUS, 0, true);

	public MonitorOptions {
		textSize = textSize == null ? TextSize.NORMAL : textSize;
		accent = accent == null || !ACCENTS.contains(accent) ? ACCENT_STATUS : accent;
		rows = Math.max(0, Math.min(MAX_ROWS, rows));
	}

	/** From a block entity's options (empty → {@link #DEFAULT}, without allocating). */
	public static MonitorOptions of(Map<String, String> o) {
		if (o == null || o.isEmpty()) {
			return DEFAULT;
		}
		int rows = 0;
		try {
			rows = Integer.parseInt(o.getOrDefault(ROWS, "0").trim());
		} catch (NumberFormatException e) {
			// default
		}
		return new MonitorOptions(TextSize.parse(o.getOrDefault(TEXT_SIZE, TextSize.NORMAL.id)), o.getOrDefault(ACCENT, ACCENT_STATUS), rows,
				!"false".equals(o.get(SHOW_AGE)));
	}

	public boolean isDefault() {
		return equals(DEFAULT);
	}

	/** The accent colour (ARGB) from the theme's token, or 0 for the status colour. */
	public int accentColor(ChatTheme t) {
		return accentColor(accent, t);
	}

	public static int accentColor(String accent, ChatTheme t) {
		return switch (accent) {
			case "accent" -> t.accent();
			case "brass" -> t.brass();
			case "working" -> t.working();
			case "agent" -> t.agent();
			case "done" -> t.done();
			case "error" -> t.error();
			default -> 0;
		};
	}

	/** Label of an accent in the page's select. */
	public static String accentLabel(String accent) {
		return switch (accent) {
			case "accent" -> "Clay (theme accent)";
			case "brass" -> "Brass";
			case "working" -> "Teal";
			case "agent" -> "Blue";
			case "done" -> "Green";
			case "error" -> "Red";
			default -> "Status colour";
		};
	}

	/** Part of the renderer's glyph cache key (with the resolved accent, so a theme reload redraws). */
	String cacheKey(int accentArgb) {
		return isDefault() ? "" : "|" + textSize.id + "," + accent + ":" + Integer.toHexString(accentArgb) + "," + rows + "," + showAge;
	}
}
