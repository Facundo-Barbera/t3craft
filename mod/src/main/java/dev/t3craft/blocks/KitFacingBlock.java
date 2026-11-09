package dev.t3craft.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A kit block with a horizontal {@link #FACING} and a shape drawn for the north-facing model, turned
 * with the state (standing desk, railing). Placed facing the player, like a furnace.
 */
public class KitFacingBlock extends HorizontalDirectionalBlock {
	private final Map<Direction, VoxelShape> shapes;

	public KitFacingBlock(BlockBehaviour.Properties properties, VoxelShape northShape) {
		super(properties);
		this.shapes = Shapes.rotateHorizontal(northShape);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	/** Standing desk, front to the north: top slab, four legs, modesty panel at the back. */
	public static VoxelShape deskShape() {
		return Shapes.or(Block.box(0, 12, 0, 16, 16, 16),
				Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3),
				Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15),
				Block.box(3, 3, 13, 13, 12, 14));
	}

	/** Railing on the north edge of the cell (the cherry railing is now {@link KitRailingBlock}; kept for callers). */
	public static VoxelShape railingShape() {
		return Block.box(0, 0, 0, 16, 16, 3);
	}
}
