package dev.t3craft.hq.citadel.hub;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Block factories of the hub pieces ({@link HubFacade}, {@link HubInterior}, {@link HubExterior}): vanilla
 * blocks only, no state of their own. Stairs follow the convention of {@code CitadelCanvas.stairs}: the
 * {@code facing} is the side of the tall back.
 */
final class HubBlocks {
	/** Window colours of the nave and the outer wall, cycled by bay. */
	static final DyeColor[] GLASS_COLORS = {DyeColor.PINK, DyeColor.LIGHT_BLUE};

	private HubBlocks() {
	}

	/**
	 * The invisible vanilla light block where a lamp must not be seen: level {@value #FILL_LEVEL}, moderate,
	 * so the nave's visible pendants and lanterns (15) stay the light one sees (citadel/light-aesthetic).
	 */
	static BlockState light() {
		return CitadelCanvas.invisibleLight(FILL_LEVEL);
	}

	/** Level of the hub's invisible fill: hung one cell above the walking cell, it reads 12 at the feet under it. */
	static final int FILL_LEVEL = 13;

	static BlockState stairsTop(Block b, Direction back) {
		return b.defaultBlockState().setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, Half.TOP);
	}

	static BlockState slabTop(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	static BlockState glass(DyeColor c) {
		return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
	}

	static BlockState pane(DyeColor c) {
		return Blocks.STAINED_GLASS_PANE.pick(c).defaultBlockState();
	}

	static BlockState hangingLantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	static BlockState chain() {
		return Blocks.COPPER_CHAIN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	}

	static BlockState rod() {
		return Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
	}

	/** A light stone post (diorite wall): pinnacles and finials. */
	static BlockState stoneWall() {
		return Blocks.DIORITE_WALL.defaultBlockState();
	}

	/** Beam along {@code axis}: a stripped cherry log. */
	static BlockState strippedLog(Direction.Axis axis) {
		return Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	static BlockState log(Direction.Axis axis) {
		return Blocks.CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** A dark oak log standing upright (the archive's pilasters). */
	static BlockState darkOakLog() {
		return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	}

	static BlockState pot(boolean flowering) {
		return (flowering ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_AZALEA).defaultBlockState();
	}

	/** Stone bricks with a deterministic scatter of cracked and mossy ones (3 in 10 each way... by cell). */
	static BlockState stoneBrick(int x, int y, int z) {
		int h = Math.floorMod(x * 7 + y * 13 + z * 31 + x * z * 5, 10);
		return (h < 6 ? Blocks.STONE_BRICKS : h < 8 ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
	}

	/** The dark base course: deepslate tiles with cracked tiles, bricks and polished stone mixed in by cell. */
	static BlockState darkBase(int x, int y, int z) {
		int h = Math.floorMod(x * 11 + y * 5 + z * 17 + x * z * 3, 10);
		return (h < 5 ? Blocks.DEEPSLATE_TILES : h < 7 ? Blocks.CRACKED_DEEPSLATE_TILES : h < 9 ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
	}

	static BlockState stoneSlab(boolean top) {
		BlockState s = Blocks.STONE_BRICK_SLAB.defaultBlockState();
		return top ? s.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP) : s.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	static BlockState stoneStairs(Direction back, boolean top) {
		BlockState s = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back);
		return s.setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState roofStairs(Direction back) {
		return Blocks.CHERRY_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	static BlockState darkStairs(Direction back, boolean top) {
		BlockState s = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back);
		return s.setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState bush() {
		return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}
}
