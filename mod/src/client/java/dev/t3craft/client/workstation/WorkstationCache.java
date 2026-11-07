package dev.t3craft.client.workstation;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Invalidation for the screens: remembers, per thread, the content hash and age label it last
 * rendered, and only produces a new {@link StationScreen} when one of them changed. The renderer
 * pushes text to its TextDisplay only then, so an unchanged thread costs a hash comparison per
 * update and never recreates entities.
 *
 * <p>Age: from {@link WorkstationThread#updatedAtMs()} when the source knows it. Otherwise from the
 * last change this cache observed; a thread seen for the first time has an unknown age (nothing is
 * shown) rather than a misleading "now", the same "silent seeding" rule as the notifications.
 *
 * <p>Pure (the caller passes the clock); not thread-safe, used on the client thread.
 */
public final class WorkstationCache {
	private static final class Entry {
		int hash;
		@Nullable Long changedAtMs;
		@Nullable String ageLabel;
		StationScreen screen;
		long version;
	}

	private final WorkstationModel model;
	private final Map<String, Entry> entries = new HashMap<>();

	public WorkstationCache(WorkstationModel model) {
		this.model = model;
	}

	/**
	 * Feeds the current state of a thread. Returns the new screen when it must be redrawn (first
	 * sight, content changed, or age label changed), null when the previous one is still right.
	 */
	public @Nullable StationScreen update(WorkstationThread t, long nowMs) {
		Entry e = entries.get(t.key());
		int hash = t.contentHash();
		boolean dirty;
		if (e == null) {
			e = new Entry();
			e.hash = hash;
			entries.put(t.key(), e);
			dirty = true;
		} else if (e.hash != hash) {
			e.hash = hash;
			e.changedAtMs = nowMs;
			dirty = true;
		} else {
			dirty = e.screen == null;
		}
		Long since = t.updatedAtMs() != null ? t.updatedAtMs() : e.changedAtMs;
		String age = since == null ? null : AgeFormat.format(nowMs - since);
		if (!dirty && Objects.equals(age, e.ageLabel)) {
			return null;
		}
		e.ageLabel = age;
		StationScreen s = model.render(t, age);
		if (s.equals(e.screen)) {
			return null;
		}
		e.screen = s;
		e.version++;
		return s;
	}

	/** Last rendered screen, or null if this thread was never fed. */
	public @Nullable StationScreen screen(String key) {
		Entry e = entries.get(key);
		return e == null ? null : e.screen;
	}

	/** Bumps every time a new screen is produced for {@code key}; 0 if never rendered. */
	public long version(String key) {
		Entry e = entries.get(key);
		return e == null ? 0 : e.version;
	}

	/** Forget threads that are gone (keeps memory bounded across many threads). */
	public void retain(Collection<String> keys) {
		Set<String> keep = keys instanceof Set<String> s ? s : new HashSet<>(keys);
		entries.keySet().removeIf(k -> !keep.contains(k));
	}

	/** Forces the next {@link #update} of every thread to render again (e.g. entities were rebuilt). */
	public void invalidateAll() {
		entries.values().forEach(e -> e.screen = null);
	}

	public void clear() {
		entries.clear();
	}

	public int size() {
		return entries.size();
	}
}
