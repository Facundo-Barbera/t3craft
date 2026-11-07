package dev.t3craft.client.blocks;

import dev.t3craft.client.chat.ChatTranscript;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The live thread streams the monitors hold: at most <b>one feed per thread key</b>, whatever the
 * number of monitors showing it (and the feed itself is a refcounted share of the connection's
 * {@code subscribeThread}, so a chat open on the same thread adds no server stream either).
 * {@link #retain} opens what became wanted and closes what stopped being wanted. Read-only: feeds
 * never send anything that needs {@code orchestration:operate}.
 *
 * <p>Opening can fail (bridge mode, environment not paired, not connected yet): the key then waits
 * {@link #RETRY_MS} before it is tried again, and the monitor keeps its status-only screen.
 * Client thread only; the transcripts themselves are thread-safe.
 */
public final class MonitorFeeds implements AutoCloseable {
	static final long RETRY_MS = 15_000;

	/** One open stream. */
	public interface Feed extends AutoCloseable {
		ChatTranscript transcript();

		/** True once the snapshot arrived and the stream is up. */
		boolean live();

		@Override
		void close();
	}

	/** Opens the feed of a thread key ({@code <envId>:<threadId>}); null = not possible now. */
	@FunctionalInterface
	public interface Opener {
		@Nullable Feed open(String threadKey);
	}

	private final Opener opener;
	private final Map<String, Feed> feeds = new LinkedHashMap<>();
	private final Map<String, Long> failedAt = new HashMap<>();

	public MonitorFeeds(Opener opener) {
		this.opener = opener;
	}

	/** Makes the open feeds exactly {@code wanted} (minus keys that cannot open yet). */
	public void retain(Set<String> wanted, long nowMs) {
		List<String> drop = new ArrayList<>();
		for (String k : feeds.keySet()) {
			if (!wanted.contains(k)) {
				drop.add(k);
			}
		}
		for (String k : drop) {
			closeQuietly(feeds.remove(k));
		}
		failedAt.keySet().retainAll(wanted);
		for (String k : wanted) {
			if (feeds.containsKey(k)) {
				continue;
			}
			Long failed = failedAt.get(k);
			if (failed != null && nowMs - failed < RETRY_MS) {
				continue;
			}
			Feed f;
			try {
				f = opener.open(k);
			} catch (RuntimeException e) {
				f = null;
			}
			if (f == null) {
				failedAt.put(k, nowMs);
			} else {
				failedAt.remove(k);
				feeds.put(k, f);
			}
		}
	}

	public @Nullable Feed feed(String threadKey) {
		return feeds.get(threadKey);
	}

	public int size() {
		return feeds.size();
	}

	public Collection<String> keys() {
		return List.copyOf(feeds.keySet());
	}

	@Override
	public void close() {
		feeds.values().forEach(MonitorFeeds::closeQuietly);
		feeds.clear();
		failedAt.clear();
	}

	private static void closeQuietly(@Nullable Feed f) {
		if (f != null) {
			try {
				f.close();
			} catch (RuntimeException ignored) {
				// a broken feed must not keep the others open
			}
		}
	}
}
