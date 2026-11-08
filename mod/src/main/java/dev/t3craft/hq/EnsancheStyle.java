package dev.t3craft.hq;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * "Ensanche" (layout/blocks): the HQ as a piece of city. Manzanas with garden patios on a street grid
 * around a plaza mayor (see {@link EnsancheGrid}, {@link EnsancheAllocator}, {@link EnsancheBuilder}).
 * Each manzana is its own barrio with a material set ({@link Mat}: brick, stucco, sandstone, cherry,
 * tuff and copper, terracotta), buildings of two to four storeys with pitched or flat roofs.
 * Desks, floors, lanterns and lit panels come from {@link HqKit} (t3craft's blocks when the blocks
 * module is on, vanilla otherwise); the fixtures (desk screens, board, beacon, archive terminal, big
 * screens, kanban wall) are written by the builder with this palette's stand-ins, exactly as in
 * cherry and classic, and swapped by the blocks module by anchor.
 *
 * <p>The spine pieces of {@link HqStyle} are never called for a town style; they delegate to cherry so
 * the class stays a complete style.
 */
final class EnsancheStyle extends HqStyle {
	static final String ID = "ensanche";

	private final Map<Role, BlockState> palette;
	private final CherryStyle spine = new CherryStyle();

	EnsancheStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		m.put(Role.FLOOR, Blocks.SPRUCE_PLANKS.defaultBlockState());
		m.put(Role.FOOTING, Blocks.POLISHED_ANDESITE.defaultBlockState());
		m.put(Role.WALL, Blocks.BRICKS.defaultBlockState());
		m.put(Role.COLUMN, Blocks.STONE_BRICKS.defaultBlockState());
		m.put(Role.BEAM, Blocks.SMOOTH_STONE.defaultBlockState());
		m.put(Role.ROOF, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
		m.put(Role.ROOF_TRIM, slab(Blocks.DEEPSLATE_TILE_SLAB));
		m.put(Role.RIDGE, Blocks.DEEPSLATE_TILES.defaultBlockState());
		m.put(Role.WINDOW, Blocks.GLASS_PANE.defaultBlockState());
		m.put(Role.DOOR, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		m.put(Role.DECOR, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		m.put(Role.PATH, Blocks.POLISHED_ANDESITE.defaultBlockState());
		m.put(Role.GARDEN, Blocks.GRASS_BLOCK.defaultBlockState());
		m.put(Role.GARDEN_TREE, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
		m.put(Role.LIGHT, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false));
		m.put(Role.DESK, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		m.put(Role.BENCH, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
		m.put(Role.BOARD, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		m.put(Role.COMPUTER_ON, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.COMPUTER_OFF, Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState());
		m.put(Role.STATUS_LAMP, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, false));
		m.put(Role.SCREEN, Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
		m.put(Role.BEACON, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
		m.put(Role.TERMINAL, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		m.put(Role.PLAQUE, Blocks.SPRUCE_WALL_SIGN.defaultBlockState());
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
	TownLayout town() {
		return EnsancheAllocator.INSTANCE;
	}

	@Override
	BlockState bench(Direction back) {
		return stairs(Blocks.DARK_OAK_STAIRS, back);
	}

	// ------------------------------------------------------------------ barrios

	/**
	 * A barrio's material set.
	 *
	 * @param wall    main facade
	 * @param wall2   the other facade (alternate buildings of the manzana)
	 * @param trim    cornices, lintels, pilasters
	 * @param plinth  footing course and the floor slabs' edge
	 * @param floor   interior floor without the blocks module
	 * @param roof    roof stairs block (pitched roofs) or the parapet's stairs (flat ones)
	 * @param roofSlab parapet / ridge slab
	 * @param roofFull full roof block (ridge)
	 * @param pitched gable roofs (true) or flat terraces
	 * @param log     tree trunks of its patio
	 * @param leaves  tree canopy of its patio (persistent)
	 */
	record Mat(String name, BlockState wall, BlockState wall2, BlockState trim, BlockState plinth, BlockState floor, Block roof,
			BlockState roofSlab, BlockState roofFull, boolean pitched, BlockState log, BlockState leaves) {
		BlockState roofStairs(Direction up) {
			return stairs(roof, up);
		}
	}

	private static final List<Mat> MATS = List.of(
			new Mat("ladrillo", Blocks.BRICKS.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
					Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.POLISHED_GRANITE_STAIRS,
					slab(Blocks.POLISHED_GRANITE_SLAB), Blocks.POLISHED_GRANITE.defaultBlockState(), true, log(Blocks.OAK_LOG),
					leaves(Blocks.OAK_LEAVES)),
			new Mat("estuco", Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
					Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.DARK_PRISMARINE_STAIRS,
					slab(Blocks.DARK_PRISMARINE_SLAB), Blocks.DARK_PRISMARINE.defaultBlockState(), true, log(Blocks.BIRCH_LOG),
					leaves(Blocks.BIRCH_LEAVES)),
			new Mat("arenisca", Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(),
					Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.BIRCH_PLANKS.defaultBlockState(),
					Blocks.SMOOTH_RED_SANDSTONE_STAIRS, slab(Blocks.SMOOTH_RED_SANDSTONE_SLAB), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState(), false,
					log(Blocks.ACACIA_LOG), leaves(Blocks.ACACIA_LEAVES)),
			new Mat("cerezo", Blocks.CALCITE.defaultBlockState(), Blocks.PALE_OAK_PLANKS.defaultBlockState(), Blocks.CHERRY_PLANKS.defaultBlockState(),
					Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.BAMBOO_MOSAIC.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS,
					slab(Blocks.DEEPSLATE_TILE_SLAB), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), true, log(Blocks.CHERRY_LOG),
					leaves(Blocks.CHERRY_LEAVES)),
			new Mat("toba", Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.POLISHED_TUFF.defaultBlockState(),
					Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
					Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.CUT_COPPER_STAIRS.waxed().unaffected(),
					slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()), Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState(), true,
					log(Blocks.SPRUCE_LOG), leaves(Blocks.SPRUCE_LEAVES)),
			new Mat("terracota", Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
					Blocks.RESIN_BRICKS.defaultBlockState(), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(),
					Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.RESIN_BRICK_STAIRS, slab(Blocks.RESIN_BRICK_SLAB),
					Blocks.RESIN_BRICKS.defaultBlockState(), false, log(Blocks.DARK_OAK_LOG), leaves(Blocks.DARK_OAK_LEAVES)));

	/**
	 * The barrio of cell {@code k}: work manzanas take the sets in spiral order, so the six around
	 * the plaza are all different (a pure function of {@code k}).
	 */
	static Mat mat(int k) {
		int ordinal = 0;
		for (int j = 1; j < k; j++) {
			if (EnsancheGrid.kind(j) == EnsancheGrid.Kind.WORK) {
				ordinal++;
			}
		}
		return MATS.get(Math.floorMod(ordinal + 3, MATS.size()));
	}

	static List<Mat> mats() {
		return MATS;
	}

	// ------------------------------------------------------------------ helpers

	static BlockState stairs(Block b, Direction up) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, up).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	static BlockState slab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	static BlockState log(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	}

	static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	// ------------------------------------------------------------------ spine pieces (unused: see class doc)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		spine.hub(c, northDoor);
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		spine.board(c, sign);
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		spine.archiveNook(c);
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		spine.portico(c);
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		spine.spineRow(c, r, eastSign, westSign);
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		spine.spineCap(c, box);
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		spine.wingEndWall(c, i, u, inner);
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		spine.segment(c, i, s, accent);
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		spine.desk(c, cells);
	}
}
