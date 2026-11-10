package dev.t3craft.mapgen.islands;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import org.jspecify.annotations.Nullable;

/**
 * The kind of an island: its vanilla palette (surface, the two layers under it, the body, the underside and its
 * stalactites, what hangs from them, the pad under buildings, the scattered plants) and the catalog designs it
 * prefers ({@link #likes}). Chosen by seed per island, loosely grouped in regions of 3x3 cells
 * ({@link IslandField}).
 *
 * <p>Every block is vanilla. Plants are only put where they survive: grass and flowers on grass or moss, petals on
 * grass, snow layers on snow, dead bushes on sand.
 */
public enum IslandBiome {
	/** Grass, dirt and stone; roots hang from a rooted-dirt underside. */
	MEADOW("meadow", b(Blocks.GRASS_BLOCK), null, b(Blocks.DIRT), b(Blocks.COARSE_DIRT), b(Blocks.STONE), b(Blocks.ANDESITE), null,
			b(Blocks.ROOTED_DIRT), b(Blocks.DIRT), b(Blocks.HANGING_ROOTS), b(Blocks.DIRT_PATH), b(Blocks.COARSE_DIRT),
			List.of(b(Blocks.SHORT_GRASS), b(Blocks.SHORT_GRASS), b(Blocks.SHORT_GRASS), b(Blocks.DANDELION), b(Blocks.POPPY),
					b(Blocks.CORNFLOWER), b(Blocks.OXEYE_DAISY)),
			4, Set.of("oak", "birch", "timber", "maple", "flower", "veg", "cottage", "fruit", "well")),
	/** Moss over cobble and stone; dripstone tips under a stone underside. */
	MOSSY("mossy", b(Blocks.MOSS_BLOCK), b(Blocks.MOSSY_COBBLESTONE), b(Blocks.COBBLESTONE), b(Blocks.MOSSY_COBBLESTONE), b(Blocks.STONE),
			b(Blocks.COBBLESTONE), null, b(Blocks.STONE), b(Blocks.TUFF), dripstoneTip(), b(Blocks.MOSSY_STONE_BRICKS), b(Blocks.STONE_BRICKS),
			List.of(b(Blocks.MOSS_CARPET), b(Blocks.MOSS_CARPET), b(Blocks.FERN), b(Blocks.SHORT_GRASS)),
			3, Set.of("stone", "mossy", "weathered", "manor", "pine", "cascade", "guardian", "tower")),
	/** Grass with pink petals, stone with calcite veins and a pale calcite underside with roots. */
	CHERRY("cherry", b(Blocks.GRASS_BLOCK), null, b(Blocks.DIRT), null, b(Blocks.STONE), b(Blocks.CALCITE), null, b(Blocks.CALCITE),
			b(Blocks.STONE), b(Blocks.HANGING_ROOTS), b(Blocks.PACKED_MUD), b(Blocks.MUD_BRICKS),
			List.of(petals(1), petals(2), petals(3), petals(4), b(Blocks.SHORT_GRASS)),
			3, Set.of("cherry", "japanese", "paper", "torii", "azalea", "zen", "teahouse", "machiya", "engawa", "koi", "tea")),
	/** Snow over stone with packed-ice veins; an icy underside. */
	SNOW("snow", b(Blocks.SNOW_BLOCK), null, b(Blocks.SNOW_BLOCK), b(Blocks.DIRT), b(Blocks.STONE), b(Blocks.PACKED_ICE), null,
			b(Blocks.PACKED_ICE), b(Blocks.BLUE_ICE), null, b(Blocks.SPRUCE_PLANKS), b(Blocks.STRIPPED_SPRUCE_WOOD), List.of(b(Blocks.SNOW)),
			8, Set.of("pine", "birch", "stone", "tower", "lookout", "copper", "forge", "library")),
	/** Sand over sandstone, a banded terracotta body and a terracotta underside. */
	DESERT("desert", b(Blocks.SAND), null, b(Blocks.SANDSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.SANDSTONE), null,
			new BlockState[] {b(Blocks.TERRACOTTA), b(Blocks.ORANGE_TERRACOTTA), b(Blocks.YELLOW_TERRACOTTA), b(Blocks.SANDSTONE)},
			b(Blocks.TERRACOTTA), b(Blocks.ORANGE_TERRACOTTA), null, b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE), List.of(b(Blocks.DEAD_BUSH)),
			10, Set.of("terrace", "stone", "sundial", "obelisk", "brazier", "pot", "stall", "trough", "rosette"));

	private final String id;
	final BlockState top;
	final @Nullable BlockState topAlt;
	final BlockState sub;
	final @Nullable BlockState subAlt;
	final BlockState body;
	final @Nullable BlockState bodyAlt;
	/** Horizontal bands of the body by y (replaces body/bodyAlt), or null. */
	final BlockState @Nullable [] bands;
	final BlockState under;
	final BlockState underAlt;
	/** What hangs under the lowest block of some columns (roots, a dripstone tip), or null. */
	final @Nullable BlockState tip;
	final BlockState pad;
	final BlockState padAlt;
	final List<BlockState> flora;
	/** One column in this many gets a plant. */
	final int floraEvery;
	private final Set<String> likes;

	IslandBiome(String id, BlockState top, @Nullable BlockState topAlt, BlockState sub, @Nullable BlockState subAlt, BlockState body,
			@Nullable BlockState bodyAlt, BlockState @Nullable [] bands, BlockState under, BlockState underAlt, @Nullable BlockState tip,
			BlockState pad, BlockState padAlt, List<BlockState> flora, int floraEvery, Set<String> likes) {
		this.id = id;
		this.top = top;
		this.topAlt = topAlt;
		this.sub = sub;
		this.subAlt = subAlt;
		this.body = body;
		this.bodyAlt = bodyAlt;
		this.bands = bands;
		this.under = under;
		this.underAlt = underAlt;
		this.tip = tip;
		this.pad = pad;
		this.padAlt = padAlt;
		this.flora = List.copyOf(flora);
		this.floraEvery = floraEvery;
		this.likes = likes;
	}

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState dripstoneTip() {
		return Blocks.POINTED_DRIPSTONE.defaultBlockState()
				.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
				.setValue(BlockStateProperties.DRIPSTONE_THICKNESS, DripstoneThickness.TIP);
	}

	private static BlockState petals(int amount) {
		return Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, amount);
	}

	/** Lower-case id ({@code meadow}, {@code mossy}...): what slots are tagged with. */
	public String id() {
		return id;
	}

	/** Whether a catalog design suits this kind of island: its id names one of the biome's keywords. */
	public boolean likes(String variantId) {
		for (String k : likes) {
			if (variantId.contains(k)) {
				return true;
			}
		}
		return false;
	}

	/** The biome a slot's tags name, or {@link #MEADOW}. */
	public static IslandBiome ofTags(Set<String> tags) {
		for (IslandBiome b : values()) {
			if (tags.contains(b.id)) {
				return b;
			}
		}
		return MEADOW;
	}

	public static @Nullable IslandBiome byId(String id) {
		String k = id.trim().toLowerCase(Locale.ROOT);
		for (IslandBiome b : values()) {
			if (b.id.equals(k)) {
				return b;
			}
		}
		return null;
	}
}
