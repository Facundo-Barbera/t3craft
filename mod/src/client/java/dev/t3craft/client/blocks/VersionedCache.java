package dev.t3craft.client.blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-key cache of something expensive to build (a plaque's glyph runs) that is rebuilt only when
 * the key's content version changes. Bounded: past {@code max} entries it starts over. Pure; the
 * render thread is its only user.
 */
final class VersionedCache<K, V> {
	private record Entry<V>(long version, V value) {
	}

	private final Map<K, Entry<V>> entries = new HashMap<>();
	private final int max;
	private int builds;

	VersionedCache(int max) {
		this.max = max;
	}

	/** The cached value for {@code key} at {@code version}; built (and counted) only on a miss. */
	V get(K key, long version, Supplier<V> build) {
		Entry<V> e = entries.get(key);
		if (e != null && e.version() == version) {
			return e.value();
		}
		if (entries.size() >= max) {
			entries.clear();
		}
		V v = build.get();
		builds++;
		entries.put(key, new Entry<>(version, v));
		return v;
	}

	/** How many times a value was built (tests, status). */
	int builds() {
		return builds;
	}

	int size() {
		return entries.size();
	}

	void clear() {
		entries.clear();
	}
}
