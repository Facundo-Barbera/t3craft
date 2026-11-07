package dev.t3craft.survival;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Items a build needs, by item id ({@code "minecraft:oak_planks"}), in first-seen order. Plain Java:
 * the caller maps each {@code BlockState} to its item ({@code state.getBlock().asItem()}) and adds the
 * id; air and blocks without an item ({@code minecraft:air}, fire, water…) go through {@link #add} too
 * and are skipped here, so the conversion stays dumb.
 * <p>Typical use by builders in survival: {@code bill.missing(inventoryCounts)} empty → take
 * {@link #items()} from the inventory and submit; otherwise tell the player what is missing.
 */
public final class MaterialBill {
	/** Ids that never cost anything (no item, or clearing). */
	public static final Set<String> FREE = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

	private final Map<String, Integer> items = new LinkedHashMap<>();

	/** Counts one block. Null/blank/free ids are ignored. */
	public MaterialBill add(String itemId) {
		return add(itemId, 1);
	}

	public MaterialBill add(String itemId, int count) {
		if (itemId == null || itemId.isBlank() || count <= 0 || FREE.contains(itemId)) {
			return this;
		}
		items.merge(itemId, count, Integer::sum);
		return this;
	}

	public static MaterialBill of(Iterable<String> itemIds) {
		MaterialBill b = new MaterialBill();
		for (String id : itemIds) {
			b.add(id);
		}
		return b;
	}

	public Map<String, Integer> items() {
		return Collections.unmodifiableMap(items);
	}

	public int total() {
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	/** What is still missing given what the player has (id → count; absent = 0). Empty when affordable. */
	public Map<String, Integer> missing(Map<String, Integer> available) {
		Map<String, Integer> out = new LinkedHashMap<>();
		items.forEach((id, need) -> {
			int have = available == null ? 0 : available.getOrDefault(id, 0);
			if (have < need) {
				out.put(id, need - have);
			}
		});
		return out;
	}

	public boolean affordable(Map<String, Integer> available) {
		return missing(available).isEmpty();
	}

	@Override
	public String toString() {
		return "MaterialBill" + items;
	}
}
