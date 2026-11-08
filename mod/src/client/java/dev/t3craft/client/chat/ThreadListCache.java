package dev.t3craft.client.chat;

import dev.t3craft.client.ModConfig;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.Presentation;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The sidebar's thread rows, built once per shell change instead of on every tick of every screen
 * (feat/t3-thread-cache): {@link #rows} returns the same list instance while no environment's shell
 * view or label set changed, so {@code T3Screen} can skip its rebuild too. The rows are every
 * listable thread of each full shell (pinned and settled included), not the NPC selection. Environments whose shell has not arrived yet (right after a game start) show the rows
 * kept on disk ({@link ThreadDiskCache}), flagged {@link #cached()} until the real ones come.
 *
 * <p>Also drives the housekeeping of {@link ThreadCache} and the disk cache from the client tick
 * ({@link #clientTick}). Client thread only.
 */
public final class ThreadListCache {
	/** Unused since fix/t3-sidebar-all-threads (rows no longer depend on time); kept for callers. */
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

	/**
	 * Direct mode rows, memoized: every thread of every environment's full shell, as T3 Code's sidebar
	 * lists them (fix/t3-sidebar-all-threads). Not the NPC list: {@link Visibility#select} (24 h window,
	 * 40 per environment) only decides who gets an NPC, so an old pinned thread was missing here.
	 * Only archived, deleted, subagent and side-chat threads are left out; pinned and settled ones stay
	 * (the sidebar groups them). Order: pinned, then active by activity, then settled.
	 */
	public List<ThreadRow> rows(DirectController direct) {
		Map<String, String> labels = direct.labels();
		Map<String, ShellState.View> views = direct.shellViews();
		ThreadDiskCache d = disk();
		// rows no longer depend on time (no recent window): only shells, labels and the disk load
		List<Object> key = new ArrayList<>();
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
		labels.forEach((env, label) -> {
			ShellState.View sv = views.get(env);
			if (sv == null) {
				return;
			}
			for (ThreadShell t : sv.threads()) {
				if (listed(t)) {
					out.add(row(env, label, t, sv.projectTitle(t.projectId())));
				}
			}
		});
		out.sort(SAVE_ORDER);
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
			Set<String> have = new HashSet<>();
			out.forEach(o -> have.add(o.key()));
			for (ThreadRow r : d.loaded().rows()) {
				if (waiting.contains(r.env()) && have.add(r.key())) {
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

	/** In the sidebar: everything but archived, deleted, subagents and side chats (T3 Code's rule). */
	public static boolean listed(ThreadShell t) {
		return t.archivedAt() == null && t.deletedAt() == null && !"subagent".equals(t.relationshipToParent()) && !t.sideChat();
	}

	/**
	 * One sidebar row from a shell thread (same title/state/activity as its NPC would have), with the
	 * rich-row details (branch, PR, diff, model, unread, run start, snooze: feat/t3-sidebar-rows).
	 */
	static ThreadRow row(String env, String envLabel, ThreadShell t, @Nullable String projectTitle) {
		NpcView v = Presentation.present(env, envLabel, t, projectTitle);
		ThreadRow r = ThreadRow.fromNpc(NpcInfo.fromView(v), null, false, false);
		long at = Visibility.lastActivity(t);
		return new ThreadRow(r.key(), r.env(), r.envLabel(), r.threadId(), r.title(), r.project(), r.state(),
				t.pendingRuntimeRequest() != null, r.activity(), Visibility.isSettled(t), at <= 0 ? null : at, t.pinned(),
				ThreadDetails.of(t, r.state()));
	}

	/**
	 * Pinned first, then active threads, then settled, each by most recent activity: also the order
	 * written to disk, so the {@link ThreadDiskCache#MAX_ROWS} cap never drops a pinned thread.
	 */
	static final Comparator<ThreadRow> SAVE_ORDER = Comparator.comparing((ThreadRow r) -> !r.pinned())
			.thenComparing(ThreadRow::settled)
			.<Long>thenComparing(r -> r.activityMs() == null ? Long.MIN_VALUE : r.activityMs(), Comparator.reverseOrder())
			.thenComparing(ThreadRow::key);

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
