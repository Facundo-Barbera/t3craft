package dev.t3craft.client.blocks;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The newest rows of a thread's log for the monitor config page's preview (T10): the rows a monitor's live log would
 * show, taken from the feed the client already has open for that thread (an open monitor of it near the player;
 * the page opens no stream of its own). Empty when there is no live feed, and then the page shows sample rows.
 */
public final class LogPreview {
	private LogPreview() {
	}

	/** At most {@code rows} newest rows of the thread {@code threadKey}, oldest first; empty when its log is not open. */
	public static List<MonitorLog.Row> latest(@Nullable String threadKey, int rows) {
		BlocksClient client = BlocksClient.get();
		if (client == null || threadKey == null || threadKey.isEmpty() || rows <= 0) {
			return List.of();
		}
		try {
			MonitorFeeds.Feed feed = client.feeds().feed(threadKey);
			if (feed == null || !feed.live()) {
				return List.of();
			}
			return MonitorLog.of(feed.transcript().view(), null, null, rows, 48).rows();
		} catch (RuntimeException e) {
			return List.of(); // a preview never breaks the page
		}
	}
}
