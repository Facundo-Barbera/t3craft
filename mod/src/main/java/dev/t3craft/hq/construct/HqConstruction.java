package dev.t3craft.hq.construct;

import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.SiteModule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds the HQ of {@code feat/hq-design} on the site of {@code feat/site} and keeps it in step with
 * the roster. One instance per world, driven by {@link #tick()} on the server thread; everything
 * that touches the game goes through {@link Ports}, so the whole flow runs in tests with fakes.
 *
 * <h2>Reconciliation</h2>
 * Each tick (after the roster settled for {@link #DEBOUNCE_MS}) the target HQ is computed from the
 * roster and the layout applied last ({@link HqTarget}: desks stay where they are, modules are added
 * at the lowest free index, settled threads go to the archive). Then, one step at a time:
 * <ol>
 *   <li><b>Expansions.</b> Site modules a move left pending (oldest first), then every grid slot up
 *   to the highest module index that the site does not have yet ({@link HqSiteFrame#wingId}).
 *   Each one is a site expansion: the site saves and verifies a snapshot of its box first, so
 *   {@code site undo} takes it away again, and {@code site move} hands it back for a rebuild. The
 *   survival policy is asked first ({@link Ports#cost}).</li>
 *   <li><b>In-place changes</b> in modules already built (a desk freed or taken, a computer moved to
 *   the archive, a wing emptied, sign text): {@link Ports#update} with the target cells of the
 *   built modules plus <b>removals</b>: the cells the previous plan used and the target does not
 *   (a module or desk that is gone) go back to the site's snapshot, so a later {@code site undo}
 *   stays exact. The port only writes cells whose recorded state differs and that still hold the
 *   site's own blocks. Nothing below {@link HqSiteFrame#MIN_Y} is built (the HQ has no basement).</li>
 *   <li>Then the target layout becomes the applied one and is saved.</li>
 * </ol>
 * The same roster gives the same target, so once applied nothing is built again (idempotent).
 *
 * <h2>Site events</h2>
 * {@link #onExpansionBuilt} after a site build; {@link #onRestored} after an undo (construction
 * holds until {@code /t3craft hq resume}, so an undo is not immediately rebuilt); {@link #onMoved}
 * after a move (the hold is lifted: the pending modules are rebuilt on the new site).
 */
public final class HqConstruction {
	/** The roster must stay unchanged this long before the HQ follows it. */
	public static final long DEBOUNCE_MS = 3_000;
	/** A site build that never reports back is given up after the site has been idle this long. */
	public static final long STALE_MS = 10_000;

	public enum Phase {
		/** fix/hq-load-npc-reconcile: the plan of the saved layout is being generated off-thread. */
		LOADING,
		/** No site yet: run {@code /t3craft site pick} and confirm. */
		NO_SITE,
		/** The site is building, restoring or interrupted ({@code /t3craft site resume}). */
		WAITING_SITE,
		/** No roster yet (not connected). */
		WAITING_ROSTER,
		/** The roster changed a moment ago. */
		SETTLING,
		EXPANDING,
		UPDATING,
		PAUSED,
		/** Something refused the next step ({@link #blocked()}); retried when the roster changes or on {@code hq build}. */
		BLOCKED,
		/** The HQ matches the roster. */
		IDLE
	}

	/** What the construction needs to know about the site. */
	public record SiteView(String id, boolean idle, CellPlan.Origin origin, List<String> modules, List<SiteModule> pending, boolean fitsHq) {
		public SiteView {
			modules = List.copyOf(modules);
			pending = List.copyOf(pending);
		}
	}

	/**
	 * One world cell to set (state already turned to the world). {@code state == null} is a
	 * <b>removal</b>: the HQ no longer uses the cell, so the site puts back what was there before it
	 * built (its snapshot), which keeps {@code site undo} exact.
	 */
	public record Cell(BlockPos pos, @Nullable BlockState state, @Nullable CompoundTag blockEntity) {
		public boolean removal() {
			return state == null;
		}
	}

	/** Outcome of a survival check: allowed, or the message to show. */
	public record Cost(boolean ok, String message) {
		public static final Cost FREE = new Cost(true, "");
	}

	/** Result of {@link Ports#update}. */
	public record Update(boolean started, int cells, @Nullable String refused) {
		public static final Update NOTHING = new Update(false, 0, null);
	}

	/** Persisted per world. */
	public record Saved(@Nullable String siteId, HqLayout layout, boolean paused) {
		public static final Saved EMPTY = new Saved(null, HqLayout.EMPTY, false);
	}

	/** Everything outside the pure logic. Called on the server thread. */
	public interface Ports {
		@Nullable SiteView site();

		/** Starts a site expansion; null when it started (completion: {@link #onExpansionBuilt}), else why not. */
		@Nullable String expand(SiteModule module, BuildPlan design);

		/** Survival policy for placing {@code states} (world states of the blocks to place). */
		Cost cost(String what, List<BlockState> states);

		/**
		 * In-place changes inside built modules. {@code cells}: the wanted state of each cell (the
		 * port skips the ones already recorded and the player's); {@code blockEntities}: cells whose
		 * block entity (sign text) must be loaded, only where the state already matches. Completion
		 * of a started update: {@link #onUpdated}.
		 */
		Update update(String label, List<Cell> cells, List<Cell> blockEntities);

		/** The HQ as it now stands: anchors and desks of the {@code built} site modules only. Null plan = no HQ. */
		void anchors(@Nullable BuildPlan plan, @Nullable HqSite site, Set<String> built);

		void save(Saved state);

		void tell(String line);

		long now();
	}

	private final Ports ports;
	private Saved saved;
	/** Null until the load finished ({@link #loading}); server thread. */
	private @Nullable BuildPlan appliedPlan;
	private @Nullable HqInput roster;
	private long rosterAt;
	private @Nullable HqTarget target;
	/** {@link dev.t3craft.hq.pack.HqLayoutPacks#generation()} {@link #target} was computed with. */
	private int targetPacks = -1;
	private @Nullable HqLayout targetBase;
	private @Nullable String busy;
	private long idleSince = -1;
	private boolean force;
	private boolean holdAfterUndo;
	private boolean syncNeeded = true;
	private final Set<String> signsPending = new LinkedHashSet<>();
	private @Nullable String blocked;
	private @Nullable Object blockedKey;
	private Phase phase = Phase.LOADING;
	private @Nullable String anchorsKey;
	/** Null while {@link #loading} runs; then the plan of the saved layout (or of the last applied target). */
	private volatile @Nullable BuildPlan design;
	/**
	 * fix/hq-build-stutter: the target being computed off-thread ({@link HqTarget#compute} generates the
	 * whole HQ plan: layout, lighting, packs) and the inputs it was asked with. Server thread.
	 */
	private java.util.concurrent.@Nullable CompletableFuture<HqTarget> computing;
	private @Nullable HqInput computingInput;
	private @Nullable HqLayout computingBase;
	private int computingPacks = -1;

	/**
	 * fix/hq-load-npc-reconcile: the plan of the saved layout, generated on a worker
	 * ({@link dev.t3craft.build.Offload}, timed as {@code hq.load_async}). The inputs (layout, style) are
	 * captured on the server thread; nothing of the world is read by the worker. Applied in
	 * {@link #reconcile} once done, if {@link #loadGen} still matches (a {@link #close} or a restart
	 * after a style change bumps it, so a stale result is dropped).
	 */
	private java.util.concurrent.@Nullable CompletableFuture<Loaded> loading;
	private int loadGen;
	private @Nullable HqLayout loadLayout;
	private @Nullable String loadStyle;

	/** A load result tagged with the {@link #loadGen} it was started for. */
	private record Loaded(int gen, BuildPlan plan) {
	}

	public HqConstruction(Ports ports, Saved saved) {
		this.ports = ports;
		this.saved = saved;
		startLoad();
	}

	/** Server thread: (re)starts the load of the plan of {@code saved.layout()} with the current style. */
	private void startLoad() {
		long t0 = System.nanoTime();
		java.util.concurrent.CompletableFuture<Loaded> old = loading;
		if (old != null) {
			old.cancel(false);
		}
		HqLayout layout = saved.layout();
		dev.t3craft.hq.HqStyle style = HqSiteFrame.style();
		int gen = ++loadGen;
		loadLayout = layout;
		loadStyle = style.id();
		loading = dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_HQ_LOAD_ASYNC,
				() -> new Loaded(gen, dev.t3craft.hq.HqPlans.generate(layout, style)));
		phase = Phase.LOADING;
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_HQ_LOAD, t0);
	}

	/**
	 * Server thread: true once the plan of the saved layout is in. While it loads, applies a finished
	 * result of the current generation; a result for an older layout or style is dropped and the load
	 * restarted.
	 */
	private boolean loaded() {
		if (appliedPlan != null) {
			return true;
		}
		java.util.concurrent.CompletableFuture<Loaded> f = loading;
		if (f == null || saved.layout() != loadLayout || !HqSiteFrame.style().id().equals(loadStyle)) {
			startLoad(); // the layout or the style changed while loading (or the load was closed)
			return false;
		}
		if (!f.isDone()) {
			phase = Phase.LOADING;
			return false;
		}
		long t0 = System.nanoTime();
		loading = null;
		Loaded r = f.join(); // a failure propagates to the runtime, which pauses the construction (as before)
		if (r.gen() != loadGen) {
			startLoad(); // a result of an older load (token mismatch): ask again for the current inputs
			return false;
		}
		BuildPlan plan = r.plan();
		appliedPlan = plan;
		if (design == null) {
			design = plan;
		}
		anchorsKey = null;
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_HQ_LOAD_APPLY, t0);
		return true;
	}

	/** World closing (server stop) or replaced: drops a load still running. Server thread. */
	public void close() {
		loadGen++;
		java.util.concurrent.CompletableFuture<Loaded> f = loading;
		loading = null;
		if (f != null) {
			f.cancel(false);
		}
		java.util.concurrent.CompletableFuture<HqTarget> c = computing;
		computing = null;
		if (c != null) {
			c.cancel(false);
		}
	}

	// ---- inputs --------------------------------------------------------------------------------

	/** The live roster (already filtered: no subagents). Unchanged input is ignored. */
	public void roster(HqInput input) {
		if (!input.equals(roster)) {
			roster = input;
			rosterAt = ports.now();
		}
	}

	/**
	 * The plan site module designs are cut from (read by the site's designer, any thread). While the
	 * load still runs (a site confirmed in the first second of the world) this waits for it.
	 */
	public BuildPlan design() {
		BuildPlan d = design;
		if (d != null) {
			return d;
		}
		java.util.concurrent.CompletableFuture<Loaded> f = loading;
		if (f != null) {
			try {
				d = f.join().plan();
			} catch (java.util.concurrent.CancellationException | java.util.concurrent.CompletionException e) {
				d = null;
			}
		}
		if (d == null) {
			// closed, cancelled or failed: generate here (rare, same plan the load gives)
			d = dev.t3craft.hq.HqPlans.generate(saved.layout(), HqSiteFrame.style());
		}
		return d;
	}

	/** {@code /t3craft hq build}: ignore the debounce and retry a blocked step. */
	public void build() {
		force = true;
		blocked = null;
	}

	public void pause() {
		saved = new Saved(saved.siteId(), saved.layout(), true);
		ports.save(saved);
	}

	/** Also lifts the hold after an undo. */
	public void resume() {
		holdAfterUndo = false;
		saved = new Saved(saved.siteId(), saved.layout(), false);
		ports.save(saved);
		build();
	}

	public void onExpansionBuilt(String moduleId) {
		if (Objects.equals(busy, moduleId)) {
			busy = null;
		}
		signsPending.add(moduleId);
		syncNeeded = true;
		anchorsKey = null;
	}

	public void onUpdated(boolean completed) {
		if (busy == null || !busy.startsWith("update")) {
			return;
		}
		busy = null;
		if (!completed) {
			block("the update of the HQ was cancelled");
			return;
		}
		HqTarget t = target;
		if (t != null) {
			apply(t);
		}
	}

	/** A site undo took a module away: hold until {@code hq resume}. */
	public void onRestored(String moduleId) {
		if (HqSiteFrame.indexOf(moduleId) != null) {
			holdAfterUndo = true;
			anchorsKey = null;
		}
	}

	/** A site move finished: rebuild its pending modules here. */
	public void onMoved() {
		holdAfterUndo = false;
		anchorsKey = null;
		syncNeeded = true;
	}

	// ---- state ---------------------------------------------------------------------------------

	public Phase phase() {
		return phase;
	}

	public @Nullable String blocked() {
		return blocked;
	}

	public HqLayout applied() {
		return saved.layout();
	}

	public boolean paused() {
		return saved.paused() || holdAfterUndo;
	}

	public @Nullable HqTarget target() {
		return target;
	}

	public @Nullable String busy() {
		return busy;
	}

	// ---- the loop ------------------------------------------------------------------------------

	public void tick() {
		long t0 = System.nanoTime();
		try {
			reconcile();
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_HQ_TICK, t0);
		}
	}

	/**
	 * fix/hq-build-stutter: the target for {@code in} on {@code base}, computed off-thread; null while it
	 * is being computed (the caller waits a tick). Same inputs give the same target, so the result does
	 * not depend on when it arrives.
	 */
	private @Nullable HqTarget targetFor(HqInput in, HqLayout base, int packs) {
		java.util.concurrent.CompletableFuture<HqTarget> f = computing;
		if (f == null || !in.equals(computingInput) || computingBase != base || computingPacks != packs) {
			if (f != null) {
				f.cancel(false);
			}
			computingInput = in;
			computingBase = base;
			computingPacks = packs;
			f = dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_HQ_TARGET,
					() -> HqTarget.compute(in, base, HqSiteFrame.MAX_MODULES));
			computing = f;
		}
		if (!f.isDone()) {
			return null;
		}
		computing = null;
		return f.join(); // a failure propagates to the runtime, which pauses the construction (as before)
	}

	private void reconcile() {
		if (!loaded()) {
			return; // fix/hq-load-npc-reconcile: phase LOADING until the plan of the saved layout is in
		}
		BuildPlan appliedPlan = java.util.Objects.requireNonNull(this.appliedPlan);
		long now = ports.now();
		SiteView site = ports.site();
		if (busy != null) {
			// A site build that never reported back (cancelled by a server stop): give it up once the site is idle.
			if (busy.startsWith("update") || site == null || !site.idle()) {
				idleSince = -1;
				return;
			}
			if (idleSince < 0) {
				idleSince = now;
			}
			if (now - idleSince < STALE_MS) {
				return;
			}
			busy = null;
			syncNeeded = true;
		}
		idleSince = -1;
		if (site == null) {
			phase = Phase.NO_SITE;
			anchors(null, null, Set.of());
			return;
		}
		if (!site.id().equals(saved.siteId())) {
			// A new site (first confirm, or a move): its base was designed from design(); start over on it.
			saved = new Saved(site.id(), saved.layout(), saved.paused());
			ports.save(saved);
			holdAfterUndo = false;
			syncNeeded = true;
			signsPending.add(HqSiteFrame.BASE);
			anchorsKey = null;
		}
		if (!site.idle()) {
			phase = Phase.WAITING_SITE;
			return;
		}
		HqSite hqSite = HqSiteFrame.hqSite(site.origin());
		if (paused()) {
			phase = Phase.PAUSED;
			anchors(appliedPlan, hqSite, Set.copyOf(site.modules()));
			return;
		}
		if (!site.fitsHq()) {
			block("this site was chosen before the HQ existed and has no room for its wings; choose a new place with /t3craft site pick and move there");
			phase = Phase.BLOCKED;
			return;
		}
		HqInput in = roster;
		if (in == null) {
			phase = Phase.WAITING_ROSTER;
			anchors(appliedPlan, hqSite, Set.copyOf(site.modules()));
			return;
		}
		if (!force && now - rosterAt < DEBOUNCE_MS) {
			phase = Phase.SETTLING;
			return;
		}
		HqTarget t = target;
		int packs = dev.t3craft.hq.pack.HqLayoutPacks.generation();
		if (t == null || !t.input().equals(in) || targetBase != saved.layout() || targetPacks != packs) {
			// feat/hq-layout-packs: a reloaded layout pack re-plans too; the in-place pass below applies the difference
			HqLayout base = saved.layout();
			t = targetFor(in, base, packs);
			if (t == null) {
				phase = Phase.SETTLING; // fix/hq-build-stutter: planned off-thread, picked up on a later tick
				return;
			}
			target = t;
			targetBase = base;
			targetPacks = packs;
		}
		design = t.plan();
		Object key = List.of(in, site.modules(), site.pending(), site.id());
		if (blocked != null && !force && key.equals(blockedKey)) {
			phase = Phase.BLOCKED;
			return;
		}
		blocked = null;
		force = false;

		// 1. expansions, one at a time
		SiteModule next = nextExpansion(site, t);
		if (next != null) {
			List<BlockState> states = statesIn(t.plan(), next.id(), hqSite);
			Cost c = ports.cost("HQ " + next.id(), states);
			if (!c.ok()) {
				blockAt(key, c.message());
				return;
			}
			String err = ports.expand(next, t.plan());
			if (err != null) {
				blockAt(key, err);
				return;
			}
			busy = next.id();
			phase = Phase.EXPANDING;
			ports.tell("HQ: building " + next.id() + " (snapshot saved first; /t3craft site undo removes it).");
			return;
		}

		// 2. in-place changes
		anchors(t.plan(), hqSite, Set.copyOf(site.modules()));
		boolean changed = !t.plan().fingerprint().equals(appliedPlan.fingerprint()) || !t.layout().equals(saved.layout());
		if (!changed && !syncNeeded && signsPending.isEmpty()) {
			phase = Phase.IDLE;
			return;
		}
		long d0 = System.nanoTime();
		Set<String> built = Set.copyOf(site.modules());
		List<Cell> cells = new ArrayList<>();
		List<Cell> signs = new ArrayList<>();
		for (Placement p : t.plan().placements()) {
			String owner = HqSiteFrame.owner(p.rel().getX(), p.rel().getZ());
			if (owner == null || !built.contains(owner) || !HqSiteFrame.builds(p.rel())) {
				continue;
			}
			Cell cell = new Cell(hqSite.cell(p.rel()), p.state().rotate(hqSite.rotation()), p.be());
			cells.add(cell);
			if (p.be() != null) {
				Placement old = appliedPlan.at(p.rel());
				if (signsPending.contains(owner) || old == null || !old.sameContent(p)) {
					signs.add(cell);
				}
			}
		}
		for (Placement p : appliedPlan.placements()) {
			String owner = HqSiteFrame.owner(p.rel().getX(), p.rel().getZ());
			// removals (module or desk no longer in the target): back to the site's snapshot
			if (t.plan().at(p.rel()) == null && owner != null && built.contains(owner) && HqSiteFrame.builds(p.rel())) {
				cells.add(new Cell(hqSite.cell(p.rel()), null, null));
			}
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_HQ_DIFF, d0);
		long u0 = System.nanoTime();
		Update u = ports.update("HQ update", cells, signs);
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_HQ_UPDATE, u0);
		if (u.refused() != null) {
			blockAt(key, u.refused());
			return;
		}
		if (u.started()) {
			busy = "update";
			phase = Phase.UPDATING;
			return;
		}
		apply(t);
	}

	private void apply(HqTarget t) {
		saved = new Saved(saved.siteId(), t.layout(), saved.paused());
		appliedPlan = t.plan();
		ports.save(saved);
		syncNeeded = false;
		signsPending.clear();
		phase = Phase.IDLE;
		if (!t.unseated().isEmpty()) {
			ports.tell("HQ: full (" + HqSiteFrame.MAX_MODULES + " wings); " + t.unseated().size() + " thread(s) wait for a free desk.");
		}
	}

	/** Pending modules of a move first (oldest first), then missing grid slots up to the highest module index. */
	static @Nullable SiteModule nextExpansion(SiteView site, HqTarget t) {
		for (SiteModule m : site.pending()) {
			if (HqSiteFrame.indexOf(m.id()) != null) {
				return m;
			}
		}
		for (int i = 0; i <= t.maxIndex(); i++) {
			String id = HqSiteFrame.wingId(i);
			if (!site.modules().contains(id)) {
				return HqSiteFrame.module(id);
			}
		}
		return null;
	}

	/** Non-air world states the target places in a site module (what it costs). */
	static List<BlockState> statesIn(BuildPlan plan, String moduleId, HqSite site) {
		List<BlockState> out = new ArrayList<>();
		for (Placement p : plan.placements()) {
			if (!p.state().isAir() && HqSiteFrame.builds(p.rel()) && moduleId.equals(HqSiteFrame.owner(p.rel().getX(), p.rel().getZ()))) {
				out.add(p.state().rotate(site.rotation()));
			}
		}
		return out;
	}

	private void anchors(@Nullable BuildPlan plan, @Nullable HqSite site, Set<String> built) {
		String k = plan == null ? "none" : plan.fingerprint() + "|" + site + "|" + new java.util.TreeSet<>(built);
		if (!k.equals(anchorsKey)) {
			anchorsKey = k;
			ports.anchors(plan, site, built);
		}
	}

	private void block(String why) {
		if (!why.equals(blocked)) {
			ports.tell("HQ: " + why);
		}
		blocked = why;
		phase = Phase.BLOCKED;
	}

	private void blockAt(Object key, String why) {
		block(why);
		blockedKey = key;
		force = false;
	}
}
