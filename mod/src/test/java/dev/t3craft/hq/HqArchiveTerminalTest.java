package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The plan reserves the archive terminal: an {@code archiveTerminal} anchor on a free hub cell, in every plan. */
class HqArchiveTerminalTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	private static boolean air(BuildPlan p, BlockPos rel) {
		var at = p.at(rel);
		return at == null || at.state().isAir();
	}

	@Test
	void everyPlanPublishesTheTerminalInTheHub() {
		for (int projects : new int[] {0, 1, 3, 6}) {
			BuildPlan p = HqGenerator.plan(HqTestSupport.input(projects, 4), null).plan();
			Anchor a = p.anchor(AnchorNames.ARCHIVE_TERMINAL);
			assertNotNull(a, projects + " projects");
			BlockPos cell = a.block();
			assertEquals(Direction.WEST, a.facing(), "screen faces into the hub");
			assertEquals("hub", p.groupAt(cell));
			var placed = p.at(cell);
			assertNotNull(placed);
			assertEquals(Blocks.LECTERN, placed.state().getBlock(), "vanilla stand-in");
			assertEquals(Direction.WEST, placed.state().getValue(BlockStateProperties.HORIZONTAL_FACING));
			// where the player stands to use it: free, on the hub floor, not the shelf or a bench
			BlockPos stand = cell.relative(a.facing());
			assertTrue(air(p, stand), "free in front of the terminal");
			assertTrue(air(p, stand.above()));
			assertTrue(!air(p, stand.below()), "floor under the player");
			assertTrue(p.anchors().values().stream().noneMatch(o -> o != a && o.block().equals(cell)), "no other anchor on its cell");
		}
	}
}
