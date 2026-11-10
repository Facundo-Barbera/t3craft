package dev.t3craft.client.demo;

import com.google.gson.JsonElement;
import dev.t3craft.T3Craft;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-spawn-start: the HQ build waits for the player when they enter the HQ world. Armed once the
 * world has loaded (settle ticks, roster ready) and a build is due; it fires on the first player
 * signal: the chat button / key / {@code /t3craft build start} ({@link #confirm}), walking more than
 * {@link #WALK_DISTANCE} blocks from where it was armed, or holding a movement key for
 * {@link #SUSTAINED_TICKS} ticks. Pure state, client thread.
 *
 * <p>Config, additive ({@code config/t3craft.json}, section {@code hq}):
 * {@code "startBuild": "on_walk_or_confirm"} (default) or {@code "immediate"} (the old behaviour).
 */
final class HqStartGate {
	static final String KEY = "startBuild";
	static final String ON_WALK_OR_CONFIRM = "on_walk_or_confirm";
	static final String IMMEDIATE = "immediate";
	static final String DEFAULT = ON_WALK_OR_CONFIRM;
	/** Horizontal blocks from the armed spot that count as "walked". */
	static final double WALK_DISTANCE = 3.0;
	/** Consecutive ticks of movement input that count as "walked" (half a second). */
	static final int SUSTAINED_TICKS = 10;

	enum Signal { NONE, CONFIRMED, WALKED, MOVED }

	private static volatile @Nullable String warnedValue;

	private boolean armed;
	private boolean confirmed;
	private double x0;
	private double z0;
	private int moving;

	boolean armed() {
		return armed;
	}

	/** Starts waiting, with the player at ({@code x}, {@code z}). */
	void arm(double x, double z) {
		armed = true;
		confirmed = false;
		x0 = x;
		z0 = z;
		moving = 0;
	}

	void disarm() {
		armed = false;
		confirmed = false;
		moving = 0;
	}

	/** The player asked to start (button, key, command). Ignored when not waiting. */
	boolean confirm() {
		if (armed) {
			confirmed = true;
		}
		return armed;
	}

	/** One client tick with the player at ({@code x}, {@code z}); {@code moveInput}: a movement key is held (no screen open). */
	Signal tick(double x, double z, boolean moveInput) {
		if (!armed) {
			return Signal.NONE;
		}
		if (confirmed) {
			return Signal.CONFIRMED;
		}
		double dx = x - x0;
		double dz = z - z0;
		if (dx * dx + dz * dz > WALK_DISTANCE * WALK_DISTANCE) {
			return Signal.WALKED;
		}
		moving = moveInput ? moving + 1 : 0;
		return moving >= SUSTAINED_TICKS ? Signal.MOVED : Signal.NONE;
	}

	/** Whether builds on entering the HQ wait for the player under {@code mode}. */
	static boolean waits(String mode) {
		return !IMMEDIATE.equals(mode);
	}

	/** {@code hq.startBuild} of the current config (never throws; invalid values are logged once and mean the default). */
	static String mode() {
		try {
			return modeOf(dev.t3craft.client.config.ConfigManager.get().extra(dev.t3craft.hq.style.HqStyles.SECTION));
		} catch (RuntimeException | LinkageError e) {
			return DEFAULT;
		}
	}

	/** {@code startBuild} of an {@code hq} section (null or not an object: the default). */
	static String modeOf(@Nullable JsonElement hq) {
		if (hq == null || !hq.isJsonObject()) {
			return DEFAULT;
		}
		JsonElement v = hq.getAsJsonObject().get(KEY);
		if (v == null || v.isJsonNull()) {
			return DEFAULT;
		}
		if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
			String s = v.getAsString().trim();
			if (IMMEDIATE.equals(s) || ON_WALK_OR_CONFIRM.equals(s)) {
				return s;
			}
		}
		String shown = v.toString();
		if (!shown.equals(warnedValue)) {
			warnedValue = shown;
			T3Craft.LOGGER.warn("[demo] invalid config key hq.{} = {}, using \"{}\"", KEY, shown, DEFAULT);
		}
		return DEFAULT;
	}
}
