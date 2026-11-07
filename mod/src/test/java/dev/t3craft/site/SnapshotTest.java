package dev.t3craft.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotTest {
	@TempDir
	Path dir;

	private static final Box BOX = new Box(0, 60, 0, 7, 70, 7);

	private static FakeWorld world() {
		FakeWorld w = new FakeWorld().flat(-4, -4, 12, 12, 64);
		w.put(3, 65, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]", FakeWorld.chest("minecraft:diamond", 3));
		w.put(4, 65, 4, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", null);
		return w;
	}

	@Test
	void stateKeyRoundTripsThroughNbt() {
		String k = StateKey.of("minecraft:oak_stairs", java.util.Map.of("half", "bottom", "facing", "east"));
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom]", k);
		assertEquals(k, StateKey.fromTag(StateKey.toTag(k)));
		assertEquals("minecraft:stone", StateKey.fromTag(StateKey.toTag("minecraft:stone")));
	}

	@Test
	void captureAndRestoreIsExact() {
		FakeWorld w = world();
		Snapshot s = Snapshot.capture("site-a", "minecraft:overworld", 1L, w, BOX);
		assertEquals(BOX.volume(), s.size());
		assertEquals(1, s.blockEntityCount());
		// wreck the area, including the chest contents and outside-box marker
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				w.set(x, 64, z, "minecraft:polished_andesite", null);
			}
		}
		w.set(3, 65, 3, "minecraft:air", null);
		w.set(20, 64, 20, "minecraft:gold_block", null);
		Restorer.Result r = Restorer.restore(w, s, null, Restorer.Mode.OVERWRITE);
		assertTrue(r.restored() >= 65);
		Snapshot again = Snapshot.capture("site-b", "minecraft:overworld", 2L, w, BOX);
		assertTrue(again.sameContent(s), "restored box equals the snapshot");
		assertEquals("minecraft:gold_block", w.state(20, 64, 20), "nothing outside the box is touched");
		CompoundTag chest = w.blockEntity(3, 65, 3);
		assertNotNull(chest);
		assertEquals("minecraft:diamond", chest.getListOrEmpty("Items").getCompoundOrEmpty(0).getStringOr("id", ""));
	}

	@Test
	void nbtRoundTripKeepsContentAndDetectsTampering() throws Exception {
		Snapshot s = Snapshot.capture("site-a", "minecraft:overworld", 1L, world(), BOX);
		Snapshot back = Snapshot.fromTag(s.toTag());
		assertTrue(back.sameContent(s));
		assertEquals(s.checksum(), back.checksum());

		CompoundTag tampered = s.toTag();
		int[] cells = tampered.getIntArray("cells").orElseThrow();
		cells[5] = cells[5] == 0 ? 1 : 0;
		tampered.putIntArray("cells", cells);
		assertThrows(Snapshot.InvalidSnapshotException.class, () -> Snapshot.fromTag(tampered));

		CompoundTag shortCells = s.toTag();
		shortCells.putIntArray("cells", new int[3]);
		assertThrows(Snapshot.InvalidSnapshotException.class, () -> Snapshot.fromTag(shortCells));

		CompoundTag wrongFormat = s.toTag();
		wrongFormat.putString("format", "something");
		assertThrows(Snapshot.InvalidSnapshotException.class, () -> Snapshot.fromTag(wrongFormat));
	}

	@Test
	void storeWritesDurablyAndRefusesDamagedFiles() throws Exception {
		SiteStore store = new SiteStore(dir);
		Snapshot s = Snapshot.capture("site-a", "minecraft:overworld", 1L, world(), BOX);
		store.saveSnapshot(s);
		Path f = store.snapshotFile("site-a");
		assertTrue(Files.exists(f));
		assertFalse(Files.exists(f.resolveSibling("site-a.nbt.tmp")), "no temp file left");
		assertTrue(store.loadSnapshot("site-a").sameContent(s));
		// the file is plain gzip NBT readable by vanilla tools
		assertEquals(Snapshot.FORMAT, NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()).getStringOr("format", ""));

		assertThrows(java.io.IOException.class, () -> store.saveSnapshot(s), "never replaces an existing snapshot");

		Files.write(f, new byte[] {1, 2, 3});
		assertThrows(Exception.class, () -> store.loadSnapshot("site-a"));
	}

	@Test
	void tempLeftoversAreCleaned() throws Exception {
		SiteStore store = new SiteStore(dir);
		Files.createDirectories(store.root().resolve("snapshots"));
		Files.writeString(store.root().resolve("snapshots").resolve("x.nbt.tmp"), "half");
		assertEquals(1, store.cleanTemp());
	}

	@Test
	void conflictsAreCellsNeitherOriginalNorPlanned() {
		FakeWorld w = world();
		Snapshot s = Snapshot.capture("site-a", "minecraft:overworld", 1L, w, BOX);
		CellPlan plan = new CellPlan(BOX, new CellPlan.Origin(3, 65, 3, CellPlan.Facing.SOUTH));
		plan.set(1, 64, 1, "minecraft:stone_bricks");
		plan.set(2, 64, 2, "minecraft:stone_bricks");
		new InstantBuilder(w).submit(plan, plan.origin(), (x, y, z) -> Restorer.isOurs(w, s, plan, x, y, z), r -> {
		});
		assertTrue(Restorer.conflicts(w, s, plan).isEmpty(), "our own blocks are not conflicts");

		w.set(2, 64, 2, "minecraft:glass", null); // player replaced one of ours
		w.set(5, 66, 5, "minecraft:torch", null); // player added a block
		CompoundTag chest = FakeWorld.chest("minecraft:dirt", 1); // player changed chest contents
		w.set(3, 65, 3, w.state(3, 65, 3), chest);
		List<Restorer.Conflict> c = Restorer.conflicts(w, s, plan);
		assertEquals(3, c.size());

		Restorer.Result keep = Restorer.restore(w, s, plan, Restorer.Mode.KEEP_PLAYER_CHANGES);
		assertEquals(1, keep.restored());
		assertEquals(3, keep.keptPlayerChanges());
		assertEquals("minecraft:glass", w.state(2, 64, 2));
		assertEquals("minecraft:grass_block[snowy=false]", w.state(1, 64, 1));

		// restore is idempotent
		Restorer.Result again = Restorer.restore(w, s, plan, Restorer.Mode.KEEP_PLAYER_CHANGES);
		assertEquals(0, again.restored());

		Restorer.restore(w, s, plan, Restorer.Mode.OVERWRITE);
		assertTrue(Snapshot.capture("x", "minecraft:overworld", 0, w, BOX).sameContent(s));
	}

	@Test
	void builderSkipsCellsThePlayerChangedAfterTheSnapshot() {
		FakeWorld w = world();
		Snapshot s = Snapshot.capture("site-a", "minecraft:overworld", 1L, w, BOX);
		w.set(1, 64, 1, "minecraft:diamond_block", null);
		CellPlan plan = new CellPlan(BOX, new CellPlan.Origin(3, 65, 3, CellPlan.Facing.SOUTH));
		plan.set(1, 64, 1, "minecraft:stone_bricks");
		plan.set(2, 64, 2, "minecraft:stone_bricks");
		SiteBuilder.Report[] out = new SiteBuilder.Report[1];
		new InstantBuilder(w).submit(plan, plan.origin(), (x, y, z) -> Restorer.isOurs(w, s, plan, x, y, z), r -> out[0] = r);
		assertEquals(1, out[0].placed());
		assertEquals(1, out[0].skipped());
		assertEquals("minecraft:diamond_block", w.state(1, 64, 1));
		// re-submitting is a no-op
		new InstantBuilder(w).submit(plan, plan.origin(), (x, y, z) -> Restorer.isOurs(w, s, plan, x, y, z), r -> out[0] = r);
		assertEquals(0, out[0].placed());
		assertEquals(1, out[0].alreadyThere());
	}

	@Test
	void planRoundTrips() throws Exception {
		CellPlan plan = new CellPlan(BOX, new CellPlan.Origin(3, 65, 3, CellPlan.Facing.WEST));
		plan.set(1, 64, 1, "minecraft:stone_bricks");
		plan.set(1, 65, 1, StateKey.AIR);
		CellPlan back = CellPlan.fromTag(plan.toTag());
		assertTrue(back.sameContent(plan));
		assertEquals(2, back.count());
	}

	@Test
	void journalRoundTrips() throws Exception {
		CellPlan.Origin o = new CellPlan.Origin(1, 65, 2, CellPlan.Facing.EAST);
		Box box = new Box(-9, 60, -9, 9, 76, 9);
		SiteModule extra = new SiteModule("project-a", "desks=4", new Rect(8, -7, 22, 7));
		SiteJournal next = new SiteJournal(SiteJournal.Phase.BUILDING, "minecraft:overworld", o, SiteRequest.GROWABLE, box,
				List.of(new Expansion("site-c", SiteRequest.GROWABLE.base(), box)), null, 0, null, List.of(extra), 4L);
		SiteJournal j = new SiteJournal(SiteJournal.Phase.RESTORING, "minecraft:overworld", o, SiteRequest.GROWABLE, box,
				List.of(new Expansion("site-a", SiteRequest.GROWABLE.base(), box), new Expansion("site-b", extra, box)), Restorer.Mode.OVERWRITE, 1, next,
				List.of(), 5L);
		assertEquals(j, SiteJournal.fromTag(j.toTag()));
		assertEquals("site-a", j.siteId());
		assertEquals("site-b", j.top().key());
		CompoundTag bad = j.toTag();
		bad.remove("stack");
		assertThrows(java.io.IOException.class, () -> SiteJournal.fromTag(bad));
		CompoundTag old = j.toTag();
		old.putInt("version", 1);
		assertThrows(java.io.IOException.class, () -> SiteJournal.fromTag(old), "a v1 journal is refused, never guessed");
	}
}
