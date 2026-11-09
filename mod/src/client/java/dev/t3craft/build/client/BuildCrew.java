package dev.t3craft.build.client;

import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildPlanner;
import dev.t3craft.client.npc.behavior.Collision;
import dev.t3craft.client.npc.behavior.GridPathfinder;
import dev.t3craft.client.npc.behavior.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The animated crew of one build, on the client thread: builders appear a short walk away and walk to
 * the site while the job already runs (feat/build-near-player: it is submitted on the first tick, no
 * wait for the crew to arrive; a player near the site sees the crew appear next to them); every block the server places is
 * assigned to a builder, who moves to a spot just outside the footprint (raised as the structure
 * grows, as if on scaffolding), faces the block, holds it and swings. When the job ends the crew
 * walks off and vanishes in a puff.
 *
 * <p>Controls ({@link #pause()}, {@link #resume()}, {@link #cancel()}) are safe from any thread.
 */
final class BuildCrew implements BuildHandle {
	private static final double SPAWN_DISTANCE = 14.0;
	private static final double LEAVE_DISTANCE = 18.0;
	private static final double WALK_SPEED = 0.22;
	private static final double WORK_SPEED = 0.35;
	private static final double MAX_WORK_SPEED = 0.6;
	/** Pace a walk would need (blocks per tick) above which the builder leaps to its spot instead. */
	private static final double LEAP_SPEED = 2 * MAX_WORK_SPEED;
	private static final int LEAVE_TIMEOUT_TICKS = 20 * 6;
	/** feat/build-near-player: builders appear this far from a player standing near the site. */
	static final double NEAR_SPAWN_DISTANCE = 3.0;
	/** fix/crew-route-stutter: a goal this close (straight, blocks) with a clear line is walked straight, no A*. */
	static final double DIRECT_RANGE = 3.0;

	interface IdSource {
		int next();
	}

	private final String label;
	private final ClientLevel level;
	private final BoundingBox bounds;
	private final Supplier<BuildJob> submitter;
	private final List<BuilderAvatar> builders = new ArrayList<>();
	private final List<Vec3> outward = new ArrayList<>();
	private volatile Phase phase = Phase.ARRIVING;
	private volatile @Nullable BuildJob job;
	private volatile boolean pauseRequested;
	private volatile boolean cancelRequested;
	private int phaseTicks;
	/** fix/hq-build-stutter: builders still to spawn (one per tick while arriving), and what they need. */
	private final int crewSize;
	private final Vec3 spawnCenter;
	private final IdSource ids;
	/** fix/crew-route-stutter: shared route searches (snapshot + background A*, per-tick budget). */
	private final CrewRoutes routes;
	/** feat/build-near-player: the crew appears by the player (and stands at the player's height over the void). */
	private final boolean nearPlayer;
	private final Vec3 viewer;
	/** Feet height used where a column has no ground at all (void world, island not built yet). */
	private final double fallbackY;
	/** Builders that appear per client tick, and that vanish per tick when leaving. */
	static final int SPAWNS_PER_TICK = 1;
	private int despawned;

	BuildCrew(String label, ClientLevel level, BoundingBox bounds, int crewSize, Vec3 approachFrom, IdSource ids, CrewRoutes routes,
			Supplier<BuildJob> submitter) {
		this(label, level, bounds, crewSize, approachFrom, false, ids, routes, submitter);
	}

	/** @param nearPlayer {@code approachFrom} is a player close to the site ({@link #near}): the crew appears by them */
	BuildCrew(String label, ClientLevel level, BoundingBox bounds, int crewSize, Vec3 approachFrom, boolean nearPlayer, IdSource ids, CrewRoutes routes,
			Supplier<BuildJob> submitter) {
		this.nearPlayer = nearPlayer;
		this.viewer = approachFrom;
		this.fallbackY = nearPlayer ? Math.floor(approachFrom.y) : bounds.minY();
		this.label = label;
		this.routes = routes;
		this.level = level;
		this.bounds = bounds;
		this.submitter = submitter;
		this.ids = ids;
		Vec3 c = center();
		// feat/build-near-player: next to the player when they are near the site, not on the far side of it
		this.spawnCenter = nearPlayer ? new Vec3(approachFrom.x, bounds.minY(), approachFrom.z) : c;
		Vec3 toViewer = new Vec3(approachFrom.x - c.x, 0, approachFrom.z - c.z);
		double base = toViewer.lengthSqr() < 1e-4 ? 0 : Math.atan2(toViewer.z, toViewer.x);
		int n = Math.max(1, crewSize);
		this.crewSize = n;
		for (int i = 0; i < n; i++) {
			// Fan out from the viewer's side so the crew is seen arriving.
			double angle = base + (i - (n - 1) / 2.0) * 0.6;
			outward.add(new Vec3(Math.cos(angle), 0, Math.sin(angle)));
		}
		// fix/hq-build-stutter: the builders appear one per tick from the first tick on (spawnSome),
		// not all inside the call that started the build.
	}

	/** Spawns up to {@link #SPAWNS_PER_TICK} builders still missing. Client thread. */
	private void spawnSome() {
		if (builders.size() >= crewSize) {
			return;
		}
		long t0 = System.nanoTime();
		Vec3 c = spawnCenter;
		for (int k = 0; k < SPAWNS_PER_TICK && builders.size() < crewSize; k++) {
			int i = builders.size();
			Vec3 dir = outward.get(i);
			BuilderAvatar b = new BuilderAvatar(level, label + "#" + i + "@" + System.identityHashCode(this), "Builder " + (i + 1), this::groundY);
			b.setId(ids.next());
			// near the player: a few blocks from them on their side of the site, else a short walk off the site
			// (dir points from the site's centre to the player: by the player, step towards the site)
			double dist = nearPlayer ? -NEAR_SPAWN_DISTANCE : SPAWN_DISTANCE;
			double sx = c.x + dir.x * dist;
			double sz = c.z + dir.z * dist;
			b.snapTo(sx, groundY(sx, sz), sz, 0f, 0f);
			level.addEntity(b);
			poof(b.position());
			Vec3 entry = nearPlayer ? perimeterSpot(viewer.x - dir.x * 2, bounds.minY(), viewer.z - dir.z * 2)
					: perimeterSpot(c.x + dir.x * 100, bounds.minY(), c.z + dir.z * 100);
			b.walkTo(entry, WALK_SPEED);
			b.lookAt(c.add(0, 1, 0));
			builders.add(b);
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_CREW_SPAWN, t0);
	}

	// ---- BuildHandle --------------------------------------------------------------------------

	@Override
	public String label() {
		return label;
	}

	@Override
	public Phase phase() {
		return phase;
	}

	@Override
	public @Nullable BuildJob job() {
		return job;
	}

	@Override
	public void pause() {
		pauseRequested = true;
		BuildJob j = job;
		if (j != null) {
			j.pause();
		}
	}

	@Override
	public void resume() {
		pauseRequested = false;
		BuildJob j = job;
		if (j != null) {
			j.resume();
		}
	}

	@Override
	public void cancel() {
		cancelRequested = true;
		BuildJob j = job;
		if (j != null) {
			j.cancel();
		}
	}

	// ---- client thread ------------------------------------------------------------------------

	boolean owns(int jobId) {
		BuildJob j = job;
		return j != null && j.id() == jobId;
	}

	ClientLevel level() {
		return level;
	}

	/** One client tick. Returns false once the crew is gone. */
	boolean tick() {
		phaseTicks++;
		switch (phase) {
			case ARRIVING -> {
				if (cancelRequested) {
					leave();
				} else {
					// feat/build-near-player: the job starts right away (it plans off-thread and lays the
					// foundation while the builders appear and walk in); it used to wait for the whole crew to
					// arrive, up to 200 ticks (10 s, always in a void world: no ground to arrive on).
					BuildJob j = submitter.get();
					job = j;
					if (pauseRequested) {
						j.pause();
					}
					if (cancelRequested) {
						j.cancel();
					}
					phase = Phase.BUILDING;
					phaseTicks = 0;
					spawnSome();
				}
			}
			case BUILDING -> {
				// finished() moves us on; the rest of the crew appears one per tick.
				spawnSome();
			}
			case LEAVING -> {
				if (despawned > 0 || phaseTicks >= LEAVE_TIMEOUT_TICKS || builders.stream().allMatch(b -> b.arrived(1.0))) {
					// fix/hq-build-stutter: they vanish one per tick, not all in the same frame
					if (despawnSome()) {
						phase = Phase.DONE;
					}
				}
			}
			case DONE -> {
				return false;
			}
		}
		return phase != Phase.DONE;
	}

	/**
	 * The server placed a block of this crew's job, on {@code front}: each front has its own builder
	 * (fronts never outnumber the crew, see {@code BuildTuning.frontsFor}), so the builders work side
	 * by side, each on its own band of the layer.
	 */
	void placed(BlockPos pos, BlockState state, int front) {
		if (phase != Phase.BUILDING || builders.isEmpty()) {
			return;
		}
		BuilderAvatar b = builders.get(Math.floorMod(front, builders.size()));
		Vec3 block = Vec3.atCenterOf(pos);
		BuildJob j = job;
		if (j == null || j.planner() != BuildPlanner.CREW) {
			// Layer order: no work spots from the server, stand outside the footprint as before.
			b.walkTo(perimeterSpot(block.x, pos.getY(), block.z), WORK_SPEED);
		}
		b.lookAt(block);
		b.place(state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem()));
	}

	/**
	 * Crew plan ({@link BuildPlanner#CREW}): builder {@code front} walks to its next work spot
	 * {@code stand} (feet cell) to place {@code target}, to arrive in {@code ticks} (the server may place
	 * a little before, once the block is in reach; the avatar swings when it gets there).
	 *
	 * <p>fix/crew-route-stutter: this used to run a full A* over the live level right here, on the
	 * render thread, for every event (180–900 ms per tick, once 3.6 s, with the independent builders).
	 * Now only cheap checks run here; the A* is asked of {@link CrewRoutes} (snapshot of the stretch,
	 * background worker, per-tick budget) and only when it can help:
	 * <ul>
	 *   <li>a walk faster than {@link #LEAP_SPEED} (even straight): leap, no route;</li>
	 *   <li>a goal within {@link #DIRECT_RANGE} with a clear line: walk straight;</li>
	 *   <li>a goal or start without built ground (virtual scaffold, cell not built yet): straight line;</li>
	 *   <li>the same start and goal routed a few ticks ago: that route again.</li>
	 * </ul>
	 * While its route is searched the builder keeps doing what it did; a newer order replaces it.
	 */
	void walking(int front, BlockPos stand, BlockPos target, int ticks) {
		if (phase != Phase.BUILDING || builders.isEmpty()) {
			return;
		}
		int index = Math.floorMod(front, builders.size());
		BuilderAvatar b = builders.get(index);
		Vec3 goal = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		Vec3 at = b.position();
		b.lookAt(Vec3.atCenterOf(target));
		double straight = Math.sqrt((goal.x - at.x) * (goal.x - at.x) + (goal.z - at.z) * (goal.z - at.z)) + Math.abs(goal.y - at.y);
		double needed = straight / Math.max(1, ticks);
		// Arrive about when the server places: its walk time is for the straight distance, capped
		// (CrewCursor.MAX_WALK_TICKS). Too far even in a straight run: a leap to the spot, no route.
		if (needed > LEAP_SPEED) {
			routes.cancel(this, index);
			b.leapTo(goal);
			return;
		}
		Collision live = Collision.of(level);
		if (straight <= DIRECT_RANGE && clearLine(live, at, goal) || !grounded(live, goal) || !grounded(live, at)) {
			routes.cancel(this, index);
			b.follow(List.of(goal), Mth.clamp(needed, WALK_SPEED, MAX_WORK_SPEED)); // straight, as the old no-route fallback
			return;
		}
		List<Vec3> cached = routes.cached(level, at, goal);
		if (cached != null) {
			routes.cancel(this, index);
			follow(b, goal, cached, ticks);
			return;
		}
		routes.request(this, index, goal, ticks);
	}

	/** {@link CrewRoutes}: where builder {@code index} starts its route from now, or null if it no longer walks for this crew. */
	@Nullable Vec3 routeStart(int index) {
		if (phase != Phase.BUILDING || index < 0 || index >= builders.size() || builders.get(index).isRemoved()) {
			return null;
		}
		return builders.get(index).position();
	}

	/** {@link CrewRoutes}: the route builder {@code index} asked for, to walk in {@code ticks}. */
	void routed(int index, List<Vec3> route, int ticks) {
		if (phase != Phase.BUILDING || index < 0 || index >= builders.size() || route.isEmpty()) {
			return;
		}
		follow(builders.get(index), route.getLast(), route, ticks);
	}

	/** Walks {@code route} (ending on {@code goal}) at the pace that arrives in {@code ticks}, or leaps if that is too fast. */
	private static void follow(BuilderAvatar b, Vec3 goal, List<Vec3> route, int ticks) {
		double len = 0;
		Vec3 prev = b.position();
		for (Vec3 p : route) {
			len += Math.sqrt((p.x - prev.x) * (p.x - prev.x) + (p.z - prev.z) * (p.z - prev.z)) + Math.abs(p.y - prev.y);
			prev = p;
		}
		double needed = len / Math.max(1, ticks);
		if (needed > LEAP_SPEED) {
			b.leapTo(goal);
		} else {
			b.follow(route, Mth.clamp(needed, WALK_SPEED, MAX_WORK_SPEED));
		}
	}

	/** Built ground under {@code p} (its column, a block up or down): else it is a scaffold spot or air. */
	private static boolean grounded(Collision live, Vec3 p) {
		int x = Mth.floor(p.x);
		int y = Mth.floor(p.y + 0.01);
		int z = Mth.floor(p.z);
		try {
			return !Double.isNaN(live.floor(x, y, z)) || !Double.isNaN(live.floor(x, y - 1, z)) || !Double.isNaN(live.floor(x, y + 1, z));
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** A short straight walk the body fits all along (same level only). */
	private static boolean clearLine(Collision live, Vec3 a, Vec3 b) {
		try {
			return new GridPathfinder(live).clear(pos(a), pos(b), null);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static Pos pos(Vec3 v) {
		return new Pos(v.x, v.y, v.z);
	}

	/** The job reached a terminal state (or the manager was cleared). */
	void finished() {
		if (phase == Phase.ARRIVING || phase == Phase.BUILDING) {
			leave();
		}
	}

	/** Removes the entities right away (level change, shutdown). */
	void discard() {
		for (BuilderAvatar b : builders) {
			if (!b.isRemoved()) {
				level.removeEntity(b.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		builders.clear();
		phase = Phase.DONE;
		routes.forget(this);
	}

	private void leave() {
		phase = Phase.LEAVING;
		routes.forget(this);
		phaseTicks = 0;
		Vec3 c = center();
		for (int i = 0; i < builders.size(); i++) {
			BuilderAvatar b = builders.get(i);
			Vec3 dir = outward.get(i);
			double tx = c.x + dir.x * LEAVE_DISTANCE;
			double tz = c.z + dir.z * LEAVE_DISTANCE;
			b.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			b.lookAt(null);
			b.walkTo(new Vec3(tx, groundY(tx, tz), tz), WALK_SPEED);
		}
	}

	/** Removes up to {@link #SPAWNS_PER_TICK} builders (with a puff); true once all are gone. */
	private boolean despawnSome() {
		long t0 = System.nanoTime();
		for (int k = 0; k < SPAWNS_PER_TICK && despawned < builders.size(); k++) {
			BuilderAvatar b = builders.get(despawned++);
			poof(b.position());
			if (!b.isRemoved()) {
				level.removeEntity(b.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_CREW_DESPAWN, t0);
		if (despawned < builders.size()) {
			return false;
		}
		discard();
		return true;
	}

	private void poof(Vec3 at) {
		for (int i = 0; i < 12; i++) {
			level.addParticle(ParticleTypes.POOF, at.x + (Math.random() - 0.5), at.y + 1.0 + Math.random(), at.z + (Math.random() - 0.5), 0, 0.05, 0);
		}
	}

	private Vec3 center() {
		return new Vec3((bounds.minX() + bounds.maxX() + 1) / 2.0, bounds.minY(), (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
	}

	/**
	 * Feet position just outside the footprint (one block out), on the side nearest to
	 * {@code (x, z)}; standing on the ground, or raised to two below {@code blockY} when building
	 * high.
	 */
	private Vec3 perimeterSpot(double x, int blockY, double z) {
		double x0 = bounds.minX() - 0.5;
		double x1 = bounds.maxX() + 1.5;
		double z0 = bounds.minZ() - 0.5;
		double z1 = bounds.maxZ() + 1.5;
		double px = Mth.clamp(x, x0, x1);
		double pz = Mth.clamp(z, z0, z1);
		double dW = px - x0;
		double dE = x1 - px;
		double dN = pz - z0;
		double dS = z1 - pz;
		double m = Math.min(Math.min(dW, dE), Math.min(dN, dS));
		if (m == dW) {
			px = x0;
		} else if (m == dE) {
			px = x1;
		} else if (m == dN) {
			pz = z0;
		} else {
			pz = z1;
		}
		double y = Math.max(groundY(px, pz), blockY - 2.0);
		return new Vec3(px, y, pz);
	}

	private double groundY(double x, double z) {
		int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		// feat/build-near-player: an empty column (void) has no ground: stand at the site's (or player's) level
		return h <= level.getMinY() ? fallbackY : h;
	}

	/** feat/build-near-player: whether {@code viewer} is within {@code radius} blocks (horizontally, and 32 vertically) of {@code box}. */
	static boolean near(BoundingBox box, Vec3 viewer, int radius) {
		double dx = Math.max(0, Math.max(box.minX() - viewer.x, viewer.x - (box.maxX() + 1)));
		double dz = Math.max(0, Math.max(box.minZ() - viewer.z, viewer.z - (box.maxZ() + 1)));
		double dy = Math.max(0, Math.max(box.minY() - viewer.y, viewer.y - (box.maxY() + 1)));
		return dx * dx + dz * dz <= (double) radius * radius && dy <= 32;
	}
}
