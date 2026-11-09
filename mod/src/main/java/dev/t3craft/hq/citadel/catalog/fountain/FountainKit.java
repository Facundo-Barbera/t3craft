package dev.t3craft.hq.citadel.catalog.fountain;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Shared bits of the fountain variants: a pen centred on the middle of the plot (cells are written as
 * offsets {@code dx} (east) / {@code dz} (south, towards the back) from the centre, so the round shapes are
 * easy), disk and octagon tests, water states and stair helpers. Not a variant itself.
 */
final class FountainKit {
	/** A still water source. */
	static final BlockState WATER = Blocks.WATER.defaultBlockState();
	/** Water falling from above (level 8): what a cascade is made of. */
	static final BlockState FALL = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
	/** A water block flowing sideways off a lip. */
	static final BlockState LIP = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);

	final CitadelCanvas c;
	final Plot p;
	/** Centre of the figure in local plot coordinates. */
	final int cu;
	final int cv;

	FountainKit(CitadelCanvas c, Plot p) {
		this.c = c;
		this.p = p;
		this.cu = (p.w() - 1) / 2;
		this.cv = (p.d() - 1) / 2;
	}

	/** Sets the block at offset {@code (dx, y, dz)} from the centre. */
	void set(int dx, int y, int dz, BlockState s) {
		p.set(c, cu + dx, y, cv + dz, s);
	}

	/** Like {@link #set} on a canvas layer ({@link CitadelCanvas#DECOR}, {@link CitadelCanvas#HANGING}). */
	void put(int dx, int y, int dz, BlockState s, int layer) {
		p.put(c, cu + dx, y, cv + dz, s, layer);
	}

	/** Deterministic 0..n-1 for the cell at offset {@code (dx, y, dz)}. */
	int hash(int dx, int y, int dz, int n) {
		return p.hash(cu + dx, y, cv + dz, n);
	}

	/** One of {@code options}, chosen by the hash of the cell, weighted by repeating entries. */
	BlockState pick(int dx, int y, int dz, BlockState... options) {
		return options[hash(dx, y, dz, options.length)];
	}

	/** A disc of radius {@code r} (r = 0 is the centre cell, 1 a 3×3, 2 a rounded 5×5…). */
	static boolean disk(int dx, int dz, int r) {
		return r >= 0 && dx * dx + dz * dz <= r * r + r;
	}

	/** An octagon of radius {@code r}: the square with its corners cut. */
	static boolean oct(int dx, int dz, int r) {
		return r >= 0 && Math.abs(dx) <= r && Math.abs(dz) <= r && Math.abs(dx) + Math.abs(dz) <= (3 * r) / 2;
	}

	/** The local direction from the cell {@code (dx, dz)} towards the centre (dominant axis). */
	static Direction toward(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	/** The local direction from the centre towards the cell {@code (dx, dz)}. */
	static Direction away(int dx, int dz) {
		return toward(dx, dz).getOpposite();
	}

	static BlockState stair(net.minecraft.world.level.block.Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	/** An upside-down stair (the underside of a bowl). */
	static BlockState stairTop(net.minecraft.world.level.block.Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
	}

	static BlockState slabTop(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState pillar(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	}
}
