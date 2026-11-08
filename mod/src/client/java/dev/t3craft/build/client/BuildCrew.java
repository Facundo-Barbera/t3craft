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
 * The animated crew of one build, on the client thread: builders appear a short walk away, walk to
 * the site, then the job is submitted ({@code submitter}); every block the server places is
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
	private static final int ARRIVE_TIMEOUT_TICKS = 20 * 10;
	private static final int LEAVE_TIMEOUT_TICKS = 20 * 6;

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

	BuildCrew(String label, ClientLevel level, BoundingBox bounds, int crewSize, Vec3 approachFrom, IdSource ids, Supplier<BuildJob> submitter) {
		this.label = label;
		this.level = level;
		this.bounds = bounds;
		this.submitter = submitter;
		Vec3 c = center();
		Vec3 toViewer = new Vec3(approachFrom.x - c.x, 0, approachFrom.z - c.z);
		double base = toViewer.lengthSqr() < 1e-4 ? 0 : Math.atan2(toViewer.z, toViewer.x);
		int n = Math.max(1, crewSize);
		for (int i = 0; i < n; i++) {
			// Fan out from the viewer's side so the crew is seen arriving.
			double angle = base + (i - (n - 1) / 2.0) * 0.6;
			Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			outward.add(dir);
			BuilderAvatar b = new BuilderAvatar(level, label + "#" + i + "@" + System.identityHashCode(this), "Builder " + (i + 1), this::groundY);
			b.setId(ids.next());
			double sx = c.x + dir.x * SPAWN_DISTANCE;
			double sz = c.z + dir.z * SPAWN_DISTANCE;
			b.snapTo(sx, groundY(sx, sz), sz, 0f, 0f);
			level.addEntity(b);
			poof(b.position());
			Vec3 entry = perimeterSpot(c.x + dir.x * 100, bounds.minY(), c.z + dir.z * 100);
			b.walkTo(entry, WALK_SPEED);
			b.lookAt(c.add(0, 1, 0));
			builders.add(b);
		}
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
				} else if (phaseTicks >= ARRIVE_TIMEOUT_TICKS || builders.stream().allMatch(b -> b.arrived(1.0))) {
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
				}
			}
			case BUILDING -> {
				// finished() moves us on; nothing to do per tick.
			}
			case LEAVING -> {
				if (phaseTicks >= LEAVE_TIMEOUT_TICKS || builders.stream().allMatch(b -> b.arrived(1.0))) {
					despawn();
					phase = Phase.DONE;
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
	 * The route is the NPCs' A* over the real blocks; a straight line only when there is none (the
	 * spot is on a virtual scaffold or the search gives up).
	 */
	void walking(int front, BlockPos stand, BlockPos target, int ticks) {
		if (phase != Phase.BUILDING || builders.isEmpty()) {
			return;
		}
		BuilderAvatar b = builders.get(Math.floorMod(front, builders.size()));
		Vec3 goal = new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		Vec3 at = b.position();
		List<Vec3> route = route(at, goal);
		double len = 0;
		Vec3 prev = at;
		for (Vec3 p : route) {
			len += Math.sqrt((p.x - prev.x) * (p.x - prev.x) + (p.z - prev.z) * (p.z - prev.z)) + Math.abs(p.y - prev.y);
			prev = p;
		}
		// Arrive about when the server places: its walk time is for the straight distance, capped
		// (CrewCursor.MAX_WALK_TICKS). A way too long even for a run is a shortcut: a leap to the spot.
		double needed = len / Math.max(1, ticks);
		if (needed > LEAP_SPEED) {
			b.leapTo(goal);
		} else {
			b.follow(route, Mth.clamp(needed, WALK_SPEED, MAX_WORK_SPEED));
		}
		b.lookAt(Vec3.atCenterOf(target));
	}

	/** A* route over the real blocks from {@code from} to {@code goal}, ending exactly on {@code goal}. */
	private List<Vec3> route(Vec3 from, Vec3 goal) {
		List<Vec3> out = new ArrayList<>();
		try {
			GridPathfinder pf = new GridPathfinder(Collision.of(level));
			List<Pos> path = pf.find(new Pos(from.x, from.y, from.z), new Pos(goal.x, goal.y, goal.z));
			if (path != null) {
				for (int i = 1; i < path.size(); i++) {
					Pos p = path.get(i);
					out.add(new Vec3(p.x(), p.y(), p.z()));
				}
			}
		} catch (RuntimeException e) {
			out.clear(); // unloaded chunk or similar: straight line below
		}
		if (out.isEmpty() || out.getLast().distanceToSqr(goal) > 1e-4) {
			out.add(goal); // last stretch onto a scaffold spot, or no route at all
		}
		return out;
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
	}

	private void leave() {
		phase = Phase.LEAVING;
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

	private void despawn() {
		for (BuilderAvatar b : builders) {
			poof(b.position());
		}
		discard();
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
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}
}
