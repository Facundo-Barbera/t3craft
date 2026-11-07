package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Which monitors merge into one screen, its origin and extent, and how it follows placing/removing blocks. */
class MonitorWallTest {
	/** A tiny world of monitors. */
	static final class World implements MonitorWall.Lookup {
		final Map<BlockPos, MonitorWall.Cell> cells = new HashMap<>();
		/** The flags the server would store (refresh results). */
		final Map<BlockPos, ScreenRect> screens = new HashMap<>();

		World put(BlockPos p, Direction facing, String binding) {
			cells.put(p.immutable(), new MonitorWall.Cell(facing, binding));
			refreshAround(p);
			return this;
		}

		World put(BlockPos p, Direction facing) {
			return put(p, facing, "");
		}

		World remove(BlockPos p) {
			cells.remove(p);
			screens.remove(p);
			refreshAround(p);
			return this;
		}

		/** What MonitorBlock.tick does: the changed cell and its six neighbours. */
		void refreshAround(BlockPos p) {
			List<BlockPos> seeds = new ArrayList<>();
			seeds.add(p);
			for (Direction d : Direction.values()) {
				seeds.add(p.relative(d));
			}
			screens.putAll(MonitorWall.resolve(this, seeds));
		}

		@Override
		public MonitorWall.Cell at(BlockPos pos) {
			return cells.get(pos);
		}
	}

	static final BlockPos O = new BlockPos(10, 64, 10);

	/** A north-facing wall: columns go west (the viewer's right), rows go down. */
	static BlockPos nc(int col, int row) {
		return O.west(col).below(row);
	}

	static World wall(int w, int h, Direction facing) {
		World world = new World();
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				world.put(O.relative(facing.getCounterClockWise(), c).below(r), facing);
			}
		}
		return world;
	}

	@Test
	void aFullRectangleIsOneScreen() {
		World w = wall(3, 2, Direction.NORTH);
		ScreenRect e = MonitorWall.extentOf(w, nc(1, 1));
		assertEquals(new ScreenRect(O, Direction.NORTH, 3, 2), e);
		for (int r = 0; r < 2; r++) {
			for (int c = 0; c < 3; c++) {
				assertEquals(e, MonitorWall.extentOf(w, nc(c, r)), "every cell sees the same screen");
				assertEquals(e, w.screens.get(nc(c, r)));
			}
		}
		assertEquals(nc(0, 1), e.bottomLeft(), "the renderer draws from the bottom-left cell");
		assertTrue(e.isDrawOrigin(nc(0, 1)));
		assertEquals(new ScreenRect.Sides(false, true, false, true), e.sides(nc(0, 0)), "top-left: bezel up and left");
		assertEquals(new ScreenRect.Sides(true, false, true, true), e.sides(nc(1, 1)), "bottom middle");
		assertEquals(new ScreenRect.Sides(true, false, true, false), e.sides(nc(2, 1)), "bottom-right");
		assertEquals(6, e.cells().size());
	}

	@Test
	void columnsFollowTheViewersRightForEveryFacing() {
		for (Direction f : Direction.Plane.HORIZONTAL) {
			World w = wall(2, 1, f);
			ScreenRect e = MonitorWall.extentOf(w, O);
			assertEquals(2, e.width(), f.toString());
			assertEquals(O, e.origin(), "left cell is the origin facing " + f);
			assertEquals(1, e.col(O.relative(f.getCounterClockWise())));
		}
		// east-facing: the viewer stands east looking west, their right is north (-Z)
		assertEquals(Direction.NORTH, new ScreenRect(O, Direction.EAST, 1, 1).right());
	}

	@Test
	void anLShapeDoesNotMerge() {
		World w = new World().put(nc(0, 0), Direction.NORTH).put(nc(1, 0), Direction.NORTH).put(nc(0, 1), Direction.NORTH);
		for (BlockPos p : List.of(nc(0, 0), nc(1, 0), nc(0, 1))) {
			ScreenRect e = MonitorWall.extentOf(w, p);
			assertFalse(e.isMulti(), "L shape: " + p);
			assertEquals(ScreenRect.Sides.NONE, e.sides(p));
		}
	}

	@Test
	void aHoleDoesNotMerge() {
		World w = new World();
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				if (r != 1 || c != 1) {
					w.put(nc(c, r), Direction.NORTH);
				}
			}
		}
		assertFalse(MonitorWall.extentOf(w, nc(0, 0)).isMulti());
		// filling the hole merges the 3×3
		w.put(nc(1, 1), Direction.NORTH);
		assertEquals(new ScreenRect(O, Direction.NORTH, 3, 3), w.screens.get(nc(2, 2)));
	}

	@Test
	void aGapSeparatesScreens() {
		World w = new World().put(nc(0, 0), Direction.NORTH).put(nc(1, 0), Direction.NORTH)
				.put(nc(3, 0), Direction.NORTH).put(nc(4, 0), Direction.NORTH);
		assertEquals(new ScreenRect(nc(0, 0), Direction.NORTH, 2, 1), MonitorWall.extentOf(w, nc(1, 0)));
		assertEquals(new ScreenRect(nc(3, 0), Direction.NORTH, 2, 1), MonitorWall.extentOf(w, nc(4, 0)));
	}

	@Test
	void mixedOrientationsDoNotMerge() {
		World w = new World().put(nc(0, 0), Direction.NORTH).put(nc(1, 0), Direction.EAST).put(nc(2, 0), Direction.NORTH);
		assertFalse(MonitorWall.extentOf(w, nc(0, 0)).isMulti());
		assertFalse(MonitorWall.extentOf(w, nc(1, 0)).isMulti());
		assertFalse(MonitorWall.extentOf(w, nc(2, 0)).isMulti());
		// a 2×1 next to a turned monitor: the pair still merges, the turned one stays alone
		World v = new World().put(nc(0, 0), Direction.NORTH).put(nc(1, 0), Direction.NORTH).put(nc(2, 0), Direction.SOUTH);
		assertEquals(2, MonitorWall.extentOf(v, nc(0, 0)).width());
		assertFalse(MonitorWall.extentOf(v, nc(2, 0)).isMulti());
	}

	@Test
	void monitorsBehindEachOtherDoNotMerge() {
		// same facing, but one block apart along the facing axis: different planes
		World w = new World().put(O, Direction.NORTH).put(O.south(), Direction.NORTH);
		assertFalse(MonitorWall.extentOf(w, O).isMulti());
	}

	@Test
	void bindingsMustAgree() {
		World w = new World().put(nc(0, 0), Direction.NORTH, "thread:a").put(nc(1, 0), Direction.NORTH, "thread:b");
		assertFalse(MonitorWall.extentOf(w, nc(0, 0)).isMulti(), "two desks bound to two threads stay apart");
		World v = new World().put(nc(0, 0), Direction.NORTH, "project:e:p").put(nc(1, 0), Direction.NORTH, "")
				.put(nc(2, 0), Direction.NORTH, "project:e:p");
		assertEquals(3, MonitorWall.extentOf(v, nc(1, 0)).width(), "unbound cells join a bound screen");
	}

	@Test
	void tooLargeDoesNotMerge() {
		World w = wall(MonitorWall.MAX_WIDTH + 1, 1, Direction.NORTH);
		assertFalse(MonitorWall.extentOf(w, O).isMulti());
		World v = wall(MonitorWall.MAX_WIDTH, MonitorWall.MAX_HEIGHT, Direction.NORTH);
		assertEquals(MonitorWall.MAX_WIDTH * MonitorWall.MAX_HEIGHT, MonitorWall.extentOf(v, O).cellCount());
	}

	@Test
	void placingAndRemovingUpdatesTheWholeGroup() {
		World w = new World().put(nc(0, 0), Direction.NORTH);
		assertFalse(w.screens.get(nc(0, 0)).isMulti());
		w.put(nc(1, 0), Direction.NORTH);
		assertEquals(new ScreenRect(O, Direction.NORTH, 2, 1), w.screens.get(nc(0, 0)), "placing a neighbour merges");
		w.put(nc(0, 1), Direction.NORTH);
		assertFalse(w.screens.get(nc(0, 0)).isMulti(), "an L splits the screen again");
		assertFalse(w.screens.get(nc(1, 0)).isMulti());
		w.put(nc(1, 1), Direction.NORTH);
		ScreenRect two = new ScreenRect(O, Direction.NORTH, 2, 2);
		for (BlockPos p : List.of(nc(0, 0), nc(1, 0), nc(0, 1), nc(1, 1))) {
			assertEquals(two, w.screens.get(p), "completing the square merges every cell: " + p);
		}
		w.remove(nc(1, 1));
		for (BlockPos p : List.of(nc(0, 0), nc(1, 0), nc(0, 1))) {
			assertFalse(w.screens.get(p).isMulti(), "removing a corner leaves an L: " + p);
		}
		w.remove(nc(0, 1));
		assertEquals(new ScreenRect(O, Direction.NORTH, 2, 1), w.screens.get(nc(1, 0)), "back to a 2×1");
	}

	@Test
	void noMonitorNoScreen() {
		assertNull(MonitorWall.extentOf(new World(), O));
		assertTrue(MonitorWall.resolve(new World(), List.of(O)).isEmpty());
	}

	@Test
	void rectGeometry() {
		ScreenRect e = new ScreenRect(O, Direction.SOUTH, 3, 2);
		// south-facing: the viewer looks north, their right is east
		assertEquals(O.east(2).below(1), e.cell(2, 1));
		assertTrue(e.contains(O.east(2).below(1)));
		assertFalse(e.contains(O.east(3)));
		assertFalse(e.contains(O.north()), "other plane");
		assertEquals(O.below(1), e.bottomLeft());
	}
}
