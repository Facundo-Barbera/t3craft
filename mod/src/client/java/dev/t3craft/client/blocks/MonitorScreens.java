package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.TextFit;
import dev.t3craft.client.workstation.WorkstationCache;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
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
 * <p>Large screens ({@link #show(Binding, int, int)}, sized by the renderer from the screen's extent):
 * a thread gets the same model with the screen's width and as many activity lines as fit (P2 replaces
 * it with the live log); a project or environment gets its {@link ProjectFeed}. Both are memoised and
 * rebuilt only when their source changes.
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

	private static final int MAX_LARGE = 64;

	private record Large(long source, StationScreen screen, long version) {
	}

	private final WorkstationCache cache = new WorkstationCache(new WorkstationModel(LAYOUT));
	private final Map<String, WorkstationThread> threads = new HashMap<>();
	private final Map<String, Large> large = new HashMap<>();
	private long revision;
	private long largeVersions;

	/** Feeds every known thread; returns true when any screen changed. */
	public boolean tick(Collection<WorkstationThread> threads, long nowMs) {
		boolean changed = false;
		Set<String> keys = new HashSet<>();
		this.threads.clear();
		for (WorkstationThread t : threads) {
			keys.add(t.key());
			this.threads.put(t.key(), t);
			changed |= cache.update(t, nowMs) != null;
		}
		int before = cache.size();
		cache.retain(keys);
		changed |= cache.size() != before;
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
			return new Shown("#unbound", 0, UNBOUND);
		}
		StationScreen s = cache.screen(b.value());
		if (s == null) {
			return new Shown("#missing:" + b.value(), 0, missing(b.value()));
		}
		return new Shown(b.value(), cache.version(b.value()), s);
	}

	/**
	 * The content of a large screen of {@code cols} characters × {@code rows} lines: a thread's screen
	 * at that size, or a project / environment feed. Other bindings show what a lone monitor shows.
	 */
	public Shown show(Binding b, int cols, int rows) {
		cols = Math.max(8, cols);
		rows = Math.max(2, rows);
		String id = b.wire() + "@" + cols + "x" + rows;
		if (b.isFeed()) {
			return memo(id, revision, () -> ProjectFeed.render(b, threads.values(), cols, rows));
		}
		if (b.kind() != Binding.Kind.THREAD || cache.screen(b.value()) == null) {
			return show(b);
		}
		WorkstationThread t = threads.get(b.value());
		if (t == null) {
			return show(b);
		}
		WorkstationModel model = new WorkstationModel(new WorkstationModel.Layout(cols, Math.max(1, rows - 3)));
		String age = cache.ageLabel(b.value());
		return memo(id, cache.version(b.value()), () -> model.render(t, age));
	}

	private Shown memo(String id, long source, java.util.function.Supplier<StationScreen> build) {
		Large l = large.get(id);
		if (l == null || l.source() != source) {
			StationScreen s = build.get();
			// versions come from one counter, so a cleared memo never reuses a version for other content
			long version = l != null && s.equals(l.screen()) ? l.version() : ++largeVersions;
			if (large.size() > MAX_LARGE) {
				large.clear();
			}
			l = new Large(source, s, version);
			large.put(id, l);
		}
		return new Shown(id, l.version(), l.screen());
	}

	/** Threads fed by the last {@link #tick} (feeds, tests). */
	List<WorkstationThread> threads() {
		return new ArrayList<>(threads.values());
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
		threads.clear();
		large.clear();
		revision++;
	}
}
