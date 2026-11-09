package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Small helpers shared by the three terrace houses ({@code house_terrace_*}): block-state factories in
 * local terms, a deterministic texture mixer and {@link Links}, which sets the sides of fences, walls
 * and panes by hand (the plan keeps states exactly as given).
 */
final class TerraceKit {
	private TerraceKit() {
	}

	/** A stair; {@code facing} is the side its tall back is on (local), {@code top} makes it upside down. */
	static BlockState stair(Block blk, Direction facing, boolean top) {
		return stairs(blk, facing).setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slabTop(Block blk) {
		return blk.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState slabBottom(Block blk) {
		return blk.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	static BlockState log(Block blk, Direction.Axis axis) {
		return blk.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** A trapdoor: {@code facing} is the side it faces; an open one lies against the wall on the opposite side. */
	static BlockState trap(Block blk, Direction facing, boolean open, boolean top) {
		return blk.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.OPEN, open)
				.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	/** One half of a door whose panel sits at the back (recessed) when {@code facing} is NORTH. */
	static BlockState door(Block blk, Direction facing, boolean lower) {
		return blk.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	static BlockState hanging(Block lantern) {
		return lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	/** A glass pane running along u (east-west) or along v. */
	static BlockState pane(Block glass, boolean alongU) {
		BlockState g = glass.defaultBlockState();
		return alongU ? g.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true)
				: g.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
	}

	/** A vine attached to the wall on {@code side} (local). */
	static BlockState vine(Direction side) {
		BlockState v = Blocks.VINE.defaultBlockState();
		return switch (side) {
			case NORTH -> v.setValue(BlockStateProperties.NORTH, true);
			case SOUTH -> v.setValue(BlockStateProperties.SOUTH, true);
			case EAST -> v.setValue(BlockStateProperties.EAST, true);
			default -> v.setValue(BlockStateProperties.WEST, true);
		};
	}

	/** [palette] a deterministic texture mix: {@code blocks[hash]}; repeat an entry to weigh it. */
	static BlockState mix(Plot p, int u, int y, int v, Block... blocks) {
		return b(blocks[p.hash(u, y, v, blocks.length)]);
	}

	// ------------------------------------------------------------------ hand-linked fences, walls and panes

	/**
	 * A set of (u, v) cells at one height whose sides must link: members are emitted, partners are only
	 * linked to (solid blocks next to the run).
	 */
	static final class Links {
		private final Set<Long> members = new HashSet<>();
		private final Set<Long> partners = new HashSet<>();

		private static long key(int u, int v) {
			return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
		}

		Links add(int u, int v) {
			members.add(key(u, v));
			return this;
		}

		Links partner(int u, int v) {
			partners.add(key(u, v));
			return this;
		}

		boolean has(int u, int v) {
			return members.contains(key(u, v));
		}

		private boolean linked(int u, int v) {
			return members.contains(key(u, v)) || partners.contains(key(u, v));
		}

		BlockState fence(Block blk, int u, int v) {
			return blk.defaultBlockState().setValue(BlockStateProperties.NORTH, linked(u, v - 1)).setValue(BlockStateProperties.SOUTH, linked(u, v + 1))
					.setValue(BlockStateProperties.WEST, linked(u - 1, v)).setValue(BlockStateProperties.EAST, linked(u + 1, v));
		}

		BlockState wall(Block blk, int u, int v) {
			boolean n = linked(u, v - 1);
			boolean s = linked(u, v + 1);
			boolean w = linked(u - 1, v);
			boolean e = linked(u + 1, v);
			boolean straight = (n && s && !e && !w) || (e && w && !n && !s);
			return blk.defaultBlockState().setValue(BlockStateProperties.UP, !straight)
					.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE).setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
					.setValue(BlockStateProperties.WEST_WALL, w ? WallSide.LOW : WallSide.NONE).setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE);
		}

		/** Emits every member at height {@code y} as a linked fence. */
		void emitFences(CitadelCanvas c, Plot p, int y, Block blk) {
			for (long k : members) {
				int u = (int) (k >> 32);
				int v = (int) k;
				p.set(c, u, y, v, fence(blk, u, v));
			}
		}

		/** Emits every member at height {@code y} as a linked wall. */
		void emitWalls(CitadelCanvas c, Plot p, int y, Block blk) {
			for (long k : members) {
				int u = (int) (k >> 32);
				int v = (int) k;
				p.set(c, u, y, v, wall(blk, u, v));
			}
		}
	}
}
