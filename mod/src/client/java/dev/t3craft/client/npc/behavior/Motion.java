// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
//
// Changes for T3Craft: Minecraft-free (Pos instead of Vec3, own angle maths), no seat delays, and
// player avoidance: the walker holds when the player stands right ahead and reports how long it
// has been blocked so the brain can re-plan around them; every sub-move goes through a collision
// resolver (the body never ends up inside a block) and the walker reports when it stops making
// progress (stuck against something) so the brain can re-plan or get unstuck.
package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic walking for one client-side NPC: follows a path (from {@link GridPathfinder}) at
 * a calm pace, easing in and out and turning smoothly, then turns to the target's yaw. No server:
 * each tick's move is resolved by a collision function ({@code mover}, see {@link Collider#move});
 * without obstacles the position is a pure function of the path and time.
 */
public final class Motion {
	/** Walking speed in blocks per tick (≈2.9 blocks/s, an unhurried walk; players sprint 5.6). */
	public static final double SPEED = 0.145;
	/** Max body turn per tick, degrees. */
	public static final float TURN = 24f;
	private static final int ACCEL_TICKS = 7;
	private static final double ARRIVE = 1.1;
	private static final double ARRIVE_MIN = 0.32;
	/** The walker holds when the avoided point is closer than this ahead of it. */
	static final double PERSONAL_SPACE = 1.3;
	/** Less than this closer to the next waypoint in a tick counts as no progress. */
	static final double PROGRESS = 0.01;
	/** A clipped walker this close (horizontally) to its waypoint takes it as reached. */
	static final double REACHED = 0.08;
	/** Rises above this (the auto step) are hopped: straight up first, then on. */
	static final double HOP = 0.6;

	private final List<Pos> path = new ArrayList<>();
	private int next;
	private boolean walking;
	private float yaw;
	private float targetYaw;
	private double pace = 1.0;
	/** feat/npc-idle-wander: extra speed factor (running back to the board); 1 = normal walk. */
	private double rush = 1.0;
	private double speed;
	private int blockedTicks;
	private int stuckTicks;
	private double bestRemaining = Double.MAX_VALUE;
	private @Nullable Float facing;

	public boolean walking() {
		return walking;
	}

	public float yaw() {
		return yaw;
	}

	public double speed() {
		return speed;
	}

	/** Consecutive ticks the walker has been held up by the avoided point. */
	public int blockedTicks() {
		return blockedTicks;
	}

	/** Consecutive walking ticks without getting closer to the next waypoint (pushing against a block). */
	public int stuckTicks() {
		return stuckTicks;
	}

	/** The waypoint being walked to (null when standing). */
	public @Nullable Pos heading() {
		return walking && next < path.size() ? path.get(next) : null;
	}

	/** Last point of the current path (null when standing). */
	public @Nullable Pos destination() {
		return walking && !path.isEmpty() ? path.getLast() : null;
	}

	public void setPace(double pace) {
		this.pace = Math.max(0.7, Math.min(1.3, pace));
	}

	/** feat/npc-idle-wander: run ({@code > 1}) or walk ({@code 1}); clamped to 1..2.2. */
	public void setRush(double rush) {
		this.rush = Math.max(1.0, Math.min(2.2, rush));
	}

	/** While standing, turn towards {@code yaw} instead of the target yaw (null = the target's). */
	public void faceTowards(@Nullable Float yaw) {
		facing = yaw;
	}

	/** Stand still right away, facing {@code yaw}. */
	public void stand(float yaw) {
		path.clear();
		next = 0;
		walking = false;
		speed = 0;
		this.targetYaw = yaw;
		blockedTicks = 0;
		resetProgress();
	}

	private void resetProgress() {
		stuckTicks = 0;
		bestRemaining = Double.MAX_VALUE;
	}

	/** Snap the body yaw (first placement). */
	public void snapYaw(float yaw) {
		this.yaw = yaw;
		this.targetYaw = yaw;
	}

	/** Walk along {@code route} (feet positions; first = current position), then face {@code endYaw}. */
	public void walk(List<Pos> route, float endYaw) {
		boolean was = walking;
		path.clear();
		path.addAll(route);
		next = path.size() > 1 ? 1 : path.size();
		walking = next < path.size();
		targetYaw = endYaw;
		blockedTicks = 0;
		resetProgress();
		if (!was) {
			speed = 0;
		}
	}

	/** {@link #step(Pos, Pos, BinaryOperator)} without obstacles. */
	public Pos step(Pos pos, @Nullable Pos avoid) {
		return step(pos, avoid, (from, to) -> to);
	}

	/**
	 * Advance one tick from {@code pos}, holding back if {@code avoid} is right ahead; each move is
	 * passed through {@code mover} (from, wanted → reached). Returns the new position.
	 */
	public Pos step(Pos pos, @Nullable Pos avoid, BinaryOperator<Pos> mover) {
		if (!walking) {
			speed = 0;
			Float f = facing;
			yaw = approachAngle(yaw, f != null ? f : targetYaw, f != null ? TURN * 0.4f : TURN * 0.6f);
			return pos;
		}
		if (avoid != null && ahead(pos, avoid)) {
			blockedTicks++;
			speed = 0;
			yaw = approachAngle(yaw, pos.yawTo(avoid), TURN * 0.3f);
			return pos;
		}
		blockedTicks = 0;
		double stride = SPEED * pace * Math.max(1.0, rush);
		double remaining = remaining(pos);
		double want = stride;
		if (remaining < ARRIVE) {
			double t = remaining / ARRIVE;
			want = stride * (ARRIVE_MIN + (1 - ARRIVE_MIN) * t * t * (3 - 2 * t));
		}
		speed = Math.min(want, speed + stride / ACCEL_TICKS);
		speed = Math.max(speed, stride * 0.18);
		double budget = speed;
		Pos p = pos;
		while (budget > 1e-6 && next < path.size()) {
			Pos goal = path.get(next);
			if (goal.y() - p.y() > HOP + 1e-6 && goal.y() - p.y() <= 1.0 + 1e-6) {
				// A whole block up with nothing to step on (the route only has these between neighbouring
				// cells): hop up on the spot first, collision-checked, then walk on at the new height.
				Pos up = new Pos(p.x(), goal.y(), p.z());
				Pos got = mover.apply(p, up);
				if (got.y() < goal.y() - 1e-6) {
					p = got; // a ceiling: no hop, the walker reports being stuck
					break;
				}
				p = got;
			}
			double dx = goal.x() - p.x();
			double dy = goal.y() - p.y();
			double dz = goal.z() - p.z();
			double horiz = Math.sqrt(dx * dx + dz * dz);
			if (horiz > 1e-4) {
				float want2 = p.yawTo(goal);
				float diff = Math.abs(wrapDegrees(want2 - yaw));
				boolean lastStep = next == path.size() - 1 && horiz < 1.25;
				if (!(lastStep && diff > 60)) {
					yaw = approachAngle(yaw, want2, TURN);
				} else {
					yaw = approachAngle(yaw, targetYaw, TURN * 0.6f);
				}
				if (diff > 100 && !lastStep) {
					speed = 0; // turn on the spot first instead of walking backwards
					break;
				}
			}
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			boolean reach = dist <= budget;
			Pos wanted = reach ? goal : p.add(dx * budget / dist, dy * budget / dist, dz * budget / dist);
			Pos got = mover.apply(p, wanted);
			if (got.distSqr(wanted) > 1e-10) {
				// Cut short by a block: stop here this tick.
				p = got;
				if (got.horizDist(goal) < REACHED && Math.abs(got.y() - goal.y()) < 0.6) {
					next++;
					resetProgress();
				}
				break;
			}
			p = wanted;
			if (reach) {
				budget -= dist;
				next++;
				resetProgress();
			} else {
				budget = 0;
			}
		}
		if (next >= path.size()) {
			walking = false;
			resetProgress();
		} else {
			double rem = Math.sqrt(p.distSqr(path.get(next)));
			if (rem < bestRemaining - PROGRESS) {
				bestRemaining = rem;
				stuckTicks = 0;
			} else {
				stuckTicks++;
			}
		}
		return p;
	}

	/** Is {@code avoid} within personal space and roughly in the walking direction? */
	private boolean ahead(Pos pos, Pos avoid) {
		if (next >= path.size()) {
			return false;
		}
		double d = pos.horizDist(avoid);
		if (d > PERSONAL_SPACE || Math.abs(avoid.y() - pos.y()) > 2) {
			return false;
		}
		Pos goal = path.get(next);
		if (goal.horizDist(pos) < 1e-4) {
			return false;
		}
		float diff = Math.abs(wrapDegrees(pos.yawTo(avoid) - pos.yawTo(goal)));
		return diff < 70;
	}

	private double remaining(Pos pos) {
		double d = 0;
		Pos p = pos;
		for (int i = next; i < path.size(); i++) {
			Pos q = path.get(i);
			d += p.horizDist(q);
			p = q;
			if (d > ARRIVE) {
				break;
			}
		}
		return d;
	}

	public static float wrapDegrees(float deg) {
		float d = deg % 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	static float approachAngle(float from, float to, float max) {
		float diff = wrapDegrees(to - from);
		if (Math.abs(diff) <= max) {
			return wrapDegrees(to);
		}
		return wrapDegrees(from + Math.signum(diff) * max);
	}
}
