package dev.t3craft.blocks;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
 * ends of the run show a cap. Where a run meets another that runs across it (an inner corner) the strip in the corner grows a
 * leg ({@link #LEFT}, {@link #RIGHT}, {@link #resolve}) so the two read as one L; {@code PlanConnect} bakes the same states in
 * generated plans. {@link KitGlow#TONE} is warm (paper/amber) or cool (T3 cyan); an empty-handed click flips it.
 */
public final class KitGlowStripBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
	public static final EnumProperty<KitGlow.Tone> TONE = KitGlow.TONE;

	/**
	 * Inner corner: the strip in front of this one runs across (it turns the run), so this strip grows a leg along that side
	 * (2 deep, as the bar) that reaches the end of the other one. {@code LEFT} is {@code FACING.ccw}, {@code RIGHT} the other
	 * side. Pure function of the front neighbour: {@link #resolve(BlockState, BlockState)}.
	 */
	public static final BooleanProperty LEFT = BooleanProperty.create("left");
	public static final BooleanProperty RIGHT = BooleanProperty.create("right");

	private final Function<BlockState, VoxelShape> shapes;

	public KitGlowStripBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM)
				.setValue(TONE, KitGlow.Tone.WARM).setValue(LEFT, false).setValue(RIGHT, false));
		this.shapes = getShapeForEachState(KitGlowStripBlock::shapeOf);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, TONE, LEFT, RIGHT);
	}

	/**
	 * The joined state of the strip {@code s} (only {@code FACING} and {@code HALF} are read) given the block in front of it.
	 * A strip of the same half in front that looks across ours has its bar on the side opposite to where it looks; the leg goes there.
	 */
	public static BlockState resolve(BlockState s, BlockState front) {
		boolean left = false;
		boolean right = false;
		if (front.getBlock() instanceof KitGlowStripBlock && front.getValue(HALF) == s.getValue(HALF)) {
			Direction f = s.getValue(FACING);
			Direction g = front.getValue(FACING);
			if (g.getAxis() != f.getAxis()) {
				Direction bar = g.getOpposite();
				left = bar == f.getCounterClockWise();
				right = bar == f.getClockWise();
			}
		}
		return s.setValue(LEFT, left).setValue(RIGHT, right);
	}

	private static VoxelShape shapeOf(BlockState s) {
		boolean high = s.getValue(HALF) == Half.TOP;
		double y0 = high ? 13 : 0;
		double y1 = high ? 16 : 3;
		// north frame: the bar hugs z 14..16; left = west (x 0), right = east (x 16)
		VoxelShape v = Block.box(0, y0, 14, 16, y1, 16);
		if (s.getValue(LEFT)) {
			v = Shapes.or(v, Block.box(0, y0, 0, 2, y1, 14));
		}
		if (s.getValue(RIGHT)) {
			v = Shapes.or(v, Block.box(14, y0, 0, 16, y1, 14));
		}
		return Shapes.rotateHorizontal(v).get(s.getValue(FACING));
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
		BlockState s = defaultBlockState().setValue(FACING, facing).setValue(HALF, high ? Half.TOP : Half.BOTTOM);
		return resolve(s, context.getLevel().getBlockState(context.getClickedPos().relative(facing)));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return dir == state.getValue(FACING) ? resolve(state, neighbourState) : state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.apply(state);
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
		BlockState m = state.rotate(mirror.getRotation(state.getValue(FACING)));
		return mirror == Mirror.NONE ? m : m.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}
}
