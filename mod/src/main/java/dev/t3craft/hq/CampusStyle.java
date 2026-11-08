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
import org.jspecify.annotations.Nullable;

/**
 * "Campus de barrios" ({@code hq.style = campus}): a style with its <b>own layout</b>. The hub is a
 * tall domed hall on a central plaza; every project gets a <b>barrio</b> on a hexagonal lattice
 * around it (a commons house and up to three 6-desk halls around a patio, each hall can take a
 * second storey); the special rooms (review, scheduled tasks, errors, lounge, archive) are
 * pavilions of their own on the vertices around the plaza, and a network of paths, plazas,
 * pergolas, ponds and bridges joins everything. Geometry: {@link CampusGeometry}; allocation:
 * {@link CampusLayout}; blocks: {@link CampusGenerator}.
 *
 * <p>The spine pieces of {@link HqStyle} are never used by this style's own plans; they delegate to
 * the cherry style so the class stays a complete {@link HqStyle} (preview, palette, tests).
 */
final class CampusStyle extends HqStyle {
	static final String ID = "campus";

	private final CherryStyle pieces = new CherryStyle();
	private final Map<Role, BlockState> palette;

	CampusStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.putAll(pieces.palette());
		m.put(Role.FLOOR, Blocks.SPRUCE_PLANKS.defaultBlockState());
		m.put(Role.FOOTING, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		m.put(Role.WALL, Blocks.QUARTZ_BRICKS.defaultBlockState());
		m.put(Role.ROOF, Blocks.DARK_PRISMARINE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.HALF, Half.BOTTOM));
		m.put(Role.ROOF_TRIM, Blocks.DARK_PRISMARINE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		m.put(Role.RIDGE, Blocks.DARK_PRISMARINE.defaultBlockState());
		m.put(Role.PATH, Blocks.POLISHED_ANDESITE.defaultBlockState());
		m.put(Role.WINDOW, Blocks.GLASS_PANE.defaultBlockState());
		m.put(Role.BENCH, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
				.setValue(BlockStateProperties.HALF, Half.BOTTOM));
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
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		return CampusLayout.allocate(input, previous);
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return new CampusGenerator(this, layout).generate();
	}

	@Override
	BlockState bench(Direction back) {
		return palette.get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	// ------------------------------------------------------------------ spine pieces (unused by the campus plan)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		pieces.hub(c, northDoor);
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		pieces.board(c, sign);
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		pieces.archiveNook(c);
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		pieces.portico(c);
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		pieces.spineRow(c, r, eastSign, westSign);
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		pieces.spineCap(c, box);
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		pieces.wingEndWall(c, i, u, inner);
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		pieces.segment(c, i, s, accent);
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		pieces.desk(c, cells);
	}
}
