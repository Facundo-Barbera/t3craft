package dev.t3craft.client.workstation;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What a workstation screen says about its thread, and in which colour. Colours and glyphs match
 * {@code dev.t3craft.t3.ThreadState} (and the bridge's presentation), so the screen, the NPC name tag
 * and the HUD agree. {@link #ARCHIVED} is workstation-only: a settled thread whose desk is being
 * vacated (hq-design / builders move it to the archive; this package only exposes the state).
 */
public enum StationStatus {
	ATTENTION(0xFFAA00, "!", "NEEDS YOU"),
	ERROR(0xFF5555, "✖", "ERROR"),
	WORKING(0x55FFFF, "⚙", "WORKING"),
	DONE(0x55FF55, "✔", "DONE"),
	IDLE(0xAAAAAA, "·", "IDLE"),
	ARCHIVED(0x777777, "▣", "ARCHIVED");

	/** 0xRRGGBB text colour of the status line. */
	public final int color;
	public final String glyph;
	public final String word;

	StationStatus(int color, String glyph, String word) {
		this.color = color;
		this.glyph = glyph;
		this.word = word;
	}

	/**
	 * Maps a thread's wire state ({@code working | needs_attention | error | done | idle}, as in
	 * {@code NpcInfo.state()}) to a status. Settled wins over everything; unknown states read as idle.
	 */
	public static StationStatus of(@Nullable String wireState, boolean settled) {
		if (settled) {
			return ARCHIVED;
		}
		if (wireState == null) {
			return IDLE;
		}
		return switch (wireState.toLowerCase(Locale.ROOT)) {
			case "working" -> WORKING;
			case "needs_attention", "attention" -> ATTENTION;
			case "error" -> ERROR;
			case "done" -> DONE;
			default -> IDLE;
		};
	}

	/**
	 * ARGB background of the screen: the status colour darkened to about 1/5 and mostly opaque, so the
	 * monitor glows faintly in its state colour while the text stays readable.
	 */
	public int background() {
		int r = ((color >> 16) & 0xFF) / 5;
		int g = ((color >> 8) & 0xFF) / 5;
		int b = (color & 0xFF) / 5;
		return 0xD0000000 | (r << 16) | (g << 8) | b;
	}
}
