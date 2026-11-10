package dev.t3craft.client.archive;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Small text helpers of the archive (pure): the close date of a row and the environment's HQ colour, used by
 * the archive terminal block's screen ({@link TerminalDisplay}). The per-slot labels in the world (four lines
 * with their own colours) are gone: the archive lives only in its screen.
 */
public final class ArchiveLabel {
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private ArchiveLabel() {
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
