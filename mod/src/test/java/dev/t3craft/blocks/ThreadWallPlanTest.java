package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.WallPlans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The HQ plan reserves the thread wall through anchors; the blocks module swaps the anchor cell. */
class ThreadWallPlanTest {
	@BeforeAll
	static void boot() {
		BlocksTestSupport.boot(); // before touching Blocks
		WALL = Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
		ANCHOR = Blocks.LOOM.defaultBlockState();
	}

	private static BlockState WALL;
	/** Stand-in for the module's anchor block (the registry is frozen in unit tests). */
	private static BlockState ANCHOR;

	private static HqBlockFixtures fixtures(boolean withBoard) {
		return new HqBlockFixtures(new HqBlockFixtures.States() {
			@Override
			public BlockState monitor(Direction facing) {
				return Blocks.OBSERVER.defaultBlockState();
			}

			@Override
			public BlockState lamp() {
				return Blocks.REDSTONE_LAMP.defaultBlockState();
			}

			@Override
			public BlockState boardLamp() {
				return Blocks.REDSTONE_LAMP.defaultBlockState();
			}

			@Override
			public BlockState threadBoard(Direction facing) {
				return withBoard ? ANCHOR : null;
			}
		});
	}

	@Test
	void sizeFollowsTheTwoAnchors() {
		// facing south: the viewer stands south looking north, their right is east (+X)
		BuildPlan p = WallPlans.wall(Direction.SOUTH, new BlockPos(0, 1, 0), new BlockPos(4, 3, 0), new BlockPos(4, 3, 0), WALL);
		HqBindings.ThreadWall w = HqBindings.threadWall(p);
		assertNotNull(w);
		assertEquals(new BlockPos(0, 1, 0), w.rel());
		assertEquals(Direction.SOUTH, w.facing());
		assertEquals(5, w.width());
		assertEquals(3, w.height());
	}

	@Test
	void sizeForEveryFacing() {
		for (Direction f : Direction.Plane.HORIZONTAL) {
			Direction right = f.getCounterClockWise();
			BlockPos end = BlockPos.ZERO.relative(right, 6).above(2);
			assertEquals(7, HqBindings.wallSize(BlockPos.ZERO, end, f)[0], f.toString());
			assertEquals(3, HqBindings.wallSize(BlockPos.ZERO, end, f)[1], f.toString());
			// end to the viewer's left, or off the plane: no size (config decides)
			assertEquals(0, HqBindings.wallSize(BlockPos.ZERO, BlockPos.ZERO.relative(right.getOpposite(), 2), f)[0]);
			assertEquals(0, HqBindings.wallSize(BlockPos.ZERO, end.relative(f), f)[0]);
		}
		assertEquals(ThreadBoardBlockEntity.MAX_WIDTH, HqBindings.wallSize(BlockPos.ZERO, new BlockPos(-40, 0, 0), Direction.NORTH)[0]);
	}

	@Test
	void withoutTheEndAnchorTheConfigDecides() {
		BuildPlan p = WallPlans.wall(Direction.NORTH, BlockPos.ZERO, new BlockPos(0, 2, 0), null, WALL);
		HqBindings.ThreadWall w = HqBindings.threadWall(p);
		assertNotNull(w);
		assertEquals(0, w.width());
		assertTrue(HqBlockFixtures.wallData(w).isEmpty(), "no size data → the anchor uses the config");
	}

	@Test
	void noAnchorNoWall() {
		BuildPlan p = WallPlans.noWall(BlockPos.ZERO, new BlockPos(4, 2, 0), WALL);
		assertNull(HqBindings.threadWall(p));
		assertSame(p, fixtures(true).apply(p), "nothing to swap: same plan");
	}

	@Test
	void fixturesSwapOnlyTheAnchorCell() {
		BuildPlan p = WallPlans.wall(Direction.SOUTH, new BlockPos(0, 1, 0), new BlockPos(4, 3, 0), new BlockPos(4, 3, 0), WALL);
		BuildPlan q = fixtures(true).apply(p);
		Placement anchor = q.at(new BlockPos(0, 1, 0));
		assertNotNull(anchor);
		assertEquals(ANCHOR, anchor.state());
		assertNotNull(anchor.be());
		assertEquals(5, anchor.be().getIntOr(ThreadBoardBlockEntity.WIDTH, 0));
		assertEquals(3, anchor.be().getIntOr(ThreadBoardBlockEntity.HEIGHT, 0));
		int swapped = 0;
		for (Placement x : q.placements()) {
			if (x.state() == ANCHOR) {
				swapped++;
			} else {
				assertEquals(p.at(x.rel()).state(), x.state(), "every other cell (the rest of the wall too) stays the plan's block");
			}
		}
		assertEquals(1, swapped);
		assertEquals(p.size(), q.size(), "positions kept");
	}

	@Test
	void standInsWithoutABoardLeaveThePlanAlone() {
		BuildPlan p = WallPlans.wall(Direction.SOUTH, new BlockPos(0, 1, 0), new BlockPos(4, 3, 0), null, WALL);
		assertSame(p, fixtures(false).apply(p));
	}

	@Test
	void vanillaHqIgnoresTheAnchor() {
		BuildPlan p = WallPlans.wall(Direction.SOUTH, new BlockPos(0, 1, 0), new BlockPos(4, 3, 0), null, WALL);
		assertSame(p, HqFixtures.VANILLA.apply(p), "without the blocks module the wall keeps the plan's blocks");
		assertFalse(HqBindings.of(p).stream().anyMatch(f -> f.rel().equals(new BlockPos(0, 1, 0))), "not a bound fixture");
	}
}
