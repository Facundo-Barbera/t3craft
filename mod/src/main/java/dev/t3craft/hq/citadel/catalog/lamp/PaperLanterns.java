package dev.t3craft.hq.citadel.catalog.lamp;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The kit's paper lanterns for the lamp variants. They are blocks of t3craft's blocks module, which may be off, so
 * they are looked up in the registry by id (no compile-time link to the kit's classes) and each has a vanilla
 * fallback of the same footprint: the lantern, a fence for the plaza lantern's post.
 *
 * <ul>
 * <li>{@link #standing()}: the 1-block andon (a warm light, 12);</li>
 * <li>{@link #hanging(String)}: a chochin, tone {@code paper | rose | vermilion} (14);</li>
 * <li>{@link #wall(Direction, String)}: a bracket lantern, pointing {@code facing} (local, the plot rotates it) (13);</li>
 * <li>{@link #tall(boolean)}: the plaza lantern, lower or upper half (15).</li>
 * </ul>
 */
final class PaperLanterns {
	private static final String NS = "t3craft";

	private PaperLanterns() {
	}

	private static BlockState kit(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(NS, path);
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).defaultBlockState() : null;
	}

	private static BlockState vanilla(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	/** {@code s} with the property {@code name} set to the value called {@code value}; unchanged if it has none such. */
	private static BlockState with(BlockState s, String name, String value) {
		Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
		return p == null ? s : withValue(s, p, value);
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState s, Property<T> p, String value) {
		return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
	}

	static BlockState standing() {
		BlockState s = kit("paper_lantern");
		return s != null ? s : vanilla(false);
	}

	static BlockState hanging(String tone) {
		BlockState s = kit("paper_lantern_hanging");
		return s != null ? with(s, "tone", tone) : vanilla(true);
	}

	static BlockState wall(Direction facing, String tone) {
		BlockState s = kit("paper_lantern_wall");
		return s != null ? with(with(s, "facing", facing.getSerializedName()), "tone", tone) : vanilla(true);
	}

	static BlockState tall(boolean upper) {
		BlockState s = kit("paper_lantern_tall");
		if (s != null) {
			return with(s, "half", upper ? "upper" : "lower");
		}
		return upper ? vanilla(false) : Blocks.CHERRY_FENCE.defaultBlockState();
	}

	/** Whether the kit's blocks are registered (the module is on). */
	static boolean custom() {
		return kit("paper_lantern_tall") != null;
	}
}
