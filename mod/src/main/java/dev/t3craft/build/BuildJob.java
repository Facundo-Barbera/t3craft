package dev.t3craft.build;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A {@link BuildPlan} placed at an origin in a dimension, with a rotation and a per-tick block
 * budget. Ticked by {@link BuildManager} on the server thread; {@link #pause()},
 * {@link #resume()}, {@link #cancel()} and progress getters are safe from any thread.
 *
 * <p>World position of entry {@code i}: {@code origin + rotate(plan[i].pos)}; its state is rotated
 * too ({@link BlockState#rotate}), so facing blocks (doors, stairs, torches) turn with the plan.
 *
 * <p><b>Fronts</b>: the plan is split into parallel fronts ({@link Fronts}, {@link FrontCursor}) that work
 * the same layer at once and never start a layer before the one below is finished; each front has its
 * own builder in the crew ({@link #frontOf}). The job wants {@link #rate()} blocks per tick and gets what
 * the global {@link BuildBudget} grants ({@link BuildManager}).
 *
 * <p><b>Charged jobs</b> (a non-null {@link BuildCost}, survival): every placed block takes its item
 * from a {@link MaterialSupply} ({@link MaterialMeter}). When something is missing the job goes to
 * {@link JobState#PAUSED} with {@link #missingMaterials()} non-empty, and resumes on its own once the
 * materials are there (checked about once a second), unless the player paused it too. With fronts:
 * the rest of the layer is checked when it starts; a front whose item ran out mid-layer waits alone
 * while the others go on, and the job waits once every front with work left is waiting. Container
 * contents in block entity data ({@code Items}) are not placed in charged jobs: they would be free loot.
 */
public final class BuildJob {
	/** Called for every block actually placed (not for skipped ones), on the server thread. */
	@FunctionalInterface
	public interface PlacedCallback {
		void placed(BuildJob job, int index, BlockPos worldPos, BlockState worldState);
	}

	/** A builder of a {@link BuildPlanner#CREW} job starts walking to {@code standWorld} (feet) for plan entry {@code index}. */
	@FunctionalInterface
	public interface WalkCallback {
		void walking(BuildJob job, int front, int index, BlockPos standWorld, int ticks);
	}

	private final int id;
	private final String label;
	private final ResourceKey<Level> dimension;
	private final BlockPos origin;
	private final Rotation rotation;
	private final BuildPlan plan;
	private final JobCursor cursor;
	private final BuildPlanner planner;
	private final double blocksPerTick;
	private final @Nullable BuildCost cost;
	private final @Nullable MaterialMeter meter;
	/** Server thread only. */
	private @Nullable MaterialSupply supply;
	private volatile boolean waitingForMaterials;
	private volatile boolean userPaused;
	private int retryIn;
	private boolean materialsChanged;
	/** Segment whose remaining entries were verified affordable; -1 = none. Server thread. */
	private int verifiedSegment = -1;
	/** Segment whose start check failed this round (other fronts block fast without re-checking). */
	private int failedSegment = -1;

	/** Retry cadence while waiting for materials, in ticks. */
	public static final int MATERIAL_RETRY_TICKS = 20;

	public BuildJob(int id, String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick) {
		this(id, label, dimension, origin, rotation, plan, blocksPerTick, null);
	}

	/** @param cost null = free (creative, demo); otherwise every placed block is paid for. One front. */
	public BuildJob(int id, String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost) {
		this(id, label, dimension, origin, rotation, plan, blocksPerTick, cost, 1);
	}

	/**
	 * @param fronts parallel fronts ({@link Fronts}); each works at {@code blocksPerTick}, so the job wants
	 *               {@code blocksPerTick × fronts} per tick ({@link #rate()}), within the global budget.
	 */
	public BuildJob(int id, String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, int fronts) {
		this(id, label, dimension, origin, rotation, plan, blocksPerTick, cost, fronts, BuildPlanner.LAYERS);
	}

	/**
	 * @param planner how the plan is ordered among the fronts: {@link BuildPlanner#LAYERS} (layer bands)
	 *                or {@link BuildPlanner#CREW} (one builder per front, zones, reach and support; the
	 *                schedule is computed here, deterministic for a plan)
	 */
	public BuildJob(int id, String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, int fronts, BuildPlanner planner) {
		if (!(blocksPerTick > 0) || Double.isInfinite(blocksPerTick)) {
			throw new IllegalArgumentException("blocksPerTick must be > 0 and finite: " + blocksPerTick);
		}
		this.id = id;
		this.label = Objects.requireNonNull(label, "label");
		this.dimension = Objects.requireNonNull(dimension, "dimension");
		this.origin = origin.immutable();
		this.rotation = Objects.requireNonNull(rotation, "rotation");
		this.plan = Objects.requireNonNull(plan, "plan");
		this.blocksPerTick = blocksPerTick;
		this.planner = Objects.requireNonNull(planner, "planner");
		int f = Math.max(1, fronts);
		if (planner == BuildPlanner.CREW) {
			int segment = cost == null ? 0 : BuildPlanner.CREW_SEGMENT;
			java.util.function.Supplier<JobCursor> crew = () -> new CrewCursor(CrewSchedule.of(plan, f), segment, BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK);
			// Small plans are planned right here; big HQ parts off-thread (the job waits in PENDING meanwhile).
			this.cursor = plan.size() <= BuildPlanner.SYNC_PLAN_LIMIT ? crew.get()
					: new DeferredCursor(crew, () -> new FrontCursor(Fronts.of(plan, f)), plan.size(), f);
		} else {
			this.cursor = new FrontCursor(Fronts.of(plan, f));
		}
		this.cost = cost;
		if (cost == null) {
			this.meter = null;
		} else {
			int[] layers = new int[plan.size()];
			for (int i = 0; i < layers.length; i++) {
				layers[i] = plan.get(i).pos().getY();
			}
			this.meter = new MaterialMeter(BlockCosts.of(plan, cost.itemOf()), layers);
		}
	}

	/** Server thread, before {@link #tick}: opens the material supply of a charged job (once). */
	public void openSupply(MinecraftServer server) {
		if (cost != null && supply == null) {
			MaterialSupply s = cost.supply().open(server, this);
			supply = s == null ? MaterialSupply.EMPTY : s;
		}
	}

	/** Sets the supply directly (tests, or a caller that already has one). Server thread. */
	public void useSupply(MaterialSupply s) {
		supply = Objects.requireNonNull(s, "supply");
	}

	/** Places up to its own {@link #rate()} into {@code sink} (no global budget). Returns the entries processed. */
	public int tick(BlockSink sink, PlacedCallback onPlaced) {
		return tick(rate(), sink, onPlaced);
	}

	/**
	 * Places up to {@code allowance} blocks this tick into {@code sink}, the fronts taking turns.
	 * Returns the number of entries processed. Server thread.
	 */
	public int tick(double allowance, BlockSink sink, PlacedCallback onPlaced) {
		return tick(allowance, sink, onPlaced, null);
	}

	/** Like {@link #tick(double, BlockSink, PlacedCallback)}; {@code onWalk} hears when a crew builder starts walking. */
	public int tick(double allowance, BlockSink sink, PlacedCallback onPlaced, @Nullable WalkCallback onWalk) {
		MaterialMeter m = meter;
		MaterialSupply sup = supply == null ? MaterialSupply.EMPTY : supply;
		IntPredicate there = j -> sink.matches(worldPos(j), worldState(j));
		if (crew() instanceof CrewCursor cc) {
			cc.probe(there);
			cc.walker(onWalk == null ? null : (front, index, step, ticks) -> onWalk.walking(this, front, index, standWorld(front, step), ticks));
		}
		if (m != null && waitingForMaterials) {
			if (cursor.state() != JobState.PAUSED || userPaused) {
				return 0;
			}
			if (--retryIn > 0) {
				return 0;
			}
			retryIn = MATERIAL_RETRY_TICKS;
			if (!m.affordable(cursor.pendingInSegment(), there, sup)) {
				return 0;
			}
			verifiedSegment = cursor.segment();
			failedSegment = -1;
			waitingForMaterials = false;
			materialsChanged = true;
			cursor.resume();
		}
		int n = cursor.tick(allowance, (front, i) -> {
			Placement p = plan.get(i);
			if (m != null) {
				int seg = cursor.segment();
				if (seg != verifiedSegment) {
					// Layer start: the rest of the layer (all fronts) must be affordable, so the job waits
					// before a layer instead of stopping halfway through it.
					if (seg == failedSegment || !m.affordable(cursor.pendingInSegment(), there, sup)) {
						failedSegment = seg;
						return BuildCursor.Outcome.BLOCKED;
					}
					verifiedSegment = seg;
				}
				if (!m.canPay(i, there, sup)) {
					// Items gone mid-layer: this front waits; the others go on with what is there.
					m.affordable(cursor.pendingInSegment(), there, sup);
					return BuildCursor.Outcome.BLOCKED;
				}
			}
			BlockPos wp = worldPos(i);
			BlockState ws = worldState(i);
			BuildCursor.Outcome o = sink.place(wp, ws, m == null ? p.blockEntity() : withoutItems(p.blockEntity()));
			if (o == BuildCursor.Outcome.PLACED) {
				if (m != null) {
					m.paid(i, sup);
				}
				onPlaced.placed(this, i, wp, ws);
			}
			return o;
		});
		if (m != null && cursor.blocked() && !waitingForMaterials) {
			waitingForMaterials = true;
			materialsChanged = true;
			retryIn = MATERIAL_RETRY_TICKS;
		}
		return n;
	}

	private static @Nullable CompoundTag withoutItems(@Nullable CompoundTag tag) {
		if (tag != null) {
			tag.remove("Items");
		}
		return tag;
	}

	/** Server thread: true once after the job started or stopped waiting for materials. */
	public boolean pollMaterialsChanged() {
		boolean c = materialsChanged;
		materialsChanged = false;
		return c;
	}

	/** Whether blocks cost materials (survival); false for free jobs. */
	public boolean charged() {
		return meter != null;
	}

	/** Paused because materials are missing (see {@link #missingMaterials()}). */
	public boolean waitingForMaterials() {
		return waitingForMaterials;
	}

	/** Item id → count the job is waiting for; empty when not waiting (and always for free jobs). */
	public Map<String, Integer> missingMaterials() {
		return meter == null || !waitingForMaterials ? Map.of() : meter.missing();
	}

	/** What the whole plan costs (item id → count); empty for free jobs. */
	public Map<String, Integer> requiredMaterials() {
		return meter == null ? Map.of() : meter.required();
	}

	/** Items taken so far; empty for free jobs. */
	public Map<String, Integer> spentMaterials() {
		return meter == null ? Map.of() : meter.spent();
	}

	public BlockPos worldPos(int index) {
		return origin.offset(plan.get(index).pos().rotate(rotation));
	}

	public BlockState worldState(int index) {
		return plan.get(index).state().rotate(rotation);
	}

	/** World-space bounding box of the whole plan. */
	public BoundingBox worldBounds() {
		return boundsOf(plan, origin, rotation);
	}

	/** World-space bounding box a plan would occupy at {@code origin} with {@code rotation}. */
	public static BoundingBox boundsOf(BuildPlan plan, BlockPos origin, Rotation rotation) {
		BlockPos a = origin.offset(plan.min().rotate(rotation));
		BlockPos b = origin.offset(plan.max().rotate(rotation));
		return BoundingBox.fromCorners(a, b);
	}

	public boolean pause() {
		userPaused = true;
		return cursor.pause() || (waitingForMaterials && cursor.state() == JobState.PAUSED);
	}

	/** Resumes a paused job; one waiting for materials checks them again right away. */
	public boolean resume() {
		userPaused = false;
		if (waitingForMaterials) {
			retryIn = 0;
			return cursor.state() == JobState.PAUSED;
		}
		return cursor.resume();
	}

	public boolean cancel() {
		return cursor.cancel();
	}

	public JobState state() {
		return cursor.state();
	}

	public double progress() {
		return cursor.progress();
	}

	public int done() {
		return cursor.done();
	}

	public int total() {
		return cursor.total();
	}

	public int placed() {
		return cursor.placed();
	}

	public int skipped() {
		return cursor.skipped();
	}

	public int failed() {
		return cursor.failed();
	}

	public int id() {
		return id;
	}

	public String label() {
		return label;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	public BuildPlan plan() {
		return plan;
	}

	/** Pace of one front. */
	public double blocksPerTick() {
		return blocksPerTick;
	}

	/** Parallel fronts of this job. */
	public int fronts() {
		return cursor.frontCount();
	}

	/** The front that places plan entry {@code index} (for the crew: one builder per front). */
	public int frontOf(int index) {
		return cursor.frontOf(index);
	}

	public BuildPlanner planner() {
		return planner;
	}

	/** The crew schedule of a {@link BuildPlanner#CREW} job; null otherwise. */
	public @Nullable CrewSchedule schedule() {
		CrewCursor cc = crew();
		return cc == null ? null : cc.schedule();
	}

	/** The crew cursor, once planned; null for layer jobs and while planning. */
	private @Nullable CrewCursor crew() {
		JobCursor c = cursor instanceof DeferredCursor d ? d.ready() : cursor;
		return c instanceof CrewCursor cc ? cc : null;
	}

	/** Waits until a {@link BuildPlanner#CREW} job finished planning (tests; never on the server thread). */
	public void awaitPlan() {
		if (cursor instanceof DeferredCursor d) {
			d.await();
		}
	}

	/** World feet cell where builder {@code front} stands for step {@code step} of its lane (CREW jobs). */
	public BlockPos standWorld(int front, int step) {
		CrewSchedule s = java.util.Objects.requireNonNull(schedule(), "not a crew job");
		int[] c = s.stand(front, step);
		return origin.offset(new BlockPos(c[0], c[1], c[2]).rotate(rotation));
	}

	/** World feet cell where builder {@code front} starts (CREW jobs). */
	public BlockPos startWorld(int front) {
		CrewSchedule s = java.util.Objects.requireNonNull(schedule(), "not a crew job");
		int[] c = s.start(front);
		return origin.offset(new BlockPos(c[0], c[1], c[2]).rotate(rotation));
	}

	/** Blocks per tick this job wants: {@code blocksPerTick × fronts}. */
	public double rate() {
		return blocksPerTick * fronts();
	}

	/** Whether front {@code f} is waiting for materials while others go on. Server thread. */
	public boolean frontWaiting(int f) {
		return cursor.frontBlocked(f);
	}

	@Override
	public String toString() {
		return "BuildJob#" + id + "(" + label + ", " + state() + ", " + done() + "/" + total() + ")";
	}
}
