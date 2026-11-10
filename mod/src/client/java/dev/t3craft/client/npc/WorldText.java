package dev.t3craft.client.npc;

import dev.t3craft.client.chat.ChatTheme;

/**
 * Shared look of the world text in hologram style (docs/features/world-text.md): NPC pills, event
 * bubbles, the decision podium bubble and the hub card. Colours come only from {@link ChatTheme}
 * tokens: the dark {@link ChatTheme#terminal()} palette (glass-like over the world), with the
 * alphas below for the backgrounds. Pure helpers plus the speech-bubble timing.
 */
public final class WorldText {
	/** Pill and card background alpha (over {@code panel}). */
	public static final int PLATE_ALPHA = 0xB8;
	/** Speech bubble background alpha (over {@code panel}). */
	public static final int BUBBLE_ALPHA = 0xD0;
	/** Shortest and longest life of an event bubble (ticks: 3.5 s and 11 s). */
	public static final int BUBBLE_MIN_TICKS = 70;
	public static final int BUBBLE_MAX_TICKS = 220;
	/** Reading time per character of the bubble text (ticks). */
	static final int BUBBLE_TICKS_PER_CHAR = 2;
	/** Fade-out at the end of a bubble's life (ticks), in {@link #FADE_STEPS} steps (one text push each). */
	public static final int FADE_TICKS = 20;
	public static final int FADE_STEPS = 5;

	private WorldText() {
	}

	/** The palette of the world text. */
	public static ChatTheme theme() {
		return ChatTheme.terminal();
	}

	/** 0xRRGGBB of an ARGB token (text components take RGB). */
	public static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}

	/** Life of an event bubble with {@code chars} characters of text: 3.5 s plus reading time, at most 11 s. */
	public static int bubbleTicks(int chars) {
		long t = BUBBLE_MIN_TICKS + (long) Math.max(0, chars) * BUBBLE_TICKS_PER_CHAR;
		return (int) Math.min(BUBBLE_MAX_TICKS, t);
	}

	/**
	 * Opacity step of a bubble with {@code left} ticks to live: {@link #FADE_STEPS} = fully opaque,
	 * 0 = gone. Quantised so the text is re-pushed only a few times while it fades.
	 */
	public static int fadeStep(int left) {
		if (left >= FADE_TICKS) {
			return FADE_STEPS;
		}
		if (left <= 0) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(left * (double) FADE_STEPS / FADE_TICKS));
	}

	/** {@code rgb} blended towards {@code toward} (both 0xRRGGBB) at {@code step / FADE_STEPS} opacity. */
	public static int fade(int rgb, int toward, int step) {
		if (step >= FADE_STEPS) {
			return rgb & 0xFFFFFF;
		}
		double t = Math.max(0, step) / (double) FADE_STEPS;
		int r = (int) Math.round(((toward >> 16) & 0xFF) + (((rgb >> 16) & 0xFF) - ((toward >> 16) & 0xFF)) * t);
		int g = (int) Math.round(((toward >> 8) & 0xFF) + (((rgb >> 8) & 0xFF) - ((toward >> 8) & 0xFF)) * t);
		int b = (int) Math.round((toward & 0xFF) + ((rgb & 0xFF) - (toward & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** Background alpha at a fade step. */
	public static int fadeAlpha(int alpha, int step) {
		return step >= FADE_STEPS ? alpha : alpha * Math.max(0, step) / FADE_STEPS;
	}
}
