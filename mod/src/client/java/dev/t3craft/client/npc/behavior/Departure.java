package dev.t3craft.client.npc.behavior;

import org.jspecify.annotations.Nullable;

/**
 * Item 22: a settled NPC "goes home". It waves, walks towards the exit (HQ entrance, else its
 * neighbourhood centre) for at most {@link #WALK_TICKS}, then fades out in a puff of particles and
 * stays hidden until T3 reflects the settle (the HQ then frees its desk) or {@link #MAX_TICKS} pass.
 * No teleport: it only walks with {@link Motion} and vanishes in place. Pure logic, no Minecraft;
 * the {@link BehaviorDirector} drives it instead of the {@link NpcBrain} while it lasts.
 */
public final class Departure {
	public enum Stage {
		/** Waves goodbye on the spot. */
		WAVE,
		/** Walks to the exit. */
		WALK,
		/** Puff of particles while fading out. */
		FADE,
		/** Hidden, waiting for T3 to reflect the settle. */
		GONE
	}

	static final int WAVE_TICKS = 20;
	static final int WALK_TICKS = 100;
	static final int FADE_TICKS = 12;
	/** Give up waiting (settle never reflected, e.g. rejected later): come back. */
	static final int MAX_TICKS = 20 * 30;

	private final Motion motion = new Motion();
	private final Pos exit;
	private final float exitYaw;
	private Pos pos;
	private Stage stage = Stage.WAVE;
	private int stageTicks;
	private int ticks;
	private boolean routed;

	public Departure(Pos from, float yaw, Anchor exit) {
		this.pos = from;
		this.exit = exit.pos();
		this.exitYaw = exit.yaw();
		motion.snapYaw(yaw);
		motion.stand(yaw);
	}

	public Stage stage() {
		return stage;
	}

	public Pos pos() {
		return pos;
	}

	public float yaw() {
		return motion.yaw();
	}

	/** Should the avatar (and its panel) be drawn. */
	public boolean visible() {
		return stage != Stage.GONE;
	}

	/** Ticks spent in the current stage. */
	public int stageTicks() {
		return stageTicks;
	}

	/** Waited too long for T3: the director drops it and the NPC comes back. */
	public boolean expired() {
		return ticks >= MAX_TICKS;
	}

	/**
	 * One tick. {@code nav} finds the route once, when it starts walking (A* over the real blocks;
	 * moves are collision-resolved). No route, or stuck against something: it fades out in place.
	 */
	public void tick(NpcBrain.Navigator nav, @Nullable Pos player) {
		ticks++;
		stageTicks++;
		switch (stage) {
			case WAVE -> {
				if (stageTicks >= WAVE_TICKS) {
					next(Stage.WALK);
				}
			}
			case WALK -> {
				if (!routed) {
					Route r = nav.route(pos, exit, player);
					if (r.status() == Route.Status.PENDING && stageTicks < WALK_TICKS) {
						break; // still searching (node budget): ask again next tick
					}
					routed = true;
					if (r.status() == Route.Status.FOUND) {
						motion.walk(r.points(), exitYaw);
					}
					// No route: never a straight line through walls; it fades out where it stands.
				}
				pos = motion.step(pos, player, nav::move);
				if (!motion.walking() || stageTicks >= WALK_TICKS || motion.stuckTicks() >= NpcBrain.STUCK_TICKS) {
					motion.stand(motion.yaw());
					next(Stage.FADE);
				}
			}
			case FADE -> {
				if (stageTicks >= FADE_TICKS) {
					next(Stage.GONE);
				}
			}
			case GONE -> {
			}
		}
	}

	public boolean walking() {
		return stage == Stage.WALK && motion.walking();
	}

	private void next(Stage s) {
		stage = s;
		stageTicks = 0;
	}
}
