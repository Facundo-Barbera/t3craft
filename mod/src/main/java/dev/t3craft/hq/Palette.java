package dev.t3craft.hq;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The HQ's materials: warm timber (stripped dark oak frame, spruce floors, pale oak infill), tuff
 * plinths, waxed copper roofs, trims and lights, and glass. Copper is always waxed so it never
 * weathers. An instance per generator (no static block-state caches: safe across hot reloads).
 *
 * <p>Look and material language inspired by AgentCraft's "Warm Studio" HQ
 * (https://github.com/blendi-remade/agentcraft, MIT); no code copied, see THIRD_PARTY_NOTICES.md.
 */
final class Palette {
	final BlockState air = Blocks.AIR.defaultBlockState();
	final BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
	final BlockState shortGrass = Blocks.SHORT_GRASS.defaultBlockState();
	final BlockState dirtPath = Blocks.DIRT_PATH.defaultBlockState();
	final BlockState plinth = Blocks.TUFF_BRICKS.defaultBlockState();
	final BlockState plinthWall = Blocks.TUFF_BRICK_WALL.defaultBlockState();
	final BlockState stoneFloor = Blocks.POLISHED_ANDESITE.defaultBlockState();
	final BlockState plinthTile = Blocks.POLISHED_TUFF.defaultBlockState();
	final Block stairBlock = Blocks.SPRUCE_STAIRS;
	/** Railing: waxed copper bars (connections need no block tags, so they bake the same in tests and in game). */
	final BlockState rail = Blocks.COPPER_BARS.waxed().unaffected().defaultBlockState();
	final BlockState woodFloor = Blocks.SPRUCE_PLANKS.defaultBlockState();
	final BlockState darkPlanks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
	final BlockState infill = Blocks.PALE_OAK_PLANKS.defaultBlockState();
	final BlockState glass = Blocks.GLASS.defaultBlockState();
	final BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
	final BlockState bookshelf = Blocks.BOOKSHELF.defaultBlockState();
	final BlockState boardFace = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
	final BlockState copper = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();
	final BlockState copperInlay = Blocks.CHISELED_COPPER.waxed().unaffected().defaultBlockState();
	final BlockState copperGrate = Blocks.COPPER_GRATE.waxed().unaffected().defaultBlockState();
	final BlockState copperSlab = Blocks.CUT_COPPER_SLAB.waxed().unaffected().defaultBlockState()
			.setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	final Block copperStairs = Blocks.CUT_COPPER_STAIRS.waxed().unaffected();
	final BlockState lightningRod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
	final BlockState chain = Blocks.COPPER_CHAIN.waxed().unaffected().defaultBlockState()
			.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	final BlockState deskTop = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	final BlockState monitorOn = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
	final BlockState monitorOff = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	final BlockState fern = Blocks.POTTED_FERN.defaultBlockState();
	final BlockState pottedAzalea = Blocks.POTTED_AZALEA.defaultBlockState();
	final BlockState pottedFloweringAzalea = Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState();
	final BlockState azalea = Blocks.AZALEA.defaultBlockState();
	final BlockState floweringAzalea = Blocks.FLOWERING_AZALEA.defaultBlockState();

	/** Timber post (vertical stripped dark oak). */
	BlockState post() {
		return beam(Direction.Axis.Y);
	}

	BlockState beam(Direction.Axis axis) {
		return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** Bottom stairs whose tall back is on the {@code back} side. */
	BlockState stairs(Block b, Direction back) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	}

	BlockState copperRoof(Direction back) {
		return stairs(copperStairs, back);
	}

	/** A seat: stairs with the backrest on {@code back}. */
	BlockState chair(Direction back) {
		return stairs(Blocks.DARK_OAK_STAIRS, back);
	}

	BlockState bench(Direction back) {
		return stairs(Blocks.SPRUCE_STAIRS, back);
	}

	BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	BlockState copperLantern(boolean hanging) {
		return Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	/** A status lamp (waxed copper bulb), off. Other modules toggle {@code lit}. */
	BlockState lamp() {
		return Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.LIT, false);
	}

	BlockState barrel(Direction lid) {
		return Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, lid);
	}

	BlockState wallSign(Direction facing) {
		return Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	/**
	 * Colour of an environment (module carpet runner and sign text): one of the 16 dye colours, from a
	 * Fibonacci mix of {@link String#hashCode()} (whose value the Java spec fixes), so an environment
	 * keeps its colour across runs, machines and versions. Distinct keys may share a colour.
	 */
	DyeColor envColor(String envKey) {
		return DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28);
	}

	DyeColor envColor(HqLayout.Module m) {
		return envColor(m.envKey().isEmpty() ? m.projectKey() : m.envKey());
	}
}
