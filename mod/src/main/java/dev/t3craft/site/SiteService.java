package dev.t3craft.site;

import dev.t3craft.T3Craft;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The site workflow, independent of Minecraft (everything goes through {@link Env}). Must be called
 * from one thread (the integrated server thread in game).
 *
 * <pre>
 * here|pick ─▶ preview (reserve outline, report, token) ─▶ confirm &lt;token&gt;
 *      re-evaluate + re-capture (must equal the preview) ─▶ base snapshot saved, fsynced, read back
 *      ─▶ plan saved ─▶ journal BUILDING [base] ─▶ builder.submit ─▶ journal BUILT
 * expand(module) (API, for the builders) ─▶ checks (envelope, overlap, nothing built/fluid there)
 *      ─▶ module snapshot saved ─▶ journal BUILDING [base, …, module] ─▶ builder.submit ─▶ BUILT
 * undo ─▶ report for the newest expansion ─▶ undo confirm &lt;token&gt; [keep|overwrite]
 *      ─▶ RESTORING ─▶ exact restore of that expansion ─▶ archived ─▶ BUILT (or NONE after the base)
 * move (needs a preview) ─▶ report ─▶ move confirm &lt;token&gt; [keep|overwrite]
 *      ─▶ new base snapshot + plan saved ─▶ RESTORING every expansion, newest first ─▶ BUILDING new base
 *      ─▶ the other modules are handed back ({@link #modulesToRebuild()}) for the builders to re-expand
 * </pre>
 * Nothing is written to the world before an explicit confirm with a fresh token (or an
 * {@link #expand} call); any change to the area between preview and confirm, or to the conflicts
 * between undo/move and its confirm, cancels the confirm. Interrupted phases are finished by
 * {@code resume} (idempotent).
 */
public final class SiteService {
	/** World access for the service. */
	public interface Env {
		@Nullable BlockWorld world(String dimension);

		@Nullable TerrainProbe terrain(String dimension);

		SiteBuilder builder(String dimension);

		long now();

		/** Asynchronous messages (build finished…) to the player. */
		void tell(String line);

		/**
		 * Makes the world's chunks durable on disk. Called after each restore and before the journal
		 * forgets the expansion, so a crash cannot leave blocks on disk with no record of them.
		 */
		default void flushWorld() {
		}
	}

	/**
	 * Reply to a command.
	 *
	 * @param outline box to draw as a preview, or null
	 * @param marks cells to highlight (conflicts), at most {@link #MAX_MARKS}
	 * @param actions commands the client offers as clickable buttons
	 */
	public record Reply(boolean ok, List<String> lines, @Nullable Box outline, @Nullable Box footprint, List<int[]> marks,
			List<String> actions) {
		static Reply fail(String... lines) {
			return new Reply(false, List.of(lines), null, null, List.of(), List.of());
		}

		static Reply info(String... lines) {
			return new Reply(true, List.of(lines), null, null, List.of(), List.of());
		}
	}

	public static final int MAX_MARKS = 256;
	public static final String CMD = "/t3craft site";

	private enum Kind {
		BUILD, UNDO, MOVE
	}

	private record Candidate(String dimension, SiteRequest request, SiteEvaluator.Evaluation eval, CellPlan plan, Snapshot before, long expires) {
	}

	private record Pending(Kind kind, String token, long expires, int conflicts) {
	}

	private final SiteStore store;
	private final Env env;
	private final ModuleDesigner designer;
	private final SiteRequest request;
	private final SecureRandom random = new SecureRandom();
	private final List<SiteListener> listeners = new CopyOnWriteArrayList<>();
	private @Nullable Candidate candidate;
	private @Nullable Pending pending;
	private SiteBuilder.@Nullable Handle running;
	private SiteBuilder.@Nullable Report lastReport;

	/** Fixed 15×15 site ({@link SiteRequest#SQUARE}) with a structure source for the base. */
	public SiteService(SiteStore store, Env env, StructureSource structure) {
		this(store, env, ModuleDesigner.of(structure), SiteRequest.SQUARE);
	}

	public SiteService(SiteStore store, Env env, ModuleDesigner designer, SiteRequest request) {
		this.store = store;
		this.env = env;
		this.designer = designer;
		this.request = request;
	}

	public SiteRequest request() {
		return request;
	}

	// ---- integration surface ------------------------------------------------------------------

	public void addListener(SiteListener l) {
		listeners.add(l);
	}

	public void removeListener(SiteListener l) {
		listeners.remove(l);
	}

	private void fire(Consumer<SiteListener> call) {
		for (SiteListener l : listeners) {
			try {
				call.accept(l);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("site listener failed", e);
			}
		}
	}

	/** The active site (any phase but NONE), or null when there is none or the journal is unreadable. */
	public @Nullable SiteHandle handle() {
		try {
			SiteJournal j = store.loadJournal();
			return j.phase() == SiteJournal.Phase.NONE ? null : SiteHandle.of(j);
		} catch (IOException e) {
			return null;
		}
	}

	/** Modules a move left for the builders to rebuild here (oldest first); empty when none. */
	public List<SiteModule> modulesToRebuild() {
		try {
			return store.loadJournal().pending();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** The saved plan (leveling + structure) of an expansion of the active site, or null. */
	public @Nullable CellPlan plan(Expansion e) {
		try {
			return store.loadPlan(e.key());
		} catch (IOException | Snapshot.InvalidSnapshotException ex) {
			return null;
		}
	}

	/** One cell an owner wants to change in place: world coordinates and the new state key. */
	public record CellChange(int x, int y, int z, @Nullable String state) {
		/** A cell the owner no longer uses: put back what was there before the site touched it. */
		public static CellChange restore(int x, int y, int z) {
			return new CellChange(x, y, z, null);
		}

		public boolean restores() {
			return state == null;
		}
	}

	/** A {@link CellChange} the site accepted, with the state the cell held when it was claimed. */
	public record Claimed(int x, int y, int z, String before, String state) {
	}

	/**
	 * In-place changes to modules that are already built (a desk freed, a computer moved to the
	 * archive): the HQ changes cells of an expansion after it was built. Keeps only the cells that lie
	 * in some expansion's box and still hold the site's own block (its plan value or the original
	 * terrain, judged on the newest expansion covering the cell, like undo does), then records the new
	 * state in the saved plan of <b>every</b> expansion covering it, so undo and later expansions keep
	 * treating it as the site's own block. The caller writes the returned cells itself, ideally only
	 * while each one still holds {@link Claimed#before}. Server thread; only when the site is built and
	 * idle (empty list otherwise). Block entities are not recorded (site plans never hold them).
	 */
	public List<Claimed> claim(List<CellChange> changes) {
		return claim(changes, true);
	}

	/**
	 * {@link #claim(List)}; with {@code record = false} nothing is saved (a dry run, e.g. to price the
	 * change first). Cells that already hold the wanted state and have it recorded are left out.
	 */
	public List<Claimed> claim(List<CellChange> changes, boolean record) {
		long t0 = System.nanoTime();
		try {
			return claimCells(changes, record);
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_SITE_CLAIM, t0);
		}
	}

	/**
	 * fix/hq-build-stutter: snapshots and plans of the built expansions, kept between claims (every HQ
	 * update claims twice, a dry run then the real one, and each used to read and decompress every
	 * snapshot of the site from disk on the server thread). A snapshot never changes under its key; a
	 * plan of an existing key is only changed by {@link #claimCells}, on the cached object itself, and
	 * saved right after (dropped from the cache if that save fails). Server thread.
	 */
	private final java.util.Map<String, Layer> layerCache = new java.util.HashMap<>();

	private Active activeCached() throws IOException, Snapshot.InvalidSnapshotException {
		SiteJournal j = store.loadJournal();
		if (j.phase() == SiteJournal.Phase.NONE) {
			throw new IOException("There is no site.");
		}
		BlockWorld w = env.world(j.dimension());
		if (w == null) {
			throw new IOException("The site's dimension (" + j.dimension() + ") is not loaded.");
		}
		java.util.Set<String> keys = new java.util.HashSet<>();
		List<Layer> layers = new ArrayList<>();
		for (Expansion e : j.stack()) {
			keys.add(e.key());
			Layer l = layerCache.get(e.key());
			if (l == null || !l.exp.equals(e)) {
				l = layer(e);
				layerCache.put(e.key(), l);
			}
			layers.add(l);
		}
		layerCache.keySet().retainAll(keys);
		return new Active(j, layers, w);
	}

	private List<Claimed> claimCells(List<CellChange> changes, boolean record) {
		if (changes.isEmpty() || running != null) {
			return List.of();
		}
		Active a;
		try {
			a = activeCached();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			T3Craft.LOGGER.warn("site claim refused: {}", e.getMessage());
			return List.of();
		}
		if (a.journal.phase() != SiteJournal.Phase.BUILT) {
			return List.of();
		}
		List<Claimed> out = new ArrayList<>();
		boolean[] dirty = new boolean[a.layers.size()];
		for (CellChange c : changes) {
			int newest = -1;
			for (int k = a.layers.size() - 1; k >= 0; k--) {
				if (a.layers.get(k).exp.box().contains(c.x(), c.y(), c.z())) {
					newest = k;
					break;
				}
			}
			if (newest < 0) {
				continue;
			}
			Layer top = a.layers.get(newest);
			String want = c.state();
			if (want == null) {
				// removal: the original terrain, from the snapshot of the oldest expansion covering the cell
				Layer first = null;
				for (Layer l : a.layers) {
					if (l.exp.box().contains(c.x(), c.y(), c.z())) {
						first = l;
						break;
					}
				}
				int i = first.snap.box().index(c.x(), c.y(), c.z());
				if (first.snap.blockEntity(i) != null) {
					continue;
				}
				want = first.snap.state(i);
			}
			String now = a.world.state(c.x(), c.y(), c.z());
			if (now.equals(want) && want.equals(top.plan.get(c.x(), c.y(), c.z()))) {
				continue;
			}
			if (!Restorer.isOurs(a.world, top.snap, top.plan, c.x(), c.y(), c.z())) {
				continue;
			}
			out.add(new Claimed(c.x(), c.y(), c.z(), now, want));
			if (!record) {
				continue;
			}
			for (int k = 0; k <= newest; k++) {
				Layer l = a.layers.get(k);
				if (l.exp.box().contains(c.x(), c.y(), c.z()) && !want.equals(l.plan.get(c.x(), c.y(), c.z()))) {
					l.plan.set(c.x(), c.y(), c.z(), want);
					dirty[k] = true;
				}
			}
		}
		try {
			for (int k = 0; k < dirty.length; k++) {
				if (dirty[k]) {
					store.savePlan(a.layers.get(k).exp.key(), a.layers.get(k).plan);
				}
			}
		} catch (IOException e) {
			T3Craft.LOGGER.warn("site claim: could not save a plan: {}", e.getMessage());
			layerCache.clear(); // the cached plans may hold changes the disk does not
			return List.of();
		}
		return out;
	}

	/** True while the site's own builder is writing (a base, an expansion or a resume). */
	public boolean busy() {
		return running != null;
	}

	/**
	 * Adds a module to the built site: checks it fits the envelope and overlaps no module, that its
	 * box (footprint + apron, below the ground to the headroom) holds no fluid and nothing built
	 * except the site's own blocks, then saves and verifies a snapshot of that box <b>before</b>
	 * leveling it and building {@link ModuleDesigner#design} there through the {@link SiteBuilder}.
	 * {@code site undo} removes it again. Server thread.
	 */
	public Reply expand(SiteModule module) {
		long t0 = System.nanoTime();
		try {
			return expandModule(module);
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_SITE_EXPAND, t0);
		}
	}

	private Reply expandModule(SiteModule module) {
		SiteJournal j;
		try {
			j = store.loadJournal();
		} catch (IOException e) {
			return Reply.fail("Cannot read the site journal: " + e.getMessage());
		}
		if (j.phase() == SiteJournal.Phase.NONE) {
			return Reply.fail("There is no site yet. " + CMD + " here | pick first.");
		}
		if (j.phase() != SiteJournal.Phase.BUILT || running != null) {
			return Reply.fail("The site is busy (" + j.phase().name().toLowerCase() + "). Wait for the build or run " + CMD + " resume.");
		}
		SiteHandle h = SiteHandle.of(j);
		for (SiteModule m : h.modules()) {
			if (m.id().equals(module.id())) {
				return Reply.fail("Module " + module.id() + " is already built.");
			}
		}
		if (!h.request().envelope().contains(module.rect())) {
			return Reply.fail("Module " + module.id() + " " + module.rect() + " does not fit the growth envelope " + h.request().envelope() + ".");
		}
		if (!h.fits(module.rect())) {
			return Reply.fail("Module " + module.id() + " " + module.rect() + " overlaps a module already built.");
		}
		BlockWorld world = env.world(j.dimension());
		TerrainProbe probe = env.terrain(j.dimension());
		if (world == null || probe == null) {
			return Reply.fail("The site's dimension (" + j.dimension() + ") is not loaded.");
		}
		// Earlier plans: their cells are the site's own blocks, not "something built in the way".
		List<CellPlan> earlier = new ArrayList<>();
		List<Box> pads = new ArrayList<>();
		try {
			for (Expansion e : j.stack()) {
				earlier.add(store.loadPlan(e.key()));
				pads.add(h.footprint(e.module().rect()));
			}
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Cannot read the site's plans: " + e.getMessage());
		}
		Leveling.Keep keep = (x, z) -> {
			for (Box p : pads) {
				if (p.containsXZ(x, z)) {
					return true;
				}
			}
			return false;
		};
		Columns cols = new Columns(probe);
		Box fp = h.footprint(module.rect());
		int a = SiteLimits.APRON;
		int floor = h.padY();
		int minTop = floor;
		int maxTop = floor;
		for (int x = fp.minX() - a; x <= fp.maxX() + a; x++) {
			for (int z = fp.minZ() - a; z <= fp.maxZ() + a; z++) {
				if (!probe.loaded(x, z)) {
					return Reply.fail("Part of the module's area is not loaded (move closer).");
				}
				if (!keep.keep(x, z)) {
					minTop = Math.min(minTop, cols.at(x, z).top());
					maxTop = Math.max(maxTop, cols.at(x, z).top());
				}
			}
		}
		Box box = new Box(fp.minX() - a, minTop - 1, fp.minZ() - a, fp.maxX() + a, Math.max(floor + SiteLimits.HEADROOM, maxTop + 2), fp.maxZ() + a);
		if (box.minY() < probe.minBuildY() || box.maxY() > probe.maxBuildY() || box.volume() > SiteLimits.MAX_VOLUME
				|| box.chunkCount() > SiteLimits.MAX_CHUNKS) {
			return Reply.fail("Module " + module.id() + " needs a box " + box.shortString() + " outside the limits.");
		}
		String blocked = blocked(probe, world, box, earlier);
		if (blocked != null) {
			return Reply.fail("Module " + module.id() + " cannot go there: " + blocked + ". Nothing was changed.");
		}
		List<String> ents = probe.foreignEntities(box);
		if (!ents.isEmpty()) {
			return Reply.fail("Module " + module.id() + ": entities in the way: " + String.join(", ", ents.subList(0, Math.min(3, ents.size()))) + ".");
		}
		CellPlan plan = Leveling.plan(cols, box, fp, j.origin(), keep);
		plan.overlay(designer.design(module, h.moduleArea(module.rect()), j.origin()));
		String key = newKey();
		Snapshot snap = Snapshot.capture(key, j.dimension(), env.now(), world, box);
		Expansion exp = new Expansion(key, module, box);
		List<SiteModule> stillPending = new ArrayList<>(j.pending());
		stillPending.removeIf(m -> m.id().equals(module.id()));
		SiteJournal next = j.push(exp, stillPending, env.now());
		try {
			store.saveSnapshot(snap);
			store.savePlan(key, plan);
			store.saveJournal(next);
		} catch (IOException e) {
			return Reply.fail("Could not save the module's snapshot safely, nothing was modified: " + e.getMessage());
		}
		startBuild(next, snap, plan);
		return new Reply(true, List.of("Module " + module.id() + ": snapshot " + key + " saved and verified (" + snap.size() + " cells). Building…",
				"Undo it any time: " + CMD + " undo"), box, fp, List.of(), List.of());
	}

	/** First reason the box cannot be built on (fluid, or a block that is neither natural nor the site's own), or null. */
	private static @Nullable String blocked(TerrainProbe probe, BlockWorld world, Box box, List<CellPlan> earlier) {
		for (int y = box.minY(); y <= box.maxY(); y++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					TerrainProbe.Kind k = probe.kind(x, y, z);
					if (k == TerrainProbe.Kind.WATER || k == TerrainProbe.Kind.LAVA) {
						return k.name().toLowerCase() + " at " + x + " " + y + " " + z;
					}
					if (k == TerrainProbe.Kind.ARTIFICIAL && !ours(world.state(x, y, z), earlier, x, y, z)) {
						return "built blocks in the way: " + probe.describe(x, y, z) + " at " + x + " " + y + " " + z;
					}
				}
			}
		}
		return null;
	}

	private static boolean ours(String state, List<CellPlan> plans, int x, int y, int z) {
		for (CellPlan p : plans) {
			if (state.equals(p.get(x, y, z))) {
				return true;
			}
		}
		return false;
	}

	// ---- selection ----------------------------------------------------------------------------

	/** Preview the site with its origin at (cx, cz); the entrance faces (px, pz). */
	public Reply here(String dimension, int cx, int cz, int px, int pz) {
		TerrainProbe probe = env.terrain(dimension);
		if (probe == null) {
			return Reply.fail("This dimension is not available.");
		}
		Columns cols = new Columns(probe);
		CellPlan.Facing facing = CellPlan.Facing.towards(px - cx, pz - cz);
		return preview(dimension, cols, SiteEvaluator.evaluate(cols, cx, cz, facing, request), null);
	}

	/** Search around (px, pz) for the best site and preview it. */
	public Reply pick(String dimension, int px, int pz) {
		TerrainProbe probe = env.terrain(dimension);
		if (probe == null) {
			return Reply.fail("This dimension is not available.");
		}
		Box avoid = null;
		try {
			SiteJournal j = store.loadJournal();
			if (j.phase() != SiteJournal.Phase.NONE && j.dimension().equals(dimension)) {
				avoid = j.reserve();
			}
		} catch (IOException e) {
			return Reply.fail("Cannot read the current site: " + e.getMessage());
		}
		Columns cols = new Columns(probe);
		SiteEvaluator.Search s = SiteEvaluator.search(cols, px, pz, SiteLimits.SEARCH_RADIUS, SiteLimits.SEARCH_STEP, avoid, request);
		if (s.best() == null) {
			List<String> lines = new ArrayList<>();
			lines.add("No suitable site (base " + request.base().rect().width() + "x" + request.base().rect().depth() + ", room to grow to "
					+ request.envelope().width() + "x" + request.envelope().depth() + ") within " + SiteLimits.SEARCH_RADIUS + " blocks (" + s.evaluated()
					+ " checked).");
			for (Map.Entry<String, Integer> e : s.rejected().entrySet()) {
				lines.add("  " + e.getValue() + "× " + e.getKey());
			}
			return new Reply(false, lines, null, null, List.of(), List.of());
		}
		return preview(dimension, cols, s.best(), s.evaluated() + " spots checked");
	}

	private Reply preview(String dimension, Columns cols, SiteEvaluator.Evaluation e, @Nullable String extra) {
		if (!e.ok() || e.box() == null) {
			candidate = null;
			List<String> lines = new ArrayList<>();
			lines.add("Site at " + e.centerX() + " " + e.centerZ() + " refused:");
			e.problems().forEach(p -> lines.add("  " + p));
			Box outline = e.reserve() != null ? e.reserve() : e.box();
			return new Reply(false, lines, outline, null, List.of(), List.of());
		}
		BlockWorld world = env.world(dimension);
		if (world == null) {
			return Reply.fail("This dimension is not available.");
		}
		SiteJournal j;
		try {
			j = store.loadJournal();
		} catch (IOException ex) {
			return Reply.fail("Cannot read the site journal: " + ex.getMessage());
		}
		CellPlan plan = basePlan(cols, e, request.base());
		Snapshot before = Snapshot.capture("preview", dimension, env.now(), world, e.box());
		long expires = env.now() + SiteLimits.PREVIEW_TTL_MS;
		candidate = new Candidate(dimension, request, e, plan, before, expires);
		Box fpFlat = e.footprint();
		Box footprint = new Box(fpFlat.minX(), e.floorY(), fpFlat.minZ(), fpFlat.maxX(), e.floorY(), fpFlat.maxZ());
		List<String> lines = new ArrayList<>();
		lines.add("Site preview at " + e.centerX() + " " + e.floorY() + " " + e.centerZ() + " (entrance " + e.facing().name().toLowerCase()
				+ "): " + e.summary() + (extra == null ? "" : ", " + extra) + ".");
		lines.add("Base " + e.box().shortString() + ": " + plan.count() + " blocks would change. A snapshot is saved before any change.");
		if (!e.envelope().equals(e.footprint())) {
			lines.add("Reserved to grow: " + e.reserve().shortString() + " (checked now, leveled module by module later).");
		}
		List<String> actions = new ArrayList<>();
		switch (j.phase()) {
			case NONE -> {
				String token = token();
				pending = new Pending(Kind.BUILD, token, expires, 0);
				lines.add("Nothing has been changed yet. Confirm within 5 minutes to build here.");
				actions.add(CMD + " confirm " + token);
			}
			case BUILT -> {
				pending = null;
				lines.add("You already have a base. To move it here (the old place is restored, the modules rebuilt here): " + CMD + " move");
				actions.add(CMD + " move");
			}
			default -> {
				pending = null;
				lines.add("A previous build/undo was interrupted: run " + CMD + " resume or " + CMD + " undo first.");
			}
		}
		actions.add(CMD + " cancel");
		return new Reply(true, lines, e.reserve(), footprint, List.of(), actions);
	}

	private CellPlan basePlan(Columns cols, SiteEvaluator.Evaluation e, SiteModule base) {
		CellPlan.Origin origin = e.origin();
		CellPlan plan = Leveling.plan(cols, e.box(), e.footprint(), origin, Leveling.Keep.NOTHING);
		Box fp = e.footprint();
		Box area = new Box(fp.minX(), e.floorY() + 1 - request.baseDepth(), fp.minZ(), fp.maxX(), e.floorY() + SiteLimits.HEADROOM, fp.maxZ());
		plan.overlay(designer.design(base, area, origin));
		return plan;
	}

	// ---- build --------------------------------------------------------------------------------

	public Reply confirm(String token) {
		Candidate c = candidate;
		Pending p = pending;
		if (p == null || p.kind != Kind.BUILD || !p.token.equals(token) || c == null) {
			return Reply.fail("Nothing to confirm (or an old confirmation). Preview again: " + CMD + " here | pick.");
		}
		if (env.now() > p.expires) {
			clear();
			return Reply.fail("The preview expired. Preview again: " + CMD + " here | pick.");
		}
		try {
			if (store.loadJournal().phase() != SiteJournal.Phase.NONE) {
				clear();
				return Reply.fail("A site already exists. Use " + CMD + " move or " + CMD + " undo.");
			}
		} catch (IOException e) {
			return Reply.fail("Cannot read the site journal: " + e.getMessage());
		}
		try {
			recapture(c);
		} catch (IllegalStateException e) {
			clear();
			return Reply.fail(e.getMessage());
		}
		String key = newKey();
		Snapshot snap = Snapshot.capture(key, c.dimension, env.now(), env.world(c.dimension), c.eval.box());
		SiteJournal j = newSite(c, c.request.base(), key, List.of());
		try {
			store.saveSnapshot(snap);
			store.savePlan(key, c.plan);
			store.saveJournal(j);
		} catch (IOException e) {
			clear();
			return Reply.fail("Could not save the snapshot safely, nothing was modified: " + e.getMessage());
		}
		clear();
		startBuild(j, snap, c.plan);
		return new Reply(true, List.of("Snapshot " + key + " saved and verified (" + snap.size() + " cells, " + snap.blockEntityCount()
				+ " block entities). Building…", "Undo any time: " + CMD + " undo"), c.eval.box(), null, List.of(), List.of());
	}

	private SiteJournal newSite(Candidate c, SiteModule base, String key, List<SiteModule> pend) {
		return new SiteJournal(SiteJournal.Phase.BUILDING, c.dimension, c.eval.origin(), c.request, c.eval.reserve(),
				List.of(new Expansion(key, base, c.eval.box())), null, 0, null, pend, env.now());
	}

	/**
	 * Re-evaluates the previewed site and re-captures its box: both must be unchanged since the preview.
	 *
	 * @throws IllegalStateException with a player-facing message otherwise
	 */
	private Snapshot recapture(Candidate c) {
		TerrainProbe probe = env.terrain(c.dimension);
		BlockWorld world = env.world(c.dimension);
		if (probe == null || world == null) {
			throw new IllegalStateException("The site's dimension is not loaded.");
		}
		SiteEvaluator.Evaluation now = SiteEvaluator.evaluate(new Columns(probe), c.eval.centerX(), c.eval.centerZ(), c.eval.facing(), c.request);
		if (!now.ok() || !c.eval.box().equals(now.box()) || now.floorY() != c.eval.floorY()) {
			throw new IllegalStateException("The area changed since the preview (" + (now.ok() ? "different ground" : now.problems().get(0))
					+ "). Nothing was modified; preview again.");
		}
		Snapshot s = Snapshot.capture("preview", c.dimension, env.now(), world, c.eval.box());
		if (!s.sameContent(c.before)) {
			throw new IllegalStateException("Blocks in the area changed since the preview. Nothing was modified; preview again.");
		}
		return s;
	}

	/** Builds the top expansion of {@code j} (journal already BUILDING and durable). */
	private void startBuild(SiteJournal j, Snapshot snap, CellPlan plan) {
		Expansion top = j.top();
		BlockWorld world = env.world(j.dimension());
		if (world == null || top == null) {
			env.tell("Site: dimension " + j.dimension() + " not loaded, build paused. Run " + CMD + " resume there.");
			return;
		}
		lastReport = null;
		SiteHandle h = SiteHandle.of(j);
		fire(l -> l.beforeExpansion(h, top, snap, plan));
		SiteBuilder.CellGuard guard = (x, y, z) -> Restorer.isOurs(world, snap, plan, x, y, z);
		SiteBuilder.Handle handle = env.builder(j.dimension()).submit(plan, plan.origin(), guard, report -> {
			running = null;
			lastReport = report;
			if (report.cancelled()) {
				return;
			}
			SiteJournal done = null;
			try {
				SiteJournal cur = store.loadJournal();
				Expansion t = cur.top();
				if (cur.phase() == SiteJournal.Phase.BUILDING && t != null && top.key().equals(t.key())) {
					done = cur.with(SiteJournal.Phase.BUILT, env.now());
					store.saveJournal(done);
				}
			} catch (IOException e) {
				env.tell("Site built, but the journal could not be updated: " + e.getMessage());
			}
			SiteHandle after = done == null ? h : SiteHandle.of(done);
			fire(l -> l.built(after, top, report));
			env.tell((j.stack().size() == 1 ? "Site built: " : "Module " + top.module().id() + " built: ") + report.placed() + " blocks placed" + (report.skipped() > 0 ? ", " + report.skipped()
					+ " left alone because you changed them (" + String.join("; ", report.skippedSample()) + ")" : "") + ".");
		});
		// A builder may finish inside submit (instant): then nothing is running any more.
		running = handle.finished() ? null : handle;
	}

	// ---- undo / move --------------------------------------------------------------------------

	private record Layer(Expansion exp, Snapshot snap, CellPlan plan) {
	}

	private record Active(SiteJournal journal, List<Layer> layers, BlockWorld world) {
		Layer top() {
			return layers.get(layers.size() - 1);
		}
	}

	private Active active() throws IOException, Snapshot.InvalidSnapshotException {
		SiteJournal j = store.loadJournal();
		if (j.phase() == SiteJournal.Phase.NONE) {
			throw new IOException("There is no site to undo.");
		}
		BlockWorld w = env.world(j.dimension());
		if (w == null) {
			throw new IOException("The site's dimension (" + j.dimension() + ") is not loaded.");
		}
		List<Layer> layers = new ArrayList<>();
		for (Expansion e : j.stack()) {
			layers.add(layer(e));
		}
		return new Active(j, layers, w);
	}

	private Layer layer(Expansion e) throws IOException, Snapshot.InvalidSnapshotException {
		Snapshot snap = store.loadSnapshot(e.key());
		CellPlan plan = store.loadPlan(e.key());
		if (!plan.box().equals(snap.box()) || !snap.box().equals(e.box())) {
			throw new Snapshot.InvalidSnapshotException("plan, snapshot and journal boxes of " + e.key() + " differ");
		}
		return new Layer(e, snap, plan);
	}

	/**
	 * Player changes in {@code layers[from..]}: each layer is judged on the cells no newer layer
	 * covers (a newer snapshot already holds the older layer's blocks).
	 */
	private static List<Restorer.Conflict> conflicts(BlockWorld w, List<Layer> layers, int from) {
		List<Restorer.Conflict> out = new ArrayList<>();
		for (int k = layers.size() - 1; k >= from; k--) {
			Layer l = layers.get(k);
			for (Restorer.Conflict c : Restorer.conflicts(w, l.snap, l.plan)) {
				boolean covered = false;
				for (int h = k + 1; h < layers.size(); h++) {
					if (layers.get(h).exp.box().contains(c.x(), c.y(), c.z())) {
						covered = true;
						break;
					}
				}
				if (!covered) {
					out.add(c);
				}
			}
		}
		return out;
	}

	public Reply undo() {
		Active a;
		try {
			a = active();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail(e.getMessage());
		}
		if (a.journal.phase() == SiteJournal.Phase.RESTORING) {
			return Reply.fail("A restore was interrupted. Run " + CMD + " resume first.");
		}
		Layer top = a.top();
		List<Restorer.Conflict> conflicts = conflicts(a.world, a.layers, a.layers.size() - 1);
		String token = token();
		pending = new Pending(Kind.UNDO, token, env.now() + SiteLimits.PREVIEW_TTL_MS, conflicts.size());
		List<String> lines = new ArrayList<>();
		if (a.layers.size() == 1) {
			lines.add("Undo removes the base and restores the area " + top.snap.box().shortString() + " exactly as it was (snapshot " + top.snap.id() + ").");
		} else {
			lines.add("Undo removes the newest module, " + top.exp.module().id() + " (" + a.layers.size() + " in the site), and restores "
					+ top.snap.box().shortString() + " as it was before it. Undo again to go further back.");
		}
		List<String> actions = new ArrayList<>();
		describeConflicts(conflicts, lines, actions, CMD + " undo confirm " + token);
		actions.add(CMD + " cancel");
		return new Reply(true, lines, top.snap.box(), null, marks(conflicts), actions);
	}

	public Reply undoConfirm(String token, Restorer.@Nullable Mode mode) {
		Pending p = pending;
		if (p == null || p.kind != Kind.UNDO || !p.token.equals(token)) {
			return Reply.fail("Nothing to confirm (or an old confirmation). Run " + CMD + " undo again.");
		}
		if (env.now() > p.expires) {
			clear();
			return Reply.fail("The confirmation expired. Run " + CMD + " undo again.");
		}
		Active a;
		try {
			a = active();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			clear();
			return Reply.fail(e.getMessage());
		}
		List<Restorer.Conflict> conflicts = conflicts(a.world, a.layers, a.layers.size() - 1);
		Reply refused = checkConflicts(p, conflicts.size(), mode, CMD + " undo");
		if (refused != null) {
			return refused;
		}
		clear();
		Restorer.Mode m = mode == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : mode;
		Layer top = a.top();
		try {
			Restored r = restore(a.journal, m, a.layers.size() - 1, null);
			String left = r.journal.phase() == SiteJournal.Phase.NONE ? "The original terrain is back; there is no site now."
					: r.journal.stack().size() + " module(s) left; undo again to remove " + r.journal.top().module().id() + ".";
			return new Reply(true, List.of("Restored " + r.restored + " blocks" + (r.kept > 0 ? ", kept " + r.kept + " of your changes" : "")
					+ ". The snapshot is archived in " + store.root().resolve("archive").getFileName() + "/.", left), top.snap.box(), null, List.of(), List.of());
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Restore stopped: " + e.getMessage() + ". Run " + CMD + " resume to finish it.");
		}
	}

	public Reply move() {
		Candidate c = candidate;
		if (c == null || env.now() > c.expires) {
			return Reply.fail("Choose the new place first: " + CMD + " here | pick, then " + CMD + " move.");
		}
		Active a;
		try {
			a = active();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail(e.getMessage());
		}
		if (a.journal.phase() != SiteJournal.Phase.BUILT) {
			return Reply.fail("The current site is not finished (" + a.journal.phase() + "). Run " + CMD + " resume first.");
		}
		if (c.dimension.equals(a.journal.dimension()) && c.eval.reserve().grow(1, 0).intersects(a.journal.reserve())) {
			return Reply.fail("The new place overlaps the current site (including its room to grow). Pick a spot further away.");
		}
		List<Restorer.Conflict> conflicts = conflicts(a.world, a.layers, 0);
		String token = token();
		pending = new Pending(Kind.MOVE, token, Math.min(c.expires, env.now() + SiteLimits.PREVIEW_TTL_MS), conflicts.size());
		List<String> lines = new ArrayList<>();
		lines.add("Move: restore the old area " + a.journal.reserve().shortString() + " (" + a.layers.size() + " module(s), newest first), then build at "
				+ c.eval.centerX() + " " + c.eval.floorY() + " " + c.eval.centerZ() + " (" + c.eval.summary() + "). The new place is snapshotted before anything changes.");
		if (a.layers.size() > 1) {
			lines.add("The builders then rebuild the other modules there: "
					+ String.join(", ", a.journal.modules().subList(1, a.layers.size()).stream().map(SiteModule::id).toList()) + ".");
		}
		List<String> actions = new ArrayList<>();
		describeConflicts(conflicts, lines, actions, CMD + " move confirm " + token);
		actions.add(CMD + " cancel");
		return new Reply(true, lines, c.eval.reserve(), null, marks(conflicts), actions);
	}

	public Reply moveConfirm(String token, Restorer.@Nullable Mode mode) {
		Pending p = pending;
		Candidate c = candidate;
		if (p == null || p.kind != Kind.MOVE || !p.token.equals(token) || c == null) {
			return Reply.fail("Nothing to confirm (or an old confirmation). Run " + CMD + " move again.");
		}
		if (env.now() > p.expires) {
			clear();
			return Reply.fail("The confirmation expired. Preview again and run " + CMD + " move.");
		}
		Active a;
		try {
			a = active();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			clear();
			return Reply.fail(e.getMessage());
		}
		List<Restorer.Conflict> conflicts = conflicts(a.world, a.layers, 0);
		Reply refused = checkConflicts(p, conflicts.size(), mode, CMD + " move");
		if (refused != null) {
			return refused;
		}
		try {
			recapture(c);
		} catch (IllegalStateException e) {
			clear();
			return Reply.fail(e.getMessage());
		}
		// The same base module (its spec), on the new place's base footprint; the others are handed back.
		List<SiteModule> old = a.journal.modules();
		SiteModule base = old.get(0).withRect(c.request.base().rect());
		List<SiteModule> toRebuild = List.copyOf(old.subList(1, old.size()));
		CellPlan plan = basePlan(new Columns(env.terrain(c.dimension)), c.eval, base);
		// 1. The new place first: if its snapshot cannot be saved, nothing at all has changed.
		String key = newKey();
		Snapshot snap = Snapshot.capture(key, c.dimension, env.now(), env.world(c.dimension), c.eval.box());
		SiteJournal next = newSite(c, base, key, toRebuild);
		try {
			store.saveSnapshot(snap);
			store.savePlan(key, plan);
		} catch (IOException e) {
			clear();
			return Reply.fail("Could not save the new place's snapshot, nothing was modified: " + e.getMessage());
		}
		clear();
		Restorer.Mode m = mode == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : mode;
		Restored r;
		try {
			r = restore(a.journal, m, 0, next);
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Move stopped: " + e.getMessage() + ". Run " + CMD + " resume to finish it.");
		}
		startBuild(next, snap, plan);
		SiteHandle h = SiteHandle.of(next);
		fire(l -> l.moved(h, toRebuild));
		List<String> lines = new ArrayList<>();
		lines.add("Old area restored (" + r.restored + " blocks" + (r.kept > 0 ? ", kept " + r.kept + " of your changes" : "") + "). Snapshot " + key
				+ " saved and verified; building the new base…");
		if (!toRebuild.isEmpty()) {
			lines.add(toRebuild.size() + " module(s) to rebuild here: " + String.join(", ", toRebuild.stream().map(SiteModule::id).toList()) + ".");
		}
		return new Reply(true, lines, c.eval.reserve(), null, List.of(), List.of());
	}

	private @Nullable Reply checkConflicts(Pending p, int now, Restorer.@Nullable Mode mode, String again) {
		if (now != p.conflicts) {
			clear();
			return Reply.fail("Blocks in the area changed since the report (" + p.conflicts + " → " + now + " of your changes). Nothing was modified; run "
					+ again + " again.");
		}
		if (now > 0 && mode == null) {
			return Reply.fail("You changed " + now + " blocks there since the build: add 'keep' (leave them) or 'overwrite' (restore the snapshot over them).");
		}
		return null;
	}

	private record Restored(SiteJournal journal, int restored, int kept) {
	}

	/**
	 * Restores expansions newest first until {@code downTo} remain, then saves the final journal:
	 * {@code then} (a move's new site), BUILT with what is left, or no site. Idempotent: a restored
	 * cell equals its snapshot, so running it again after a crash skips it.
	 */
	private Restored restore(SiteJournal from, Restorer.Mode mode, int downTo, @Nullable SiteJournal then)
			throws IOException, Snapshot.InvalidSnapshotException {
		if (running != null) {
			running.cancel();
			running = null;
		}
		BlockWorld w = env.world(from.dimension());
		if (w == null) {
			throw new IOException("the site's dimension (" + from.dimension() + ") is not loaded");
		}
		SiteJournal j = from.restoring(mode, downTo, then, env.now());
		store.saveJournal(j);
		int restored = 0;
		int kept = 0;
		while (j.stack().size() > downTo) {
			SiteHandle before = SiteHandle.of(j);
			Layer l = layer(j.top());
			Restorer.Result r = Restorer.restore(w, l.snap, l.plan, mode);
			env.flushWorld();
			j = j.pop(env.now());
			store.saveJournal(j);
			store.archive(l.exp.key());
			restored += r.restored();
			kept += r.keptPlayerChanges();
			fire(x -> x.restored(before, l.exp, r));
		}
		SiteJournal fin = then != null ? then : j.stack().isEmpty() ? SiteJournal.EMPTY : j.with(SiteJournal.Phase.BUILT, env.now());
		store.saveJournal(fin);
		return new Restored(fin, restored, kept);
	}

	private static void describeConflicts(List<Restorer.Conflict> conflicts, List<String> lines, List<String> actions, String confirm) {
		if (conflicts.isEmpty()) {
			lines.add("No changes of yours were found in the area. Nothing has been changed yet.");
			actions.add(confirm);
			return;
		}
		lines.add("You changed " + conflicts.size() + " blocks there since the build (highlighted), e.g.:");
		conflicts.stream().limit(5).forEach(c -> lines.add("  " + c.describe()));
		lines.add("Nothing has been changed yet. Choose: keep your changes, or overwrite them with the snapshot.");
		actions.add(confirm + " keep");
		actions.add(confirm + " overwrite");
	}

	private static List<int[]> marks(List<Restorer.Conflict> conflicts) {
		List<int[]> out = new ArrayList<>();
		for (Restorer.Conflict c : conflicts) {
			if (out.size() >= MAX_MARKS) {
				break;
			}
			out.add(new int[] {c.x(), c.y(), c.z()});
		}
		return out;
	}

	// ---- recovery / status --------------------------------------------------------------------

	/** Finishes an interrupted build, undo or move. Safe to run twice. */
	public Reply resume() {
		SiteJournal j;
		try {
			j = store.loadJournal();
		} catch (IOException e) {
			return Reply.fail("Cannot read the site journal: " + e.getMessage());
		}
		try {
			switch (j.phase()) {
				case NONE -> {
					return Reply.info("Nothing to resume.");
				}
				case BUILT -> {
					return Reply.info("The site is built; nothing to resume.");
				}
				case BUILDING -> {
					if (running != null) {
						return Reply.info("The build is running.");
					}
					Layer l;
					try {
						l = layer(j.top());
					} catch (Snapshot.BrokenSnapshotException e) {
						return discardBroken(j);
					}
					startBuild(j, l.snap, l.plan);
					return Reply.info("Resuming the build of " + l.exp.module().id() + " (cells you changed are left alone).");
				}
				case RESTORING -> {
					Restorer.Mode m = j.restoreMode() == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : j.restoreMode();
					Restored r = restore(j, m, j.restoreDownTo(), j.next());
					SiteJournal next = j.next();
					if (next != null) {
						Layer l = layer(next.top());
						startBuild(next, l.snap, l.plan);
						SiteHandle h = SiteHandle.of(next);
						fire(x -> x.moved(h, next.pending()));
						return Reply.info("Old area restored (" + r.restored + " blocks); building the new base " + next.siteId() + ".");
					}
					return Reply.info("Restore finished (" + r.restored + " blocks).");
				}
			}
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Cannot resume: " + e.getMessage() + ". The snapshot files are in " + store.root() + ".");
		}
		return Reply.fail("unreachable");
	}

	/**
	 * Drops the top expansion of a BUILDING journal whose snapshot is {@link Snapshot#BROKEN_VERSION}.
	 * Safe: that version read every block as air and failed on its first non-air write, so it never
	 * placed a block; its files are archived, not deleted.
	 */
	private Reply discardBroken(SiteJournal j) throws IOException {
		Expansion top = j.top();
		store.archive(top.key());
		SiteJournal rest = j.pop(env.now());
		store.saveJournal(rest.stack().isEmpty() ? SiteJournal.EMPTY : rest.with(SiteJournal.Phase.BUILT, env.now()));
		return Reply.info("The interrupted build of " + top.module().id() + " came from a broken version and never placed a block;"
				+ " it was discarded (files archived). Run " + CMD + " again.");
	}

		/** On world load: cleans temp files and reports an interrupted phase (or null when all is fine). */
	public @Nullable String onLoad() {
		try {
			store.cleanTemp();
			SiteJournal j = store.loadJournal();
			return switch (j.phase()) {
				case BUILDING -> "T3Craft site " + j.siteId() + ": the build of " + j.top().module().id() + " was interrupted. " + CMD
						+ " resume to finish it, or " + CMD + " undo to restore its area.";
				case RESTORING -> "T3Craft site " + j.siteId() + ": a restore was interrupted. " + CMD + " resume to finish it.";
				default -> null;
			};
		} catch (IOException e) {
			return "T3Craft site: cannot read " + store.journalFile() + " (" + e.getMessage() + "). Site commands are blocked until it is fixed.";
		}
	}

	public Reply status() {
		SiteJournal j;
		try {
			j = store.loadJournal();
		} catch (IOException e) {
			return Reply.fail("Cannot read the site journal: " + e.getMessage());
		}
		if (j.phase() == SiteJournal.Phase.NONE) {
			return Reply.info("No site yet. " + CMD + " here (in front of you) or " + CMD + " pick (search nearby).");
		}
		List<String> lines = new ArrayList<>();
		lines.add("Site " + j.siteId() + " (" + j.phase().name().toLowerCase() + ") in " + j.dimension() + ", reserve " + j.reserve().shortString() + ".");
		lines.add("Modules (oldest first): " + String.join(", ", j.modules().stream().map(m -> m.id() + " " + m.rect()).toList()) + ".");
		if (!j.pending().isEmpty()) {
			lines.add("To rebuild after the move: " + String.join(", ", j.pending().stream().map(SiteModule::id).toList()) + ".");
		}
		SiteBuilder.Report r = lastReport;
		if (r != null) {
			lines.add("Last build: " + r.placed() + " placed, " + r.skipped() + " left alone, " + r.alreadyThere() + " already in place.");
		}
		return new Reply(true, lines, j.reserve(), null, List.of(), List.of());
	}

	public Reply cancel() {
		boolean had = pending != null || candidate != null;
		clear();
		return Reply.info(had ? "Preview cancelled. Nothing was changed." : "Nothing to cancel.");
	}

	private void clear() {
		pending = null;
		candidate = null;
	}

	private String token() {
		byte[] b = new byte[3];
		random.nextBytes(b);
		return HexFormat.of().formatHex(b);
	}

	private String newKey() {
		String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(env.now()));
		return "site-" + ts + "-" + token();
	}
}
