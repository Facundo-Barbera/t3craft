package dev.t3craft.survival;

import dev.t3craft.build.MaterialSupply;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * {@link MaterialSupply} over vanilla {@link Container}s (a player's inventory, chests), taken in list
 * order. {@link #refresh()} re-reads the list (so storage chests placed or broken meanwhile count).
 * Server thread only. Only the first {@code slotLimit} slots of each container are used: for the
 * player inventory that is the 36 main slots, never armour or the offhand.
 */
public final class ContainerSupply implements MaterialSupply {
	/** A container and how many of its slots may be used. */
	public record Source(Container container, int slotLimit) {
		public static Source all(Container c) {
			return new Source(c, Integer.MAX_VALUE);
		}
	}

	private final Supplier<List<Source>> sources;
	private List<Source> current;

	public ContainerSupply(Supplier<List<Source>> sources) {
		this.sources = sources;
		this.current = List.copyOf(sources.get());
	}

	@Override
	public void refresh() {
		current = List.copyOf(sources.get());
	}

	@Override
	public int count(String itemId) {
		int n = 0;
		for (Source s : current) {
			Container c = s.container();
			int slots = Math.min(c.getContainerSize(), s.slotLimit());
			for (int i = 0; i < slots; i++) {
				ItemStack st = c.getItem(i);
				if (matches(st, itemId)) {
					n += st.getCount();
				}
			}
		}
		return n;
	}

	@Override
	public int take(String itemId, int n) {
		int taken = 0;
		for (Source s : current) {
			Container c = s.container();
			int slots = Math.min(c.getContainerSize(), s.slotLimit());
			boolean changed = false;
			for (int i = 0; i < slots && taken < n; i++) {
				ItemStack st = c.getItem(i);
				if (matches(st, itemId)) {
					int k = Math.min(n - taken, st.getCount());
					c.removeItem(i, k);
					taken += k;
					changed = true;
				}
			}
			if (changed) {
				c.setChanged();
			}
			if (taken >= n) {
				break;
			}
		}
		return taken;
	}

	private static boolean matches(ItemStack st, String itemId) {
		return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(itemId);
	}
}
