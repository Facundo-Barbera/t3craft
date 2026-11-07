package dev.t3craft.site;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The site workflow, independent of Minecraft (everything goes through {@link Env}). Must be called
 * from one thread (the integrated server thread in game).
 *
 * <pre>
 * here|pick ─▶ preview (outline, report, token) ─▶ confirm &lt;token&gt;
 *      re-evaluate + re-capture (must equal the preview) ─▶ snapshot saved, fsynced, read back
 *      ─▶ plan saved ─▶ journal BUILDING ─▶ builder.submit(plan, origin) ─▶ journal BUILT
 * undo ─▶ report (conflicts = cells the player changed) ─▶ undo-confirm &lt;token&gt; [keep|overwrite]
 *      ─▶ journal RESTORING ─▶ exact restore ─▶ snapshot archived ─▶ journal NONE
 * move (needs a preview) ─▶ report ─▶ move-confirm &lt;token&gt; [keep|overwrite]
 *      ─▶ new snapshot + plan saved ─▶ RESTORING old ─▶ archive old ─▶ BUILDING new
 * </pre>
 * Nothing is written to the world before an explicit confirm with a fresh token; any change to the
 * area between preview and confirm, or to the conflicts between undo/move and its confirm, cancels
 * the confirm. Interrupted phases are finished by {@code resume} (idempotent).
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
		 * Makes the world's chunks durable on disk. Called after a restore and before the journal
		 * forgets the site, so a crash cannot leave the base on disk with no record of it.
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

	private record Candidate(String dimension, SiteEvaluator.Evaluation eval, CellPlan plan, Snapshot before, long expires) {
	}

	private record Pending(Kind kind, String token, long expires, int conflicts) {
	}

	private final SiteStore store;
	private final Env env;
	private final StructureSource structure;
	private final SecureRandom random = new SecureRandom();
	private @Nullable Candidate candidate;
	private @Nullable Pending pending;
	private SiteBuilder.@Nullable Handle running;
	private SiteBuilder.@Nullable Report lastReport;

	public SiteService(SiteStore store, Env env, StructureSource structure) {
		this.store = store;
		this.env = env;
		this.structure = structure;
	}

	// ---- selection ----------------------------------------------------------------------------

	/** Preview the site centred on (cx, cz); the entrance faces (px, pz). */
	public Reply here(String dimension, int cx, int cz, int px, int pz) {
		TerrainProbe probe = env.terrain(dimension);
		if (probe == null) {
			return Reply.fail("This dimension is not available.");
		}
		Columns cols = new Columns(probe);
		return preview(dimension, cols, SiteEvaluator.evaluate(cols, cx, cz), px, pz, null);
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
			if (j.phase() != SiteJournal.Phase.NONE && j.dimension().equals(dimension) && j.siteId() != null) {
				avoid = store.loadSnapshot(j.siteId()).box();
			}
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Cannot read the current site: " + e.getMessage());
		}
		Columns cols = new Columns(probe);
		SiteEvaluator.Search s = SiteEvaluator.search(cols, px, pz, SiteLimits.SEARCH_RADIUS, SiteLimits.SEARCH_STEP, avoid);
		if (s.best() == null) {
			List<String> lines = new ArrayList<>();
			lines.add("No suitable " + SiteLimits.FOOTPRINT + "x" + SiteLimits.FOOTPRINT + " site within " + SiteLimits.SEARCH_RADIUS
					+ " blocks (" + s.evaluated() + " checked).");
			for (Map.Entry<String, Integer> e : s.rejected().entrySet()) {
				lines.add("  " + e.getValue() + "× " + e.getKey());
			}
			return new Reply(false, lines, null, null, List.of(), List.of());
		}
		return preview(dimension, cols, s.best(), px, pz, s.evaluated() + " spots checked");
	}

	private Reply preview(String dimension, Columns cols, SiteEvaluator.Evaluation e, int px, int pz, @Nullable String extra) {
		Box fpFlat = e.footprint();
		if (!e.ok() || e.box() == null) {
			candidate = null;
			List<String> lines = new ArrayList<>();
			lines.add("Site at " + e.centerX() + " " + e.centerZ() + " refused:");
			e.problems().forEach(p -> lines.add("  " + p));
			Box outline = e.box();
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
		CellPlan.Facing facing = CellPlan.Facing.towards(px - e.centerX(), pz - e.centerZ());
		CellPlan plan = fullPlan(cols, e, facing);
		Snapshot before = Snapshot.capture("preview", dimension, env.now(), world, e.box());
		long expires = env.now() + SiteLimits.PREVIEW_TTL_MS;
		candidate = new Candidate(dimension, e, plan, before, expires);
		Box footprint = new Box(fpFlat.minX(), e.floorY(), fpFlat.minZ(), fpFlat.maxX(), e.floorY(), fpFlat.maxZ());
		List<String> lines = new ArrayList<>();
		lines.add("Site preview at " + e.centerX() + " " + e.floorY() + " " + e.centerZ() + " (entrance " + facing.name().toLowerCase()
				+ "): " + e.summary() + (extra == null ? "" : ", " + extra) + ".");
		lines.add("Area " + e.box().shortString() + ": " + plan.count() + " blocks would change. A snapshot is saved before any change.");
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
				lines.add("You already have a base. To move it here (the old place is restored): " + CMD + " move");
				actions.add(CMD + " move");
			}
			default -> {
				pending = null;
				lines.add("A previous build/undo was interrupted: run " + CMD + " resume or " + CMD + " undo first.");
			}
		}
		actions.add(CMD + " cancel");
		return new Reply(true, lines, e.box(), footprint, List.of(), actions);
	}

	private CellPlan fullPlan(Columns cols, SiteEvaluator.Evaluation e, CellPlan.Facing facing) {
		CellPlan plan = Leveling.plan(cols, e, facing);
		Box fp = e.footprint();
		Box area = new Box(fp.minX(), e.floorY() + 1, fp.minZ(), fp.maxX(), e.floorY() + SiteLimits.HEADROOM, fp.maxZ());
		CellPlan design = structure.design(area, plan.origin());
		plan.overlay(design);
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
		String id = newSiteId();
		Snapshot snap = Snapshot.capture(id, c.dimension, env.now(), env.world(c.dimension), c.eval.box());
		try {
			store.saveSnapshot(snap);
			store.savePlan(id, c.plan);
			store.saveJournal(new SiteJournal(SiteJournal.Phase.BUILDING, id, null, c.dimension, null, env.now()));
		} catch (IOException e) {
			clear();
			return Reply.fail("Could not save the snapshot safely, nothing was modified: " + e.getMessage());
		}
		clear();
		startBuild(id, c.dimension, snap, c.plan);
		return new Reply(true, List.of("Snapshot " + id + " saved and verified (" + snap.size() + " cells, " + snap.blockEntityCount()
				+ " block entities). Building…", "Undo any time: " + CMD + " undo"), c.eval.box(), null, List.of(), List.of());
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
		SiteEvaluator.Evaluation now = SiteEvaluator.evaluate(new Columns(probe), c.eval.centerX(), c.eval.centerZ());
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

	private void startBuild(String id, String dimension, Snapshot snap, CellPlan plan) {
		BlockWorld world = env.world(dimension);
		if (world == null) {
			env.tell("Site " + id + ": dimension not loaded, build paused. Run " + CMD + " resume there.");
			return;
		}
		lastReport = null;
		SiteBuilder.CellGuard guard = (x, y, z) -> Restorer.isOurs(world, snap, plan, x, y, z);
		running = env.builder(dimension).submit(plan, plan.origin(), guard, report -> {
			running = null;
			lastReport = report;
			if (report.cancelled()) {
				return;
			}
			try {
				SiteJournal j = store.loadJournal();
				if (j.phase() == SiteJournal.Phase.BUILDING && id.equals(j.siteId())) {
					store.saveJournal(new SiteJournal(SiteJournal.Phase.BUILT, id, null, dimension, null, env.now()));
				}
			} catch (IOException e) {
				env.tell("Site built, but the journal could not be updated: " + e.getMessage());
			}
			env.tell("Site built: " + report.placed() + " blocks placed" + (report.skipped() > 0
					? ", " + report.skipped() + " left alone because you changed them (" + String.join("; ", report.skippedSample()) + ")"
					: "") + ".");
		});
	}

	// ---- undo / move --------------------------------------------------------------------------

	private record Active(SiteJournal journal, Snapshot snap, CellPlan plan, BlockWorld world) {
	}

	private Active active() throws IOException, Snapshot.InvalidSnapshotException {
		SiteJournal j = store.loadJournal();
		if (j.phase() == SiteJournal.Phase.NONE || j.siteId() == null) {
			throw new IOException("There is no site to undo.");
		}
		Snapshot snap = store.loadSnapshot(j.siteId());
		CellPlan plan = store.loadPlan(j.siteId());
		if (!plan.box().equals(snap.box())) {
			throw new Snapshot.InvalidSnapshotException("plan and snapshot boxes differ");
		}
		BlockWorld w = env.world(snap.dimension());
		if (w == null) {
			throw new IOException("The site's dimension (" + snap.dimension() + ") is not loaded.");
		}
		return new Active(j, snap, plan, w);
	}

	public Reply undo() {
		Active a;
		try {
			a = active();
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail(e.getMessage());
		}
		List<Restorer.Conflict> conflicts = Restorer.conflicts(a.world, a.snap, a.plan);
		String token = token();
		pending = new Pending(Kind.UNDO, token, env.now() + SiteLimits.PREVIEW_TTL_MS, conflicts.size());
		List<String> lines = new ArrayList<>();
		lines.add("Undo restores the area " + a.snap.box().shortString() + " exactly as it was before (snapshot " + a.snap.id() + ").");
		List<String> actions = new ArrayList<>();
		describeConflicts(conflicts, lines, actions, CMD + " undo confirm " + token);
		actions.add(CMD + " cancel");
		return new Reply(true, lines, a.snap.box(), null, marks(conflicts), actions);
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
		List<Restorer.Conflict> conflicts = Restorer.conflicts(a.world, a.snap, a.plan);
		Reply refused = checkConflicts(p, conflicts.size(), mode, CMD + " undo");
		if (refused != null) {
			return refused;
		}
		clear();
		Restorer.Mode m = mode == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : mode;
		try {
			Restorer.Result r = restoreAndArchive(a, m, null);
			return new Reply(true, List.of("Restored " + r.restored() + " blocks" + (r.keptPlayerChanges() > 0
					? ", kept " + r.keptPlayerChanges() + " of your changes" : "") + ". The snapshot is archived in "
					+ store.root().resolve("archive").getFileName() + "/."), a.snap.box(), null, List.of(), List.of());
		} catch (IOException e) {
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
		if (c.dimension.equals(a.snap.dimension()) && c.eval.box().grow(1, 0).intersects(a.snap.box())) {
			return Reply.fail("The new place overlaps the current site. Pick a spot further away.");
		}
		List<Restorer.Conflict> conflicts = Restorer.conflicts(a.world, a.snap, a.plan);
		String token = token();
		pending = new Pending(Kind.MOVE, token, Math.min(c.expires, env.now() + SiteLimits.PREVIEW_TTL_MS), conflicts.size());
		List<String> lines = new ArrayList<>();
		lines.add("Move: restore the old area " + a.snap.box().shortString() + ", then build at " + c.eval.centerX() + " " + c.eval.floorY() + " "
				+ c.eval.centerZ() + " (" + c.eval.summary() + "). The new place is snapshotted before anything changes.");
		List<String> actions = new ArrayList<>();
		describeConflicts(conflicts, lines, actions, CMD + " move confirm " + token);
		actions.add(CMD + " cancel");
		return new Reply(true, lines, c.eval.box(), null, marks(conflicts), actions);
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
		List<Restorer.Conflict> conflicts = Restorer.conflicts(a.world, a.snap, a.plan);
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
		// 1. The new place first: if its snapshot cannot be saved, nothing at all has changed.
		String id = newSiteId();
		Snapshot snap = Snapshot.capture(id, c.dimension, env.now(), env.world(c.dimension), c.eval.box());
		try {
			store.saveSnapshot(snap);
			store.savePlan(id, c.plan);
		} catch (IOException e) {
			clear();
			return Reply.fail("Could not save the new place's snapshot, nothing was modified: " + e.getMessage());
		}
		clear();
		Restorer.Mode m = mode == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : mode;
		Restorer.Result r;
		try {
			r = restoreAndArchive(a, m, id);
			store.saveJournal(new SiteJournal(SiteJournal.Phase.BUILDING, id, null, c.dimension, null, env.now()));
		} catch (IOException e) {
			return Reply.fail("Move stopped: " + e.getMessage() + ". Run " + CMD + " resume to finish it.");
		}
		startBuild(id, c.dimension, snap, c.plan);
		return new Reply(true, List.of("Old area restored (" + r.restored() + " blocks" + (r.keptPlayerChanges() > 0 ? ", kept "
				+ r.keptPlayerChanges() + " of your changes" : "") + "). Snapshot " + id + " saved and verified; building the new site…"),
				c.eval.box(), null, List.of(), List.of());
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

	private Restorer.Result restoreAndArchive(Active a, Restorer.Mode mode, @Nullable String next) throws IOException {
		if (running != null) {
			running.cancel();
			running = null;
		}
		store.saveJournal(new SiteJournal(SiteJournal.Phase.RESTORING, a.snap.id(), next, a.snap.dimension(), mode, env.now()));
		Restorer.Result r = Restorer.restore(a.world, a.snap, a.plan, mode);
		env.flushWorld();
		store.archive(a.snap.id());
		store.saveJournal(next == null ? new SiteJournal(SiteJournal.Phase.NONE, null, null, "", null, env.now())
				: new SiteJournal(SiteJournal.Phase.BUILDING, next, null, a.snap.dimension(), null, env.now()));
		return r;
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
					Snapshot snap = store.loadSnapshot(j.siteId());
					CellPlan plan = store.loadPlan(j.siteId());
					startBuild(j.siteId(), snap.dimension(), snap, plan);
					return Reply.info("Resuming the build of " + j.siteId() + " (cells you changed are left alone).");
				}
				case RESTORING -> {
					Active a = active();
					Restorer.Mode m = j.restoreMode() == null ? Restorer.Mode.KEEP_PLAYER_CHANGES : j.restoreMode();
					Restorer.Result r = restoreAndArchive(a, m, j.nextSiteId());
					if (j.nextSiteId() != null) {
						Snapshot snap = store.loadSnapshot(j.nextSiteId());
						CellPlan plan = store.loadPlan(j.nextSiteId());
						startBuild(j.nextSiteId(), snap.dimension(), snap, plan);
						return Reply.info("Old area restored (" + r.restored() + " blocks); building the new site " + j.nextSiteId() + ".");
					}
					return Reply.info("Restore finished (" + r.restored() + " blocks).");
				}
			}
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			return Reply.fail("Cannot resume: " + e.getMessage() + ". The snapshot files are in " + store.root() + ".");
		}
		return Reply.fail("unreachable");
	}

	/** On world load: cleans temp files and reports an interrupted phase (or null when all is fine). */
	public @Nullable String onLoad() {
		try {
			store.cleanTemp();
			SiteJournal j = store.loadJournal();
			return switch (j.phase()) {
				case BUILDING -> "T3Craft site " + j.siteId() + ": the build was interrupted. " + CMD + " resume to finish it, or " + CMD
						+ " undo to restore the area.";
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
			return Reply.info("No site yet. " + CMD + " here (where you stand) or " + CMD + " pick (search nearby).");
		}
		List<String> lines = new ArrayList<>();
		Box box = null;
		try {
			Snapshot s = store.loadSnapshot(j.siteId());
			box = s.box();
			lines.add("Site " + j.siteId() + " (" + j.phase().name().toLowerCase() + ") in " + s.dimension() + ", area " + box.shortString() + ".");
		} catch (IOException | Snapshot.InvalidSnapshotException e) {
			lines.add("Site " + j.siteId() + " (" + j.phase().name().toLowerCase() + "): snapshot unreadable: " + e.getMessage());
		}
		SiteBuilder.Report r = lastReport;
		if (r != null) {
			lines.add("Last build: " + r.placed() + " placed, " + r.skipped() + " left alone, " + r.alreadyThere() + " already in place.");
		}
		return new Reply(true, lines, box, null, List.of(), List.of());
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

	private String newSiteId() {
		String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(env.now()));
		return "site-" + ts + "-" + token();
	}
}
