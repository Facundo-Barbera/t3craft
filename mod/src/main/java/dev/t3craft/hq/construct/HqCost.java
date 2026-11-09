package dev.t3craft.hq.construct;

import dev.t3craft.survival.MaterialBill;
import dev.t3craft.survival.MaterialSource;
import dev.t3craft.survival.PlayContext;
import dev.t3craft.survival.SurvivalRules;
import dev.t3craft.survival.Verdict;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Asks the survival policy ({@code feat/survival}) whether a construction step may run. This only
 * <b>checks</b>: charging the materials while the builders place them is done by the builders
 * ({@code feat/builders-survival}). Without a player context (nobody in the world) the step waits.
 */
public final class HqCost {
	private HqCost() {
	}

	/** The materials a list of world states needs: one item per block, doors and beds counted once. */
	public static MaterialBill bill(Iterable<BlockState> states) {
		MaterialBill bill = new MaterialBill();
		for (BlockState s : states) {
			if (s.isAir() || upperHalf(s) || s.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
				continue;
			}
			bill.add(BuiltInRegistries.ITEM.getKey(s.getBlock().asItem()).toString());
		}
		return bill;
	}

	static boolean upperHalf(BlockState s) {
		if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return true;
		}
		return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}

	/**
	 * The verdict for building {@code bill} as {@code ctx}: refused by the policy, free, or taken from
	 * the inventory ({@code inventory}: item id → count), in which case everything must be there.
	 */
	public static HqConstruction.Cost check(String what, @Nullable PlayContext ctx, SurvivalRules rules, MaterialBill bill,
			Map<String, Integer> inventory) {
		if (ctx == null) {
			return new HqConstruction.Cost(false, what + " waits for the world's owner to be online");
		}
		Verdict v = rules.buildInWorld(ctx);
		if (!v.allowed()) {
			return new HqConstruction.Cost(false, what + ": the survival rules do not allow building (" + v.reason() + ")");
		}
		if (rules.materials(ctx) == MaterialSource.FREE || bill.isEmpty()) {
			return HqConstruction.Cost.FREE;
		}
		Map<String, Integer> missing = bill.missing(inventory);
		if (missing.isEmpty()) {
			return new HqConstruction.Cost(true, what + " needs " + bill.total() + " blocks from your inventory: you have them all");
		}
		int total = missing.values().stream().mapToInt(Integer::intValue).sum();
		String list = missing.entrySet().stream().limit(6).map(e -> e.getValue() + "× " + e.getKey().replace("minecraft:", ""))
				.collect(Collectors.joining(", "));
		return new HqConstruction.Cost(false, what + " needs " + bill.total() + " blocks; missing " + total + ": " + list
				+ (missing.size() > 6 ? ", …" : "") + ". Gather them and run /t3craft hq build");
	}
}
