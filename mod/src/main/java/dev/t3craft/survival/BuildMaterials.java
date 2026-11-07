package dev.t3craft.survival;

import dev.t3craft.build.BlockCosts;
import dev.t3craft.build.BuildCost;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.Placement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Adapter between the survival policy and the builders ({@code dev.t3craft.build}): decides whether
 * a build may run and whether it pays, computes its {@link MaterialBill} and opens the supply its
 * blocks are taken from. The charging itself happens inside the build job.
 *
 * <p>Supply, in this order:
 * <ol>
 *   <li><b>storage</b>: chests and barrels within {@value #STORAGE_RING} blocks around the site
 *   (outside its bounds, so a chest the plan itself places is never raided), from one block below
 *   the site to its top;</li>
 *   <li>the requesting player's inventory (36 main slots).</li>
 * </ol>
 */
public final class BuildMaterials {
	/** How far outside the site bounds storage containers are looked for. */
	public static final int STORAGE_RING = 2;

	private BuildMaterials() {
	}

	/** What the policy says about a build requested by {@code ctx}. */
	public record Decision(Verdict verdict, MaterialSource source) {
		/** Allowed and paid with materials. */
		public boolean charged() {
			return verdict.allowed() && source == MaterialSource.PLAYER_INVENTORY;
		}
	}

	public static Decision decide(SurvivalRules rules, PlayContext ctx) {
		Verdict v = rules.buildInWorld(ctx);
		return new Decision(v, v.allowed() ? rules.materials(ctx) : MaterialSource.FREE);
	}

	/** Everything {@code plan} costs (the same per-block rule the job charges with). */
	public static MaterialBill bill(BuildPlan plan) {
		MaterialBill bill = new MaterialBill();
		for (Placement p : plan.placements()) {
			bill.add(BlockCosts.itemId(p.state()));
		}
		return bill;
	}

	/** Cost of a build requested by {@code player}: storage by the site first, then the inventory. */
	public static BuildCost cost(UUID player) {
		return BuildCost.of((server, job) -> {
			ServerLevel level = server.getLevel(job.dimension());
			BoundingBox site = job.worldBounds();
			return new ContainerSupply(() -> {
				List<ContainerSupply.Source> out = new ArrayList<>();
				if (level != null) {
					for (Container c : storageAround(level, site)) {
						out.add(ContainerSupply.Source.all(c));
					}
				}
				ServerPlayer p = server.getPlayerList().getPlayer(player);
				if (p != null && !p.isRemoved()) {
					out.add(new ContainerSupply.Source(p.getInventory(), Inventory.INVENTORY_SIZE));
				}
				return out;
			});
		});
	}

	/** Chests and barrels in the ring around {@code site} (not inside it). */
	public static List<Container> storageAround(ServerLevel level, BoundingBox site) {
		List<Container> out = new ArrayList<>();
		int r = STORAGE_RING;
		for (BlockPos pos : BlockPos.betweenClosed(site.minX() - r, site.minY() - 1, site.minZ() - r, site.maxX() + r, site.maxY(), site.maxZ() + r)) {
			if (site.isInside(pos) || !level.isLoaded(pos)) {
				continue;
			}
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
				out.add((Container) be);
			}
		}
		return out;
	}

	/** "12 × Oak Planks, 3 × Glass" with translated item names. */
	public static MutableComponent describe(Map<String, Integer> items) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Map.Entry<String, Integer> e : items.entrySet()) {
			if (!first) {
				out.append(", ");
			}
			first = false;
			out.append(e.getValue() + " × ").append(itemName(e.getKey()));
		}
		return out;
	}

	private static Component itemName(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key != null) {
			Item item = BuiltInRegistries.ITEM.getValue(key);
			if (item != null && BuiltInRegistries.ITEM.getKey(item).equals(key)) {
				return Component.translatable(item.getDescriptionId());
			}
		}
		return Component.literal(id);
	}
}
