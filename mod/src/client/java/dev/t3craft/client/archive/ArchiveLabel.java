package dev.t3craft.client.archive;

import dev.t3craft.client.workstation.TextFit;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Text of a slot's label (four short lines, fits on one block face) and of a catalog row. Pure.
 *
 * <pre>
 *   Fix login redirect l…     title, white
 *   ● vps                     environment, in its HQ colour
 *   ✔ 2026-11-03              kind + close date, grey
 *   t3craft                   project, light grey
 * </pre>
 */
public final class ArchiveLabel {
	/** Characters per label line (≈ 0.9 block at {@code ArchiveRenderer}'s scale). */
	public static final int WIDTH = 22;
	public static final int TITLE = 0xFFFFFF;
	public static final int DATE = 0xAAAAAA;
	public static final int PROJECT = 0xCCCCCC;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public record Line(String text, int color) {
	}

	private ArchiveLabel() {
	}

	public static List<Line> lines(ArchiveEntry e, ZoneId zone) {
		return List.of(
				new Line(TextFit.clip(e.title(), WIDTH), TITLE),
				new Line(TextFit.clip("● " + e.envLabel(), WIDTH), envColor(e.env())),
				new Line(e.kind().glyph + " " + date(e.closedAtMs(), zone), DATE),
				new Line(TextFit.clip(e.projectLabel(), WIDTH), PROJECT));
	}

	/** {@code yyyy-MM-dd} in {@code zone}, or {@code ?} when unknown. */
	public static String date(@Nullable Long ms, ZoneId zone) {
		return ms == null ? "?" : DAY.format(Instant.ofEpochMilli(ms).atZone(zone));
	}

	/**
	 * The environment's HQ colour (same formula as the module runners and signs in hq-design:
	 * {@code DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28)}).
	 */
	public static DyeColor envDye(String envKey) {
		return DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28);
	}

	/** Text colour of {@link #envDye}, lifted towards white when too dark to read on a dark label. */
	public static int envColor(String envKey) {
		return readable(envDye(envKey).getTextColor() & 0xFFFFFF);
	}

	static int readable(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		int luma = (r * 299 + g * 587 + b * 114) / 1000;
		if (luma >= 80) {
			return rgb;
		}
		return (r + 0xFF) / 2 << 16 | (g + 0xFF) / 2 << 8 | (b + 0xFF) / 2;
	}
}
