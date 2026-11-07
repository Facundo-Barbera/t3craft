package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Growth envelope, per-expansion snapshot stack, move with modules. */
class ModularSiteTest {
	static final String DIM = "minecraft:overworld";
	static final String CONFIRM = "/t3craft site confirm ";
	/** East of the base (base is [-7..7]² plan-relative). */
	static final SiteModule A = new SiteModule("project-a", "desks=4", new Rect(8, -7, 15, 7));
	/** Behind the base. */
	static final SiteModule B = new SiteModule("project-b", "desks=4", new Rect(-7, 8, 7, 22));
	/** The whole area the tests can touch around the first site. */
	static final Box AREA = new Box(-24, 45, -24, 24, 90, 32);

	@TempDir
	Path dir;

	FakeWorld world;
	long now = 1_700_000_000_000L;
	SiteStore store;
	SiteService service;
	final List<String> events = new ArrayList<>();

	@BeforeEach
	void setUp() {
		world = new FakeWorld().flat(-120, -120, 120, 120, 64);
		store = new SiteStore(dir);
		service = newService(SiteRequest.GROWABLE);
	}

	SiteService newService(SiteRequest request) {
		SiteService s = new SiteService(store, new SiteService.Env() {
			@Override
			public @Nullable BlockWorld world(String dimension) {
				return world;
			}

			@Override
			public @Nullable TerrainProbe terrain(String dimension) {
				return world;
			}

			@Override
			public SiteBuilder builder(String dimension) {
				return new InstantBuilder(world);
			}

			@Override
			public long now() {
				return now++;
			}

			@Override
			public void tell(String line) {
			}
		}, ModuleDesigner.PLACEHOLDER, request);
		s.addListener(new SiteListener() {
			@Override
			public void beforeExpansion(SiteHandle site, Expansion e, Snapshot snapshot, CellPlan plan) {
				assertTrue(Files.exists(store.snapshotFile(e.key())), "snapshot is on disk before any block");
				events.add("before " + e.module().id());
			}

			@Override
			public void built(SiteHandle site, Expansion e, SiteBuilder.Report report) {
				events.add("built " + e.module().id());
			}

			@Override
			public void restored(SiteHandle site, Expansion e, Restorer.Result result) {
				events.add("restored " + e.module().id());
			}

			@Override
			public void moved(SiteHandle site, List<SiteModule> toRebuild) {
				events.add("moved " + toRebuild.stream().map(SiteModule::id).toList());
			}
		});
		return s;
	}

	static String token(SiteService.Reply r, String prefix) {
		for (String a : r.actions()) {
			if (a.startsWith(prefix)) {
				return a.substring(prefix.length()).trim().split(" ")[0];
			}
		}
		throw new AssertionError("no action " + prefix + " in " + r.actions() + " / " + r.lines());
	}

	Snapshot area() {
		return Snapshot.capture("x", DIM, 0, world, AREA);
	}

	/** Site at the origin, entrance north (player at 0, -30): plan-relative = world offsets. */
	void buildBase() {
		SiteService.Reply p = service.here(DIM, 0, 0, 0, -30);
		assertTrue(p.ok(), p.lines().toString());
		SiteService.Reply c = service.confirm(token(p, CONFIRM));
		assertTrue(c.ok(), c.lines().toString());
	}

	void undoTop() {
		SiteService.Reply u = service.undo();
		assertTrue(u.ok(), u.lines().toString());
		SiteService.Reply d = service.undoConfirm(token(u, "/t3craft site undo confirm "), null);
		assertTrue(d.ok(), d.lines().toString());
	}

	// ---- reserve ------------------------------------------------------------------------------

	@Test
	void reserveRefusesWaterInsideTheEnvelopeButOutsideTheBase() {
		world.put(0, 64, 18, "minecraft:water", null);
		SiteService.Reply r = service.here(DIM, 0, 0, 0, -30);
		assertFalse(r.ok());
		assertTrue(String.join("\n", r.lines()).contains("water"), r.lines().toString());
		assertTrue(newService(SiteRequest.SQUARE).here(DIM, 0, 0, 0, -30).ok(), "the fixed footprint alone would fit");
	}

	@Test
	void reserveRefusesBuiltBlocksAndSteepGround() {
		world.put(12, 65, 20, "minecraft:oak_planks", null);
		SiteService.Reply r = service.here(DIM, 0, 0, 0, -30);
		assertFalse(r.ok());
		assertTrue(String.join("\n", r.lines()).contains("oak_planks"));

		world.put(12, 65, 20, StateKey.AIR, null);
		for (int x = -15; x <= 15; x++) {
			for (int z = 16; z <= 23; z++) {
				world.column(x, z, 64 + SiteLimits.MAX_ENVELOPE_RELIEF + 1);
			}
		}
		SiteEvaluator.Evaluation e = SiteEvaluator.evaluate(new Columns(world), 0, 0, CellPlan.Facing.NORTH, SiteRequest.GROWABLE);
		assertFalse(e.ok());
		assertTrue(e.problems().get(0).contains("to grow"), e.problems().toString());
		assertTrue(SiteEvaluator.evaluate(new Columns(world), 0, 0, CellPlan.Facing.NORTH, SiteRequest.SQUARE).ok());
	}

	@Test
	void reserveTurnsWithTheEntranceAndSearchKeepsItClear() {
		// entrance east: the envelope grows west (behind the base)
		SiteEvaluator.Evaluation e = SiteEvaluator.evaluate(new Columns(world), 0, 0, CellPlan.Facing.EAST, SiteRequest.GROWABLE);
		assertTrue(e.ok(), e.problems().toString());
		assertEquals(new Box(-23, 0, -15, 7, 0, 15), e.envelope());
		world.put(-20, 64, 0, "minecraft:lava", null);
		assertFalse(SiteEvaluator.evaluate(new Columns(world), 0, 0, CellPlan.Facing.EAST, SiteRequest.GROWABLE).ok());
		assertTrue(SiteEvaluator.evaluate(new Columns(world), 0, 0, CellPlan.Facing.WEST, SiteRequest.GROWABLE).ok(), "facing west the lava is in front");
	}

	@Test
	void onlyTheBaseIsLeveledAtFirst() throws Exception {
		Snapshot pristine = area();
		buildBase();
		SiteJournal j = store.loadJournal();
		assertEquals(SiteJournal.Phase.BUILT, j.phase());
		assertEquals(List.of("base"), j.modules().stream().map(SiteModule::id).toList());
		assertEquals(Leveling.PAD, world.state(0, 64, 0));
		// envelope, outside the base and its apron: untouched
		assertEquals(pristine.state(AREA.index(0, 64, 18)), world.state(0, 64, 18));
		assertEquals(pristine.state(AREA.index(13, 64, 0)), world.state(13, 64, 0));
		SiteHandle h = service.handle();
		assertNotNull(h);
		assertEquals(new Box(-17, 63, -9, 17, 76, 25), h.reserve());
		assertEquals(new Box(8, 64, -7, 15, 64, 7), h.footprint(A.rect()));
		assertTrue(h.fits(A.rect()));
		assertFalse(h.fits(new Rect(0, 0, 9, 9)), "overlaps the base");
		assertFalse(h.fits(new Rect(10, -7, 18, 7)), "outside the envelope");
	}

	// ---- expansions and the undo stack ---------------------------------------------------------

	@Test
	void eachExpansionIsSnapshottedAndUndoWalksBackToTheOriginalTerrain() throws Exception {
		Snapshot pristine = area();
		buildBase();
		Snapshot afterBase = area();
		SiteService.Reply ra = service.expand(A);
		assertTrue(ra.ok(), ra.lines().toString());
		assertEquals(Leveling.PAD, world.state(12, 64, 0));
		assertEquals("minecraft:oak_log[axis=y]", world.state(8, 65, -7), "A's corner post");
		assertEquals(Leveling.PAD_EDGE, world.state(7, 64, 0), "the base pad is left as it was");
		Snapshot afterA = area();
		assertTrue(service.expand(B).ok());
		assertEquals(Leveling.PAD, world.state(0, 64, 15));
		SiteJournal j = store.loadJournal();
		assertEquals(3, j.stack().size());
		for (Expansion e : j.stack()) {
			assertTrue(Files.exists(store.snapshotFile(e.key())), "one durable snapshot per expansion");
		}
		assertEquals(List.of("before base", "built base", "before project-a", "built project-a", "before project-b", "built project-b"), events);

		undoTop();
		assertTrue(area().sameContent(afterA), "undo removes only the newest module");
		assertEquals(List.of("base", "project-a"), store.loadJournal().modules().stream().map(SiteModule::id).toList());
		assertEquals(SiteJournal.Phase.BUILT, store.loadJournal().phase());
		undoTop();
		assertTrue(area().sameContent(afterBase));
		undoTop();
		assertTrue(area().sameContent(pristine), "back to the original terrain");
		assertEquals(SiteJournal.Phase.NONE, store.loadJournal().phase());
		assertNull(service.handle());
		assertTrue(events.containsAll(List.of("restored project-b", "restored project-a", "restored base")));
	}

	@Test
	void undoOfAnExpansionAsksAboutPlayerChangesInItsBoxOnly() throws Exception {
		buildBase();
		world.put(0, 65, 0, "minecraft:oak_planks", null); // player block on the base
		assertTrue(service.expand(A).ok());
		world.put(12, 65, 0, "minecraft:glass", null); // player block on module A
		SiteService.Reply u = service.undo();
		assertEquals(1, u.marks().size(), u.lines().toString());
		assertEquals(12, u.marks().get(0)[0]);
		SiteService.Reply noMode = service.undoConfirm(token(u, "/t3craft site undo confirm "), null);
		assertFalse(noMode.ok());
		assertTrue(service.undoConfirm(token(u, "/t3craft site undo confirm "), Restorer.Mode.KEEP_PLAYER_CHANGES).ok());
		assertEquals("minecraft:glass", world.state(12, 65, 0), "kept");
		assertEquals(StateKey.AIR, world.state(8, 65, -7), "A's post removed");
		assertEquals("minecraft:oak_planks", world.state(0, 65, 0), "the base is untouched by undoing A");
	}

	@Test
	void expansionIsRefusedWithoutTouchingAnything() throws Exception {
		assertFalse(service.expand(A).ok(), "no site yet");
		buildBase();
		SiteJournal before = store.loadJournal();
		int writes = world.writes;
		assertFalse(service.expand(new SiteModule("base", "", new Rect(8, -7, 15, 7))).ok(), "duplicate id");
		assertFalse(service.expand(new SiteModule("x", "", new Rect(0, 0, 9, 9))).ok(), "overlaps the base");
		assertFalse(service.expand(new SiteModule("x", "", new Rect(10, -7, 18, 7))).ok(), "outside the envelope");
		world.put(14, 65, 3, "minecraft:crafting_table", null);
		writes = world.writes;
		SiteService.Reply r = service.expand(A);
		assertFalse(r.ok());
		assertTrue(r.lines().get(0).contains("crafting_table"), r.lines().toString());
		world.put(14, 65, 3, StateKey.AIR, null);
		world.put(16, 64, 3, "minecraft:water", null); // in A's apron
		writes = world.writes;
		assertFalse(service.expand(A).ok());
		assertEquals(writes, world.writes);
		assertEquals(before, store.loadJournal());
	}

	@Test
	void interruptedExpansionResumesAndCanBeUndone() throws Exception {
		Snapshot pristine = area();
		buildBase();
		Snapshot afterBase = area();
		// expansion whose build never finishes (game quit)
		SiteService held = new SiteService(store, new SiteService.Env() {
			@Override
			public BlockWorld world(String d) {
				return world;
			}

			@Override
			public TerrainProbe terrain(String d) {
				return world;
			}

			@Override
			public SiteBuilder builder(String d) {
				return (plan, origin, guard, done) -> new SiteBuilder.Handle() {
					@Override
					public boolean finished() {
						return false;
					}

					@Override
					public void cancel() {
					}
				};
			}

			@Override
			public long now() {
				return now++;
			}

			@Override
			public void tell(String line) {
			}
		}, ModuleDesigner.PLACEHOLDER, SiteRequest.GROWABLE);
		assertTrue(held.expand(A).ok());
		assertEquals(SiteJournal.Phase.BUILDING, store.loadJournal().phase());
		SiteService fresh = newService(SiteRequest.GROWABLE);
		assertTrue(fresh.onLoad().contains("project-a"));
		assertTrue(fresh.resume().ok());
		assertEquals(SiteJournal.Phase.BUILT, store.loadJournal().phase());
		assertEquals(Leveling.PAD, world.state(12, 64, 0));
		service = fresh;
		undoTop();
		assertTrue(area().sameContent(afterBase));
		undoTop();
		assertTrue(area().sameContent(pristine));
	}

	// ---- move -------------------------------------------------------------------------------

	@Test
	void moveRestoresEveryExpansionAndHandsBackTheModules() throws Exception {
		Snapshot pristine = area();
		buildBase();
		assertTrue(service.expand(A).ok());
		assertTrue(service.expand(B).ok());
		events.clear();
		// new place: origin (-60, 0), entrance east (player at -30, 0): plan +z grows towards world -x
		SiteService.Reply p = service.here(DIM, -60, 0, -30, 0);
		assertTrue(p.ok(), p.lines().toString());
		SiteService.Reply m = service.move();
		assertTrue(m.ok(), m.lines().toString());
		assertTrue(String.join("\n", m.lines()).contains("project-a, project-b"));
		SiteService.Reply done = service.moveConfirm(token(m, "/t3craft site move confirm "), null);
		assertTrue(done.ok(), done.lines().toString());

		assertTrue(area().sameContent(pristine), "the old place is exactly as it was");
		assertEquals(List.of("restored project-b", "restored project-a", "restored base", "before base", "built base", "moved [project-a, project-b]"),
				events);
		SiteJournal j = store.loadJournal();
		assertEquals(SiteJournal.Phase.BUILT, j.phase());
		assertEquals(List.of("base"), j.modules().stream().map(SiteModule::id).toList());
		assertEquals(List.of(A, B), service.modulesToRebuild());
		assertEquals(Leveling.PAD, world.state(-60, 64, 0));

		// the builders rebuild the same modules (same plan-relative rects) at the new place
		assertTrue(service.expand(A).ok());
		assertEquals(List.of(B), service.modulesToRebuild());
		assertTrue(service.expand(B).ok());
		assertTrue(service.modulesToRebuild().isEmpty());
		SiteHandle h = service.handle();
		assertEquals(new Box(-67, 64, 8, -53, 64, 15), h.footprint(A.rect()), "A turned with the entrance");
		assertEquals(Leveling.PAD, world.state(-60, 64, 12));
		assertEquals(Leveling.PAD, world.state(-75, 64, 0), "B behind the base");
		// and everything can still be undone, module by module
		undoTop();
		undoTop();
		undoTop();
		assertEquals(SiteJournal.Phase.NONE, store.loadJournal().phase());
	}

	@Test
	void moveIntoTheOldReserveIsRefused() {
		buildBase();
		SiteService.Reply p = service.here(DIM, 0, 30, 0, 0);
		assertTrue(p.ok(), p.lines().toString());
		SiteService.Reply m = service.move();
		assertFalse(m.ok());
		assertTrue(m.lines().get(0).contains("overlaps"));
	}

	@Test
	void interruptedMoveIsFinishedByResume() throws Exception {
		Snapshot pristine = area();
		buildBase();
		assertTrue(service.expand(A).ok());
		SiteJournal built = store.loadJournal();
		// crash after the restore started: the new base is saved, the old stack is half restored
		SiteService.Reply p = service.here(DIM, -60, 0, -30, 0);
		SiteService.Reply m = service.move();
		assertTrue(service.moveConfirm(token(m, "/t3craft site move confirm "), null).ok());
		SiteJournal moved = store.loadJournal();
		// replay: put the old site back in the journal as RESTORING with the new site as next
		for (Expansion e : built.stack()) {
			Files.move(store.root().resolve("archive").resolve(e.key() + ".nbt"), store.snapshotFile(e.key()));
			Files.move(store.root().resolve("archive").resolve(e.key() + ".plan.nbt"), store.planFile(e.key()));
		}
		store.saveJournal(built.restoring(Restorer.Mode.KEEP_PLAYER_CHANGES, 0, moved.with(SiteJournal.Phase.BUILDING, now), now));
		SiteService fresh = newService(SiteRequest.GROWABLE);
		assertTrue(fresh.onLoad().contains("restore"));
		SiteService.Reply r = fresh.resume();
		assertTrue(r.ok(), r.lines().toString());
		assertTrue(area().sameContent(pristine));
		assertEquals(SiteJournal.Phase.BUILT, store.loadJournal().phase());
		assertEquals(List.of(A), fresh.modulesToRebuild());
	}
}
