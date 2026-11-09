package dev.t3craft.blocks;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The other lanterns of the paper-lantern family (the standing one is {@link KitLanternBlock}, the only one
 * that can be bound to a thread: its body is the one the lamp BER lays its glow on). These three are decoration
 * with real light, and their silhouettes differ on purpose so they read apart from afar:
 * <ul>
 *   <li>{@link Hanging}: a round chochin on a cord (hangs from a chain, a beam or an eave), three paper tones;</li>
 *   <li>{@link Wall}: a small box on an iron bracket out of a wall, beam or post, three paper tones, turned by
 *       {@code facing} (the side it points to; the wall is behind it);</li>
 *   <li>{@link Tall}: the plaza lantern, two cells high (stone plinth, cherry post, a big paper box under a hip
 *       roof), placed like a door or a tall flower.</li>
 * </ul>
 * None checks for support: they are placed by plans (and by hand) wherever the builder wants them.
 */
public final class KitLanterns {
	/** Light of the hanging chochin. */
	public static final int HANGING_LIGHT = 14;
	/** Light of the wall lantern. */
	public static final int WALL_LIGHT = 13;
	/** Light of the plaza lantern (both halves). */
	public static final int TALL_LIGHT = 15;

	/** Paper colour of the hanging and wall lanterns. */
	public enum Tone implements StringRepresentable {
		PAPER, ROSE, VERMILION;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);

	private KitLanterns() {
	}

	/** Chochin: the barrel (2..14 px) and the cord up to the top of the cell. */
	public static final class Hanging extends Block {
		private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 2, 2, 14, 14, 14), Block.box(6, 14, 6, 10, 16, 10));

		public Hanging(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(TONE, Tone.PAPER));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(TONE);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}
	}

	/** Bracket lantern drawn for {@code facing = north} (the wall to the south) and turned with the state. */
	public static final class Wall extends KitFacingBlock {
		public Wall(BlockBehaviour.Properties properties) {
			super(properties, shape());
			registerDefaultState(defaultBlockState().setValue(TONE, Tone.PAPER));
		}

		private static VoxelShape shape() {
			return Shapes.or(Block.box(3, 1, 3, 13, 14, 13), Block.box(7, 10, 8, 9, 15, 16), Block.box(5, 2, 15, 11, 15, 16));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(TONE);
		}

		/** Clicked on the side of a block: it points away from that block. From above or below, it faces the player. */
		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			Direction face = context.getClickedFace();
			Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
			return defaultBlockState().setValue(FACING, facing);
		}
	}

	/** The plaza lantern: two cells, {@link #HALF}; it drops one item (from the lower half), like a door. */
	public static final class Tall extends Block {
		public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
		private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(5, 4, 5, 11, 16, 11));
		private static final VoxelShape UPPER = Shapes.or(Block.box(5, 0, 5, 11, 2, 11), Block.box(0, 2, 0, 16, 16, 16));

		public Tall(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(HALF);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
		}

		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			BlockPos pos = context.getClickedPos();
			Level level = context.getLevel();
			return pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context) ? super.getStateForPlacement(context) : null;
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
			level.setBlockAndUpdate(pos.above(), defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER));
		}

		/** The upper half needs the lower one under it; a half whose partner is gone goes too (as a door does). */
		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
				BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
			DoubleBlockHalf half = state.getValue(HALF);
			boolean towardsPartner = dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP);
			if (towardsPartner && !(neighbourState.is(this) && neighbourState.getValue(HALF) != half)) {
				return Blocks.AIR.defaultBlockState();
			}
			return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
		}

		@Override
		public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
			if (!level.isClientSide()) {
				if (player.preventsBlockDrops()) {
					preventDropFromLowerHalf(level, pos, state, player);
				} else {
					dropResources(state, level, pos, null, player, player.getMainHandItem());
				}
			}
			return super.playerWillDestroy(level, pos, state, player);
		}

		@Override
		public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
				ItemStack destroyedWith) {
			super.playerDestroy(level, player, pos, Blocks.AIR.defaultBlockState(), blockEntity, destroyedWith);
		}

		/** Creative: breaking the upper half removes the lower one without a drop (flag 35 = no drops, no neighbour update). */
		private static void preventDropFromLowerHalf(Level level, BlockPos pos, BlockState state, Player player) {
			if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
				BlockPos lowerPos = pos.below();
				BlockState lower = level.getBlockState(lowerPos);
				if (lower.is(state.getBlock()) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
					level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), 35);
					level.levelEvent(player, 2001, lowerPos, Block.getId(lower));
				}
			}
		}
	}
}
