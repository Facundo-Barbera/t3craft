package dev.t3craft.hq;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * "Torres con atrio y puentes" (layout/vertical): not a skin of the shared wing grid but its own
 * layout. {@link HqGenerator} hands plans of this style to {@link TowerGenerator}, which builds the
 * towers of {@link TowerLayout} (atrium hub, half-floor parcels, monumental stairs, skybridges,
 * podium and plaza). The wing pieces of {@link HqStyle} are never asked for, so they throw.
 *
 * <p>Listed in {@link HqStyles#layouts()}, not in {@link HqStyles#all()}: the styles of
 * {@code all()} share one geometry (their tests compare them cell by cell); this one does not.
 * Select it with {@code "hq": {"style": "vertical"}}.
 */
final class VerticalStyle extends HqStyle {
	static final String ID = "vertical";

	private final Map<Role, BlockState> palette;

	VerticalStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.put(Role.FLOOR, Blocks.CHERRY_PLANKS.defaultBlockState());
		m.put(Role.FOOTING, Blocks.SMOOTH_QUARTZ.defaultBlockState());
		m.put(Role.WALL, Blocks.CALCITE.defaultBlockState());
		m.put(Role.COLUMN, Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
		m.put(Role.BEAM, Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		m.put(Role.ROOF, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM));
		m.put(Role.ROOF_TRIM, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		m.put(Role.RIDGE, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		m.put(Role.WINDOW, Blocks.GLASS_PANE.defaultBlockState());
		m.put(Role.DOOR, Blocks.POLISHED_ANDESITE.defaultBlockState());
		m.put(Role.DECOR, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		m.put(Role.PATH, Blocks.SMOOTH_STONE.defaultBlockState());
		m.put(Role.GARDEN, Blocks.MOSS_BLOCK.defaultBlockState());
		m.put(Role.GARDEN_TREE, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
		m.put(Role.LIGHT, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
		m.put(Role.DESK, Blocks.CHERRY_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		m.put(Role.BENCH, Blocks.CHERRY_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM));
		m.put(Role.BOARD, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		m.put(Role.COMPUTER_ON, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.COMPUTER_OFF, Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState());
		m.put(Role.STATUS_LAMP, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, false));
		m.put(Role.SCREEN, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.BEACON, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
		m.put(Role.TERMINAL, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		m.put(Role.PLAQUE, Blocks.CHERRY_WALL_SIGN.defaultBlockState());
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	@Override
	BlockState bench(Direction back) {
		return palette.get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	private static UnsupportedOperationException notAWing() {
		return new UnsupportedOperationException("the vertical HQ has no wing pieces (TowerGenerator builds it)");
	}

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		throw notAWing();
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		throw notAWing();
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		throw notAWing();
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		throw notAWing();
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, SignText eastSign, SignText westSign) {
		throw notAWing();
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		throw notAWing();
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		throw notAWing();
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		throw notAWing();
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		throw notAWing();
	}
}
