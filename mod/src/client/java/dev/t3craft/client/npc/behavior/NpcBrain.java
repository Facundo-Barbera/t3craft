package dev.t3craft.client.npc.behavior;

import java.util.List;
import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

/**
 * Per-NPC state machine. Each client tick the {@link BehaviorDirector} feeds it the thread's
 * {@link Mode}, where its spots are ({@link Targets}) and where the player is; the brain moves
 * (via {@link Motion}) and exposes a pose snapshot (position, yaws, hop, crouch, arm swing, emote,
 * bubble/icon wishes) that the director applies to the entities. No Minecraft classes, deterministic
 * per NPC id, so it is unit-tested directly.
 *
 * <ul>
 *   <li>{@link Mode#WORKING}: walk to the desk, then type (alternating arm swings, head down).
 *   <li>{@link Mode#ATTENTION}: walk to a board slot, wait facing the board, turn to and wave at
 *       the player when they come close; floating icon and bubble stay on.
 *   <li>{@link Mode#IDLE}: wander to random reachable points around home, pause and look around.
 *   <li>{@link Mode#COMPLETED}: hop and cheer for a few seconds, then walk to the rest spot and
 *       rest (crouched).
 * </ul>
 *
 * Player avoidance: paths avoid the player's cells; a walker holds when the player is right ahead
 * and re-plans around them after a moment; a standing NPC the player walks into steps aside.
 */
public final class NpcBrain {
	/** What the director resolved for this NPC this tick. {@code boardSlot} is null when not needed. */
	public record Targets(Anchor desk, @Nullable Anchor boardSlot, Anchor home, double wanderRadius, Anchor rest) {
	}

	/** Finds a walking route; returns null when there is none (the brain then walks straight). */
	@FunctionalInterface
	public interface Navigator {
		@Nullable List<Pos> path(Pos from, Pos to, @Nullable Pos avoid);

		/** Straight lines only (no level). */
		Navigator STRAIGHT = (from, to, avoid) -> List.of(from, to);
	}

	public enum Phase {
		WALKING, TYPING, WAITING, WANDER_PAUSE, CELEBRATING, RESTING
	}

	static final int CELEBRATE_TICKS = 70;
	static final int FLASH_TICKS = 120;
	static final int REPLAN_BLOCKED_TICKS = 20;
	static final double SNAP_DISTANCE = 48;
	static final double PLAYER_NEAR = 6;
	static final double BUBBLE_NEAR = 4.5;
	static final double STEP_ASIDE = 0.8;

	private final String id;
	private final SplittableRandom rnd;
	private final Motion motion = new Motion();
	private @Nullable Mode mode;
	private boolean error;
	private Phase phase = Phase.WALKING;
	private Pos pos;
	private @Nullable Pos goal;
	private float goalYaw;
	private int phaseTicks;
	private int tick;
	private int flash;
	private int nextSwing;
	private boolean offHand;
	private int stepAsideCooldown;
	private int wanderFailures;
	private boolean placed;
	/** Spot next to the player while coming to them in ATTENTION ({@link ApproachPlanner}); null = not approaching. */
	private @Nullable Anchor approach;
	/** No route to {@link #approach}: stay put and gesture from here instead. */
	private boolean approachStuck;

	// Pose snapshot, valid after tick().
	private float headYaw;
	private float pitch;
	private double hop;
	private boolean crouch;
	private Hand swing = Hand.NONE;
	private Emote emote = Emote.NONE;
	private boolean teleported;

	public NpcBrain(String id, Pos spawn) {
		this.id = id;
		this.rnd = new SplittableRandom(id.hashCode() * 0x9E3779B97F4A7C15L);
		this.pos = spawn;
		motion.setPace(0.9 + rnd.nextDouble() * 0.17);
		this.nextSwing = 3 + rnd.nextInt(5);
	}

	public String id() {
		return id;
	}

	public @Nullable Mode mode() {
		return mode;
	}

	public Phase phase() {
		return phase;
	}

	public Pos pos() {
		return pos;
	}

	public float bodyYaw() {
		return motion.yaw();
	}

	public float headYaw() {
		return headYaw;
	}

	public float pitch() {
		return pitch;
	}

	/** Vertical offset above the feet position (celebration hops). */
	public double hop() {
		return hop;
	}

	public boolean crouch() {
		return crouch;
	}

	public Hand swing() {
		return swing;
	}

	public Emote emote() {
		return emote;
	}

	public boolean walking() {
		return motion.walking();
	}

	/** True on the tick the NPC jumped to its position (first placement, far away): snap, don't lerp. */
	public boolean teleported() {
		return teleported;
	}

	/** Floating "!"/"✖" icon wanted. */
	public boolean iconWanted() {
		return mode == Mode.ATTENTION;
	}

	/** Should the speech bubble be shown, given the player's distance (NaN = no player)? */
	public boolean bubbleWanted(double playerDist) {
		return mode == Mode.ATTENTION || flash > 0 || playerDist < BUBBLE_NEAR;
	}

	/** The thread's summary changed: show the bubble for a while. */
	public void summaryChanged() {
		flash = FLASH_TICKS;
	}

	public boolean error() {
		return error;
	}

	/**
	 * Come to the player: in ATTENTION, walk to {@code spot} instead of the board slot (null = go back
	 * to the normal spot). Call before {@link #tick}. Walks only; if there is no route the NPC stays
	 * where it is and gestures at the player.
	 */
	public void approach(@Nullable Anchor spot) {
		if (spot == null || approach == null || spot.pos().distSqr(approach.pos()) > 0.25) {
			approachStuck = false;
		}
		approach = spot;
	}

	public boolean approaching() {
		return approach != null && mode == Mode.ATTENTION;
	}

	/** One tick. {@code player} is the local player's feet position or null. */
	public void tick(Mode newMode, boolean isError, Targets t, @Nullable Pos player, Navigator nav) {
		tick++;
		swing = Hand.NONE;
		teleported = false;
		hop = 0;
		if (flash > 0) {
			flash--;
		}
		if (stepAsideCooldown > 0) {
			stepAsideCooldown--;
		}
		boolean first = !placed;
		if (newMode != mode || isError != error) {
			Mode old = mode;
			mode = newMode;
			error = isError;
			enter(newMode, old, first, t, nav, player);
		}
		if (first) {
			placed = true;
		}
		phaseTicks++;

		// Re-target when the spot moved (layout change, base built or removed).
		Anchor want = currentAnchor(t);
		if (want != null && (goal == null || want.pos().distSqr(goal) > 0.25)) {
			setGoal(want, nav, player);
		}

		if (motion.walking()) {
			Pos before = pos;
			pos = motion.step(pos, player);
			if (motion.blockedTicks() == REPLAN_BLOCKED_TICKS && goal != null) {
				route(goal, goalYaw, nav, player);
			} else if (motion.blockedTicks() > REPLAN_BLOCKED_TICKS * 4 && goal != null) {
				// Still stuck (player parked on the route): walk straight past them.
				motion.walk(List.of(pos, goal), goalYaw);
			}
			if (!motion.walking()) {
				arrived();
			}
			if (before.distSqr(pos) > 1e-8 || motion.walking()) {
				lookWhileWalking(player);
				crouch = false;
				emote = Emote.NONE;
				return;
			}
		} else if (player != null && stepAsideCooldown == 0 && pos.horizDist(player) < STEP_ASIDE && Math.abs(player.y() - pos.y()) < 2) {
			stepAside(player, nav);
			return;
		} else if (goal != null && phase != Phase.WANDER_PAUSE && phase != Phase.CELEBRATING && pos.horizDist(goal) > 0.6
				&& (player == null || goal.horizDist(player) > 1.0)) {
			// Pushed off the spot (stepped aside): go back once the spot is free.
			route(goal, goalYaw, nav, player);
		}
		motion.step(pos, null);
		act(t, player, nav);
	}

	private void enter(Mode m, @Nullable Mode old, boolean first, Targets t, Navigator nav, @Nullable Pos player) {
		phaseTicks = 0;
		crouch = false;
		wanderFailures = 0;
		goal = null;
		if (!first) {
			flash = FLASH_TICKS;
		}
		if (m == Mode.COMPLETED && !first && old != Mode.COMPLETED) {
			phase = Phase.CELEBRATING;
			motion.stand(motion.yaw());
			return;
		}
		Anchor a = currentAnchor(t);
		if (a == null) {
			return;
		}
		if (first || pos.distSqr(a.pos()) > SNAP_DISTANCE * SNAP_DISTANCE) {
			// Joining a world (or far away): be there already, no parade of walkers.
			pos = a.pos();
			goal = a.pos();
			goalYaw = a.yaw();
			motion.stand(a.yaw());
			motion.snapYaw(a.yaw());
			teleported = true;
			phase = restingPhase();
			return;
		}
		setGoal(a, nav, player);
	}

	/** Where the current mode wants the NPC (null while celebrating or when wandering picks itself). */
	private @Nullable Anchor currentAnchor(Targets t) {
		if (mode == null) {
			return null;
		}
		return switch (mode) {
			case WORKING -> t.desk();
			case ATTENTION -> approach != null ? (approachStuck ? null : approach) : t.boardSlot() != null ? t.boardSlot() : t.desk();
			case COMPLETED -> phase == Phase.CELEBRATING ? null : t.rest();
			case IDLE -> goal == null ? wanderPoint(t) : null;
		};
	}

	private Anchor wanderPoint(Targets t) {
		double r = Math.max(1.0, t.wanderRadius()) * Math.sqrt(rnd.nextDouble());
		double a = rnd.nextDouble() * Math.PI * 2;
		Pos p = t.home().pos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
		return new Anchor(p, (float) (rnd.nextDouble() * 360 - 180));
	}

	private void setGoal(Anchor a, Navigator nav, @Nullable Pos player) {
		goal = a.pos();
		goalYaw = a.yaw();
		if (pos.horizDist(goal) < 0.05 && Math.abs(pos.y() - goal.y()) < 0.5) {
			motion.stand(goalYaw);
			phase = restingPhase();
			phaseTicks = 0;
			return;
		}
		route(goal, goalYaw, nav, player);
	}

	private void route(Pos to, float yaw, Navigator nav, @Nullable Pos player) {
		List<Pos> p = nav.path(pos, to, player);
		if (p == null && approaching() && to.equals(approach.pos())) {
			// No way to the player: no teleport, no walking through walls; wave from here.
			approachStuck = true;
			goal = pos;
			motion.stand(motion.yaw());
			phase = Phase.WAITING;
			phaseTicks = 0;
			return;
		}
		if (p == null && mode == Mode.IDLE) {
			// Unreachable wander point: try another one shortly.
			wanderFailures++;
			goal = pos;
			phase = Phase.WANDER_PAUSE;
			phaseTicks = 0;
			return;
		}
		motion.walk(p != null ? p : List.of(pos, to), yaw);
		phase = Phase.WALKING;
		phaseTicks = 0;
	}

	private void arrived() {
		phase = restingPhase();
		phaseTicks = 0;
	}

	/** The phase an NPC is in once standing on its spot in the current mode. */
	private Phase restingPhase() {
		if (mode == null) {
			return Phase.WAITING;
		}
		return switch (mode) {
			case WORKING -> Phase.TYPING;
			case ATTENTION -> Phase.WAITING;
			case IDLE -> Phase.WANDER_PAUSE;
			case COMPLETED -> Phase.RESTING;
		};
	}

	private void stepAside(Pos player, Navigator nav) {
		stepAsideCooldown = 40;
		double dx = pos.x() - player.x();
		double dz = pos.z() - player.z();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-3) {
			double a = rnd.nextDouble() * Math.PI * 2;
			dx = Math.cos(a);
			dz = Math.sin(a);
			len = 1;
		}
		Pos to = pos.add(dx / len * 1.6, 0, dz / len * 1.6);
		List<Pos> p = nav.path(pos, to, null);
		motion.walk(p != null ? p : List.of(pos, to), pos.yawTo(player));
		phase = Phase.WALKING;
		crouch = false;
	}

	private void lookWhileWalking(@Nullable Pos player) {
		pitch = 0;
		headYaw = motion.yaw();
		if (player != null && pos.horizDist(player) < 3) {
			headYaw = clampHead(pos.yawTo(player), motion.yaw());
		}
	}

	private void act(Targets t, @Nullable Pos player, Navigator nav) {
		double pd = player == null ? Double.NaN : pos.horizDist(player);
		boolean near = player != null && pd < PLAYER_NEAR;
		crouch = false;
		pitch = 0;
		headYaw = motion.yaw();
		motion.faceTowards(null);
		switch (phase) {
			case TYPING -> {
				emote = Emote.TYPING;
				pitch = 22;
				headYaw = motion.yaw() + (float) Math.sin(tick * 0.05 + id.length()) * 8;
				if (--nextSwing <= 0) {
					swing = offHand ? Hand.OFF : Hand.MAIN;
					offHand = !offHand;
					nextSwing = 3 + rnd.nextInt(6);
					if (rnd.nextInt(10) == 0) {
						nextSwing += 25; // a short think
					}
				}
				if (near) {
					headYaw = clampHead(pos.yawTo(player), motion.yaw());
					pitch = 5;
				}
			}
			case WAITING -> {
				emote = error ? Emote.ERROR : Emote.EXCLAIM;
				if (near || (approaching() && player != null)) {
					motion.faceTowards(pos.yawTo(player));
					headYaw = pos.yawTo(player);
					pitch = (float) -Math.toDegrees(Math.atan2(player.y() + 1.6 - (pos.y() + 1.6), Math.max(0.1, pd)));
					if (phaseTicks % 50 == 0) {
						swing = Hand.MAIN;
						emote = Emote.WAVE;
					}
				} else {
					// fidget: look around the board now and then
					headYaw = motion.yaw() + (float) Math.sin(tick * 0.04 + id.length()) * 20;
				}
			}
			case WANDER_PAUSE -> {
				emote = (phaseTicks / 80) % 3 == 1 ? Emote.MUSIC : Emote.LOOK_AROUND;
				headYaw = motion.yaw() + (float) Math.sin(tick * 0.06 + id.length()) * 50;
				if (near && pd < 4) {
					headYaw = clampHead(pos.yawTo(player), motion.yaw());
					emote = Emote.WAVE;
				}
				int pause = 70 + (int) (Math.abs(id.hashCode() % 90)) + Math.min(wanderFailures, 5) * 10;
				if (phaseTicks > pause && mode == Mode.IDLE) {
					goal = null; // currentAnchor() picks a new wander point next tick
				}
			}
			case CELEBRATING -> {
				emote = Emote.CELEBRATE;
				int k = phaseTicks % 14;
				hop = k < 10 ? Math.sin(Math.PI * k / 10.0) * 0.55 : 0;
				if (phaseTicks % 7 == 0) {
					swing = (phaseTicks / 7) % 2 == 0 ? Hand.MAIN : Hand.OFF;
				}
				motion.faceTowards(motion.yaw() + 18f);
				pitch = -15;
				if (phaseTicks >= CELEBRATE_TICKS) {
					phase = Phase.WALKING; // currentAnchor() now returns the rest spot
					phaseTicks = 0;
					goal = null;
				}
			}
			case RESTING -> {
				emote = Emote.SLEEPY;
				crouch = true;
				pitch = 18;
				if (near && pd < 3.5) {
					headYaw = clampHead(pos.yawTo(player), motion.yaw());
					pitch = 0;
				}
			}
			case WALKING -> {
				// Standing with nothing to do (e.g. route found nothing): act as on the spot.
				phase = restingPhase();
			}
		}
	}

	/** Heads turn at most 70° from the body. */
	static float clampHead(float want, float body) {
		float d = Motion.wrapDegrees(want - body);
		return body + Math.max(-70f, Math.min(70f, d));
	}
}
