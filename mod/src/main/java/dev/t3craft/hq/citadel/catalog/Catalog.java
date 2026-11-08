package dev.t3craft.hq.citadel.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * The registry of {@link Variant}s, read from {@link CatalogEntries#ALL}. Family leaders ask it for a
 * variant that fits a plot ({@link #pick}); the gallery ({@code hqRender --catalog}) lists them.
 */
public final class Catalog {
	private Catalog() {
	}

	private static final Map<Family, List<Variant>> BY_FAMILY = new EnumMap<>(Family.class);
	private static final Map<String, Variant> BY_ID = new HashMap<>();

	static {
		for (Family f : Family.values()) {
			BY_FAMILY.put(f, new ArrayList<>());
		}
		for (Variant v : CatalogEntries.ALL) {
			if (BY_ID.put(v.id(), v) != null) {
				throw new IllegalStateException("duplicate catalog id " + v.id());
			}
			BY_FAMILY.get(v.family()).add(v);
		}
		BY_FAMILY.replaceAll((f, l) -> Collections.unmodifiableList(l));
	}

	/** Every variant, in registration (id) order. */
	public static List<Variant> all() {
		return CatalogEntries.ALL;
	}

	/** The variants of a family, in id order (empty if none yet). */
	public static List<Variant> of(Family f) {
		return BY_FAMILY.get(f);
	}

	/** The variant with that id, or null. */
	public static @Nullable Variant byId(String id) {
		return BY_ID.get(id);
	}

	/** A variant of {@code f} that fits {@code p}, chosen by its seed and weights; null if none fits. */
	public static @Nullable Variant pick(Family f, Plot p) {
		return pick(f, p, null);
	}

	/**
	 * Like {@link #pick(Family, Plot)} with weights overriding {@link Variant#weight()} by id (a leader
	 * that wants more of one design in its quarter). Deterministic: same family, plot and weights, same pick.
	 */
	public static @Nullable Variant pick(Family f, Plot p, @Nullable Map<String, Integer> weights) {
		List<Variant> fit = new ArrayList<>();
		List<Integer> ws = new ArrayList<>();
		int total = 0;
		for (Variant v : of(f)) {
			if (!v.fits(p)) {
				continue;
			}
			int w = weights != null && weights.containsKey(v.id()) ? weights.get(v.id()) : v.weight();
			if (w <= 0) {
				continue;
			}
			fit.add(v);
			ws.add(w);
			total += w;
		}
		if (total == 0) {
			return null;
		}
		int r = new Random(p.seed() * 31 + f.ordinal()).nextInt(total);
		for (int i = 0; i < fit.size(); i++) {
			r -= ws.get(i);
			if (r < 0) {
				return fit.get(i);
			}
		}
		return fit.get(fit.size() - 1);
	}
}
