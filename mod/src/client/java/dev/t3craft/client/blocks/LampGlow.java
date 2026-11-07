// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.LampStatus;

/**
 * How a lamp glows over time, and where motes rise. Pure (time in, numbers out) so the curves are
 * testable; {@link LampRenderer} and {@link HubBeacon} only draw what this says.
 *
 * <ul>
 *   <li>{@link #level}: glow intensity 0..1. "Needs you" breathes slowly and deeply (a 4 s breath,
 *       {@link #ATTENTION_HZ}); an error pulses faster and shallower; working shimmers gently; done
 *       and idle hold a steady glow; off is dark.</li>
 *   <li>{@link #motes}: dust motes in the lamp's colour, only on lamps that ask for the player: desk
 *       lamps and the beacon in ATTENTION, and the lit bulbs of the "needs attention" board.</li>
 *   <li>{@link #beaconMode}: who draws the hub beacon (the lamp's renderer, a client-only display, or
 *       nobody).</li>
 * </ul>
 *
 * <p>Adapted from AgentCraft's {@code StatusLampRenderer.breathe/beacon} (sine-breathing additive glow
 * over the lens) and {@code HqClientFeature.motes}; the per-state curves are t3craft's own.
 */
public final class LampGlow {
	/** "Needs you": one slow breath every 4 s. */
	public static final double ATTENTION_HZ = 0.25;
	public static final double ERROR_HZ = 1.0;
	public static final double WORKING_HZ = 0.6;

	/** Who draws the hub beacon. */
	public enum BeaconMode {
		/** No standing HQ (or its hub is not built yet). */
		NONE,
		/** The blocks module's status lamp bound {@code beacon} sits in the lantern cap: its BER. */
		BLOCK,
		/** Module off (or an HQ built without the lamp): a client-only glow over the lantern. */
		CLIENT
	}

	private LampGlow() {
	}

	/** Glow intensity of a lamp in state {@code s} at {@code seconds} (game time), 0..1. */
	public static float level(LampStatus s, double seconds) {
		return switch (s) {
			case OFF -> 0f;
			case IDLE -> 0.25f;
			case DONE -> 0.45f;
			case WORKING -> wave(seconds, WORKING_HZ, 0.45f, 0.7f);
			case ERROR -> wave(seconds, ERROR_HZ, 0.55f, 0.9f);
			case ATTENTION -> wave(seconds, ATTENTION_HZ, 0.15f, 1f);
		};
	}

	/** Whether the glow of {@code s} changes over time. */
	public static boolean breathes(LampStatus s) {
		return s == LampStatus.ATTENTION || s == LampStatus.ERROR || s == LampStatus.WORKING;
	}

	/** {@code lo} at t = 0, {@code hi} half a period later; smooth (raised cosine). */
	static float wave(double seconds, double hz, float lo, float hi) {
		double k = 0.5 - 0.5 * Math.cos(2 * Math.PI * hz * seconds);
		return (float) (lo + (hi - lo) * k);
	}

	/** {@code level} mapped to an alpha between {@code min} and {@code max} (0..255). */
	public static int alpha(float level, int min, int max) {
		float l = Math.max(0f, Math.min(1f, level));
		return Math.max(0, Math.min(255, Math.round(min + (max - min) * l)));
	}

	/** {@code 0xAARRGGBB} of {@code rgb} at {@code alpha}. */
	public static int argb(int rgb, int alpha) {
		return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
	}

	/** Whether a lamp of binding kind {@code kind} in state {@code s} gives off motes. */
	public static boolean motes(Binding.Kind kind, LampStatus s) {
		if (kind == Binding.Kind.BOARD) {
			// a lit board bulb is a thread waiting on the player (needs you or error)
			return s == LampStatus.ATTENTION || s == LampStatus.ERROR;
		}
		return s == LampStatus.ATTENTION;
	}

	/**
	 * @param hqStanding an HQ stands in this world and its hub is built
	 * @param lampAtCap  the blocks module is on and its beacon lamp is in the lantern cap
	 */
	public static BeaconMode beaconMode(boolean hqStanding, boolean lampAtCap) {
		if (!hqStanding) {
			return BeaconMode.NONE;
		}
		return lampAtCap ? BeaconMode.BLOCK : BeaconMode.CLIENT;
	}
}
