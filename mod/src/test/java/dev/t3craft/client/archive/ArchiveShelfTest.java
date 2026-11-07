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

class ArchiveShelfTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static List<BuildPlan> plans() {
		HqInput.Builder b = HqInput.builder();
		for (int i = 0; i < 20; i++) {
			b.thread("vps:p" + (i % 4), "p" + (i % 4), "vps", "vps", "vps:t" + i, i % 5 == 4);
		}
		return List.of(HqPlans.generate(HqLayout.EMPTY), HqPlans.generate(HqLayout.plan(b.build())));
	}

	/** The fallback shelf is inside the hub, next to the board, in cells free in every plan, with air in front. */
	@Test
	void fallbackCellsAreFreeAndFaceTheHub() {
		List<Anchor> slots = ArchiveShelf.FALLBACK.slots();
		assertEquals(22, ArchiveShelf.FALLBACK.capacity());
		Set<BlockPos> cells = new HashSet<>();
		for (Anchor a : slots) {
			assertTrue(cells.add(a.block()), "unique " + a);
			assertEquals(Direction.SOUTH, a.facing());
			assertTrue(Math.abs(a.block().getX()) <= 7 && a.block().getY() >= 1 && a.block().getY() <= 4, "inside the hub " + a);
		}
		for (BuildPlan plan : plans()) {
			Anchor board = plan.anchor(AnchorNames.BOARD);
			assertNotNull(board);
			for (Anchor a : slots) {
				assertAir(plan, a.block());
				assertAir(plan, a.block().relative(a.facing()));
				assertEquals(board.block().getZ(), a.block().getZ(), "in line with the board " + a);
				assertEquals("hub", plan.groupAt(a.block()));
			}
			// the floor under the bottom row is solid
			for (Anchor a : slots) {
				if (a.block().getY() == 1) {
					Placement floor = plan.at(a.block().below());
					assertNotNull(floor);
					assertFalse(floor.state().isAir());
				}
			}
		}
		assertEquals(new BlockPos(-5, 2, -4), slots.get(0).block(), "slot 0: eye level, nearest the board");
	}

	private static void assertAir(BuildPlan plan, BlockPos p) {
		Placement at = plan.at(p);
		assertTrue(at == null || at.state().isAir(), "air at " + p + ": " + at);
	}

	/** fix/hq-archive's anchors are read by name, in order, until the first missing one. */
	@Test
	void readsShelfAnchorsFromAPlan() {
		BuildPlan plan = plans().get(1);
		// stand-in for archiveShelfSlot(i): the plan's board slots are a named, numbered series
		ArchiveShelf shelf = ArchiveShelf.fromPlan(plan, i -> "board_slot_" + i);
		assertTrue(shelf.capacity() > 0);
		for (int i = 0; i < shelf.capacity(); i++) {
			assertEquals(plan.anchor("board_slot_" + i), shelf.slots().get(i));
		}
		assertEquals(0, ArchiveShelf.fromPlan(plan, i -> "nope_" + i).capacity());
	}

	@Test
	void worldTransformAndHeader() {
		HqSite site = new HqSite(new BlockPos(100, 70, -20), Rotation.CLOCKWISE_90);
		ArchiveShelf.WorldSlot w = ArchiveShelf.world(ArchiveShelf.FALLBACK, site, 3);
		Anchor rel = ArchiveShelf.FALLBACK.slots().get(3);
		assertEquals(site.cell(rel.block()), w.cell());
		assertEquals(site.direction(Direction.SOUTH), w.face().facing());

		Anchor head = ArchiveShelf.header(ArchiveShelf.FALLBACK, new HqSite(BlockPos.ZERO, Rotation.NONE));
		assertEquals(0.5, head.x(), 1e-9, "centred between the wings (over the board)");
		assertEquals(5.15, head.y(), 1e-9, "just above the top row");
		assertEquals(-3.0, head.z(), 1e-9, "on the faces' plane");
		assertEquals(Direction.SOUTH, head.facing());
		assertNull(ArchiveShelf.header(ArchiveShelf.of(List.of()), site));
	}
}
