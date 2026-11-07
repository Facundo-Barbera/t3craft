package dev.t3craft.blocks;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Room plaque: a cherry-framed ink plate hung on the wall behind it, its front towards {@link #FACING}
 * (same convention as a wall sign). The client's {@code PlaqueRenderer} draws the
 * {@link PlaqueBlockEntity}'s text on the plate. No collision, like a sign.
 */
public final class KitPlaqueBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Depth of the plate's front face behind the block's north face (model: z 14..16). */
	public static final float FACE_DEPTH = 14f / 16f;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(1, 3, 14, 15, 13, 16));

	private final Supplier<BlockEntityType<PlaqueBlockEntity>> type;

	public KitPlaqueBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<PlaqueBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		Direction front = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, front);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PlaqueBlockEntity(type.get(), pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
