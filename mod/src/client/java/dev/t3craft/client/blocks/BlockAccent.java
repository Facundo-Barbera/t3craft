package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.ChatTheme;
import java.util.List;
import java.util.Map;

/**
 * The {@code accent} option shared by the block config pages (issue #43): {@code "status"} (the block's own colour,
 * the default, never stored) or a token of the UI theme, the same ones as {@link MonitorOptions#ACCENTS}. A missing or
 * unknown value is the default. Pure apart from the theme lookup.
 */
public final class BlockAccent {
	public static final String KEY = MonitorOptions.ACCENT;
	/** The block's own colour (a monitor's status colour, a merge station's PR state colour, the archive's amber). */
	public static final String OWN = MonitorOptions.ACCENT_STATUS;
	/** The choices of a page's select: {@link #OWN} first, then the theme tokens. */
	public static final List<String> CHOICES = MonitorOptions.ACCENTS;

	private BlockAccent() {
	}

	/** The accent id stored in {@code options}, or {@link #OWN}. */
	public static String of(Map<String, String> options) {
		String a = options == null ? null : options.get(KEY);
		return a != null && CHOICES.contains(a) ? a : OWN;
	}

	/** Label of {@code id} in a select; {@code ownLabel} names {@link #OWN} for the block ("Status colour", "Amber"). */
	public static String label(String id, String ownLabel) {
		return OWN.equals(id) ? ownLabel : MonitorOptions.accentLabel(id);
	}

	/** The accent as opaque ARGB, or 0 for {@link #OWN} (the caller keeps the block's own colour). */
	public static int argb(String id, ChatTheme theme) {
		int c = MonitorOptions.accentColor(id, theme);
		return c == 0 ? 0 : 0xFF000000 | c;
	}
}
