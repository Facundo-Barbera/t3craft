package dev.t3craft.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The glow strip: a thin bar of light (16 long, 3 high, 2 deep) along a beam, a skirting or the edge of a step.
 * It hugs one edge of its cell: {@link #FACING} is where it looks (the wall it hugs is behind it, as with a plaque
 * or a button) and {@link #HALF} is the bottom or the top of the cell. Light 12, no collision (a strip on a stair
 * tread or a floor never snags anybody), no block entity.
 *
 * <p>Strips connect in line by geometry: every strip is a full-length bar whose two ends are plain faces, and the faces
 * where two strips meet are hidden inside the bar, so a run of strips reads as one continuous strip and only the
 * ends of the run show a cap. No connection state, so nothing to resolve in a plan (and nothing for
 * {@code PlanConnect}). {@link KitGlow#TONE} is warm (paper/amber) or cool (T3 cyan); an empty-handed click flips it.
 */
public final class KitGlowStripBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
	public static final EnumProperty<KitGlow.Tone> TONE = KitGlow.TONE;

	private final Map<Direction, VoxelShape> bottom = Shapes.rotateHorizontal(Block.box(0, 0, 14, 16, 3, 16));
	private final Map<Direction, VoxelShape> top = Shapes.rotateHorizontal(Block.box(0, 13, 14, 16, 16, 16));

	public KitGlowStripBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM)
				.setValue(TONE, KitGlow.Tone.WARM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, TONE);
	}

	/**
	 * Placed against the face the player clicks: on a wall it looks away from the wall and takes the bottom or the top
	 * by where the click landed (as a stair does); on a floor or a ceiling it hugs the far edge of the cell, looking
	 * back at the player.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
		boolean high = clicked == Direction.DOWN
				|| clicked.getAxis().isHorizontal() && context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
		return defaultBlockState().setValue(FACING, facing).setValue(HALF, high ? Half.TOP : Half.BOTTOM);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return (state.getValue(HALF) == Half.TOP ? top : bottom).get(state.getValue(FACING));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return KitGlow.flipTone(state, level, pos, player);
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
