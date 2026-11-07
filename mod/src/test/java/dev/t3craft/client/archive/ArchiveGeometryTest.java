package dev.t3craft.client.archive;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArchiveGeometryTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void oneDistinctBasementCellPerSlot() {
		List<Anchor> slots = ArchiveGeometry.slots();
		assertEquals(HqLayout.ARCHIVE_CAPACITY, slots.size());
		Set<BlockPos> cells = new HashSet<>();
		for (Anchor a : slots) {
			assertTrue(cells.add(a.block()), "unique cell " + a);
			assertTrue(a.block().getY() < 0, "in the basement " + a);
		}
	}

	/** A thread's label goes where the generator puts that thread's archived computer. */
	@Test
	void matchesGeneratorAnchorsForRealThreads() {
		HqInput.Builder b = HqInput.builder();
		for (int i = 0; i < 12; i++) {
			b.thread("vps:p" + (i % 3), "p", "vps", "vps", "vps:t" + i, i % 4 != 0);
		}
		HqLayout layout = HqLayout.plan(b.build());
		BuildPlan plan = HqPlans.generate(layout);
		for (HqLayout.Archived a : layout.archive()) {
			Anchor real = plan.anchor(AnchorNames.archive(a.threadId()));
			Anchor ours = ArchiveGeometry.slots().get(a.slot());
			assertEquals(real.block(), ours.block());
			assertEquals(real.facing(), ours.facing());
			assertEquals(real.x(), ours.x(), 1e-9);
			assertEquals(real.z(), ours.z(), 1e-9);
		}
	}

	/** The catalog stands on the basement floor, in air, with air in front, in every plan. */
	@Test
	void catalogCellIsFree() {
		for (HqLayout layout : List.of(HqLayout.EMPTY, ArchiveSlots.assign(ArchiveSlotsTest.entries(HqLayout.ARCHIVE_CAPACITY), null))) {
			BuildPlan plan = HqPlans.generate(layout);
			BlockPos c = ArchiveGeometry.CATALOG_REL;
			assertAir(plan, c);
			assertAir(plan, c.above());
			assertAir(plan, c.relative(ArchiveGeometry.CATALOG_FACING));
			assertAir(plan, c.relative(ArchiveGeometry.CATALOG_FACING).above());
			Placement floor = plan.at(c.below());
			assertNotNull(floor);
			assertFalse(floor.state().isAir(), "floor under the catalog");
			assertEquals("archive", plan.groupAt(c));
		}
	}

	private static void assertAir(BuildPlan plan, BlockPos p) {
		Placement at = plan.at(p);
		assertTrue(at == null || at.state().isAir(), "air at " + p + ": " + at);
	}

	@Test
	void worldTransformFollowsTheSite() {
		HqSite site = new HqSite(new BlockPos(100, 70, -20), Rotation.CLOCKWISE_90);
		ArchiveGeometry.WorldSlot w = ArchiveGeometry.world(site, 0);
		Anchor rel = ArchiveGeometry.slots().get(0);
		assertEquals(site.cell(rel.block()), w.cell());
		assertEquals(site.direction(rel.facing()), w.face().facing());
		ArchiveGeometry.WorldCatalog cat = ArchiveGeometry.catalog(site);
		assertEquals(site.cell(ArchiveGeometry.CATALOG_REL), cat.cell());
		assertEquals(Rotation.CLOCKWISE_90.rotate(Direction.WEST), cat.facing());
	}
}
