package dev.t3craft.client.npc.helpers;

import dev.t3craft.client.npc.behavior.Motion;

/**
 * Pure geometry: where helpers stand around their parent and how they follow it. Uses Minecraft's
 * yaw convention (degrees; facing vector {@code (-sin yaw, cos yaw)} on x/z).
 *
 * <p>Helpers fan out on an arc BEHIND the parent (so they never block its desk or its face), up to
 * {@link #MAX_ARC_DEG} wide, spaced {@link #STEP_DEG} apart. When the parent sits at its desk the
 * arc therefore wraps around the chair; when it walks, the helpers trail after it.
 */
public final class HelperFormation {
	public static final double RADIUS = 1.4;
	static final double STEP_DEG = 50;
	static final double MAX_ARC_DEG = 240;
	/** Blocks per tick a helper may cover (a bit faster than the parent's walk so it catches up). */
	public static final double MAX_STEP = 0.22;
	/** Closer than this to its spot a helper stands still (no jitter while the parent fidgets). */
	public static final double DEADZONE = 0.2;
	/** Farther than this (parent teleported, new desk) the helper snaps to its spot. */
	public static final double SNAP_DISTANCE = 8.0;

	public record Spot(double x, double z) {
		double dist(Spot o) {
			return Math.hypot(o.x - x, o.z - z);
		}
	}

	private HelperFormation() {
	}

	/** Angle in degrees off "straight behind" for slot {@code index} of {@code count} (0 = behind). */
	public static double angle(int index, int count) {
		if (count <= 1) {
			return 0;
		}
		double arc = Math.min(MAX_ARC_DEG, STEP_DEG * (count - 1));
		return -arc / 2 + arc * index / (count - 1);
	}

	/** World spot for slot {@code index} of {@code count} around a parent at (px, pz) facing {@code yawDeg}. */
	public static Spot spot(double px, double pz, float yawDeg, int index, int count) {
		double yaw = Math.toRadians(yawDeg);
		// Behind = minus the facing vector.
		double bx = Math.sin(yaw);
		double bz = -Math.cos(yaw);
		double a = Math.toRadians(angle(index, count));
		double rx = bx * Math.cos(a) - bz * Math.sin(a);
		double rz = bx * Math.sin(a) + bz * Math.cos(a);
		return new Spot(px + rx * RADIUS, pz + rz * RADIUS);
	}

	/** One tick of following: stay inside the deadzone, snap when far, else move at most {@link #MAX_STEP}. */
	public static Spot step(Spot current, Spot target) {
		double d = current.dist(target);
		if (d <= DEADZONE) {
			return current;
		}
		if (d >= SNAP_DISTANCE) {
			return target;
		}
		double f = Math.min(1.0, MAX_STEP / d);
		return new Spot(current.x + (target.x - current.x) * f, current.z + (target.z - current.z) * f);
	}

	/** Yaw (degrees) to look from {@code from} towards {@code to}; {@code fallback} when they coincide. */
	public static float yawTowards(Spot from, Spot to, float fallback) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
			return fallback;
		}
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Moves {@code from} towards {@code to} by {@code factor} along the shortest way round (degrees). */
	public static float lerpYaw(float from, float to, float factor) {
		float diff = Motion.wrapDegrees(to - from);
		return Motion.wrapDegrees(from + diff * Math.max(0f, Math.min(1f, factor)));
	}
}
