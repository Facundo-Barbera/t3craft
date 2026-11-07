package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.TextFit;
import dev.t3craft.client.workstation.WorkstationCache;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What every monitor shows, from the thread states the client already has (fed once per tick by
 * {@code BlocksClient}; no subscription of its own). Reuses {@link WorkstationModel} (same lines,
 * colours and wrapping as the workstation screens, narrower layout for a one-block screen) and
 * {@link WorkstationCache} (per-thread content hash + age label → a new screen and a bumped
 * version only when something changed).
 *
 * <p>The renderer asks {@link #show(Binding)} each frame and rebuilds its glyph runs only when
 * {@link Shown#version()} changes. Pure (the caller passes the clock); client thread only.
 */
public final class MonitorScreens {
	/** 22 characters per line on a one-block screen, three activity lines. */
	public static final WorkstationModel.Layout LAYOUT = new WorkstationModel.Layout(22, 3);

	/** One monitor's content and its version ({@code id} + {@code version} identify it for caching). */
	public record Shown(String id, long version, StationScreen screen) {
	}

	static final StationScreen UNBOUND = new StationScreen(List.of(
			new StationScreen.Line("T3Craft monitor", WorkstationModel.DIM_COLOR),
			new StationScreen.Line("· NO THREAD", StationStatus.IDLE.color)), StationStatus.IDLE, 0xF0101010);

	private static final Shown UNBOUND_SHOWN = new Shown("#unbound", 0, UNBOUND);

	private final WorkstationCache cache = new WorkstationCache(new WorkstationModel(LAYOUT));
	/** "Thread not known" screens by key: built once, not per frame. */
	private final Map<String, Shown> missing = new HashMap<>();
	private long revision;
	/**
	 * Bumped when threads are dropped from the cache (or on clear): a thread that comes back restarts
	 * its cache version at 1, so the epoch keeps {@link Shown#version()} from repeating a version
	 * the renderer already cached with other content.
	 */
	private long epoch;

	/** Feeds every known thread; returns true when any screen changed. */
	public boolean tick(Collection<WorkstationThread> threads, long nowMs) {
		boolean changed = false;
		Set<String> keys = new HashSet<>();
		for (WorkstationThread t : threads) {
			keys.add(t.key());
			changed |= cache.update(t, nowMs) != null;
		}
		int before = cache.size();
		cache.retain(keys);
		if (cache.size() != before) {
			changed = true;
			epoch++;
		}
		if (missing.size() > 256) {
			missing.clear();
		}
		if (changed) {
			revision++;
		}
		return changed;
	}

	/** Bumps whenever any screen changed (cheap "is anything new" check). */
	public long revision() {
		return revision;
	}

	/** The screen of a monitor with the given effective binding. */
	public Shown show(Binding b) {
		if (b.kind() != Binding.Kind.THREAD) {
			return UNBOUND_SHOWN;
		}
		StationScreen s = cache.screen(b.value());
		if (s == null) {
			return missing.computeIfAbsent(b.value(), k -> new Shown("#missing:" + k, 0, missing(k)));
		}
		return new Shown(b.value(), (epoch << 32) | cache.version(b.value()), s);
	}

	/** A thread the client does not know (not connected yet, hidden, or gone). */
	static StationScreen missing(String key) {
		int colon = key.indexOf(':');
		String title = TextFit.clip(colon > 0 ? key.substring(colon + 1) : key, LAYOUT.width());
		return new StationScreen(List.of(
				new StationScreen.Line(title, WorkstationModel.DIM_COLOR),
				new StationScreen.Line("· OFFLINE", StationStatus.IDLE.color)), StationStatus.IDLE, 0xF0101010);
	}

	public void clear() {
		cache.clear();
		missing.clear();
		epoch++;
		revision++;
	}
}
