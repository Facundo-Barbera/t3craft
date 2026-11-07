package dev.t3craft.build;

import java.util.LinkedHashMap;
import java.util.Map;

/** Item counts in a map; records what was taken. */
final class FakeSupply implements MaterialSupply {
	final Map<String, Integer> stock = new LinkedHashMap<>();
	final Map<String, Integer> taken = new LinkedHashMap<>();
	int refreshes;

	FakeSupply put(String id, int n) {
		stock.merge(id, n, Integer::sum);
		return this;
	}

	@Override
	public int count(String itemId) {
		return stock.getOrDefault(itemId, 0);
	}

	@Override
	public int take(String itemId, int n) {
		int k = Math.min(n, count(itemId));
		if (k > 0) {
			stock.merge(itemId, -k, Integer::sum);
			taken.merge(itemId, k, Integer::sum);
		}
		return k;
	}

	@Override
	public void refresh() {
		refreshes++;
	}
}
