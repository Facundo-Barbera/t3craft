package dev.t3craft.build;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * Charging core of a {@link BuildJob}, plain Java (unit-tested with fakes). Works on plan indices:
 * {@code costs[i]} is the item entry {@code i} consumes (null = free) and {@code layers[i]} its layer
 * (the plan-relative y). A layer is a run of consecutive entries with the same y.
 *
 * <p>Protocol, on the server thread: {@link #before} for an index about to be placed; if it says no,
 * the job pauses ("missing materials", {@link #missing()}) without consuming the index. {@link #paid}
 * after the block was really placed (skipped entries, already in the world, cost nothing).
 * {@link #retry} decides when a waiting job may go on.
 *
 * <p>Checks: when a layer starts, everything the rest of the layer still needs must be available, so
 * a job waits before a layer instead of stopping halfway through it; each block is checked again when
 * placed, because the player may have dropped or used items meanwhile. Getters are thread-safe.
 */
public final class MaterialMeter {
	private final @Nullable String[] costs;
	private final int[] layers;
	private final Map<String, Integer> required;
	/** Entries before this index (in the current layer) were verified affordable. */
	private int verifiedUntil;
	private final Map<String, Integer> spentLive = new LinkedHashMap<>();
	private volatile Map<String, Integer> spent = Map.of();
	private volatile Map<String, Integer> missing = Map.of();

	public MaterialMeter(@Nullable String[] costs, int[] layers) {
		if (costs.length != layers.length) {
			throw new IllegalArgumentException("costs and layers differ in length");
		}
		this.costs = costs.clone();
		this.layers = layers.clone();
		Map<String, Integer> req = new LinkedHashMap<>();
		for (String id : this.costs) {
			if (id != null) {
				req.merge(id, 1, Integer::sum);
			}
		}
		this.required = Collections.unmodifiableMap(req);
	}

	/** May entry {@code index} be placed now? False → missing materials, recorded in {@link #missing()}. */
	public boolean before(int index, IntPredicate alreadyThere, MaterialSupply supply) {
		if (index >= verifiedUntil) {
			int end = layerEnd(index);
			if (!affordable(index, end, alreadyThere, supply)) {
				return false;
			}
			verifiedUntil = end;
		}
		String id = costs[index];
		if (id != null && supply.count(id) < 1 && !alreadyThere.test(index)) {
			verifiedUntil = index;
			affordable(index, layerEnd(index), alreadyThere, supply); // records what the rest of the layer lacks
			return false;
		}
		return true;
	}

	/** Entry {@code index} was placed: take its item. */
	public void paid(int index, MaterialSupply supply) {
		String id = costs[index];
		if (id != null && supply.take(id, 1) > 0) {
			spentLive.merge(id, 1, Integer::sum);
			spent = Map.copyOf(spentLive);
		}
	}

	/** While waiting at {@code index}: true (and {@link #missing()} cleared) once the rest of its layer is affordable. */
	public boolean retry(int index, IntPredicate alreadyThere, MaterialSupply supply) {
		if (index >= costs.length) {
			missing = Map.of();
			return true;
		}
		int end = layerEnd(index);
		if (!affordable(index, end, alreadyThere, supply)) {
			return false;
		}
		verifiedUntil = end;
		return true;
	}

	/**
	 * Fronts protocol (any order inside a layer): can every entry of {@code pending} (the rest of the
	 * current layer, across all fronts) be paid now? Records {@link #missing()} either way.
	 */
	public boolean affordable(int[] pending, IntPredicate alreadyThere, MaterialSupply supply) {
		Map<String, Integer> need = new LinkedHashMap<>();
		for (int i : pending) {
			String id = costs[i];
			if (id != null && !alreadyThere.test(i)) {
				need.merge(id, 1, Integer::sum);
			}
		}
		return check(need, supply);
	}

	/** Fronts protocol: can entry {@code index} be paid right now (free, already there, or one item in stock)? */
	public boolean canPay(int index, IntPredicate alreadyThere, MaterialSupply supply) {
		String id = costs[index];
		return id == null || supply.count(id) >= 1 || alreadyThere.test(index);
	}

	private boolean affordable(int from, int to, IntPredicate alreadyThere, MaterialSupply supply) {
		Map<String, Integer> need = new LinkedHashMap<>();
		for (int i = from; i < to; i++) {
			String id = costs[i];
			if (id != null && !alreadyThere.test(i)) {
				need.merge(id, 1, Integer::sum);
			}
		}
		return check(need, supply);
	}

	private boolean check(Map<String, Integer> need, MaterialSupply supply) {
		if (need.isEmpty()) {
			missing = Map.of();
			return true;
		}
		supply.refresh();
		Map<String, Integer> lack = new LinkedHashMap<>();
		need.forEach((id, n) -> {
			int have = supply.count(id);
			if (have < n) {
				lack.put(id, n - have);
			}
		});
		missing = Collections.unmodifiableMap(lack);
		return lack.isEmpty();
	}

	/** First index after {@code index}'s layer. */
	int layerEnd(int index) {
		int y = layers[index];
		int j = index + 1;
		while (j < layers.length && layers[j] == y) {
			j++;
		}
		return j;
	}

	/** What the whole plan costs (item id → count), whether or not some blocks already exist. */
	public Map<String, Integer> required() {
		return required;
	}

	/** Items taken so far. */
	public Map<String, Integer> spent() {
		return spent;
	}

	/** What the job is waiting for (item id → count still lacking); empty when not short. */
	public Map<String, Integer> missing() {
		return missing;
	}
}
