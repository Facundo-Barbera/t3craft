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

class SiteServiceTest {
	static final String DIM = "minecraft:overworld";

	@TempDir
	Path dir;

	FakeWorld world;
	long now = 1_700_000_000_000L;
	final List<String> told = new ArrayList<>();
	SiteStore store;
	SiteService service;
	/** When set, the builder records the plan and does not build (an "animated" builder still running). */
	boolean holdBuilds;

	@BeforeEach
	void setUp() {
		world = new FakeWorld().flat(-80, -80, 80, 80, 64);
		store = new SiteStore(dir);
		service = newService();
	}

	SiteService newService() {
		return new SiteService(store, new SiteService.Env() {
			@Override
			public @Nullable BlockWorld world(String dimension) {
				return DIM.equals(dimension) ? world : null;
			}

			@Override
			public @Nullable TerrainProbe terrain(String dimension) {
				return DIM.equals(dimension) ? world : null;
			}

			@Override
			public SiteBuilder builder(String dimension) {
				if (holdBuilds) {
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
				return new InstantBuilder(world);
			}

			@Override
			public long now() {
				return now;
			}

			@Override
			public void tell(String line) {
				told.add(line);
			}
		}, StructureSource.PLACEHOLDER);
	}

	static String token(SiteService.Reply r, String prefix) {
		for (String a : r.actions()) {
			if (a.startsWith(prefix)) {
				return a.substring(prefix.length()).trim().split(" ")[0];
			}
		}
		throw new AssertionError("no action " + prefix + " in " + r.actions());
	}

	Snapshot boxNow(Box b) {
		return Snapshot.capture("x", DIM, 0, world, b);
	}

	@Test
	void previewChangesNothingAndConfirmBuildsAfterADurableSnapshot() throws Exception {
		Box area = new Box(-20, 50, -20, 20, 90, 20);
		Snapshot pristine = boxNow(area);
		int writesBefore = world.writes;
		SiteService.Reply p = service.here(DIM, 0, 0, 0, 12);
		assertTrue(p.ok(), p.lines().toString());
		assertNotNull(p.outline());
		assertEquals(writesBefore, world.writes, "preview never writes");
		assertTrue(boxNow(area).sameContent(pristine));

		String t = token(p, "/t3craft site confirm ");
		assertFalse(service.confirm("bogus").ok());
		SiteService.Reply c = service.confirm(t);
		assertTrue(c.ok(), c.lines().toString());
		SiteJournal j = store.loadJournal();
		assertEquals(SiteJournal.Phase.BUILT, j.phase());
		Snapshot snap = store.loadSnapshot(j.siteId());
		assertTrue(snap.sameContent(Snapshot.capture("y", DIM, 0, pristine(area, pristine), snap.box())), "snapshot = terrain before");
		assertEquals(Leveling.PAD, world.state(0, 64, 0));
		assertEquals("minecraft:oak_log[axis=y]", world.state(-7, 65, -7));
		assertFalse(service.confirm(t).ok(), "a token works once");

		// clean undo restores exactly
		SiteService.Reply u = service.undo();
		assertTrue(u.ok());
		assertTrue(u.marks().isEmpty());
		SiteService.Reply done = service.undoConfirm(token(u, "/t3craft site undo confirm "), null);
		assertTrue(done.ok(), done.lines().toString());
		assertTrue(boxNow(area).sameContent(pristine), "area is exactly as before");
		assertEquals(SiteJournal.Phase.NONE, store.loadJournal().phase());
		assertTrue(Files.exists(store.root().resolve("archive").resolve(j.siteId() + ".nbt")), "snapshot kept in archive");
	}

	/** World view of the pristine snapshot (for comparing sub-boxes). */
	private static BlockWorld pristine(Box area, Snapshot s) {
		return new BlockWorld() {
			@Override
			public String state(int x, int y, int z) {
				return s.state(area.index(x, y, z));
			}

			@Override
			public net.minecraft.nbt.CompoundTag blockEntity(int x, int y, int z) {
				return s.blockEntity(area.index(x, y, z));
			}

			@Override
			public void set(int x, int y, int z, String state, net.minecraft.nbt.CompoundTag be) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int minBuildY() {
				return -64;
			}

			@Override
			public int maxBuildY() {
				return 319;
			}
		};
	}

	@Test
	void confirmIsRefusedWhenTheAreaChangedSinceThePreview() throws Exception {
		SiteService.Reply p = service.here(DIM, 0, 0, 0, 12);
		world.set(3, 65, 3, "minecraft:short_grass", null);
		SiteService.Reply c = service.confirm(token(p, "/t3craft site confirm "));
		assertFalse(c.ok());
		assertEquals(SiteJournal.Phase.NONE, store.loadJournal().phase());
		assertFalse(Files.exists(store.root().resolve("snapshots")) && Files.list(store.root().resolve("snapshots")).findAny().isPresent());
	}

	@Test
	void expiredPreviewIsRefused() {
		SiteService.Reply p = service.here(DIM, 0, 0, 0, 12);
		now += SiteLimits.PREVIEW_TTL_MS + 1;
		assertFalse(service.confirm(token(p, "/t3craft site confirm ")).ok());
	}

	@Test
	void undoWithPlayerChangesNeedsAnExplicitChoice() throws Exception {
		Box area = new Box(-20, 50, -20, 20, 90, 20);
		Snapshot pristine = boxNow(area);
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));
		world.set(1, 65, 1, "minecraft:crafting_table", null); // player's own block on the pad
		world.set(2, 64, 2, "minecraft:gold_block", null); // player replaced a pad block

		SiteService.Reply u = service.undo();
		assertEquals(2, u.marks().size());
		String t = token(u, "/t3craft site undo confirm ");
		SiteService.Reply noChoice = service.undoConfirm(t, null);
		assertFalse(noChoice.ok(), "never overwrites silently");
		assertEquals("minecraft:crafting_table", world.state(1, 65, 1));

		// the player changes something else before confirming: the old report is stale
		world.set(3, 65, 3, "minecraft:torch", null);
		assertFalse(service.undoConfirm(t, Restorer.Mode.KEEP_PLAYER_CHANGES).ok());
		assertEquals(Leveling.PAD, world.state(0, 64, 0), "still nothing restored");

		SiteService.Reply u2 = service.undo();
		assertEquals(3, u2.marks().size());
		SiteService.Reply keep = service.undoConfirm(token(u2, "/t3craft site undo confirm "), Restorer.Mode.KEEP_PLAYER_CHANGES);
		assertTrue(keep.ok(), keep.lines().toString());
		assertEquals("minecraft:crafting_table", world.state(1, 65, 1));
		assertEquals("minecraft:gold_block", world.state(2, 64, 2));
		assertEquals("minecraft:grass_block[snowy=false]", world.state(0, 64, 0));
		assertFalse(boxNow(area).sameContent(pristine));
	}

	@Test
	void undoOverwriteRestoresEverything() throws Exception {
		Box area = new Box(-20, 50, -20, 20, 90, 20);
		Snapshot pristine = boxNow(area);
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));
		world.set(1, 65, 1, "minecraft:crafting_table", null);
		SiteService.Reply u = service.undo();
		assertTrue(service.undoConfirm(token(u, "/t3craft site undo confirm "), Restorer.Mode.OVERWRITE).ok());
		assertTrue(boxNow(area).sameContent(pristine));
	}

	@Test
	void moveRestoresTheOldPlaceAndBuildsTheNewOne() throws Exception {
		Box oldArea = new Box(-20, 50, -20, 20, 90, 20);
		Snapshot oldPristine = boxNow(oldArea);
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));

		assertFalse(service.move().ok(), "move needs a preview of the new place");
		assertFalse(service.here(DIM, 10, 0, 10, 12).ok(), "the base's own blocks count as built blocks");
		SiteService.Reply touching = service.here(DIM, 19, 0, 19, 12); // box starts right next to the old box
		assertTrue(touching.ok(), touching.lines().toString());
		assertFalse(service.move().ok(), "touching the current site is refused");

		SiteService.Reply p = service.here(DIM, 40, 0, 40, 12);
		assertTrue(p.ok());
		assertTrue(p.actions().contains("/t3craft site move"), "with a site, the preview offers move, not confirm");
		SiteService.Reply m = service.move();
		assertTrue(m.ok(), m.lines().toString());
		SiteService.Reply done = service.moveConfirm(token(m, "/t3craft site move confirm "), null);
		assertTrue(done.ok(), done.lines().toString());

		assertTrue(boxNow(oldArea).sameContent(oldPristine), "old place exactly restored");
		assertEquals(Leveling.PAD, world.state(40, 64, 0));
		SiteJournal j = store.loadJournal();
		assertEquals(SiteJournal.Phase.BUILT, j.phase());
		assertEquals(40, store.loadSnapshot(j.siteId()).box().minX() + SiteLimits.FOOTPRINT / 2 + SiteLimits.APRON);
	}

	@Test
	void interruptedBuildIsReportedAndResumed() throws Exception {
		holdBuilds = true;
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));
		assertEquals(SiteJournal.Phase.BUILDING, store.loadJournal().phase());
		// the game quits mid-build; next load
		holdBuilds = false;
		SiteService fresh = newService();
		String msg = fresh.onLoad();
		assertNotNull(msg);
		assertTrue(msg.contains("interrupted"));
		assertTrue(fresh.resume().ok());
		assertEquals(SiteJournal.Phase.BUILT, store.loadJournal().phase());
		assertEquals(Leveling.PAD, world.state(0, 64, 0));
		assertNull(fresh.onLoad());
	}

	@Test
	void interruptedUndoCanStillBeFinishedAndIsIdempotent() throws Exception {
		Box area = new Box(-20, 50, -20, 20, 90, 20);
		Snapshot pristine = boxNow(area);
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));
		SiteJournal built = store.loadJournal();
		// simulate a crash halfway through a restore: journal says RESTORING, half the cells restored
		Snapshot snap = store.loadSnapshot(built.siteId());
		for (int i = 0; i < snap.size() / 2; i++) {
			world.set(snap.box().xOf(i), snap.box().yOf(i), snap.box().zOf(i), snap.state(i), snap.blockEntity(i));
		}
		store.saveJournal(new SiteJournal(SiteJournal.Phase.RESTORING, built.siteId(), null, DIM, Restorer.Mode.KEEP_PLAYER_CHANGES, now));
		SiteService fresh = newService();
		assertTrue(fresh.onLoad().contains("restore"));
		SiteService.Reply r = fresh.resume();
		assertTrue(r.ok(), r.lines().toString());
		assertTrue(boxNow(area).sameContent(pristine));
		assertEquals(SiteJournal.Phase.NONE, store.loadJournal().phase());
	}

	@Test
	void refusedSiteExplainsWhy() {
		world.put(2, 65, 2, "minecraft:oak_planks", null);
		SiteService.Reply r = service.here(DIM, 0, 0, 0, 12);
		assertFalse(r.ok());
		assertTrue(String.join("\n", r.lines()).contains("oak_planks"));
		assertTrue(r.actions().isEmpty());
	}

	@Test
	void pickPreviewsTheBestSpot() {
		SiteService.Reply r = service.pick(DIM, 0, 0);
		assertTrue(r.ok(), r.lines().toString());
		assertNotNull(r.outline());
	}

	@Test
	void secondSiteNeedsMoveOrUndo() throws Exception {
		service.confirm(token(service.here(DIM, 0, 0, 0, 12), "/t3craft site confirm "));
		SiteService.Reply p = service.here(DIM, 50, 50, 50, 60);
		assertTrue(p.actions().stream().noneMatch(a -> a.contains("confirm")));
	}
}
