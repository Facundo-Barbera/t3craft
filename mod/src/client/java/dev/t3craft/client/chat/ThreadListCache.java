package dev.t3craft.client.chat;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.Visibility;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The sidebar's thread rows, built once per shell change instead of on every tick of every screen
 * (feat/t3-thread-cache): {@link #rows} returns the same list instance while no environment's shell
 * view, label set or {@link #TIME_BUCKET_MS} time bucket changed, so {@code T3Screen} can skip its
 * rebuild too. Environments whose shell has not arrived yet (right after a game start) show the rows
 * kept on disk ({@link ThreadDiskCache}), flagged {@link #cached()} until the real ones come.
 *
 * <p>Also drives the housekeeping of {@link ThreadCache} and the disk cache from the client tick
 * ({@link #clientTick}). Client thread only.
 */
public final class ThreadListCache {
	/** Visibility rules depend on time (recently active, settled age): rebuild at least this often. */
	public static final long TIME_BUCKET_MS = 5_000;
	public static final int HOUSEKEEP_TICKS = 20;
	public static final long SAVE_EVERY_MS = 30_000;

	private static final ThreadListCache INSTANCE = new ThreadListCache();

	public static ThreadListCache get() {
		return INSTANCE;
	}

	private @Nullable ThreadDiskCache disk;
	private @Nullable List<Object> memoKey;
	private List<ThreadRow> memo = List.of();
	private boolean memoCached;
	/** Last rows built from real shells (what goes to disk). */
	private List<ThreadRow> lastReal = List.of();
	private int ticks;
	private long savedAt;

	private ThreadListCache() {
	}

	private ThreadDiskCache disk() {
		ThreadDiskCache d = disk;
		if (d == null) {
			ThreadDiskCache made;
			try {
				made = new ThreadDiskCache(ModConfig.dataDir());
			} catch (RuntimeException | LinkageError e) {
				made = new ThreadDiskCache(java.nio.file.Path.of("config", "t3craft"));
			}
			disk = d = made;
			d.loadAsync();
			ThreadDiskCache seeds = d;
			ThreadCache.get().setSeeds(seeds::tail);
		}
		return d;
	}

	/** True when the last {@link #rows} included rows from the disk cache. */
	public boolean cached() {
		return memoCached;
	}

	/** Direct mode rows (visible threads with activity, pending and pinned), memoized. */
	public List<ThreadRow> rows(DirectController direct) {
		long now = System.currentTimeMillis();
		Map<String, String> labels = direct.labels();
		Map<String, ShellState.View> views = direct.shellViews();
		ThreadDiskCache d = disk();
		List<Object> key = new ArrayList<>();
		key.add(now / TIME_BUCKET_MS);
		key.add(d.loadDone());
		labels.forEach((id, label) -> {
			key.add(id);
			key.add(label);
			key.add(System.identityHashCode(views.get(id)));
		});
		if (key.equals(memoKey)) {
			return memo;
		}
		memoKey = key;
		List<ThreadRow> out = new ArrayList<>();
		Map<String, Map<String, ThreadShell>> shells = new HashMap<>();
		for (NpcView v : direct.npcs(now)) {
			Map<String, ThreadShell> byId = shells.computeIfAbsent(v.env(), e -> {
				Map<String, ThreadShell> m = new HashMap<>();
				ShellState.View sv = views.get(e);
				if (sv != null) {
					sv.threads().forEach(s -> m.put(s.id(), s));
				}
				return m;
			});
			ThreadShell shell = byId.get(v.threadId());
			Long at = shell == null ? null : Visibility.lastActivity(shell);
			boolean pending = shell != null && shell.pendingRuntimeRequest() != null;
			out.add(ThreadRow.fromNpc(NpcInfo.fromView(v), at == null || at <= 0 ? null : at, pending, shell != null && shell.pinned()));
		}
		List<ThreadRow> real = List.copyOf(out);
		// environments still waiting for their first shell: the rows kept on disk, until it arrives
		boolean usedDisk = false;
		Set<String> waiting = new HashSet<>();
		labels.keySet().forEach(id -> {
			ShellState.View sv = views.get(id);
			if (sv == null || !sv.synced()) {
				waiting.add(id);
			}
		});
		if (!waiting.isEmpty()) {
			for (ThreadRow r : d.loaded().rows()) {
				if (waiting.contains(r.env()) && out.stream().noneMatch(o -> o.key().equals(r.key()))) {
					out.add(r);
					usedDisk = true;
				}
			}
		}
		if (!real.isEmpty() && waiting.isEmpty()) {
			lastReal = real;
		}
		List<ThreadRow> result = List.copyOf(out);
		if (!result.equals(memo)) {
			memo = result;
		}
		memoCached = usedDisk;
		return memo;
	}

	/**
	 * Client tick hook (cheap: real work once a second). Closes expired lingering subscriptions,
	 * drops cached threads that left their synced shell (archived/deleted), and writes the disk
	 * cache every {@link #SAVE_EVERY_MS} when something changed.
	 */
	public void clientTick(@Nullable DirectController direct) {
		if (++ticks % HOUSEKEEP_TICKS != 0) {
			return;
		}
		ThreadDiskCache d = disk();
		ThreadCache.get().tick(envId -> {
			if (direct == null) {
				return null;
			}
			ShellState.View v = direct.view(envId);
			if (v == null || !v.synced()) {
				return null;
			}
			Set<String> ids = new HashSet<>();
			v.threads().forEach(t -> ids.add(t.id()));
			return ids;
		});
		long now = System.currentTimeMillis();
		if (direct != null && d.loadDone() && now - savedAt >= SAVE_EVERY_MS) {
			save();
		}
	}

	/** Writes the list and the recent tails now (off-thread); used when the T3 screen closes. */
	public void save() {
		ThreadDiskCache d = disk();
		if (!d.loadDone()) {
			return; // never overwrite what is still being read
		}
		savedAt = System.currentTimeMillis();
		List<ThreadRow> rows = lastReal.isEmpty() ? d.loaded().rows() : lastReal;
		List<ThreadCache.Entry> recent = ThreadCache.get().recent(ThreadDiskCache.TAIL_THREADS);
		if (rows.isEmpty() && recent.isEmpty()) {
			return;
		}
		d.saveAsync(rows, recent);
	}
}
