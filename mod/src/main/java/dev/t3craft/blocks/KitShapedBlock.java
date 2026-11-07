package dev.t3craft.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A kit block without state whose shape is not a full cube (column cap, planter, stone lantern). */
public final class KitShapedBlock extends Block {
	private final VoxelShape shape;

	public KitShapedBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
		super(properties);
		this.shape = shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	/** Column cap: a neck the width of a post, flaring into a full-width crown. */
	public static VoxelShape capShape() {
		return Shapes.or(Block.box(2, 0, 2, 14, 8, 14), Block.box(0, 8, 0, 16, 16, 16));
	}

	/** Potted cherry: clay pot, trunk and a blossom crown. */
	public static VoxelShape planterShape() {
		return Shapes.or(Block.box(4, 0, 4, 12, 6, 12), Block.box(7, 6, 7, 9, 10, 9), Block.box(2, 9, 2, 14, 16, 14));
	}

	/** Stone lantern (tōrō): base, post, light box, roof and knob. */
	public static VoxelShape stoneLanternShape() {
		return Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(6, 2, 6, 10, 6, 10), Block.box(4, 6, 4, 12, 11, 12),
				Block.box(2, 11, 2, 14, 13, 14), Block.box(6, 13, 6, 10, 16, 10));
	}
}
