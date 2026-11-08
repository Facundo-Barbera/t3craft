package dev.t3craft.hq;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * layout/ring, "Ciudad en anillos": a style that brings its <b>own layout</b> instead of filling the
 * spine grid of {@link HqGeometry}. A domed octagonal hub stands on a podium in the middle of a
 * sunken garden patio; an elevated cloister (the first ring) goes all the way round it, linked to the
 * hub by bridges over the patio; radial avenues ramp down from the cloister to rings of wedge-shaped
 * sectors, one per project; special rooms (a tower, rotundas, a sunken patio) stand on the nodes
 * where the radials cross the ring street. See {@link RingGeometry} for the geometry and the growth
 * rules and {@link RingGenerator} for the plan.
 *
 * <p>{@link HqGenerator#generate} hands layouts of this style to {@link RingGenerator}; the
 * spine-grid pieces of {@link HqStyle} are never called, so they do nothing. The palette lists every
 * role (other tools read it); the generator mixes it with {@link HqKit} roles (floors, wall panels,
 * beams, lanterns, desks, planters) so the blocks module's kit is used when it is on.
 */
public final class RingStyle extends HqStyle {
	public static final String ID = "ring";

	private static final Map<Role, BlockState> PALETTE = palette0();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return PALETTE;
	}

	@Override
	public HqLayout.Placer placer() {
		return RingGeometry::place;
	}

	private static Map<Role, BlockState> palette0() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.put(Role.FLOOR, Blocks.POLISHED_TUFF.defaultBlockState());
		m.put(Role.FOOTING, Blocks.TUFF_BRICKS.defaultBlockState());
		m.put(Role.WALL, Blocks.SMOOTH_QUARTZ.defaultBlockState());
		m.put(Role.COLUMN, Blocks.QUARTZ_PILLAR.defaultBlockState());
		m.put(Role.BEAM, Blocks.CHISELED_TUFF.defaultBlockState());
		m.put(Role.ROOF, Blocks.CUT_COPPER_STAIRS.waxed().oxidized().defaultBlockState());
		m.put(Role.ROOF_TRIM, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		m.put(Role.RIDGE, Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState());
		m.put(Role.WINDOW, Blocks.GLASS.defaultBlockState());
		m.put(Role.DOOR, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
		m.put(Role.DECOR, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		m.put(Role.PATH, Blocks.TUFF_BRICKS.defaultBlockState());
		m.put(Role.GARDEN, Blocks.MOSS_BLOCK.defaultBlockState());
		m.put(Role.GARDEN_TREE, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
		m.put(Role.LIGHT, Blocks.SEA_LANTERN.defaultBlockState());
		m.put(Role.DESK, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		m.put(Role.BENCH, Blocks.POLISHED_TUFF_STAIRS.defaultBlockState());
		m.put(Role.BOARD, Blocks.CONCRETE.black().defaultBlockState());
		m.put(Role.COMPUTER_ON, Blocks.CONCRETE.black().defaultBlockState());
		m.put(Role.COMPUTER_OFF, Blocks.CONCRETE.gray().defaultBlockState());
		m.put(Role.STATUS_LAMP, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState());
		m.put(Role.SCREEN, Blocks.CONCRETE.black().defaultBlockState());
		m.put(Role.BEACON, Blocks.SEA_LANTERN.defaultBlockState());
		m.put(Role.TERMINAL, Blocks.LECTERN.defaultBlockState());
		m.put(Role.PLAQUE, Blocks.CHERRY_WALL_SIGN.defaultBlockState());
		return Map.copyOf(m);
	}

	@Override
	BlockState bench(Direction back) {
		return Blocks.POLISHED_TUFF_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	// ------------------------------------------------------------------ spine-grid pieces: unused (RingGenerator draws everything)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, net.minecraft.world.item.DyeColor accent) {
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
	}
}
