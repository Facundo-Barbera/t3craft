package dev.t3craft.client.queue;

import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Which threads are worth watching for a queue, from the shell views alone (no extra calls): a
 * queue only builds up behind a busy run, so working threads, most recent run first, capped.
 * Pure.
 */
public final class QueueTargets {
	/** Background watches for the NPC badge. */
	public static final int BADGE_CAP = 4;
	/** Threads the queue screen watches at once. */
	public static final int SCREEN_CAP = 16;

	private QueueTargets() {
	}

	public static List<QueueStore.Key> working(Map<String, ShellState.View> views, int cap) {
		record Cand(QueueStore.Key key, long at) {
		}
		List<Cand> c = new ArrayList<>();
		views.forEach((env, view) -> {
			for (ThreadShell t : view.threads()) {
				if (t.archivedAt() == null && t.deletedAt() == null && ThreadState.of(t) == ThreadState.WORKING) {
					c.add(new Cand(QueueStore.Key.of(env, t.id()), latest(t)));
				}
			}
		});
		c.sort(Comparator.comparingLong(Cand::at).reversed().thenComparing(x -> x.key().toString()));
		List<QueueStore.Key> out = new ArrayList<>();
		for (int i = 0; i < c.size() && i < cap; i++) {
			out.add(c.get(i).key());
		}
		return out;
	}

	/** Thread title from the shell views, or null. */
	public static @Nullable String title(Map<String, ShellState.View> views, String env, String threadId) {
		ShellState.View v = views.get(env);
		if (v == null) {
			return null;
		}
		for (ThreadShell t : v.threads()) {
			if (t.id().equals(threadId)) {
				return t.title().isBlank() ? null : t.title();
			}
		}
		return null;
	}

	private static long latest(ThreadShell t) {
		Long[] ts = {t.latestRunRequestedAt(), t.latestRunStartedAt(), t.activityRunStartedAt(), t.latestUserMessageAt()};
		long best = 0;
		for (Long x : ts) {
			if (x != null && x > best) {
				best = x;
			}
		}
		return best;
	}
}
