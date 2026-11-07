package dev.t3craft.blocks;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Paper lantern. A {@link LampBlockEntity} like the status lamp, with the same {@link StatusLampBlock#STATUS}
 * property, so {@link LampApplier} drives it and the lamp BER makes it breathe with its bound thread
 * ({@code {"binding": "thread:<key>"}} or {@code module:<keys>}). Unbound or off it is a plain warm
 * paper lantern ({@link #OFF_LIGHT}): decoration first, signal when bound.
 */
public final class KitLanternBlock extends BaseEntityBlock {
	/** Light when unbound / off: a warm decorative glow. */
	public static final int OFF_LIGHT = 12;
	/** The paper body (3..13 px), where the BER draws the glow. */
	public static final float BODY_INSET = 3f / 16f;
	private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(3, 2, 3, 13, 14, 13), Block.box(5, 14, 5, 11, 16, 11));

	private final Supplier<BlockEntityType<LampBlockEntity>> type;

	public KitLanternBlock(BlockBehaviour.Properties properties, Supplier<BlockEntityType<LampBlockEntity>> type) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(StatusLampBlock.STATUS, LampStatus.OFF));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(StatusLampBlock.STATUS);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LampBlockEntity(type.get(), pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	public static int light(BlockState state) {
		if (!state.hasProperty(StatusLampBlock.STATUS)) {
			return 0;
		}
		LampStatus s = state.getValue(StatusLampBlock.STATUS);
		return s == LampStatus.OFF ? OFF_LIGHT : Math.max(9, s.light);
	}
}
