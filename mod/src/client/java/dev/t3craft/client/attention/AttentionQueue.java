package dev.t3craft.client.attention;

import dev.t3craft.client.chat.ThreadOrder;
import dev.t3craft.client.chat.ThreadRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The attention queue as a pure decision (no Minecraft classes): which threads want the player, in
 * which order, and where the "next" key goes after the last jump. Docs: {@code docs/features/attention-queue.md}.
 *
 * <p>Order is the list's urgency order without the pinned-first rule (a pinned thread must not
 * outrank one that is blocked on you): needs you, error, approval pending; inside a class the most
 * recently active first. Only urgent rows with a known thread id are in the queue.
 *
 * <p>The cursor is the thread the last "next" jumped to. The next press moves to the one after it
 * (wrapping); when the cursor is gone (answered) or older than {@link #CURSOR_MS}, the cycle restarts
 * at the most urgent. Client thread only.
 */
public final class AttentionQueue {
	/** A cursor older than this is forgotten: the cycle restarts from the most urgent thread. */
	public static final long CURSOR_MS = 30_000;

	private static final Comparator<ThreadRow> URGENCY = ThreadOrder.<ThreadRow>of(r -> false, ThreadRow::attention, ThreadRow::activityMs,
			ThreadRow::settled, ThreadRow::title, ThreadRow::key);

	private @Nullable String cursor;
	private long cursorAt;
	private @Nullable String lastOpened;

	/** The urgent rows, most urgent first. */
	public static List<ThreadRow> rank(List<ThreadRow> rows) {
		List<ThreadRow> out = new ArrayList<>();
		for (ThreadRow r : rows) {
			if (r.needsAttention() && r.threadId() != null) {
				out.add(r);
			}
		}
		out.sort(URGENCY);
		return out;
	}

	/** Whether a press now continues a cycle (a seed such as "the thread that just asked" is then not wanted). */
	public boolean cycling(long nowMs) {
		return cursor != null && nowMs - cursorAt <= CURSOR_MS;
	}

	/**
	 * The thread a "next" press opens, or null when nobody waits. Moves the cursor onto it.
	 *
	 * @param recentAsk the thread whose needs-input notice just came in (it starts a fresh cycle when
	 *                  it is still urgent), or null
	 */
	public @Nullable ThreadRow next(List<ThreadRow> rows, @Nullable String recentAsk, long nowMs) {
		List<ThreadRow> queue = rank(rows);
		if (queue.isEmpty()) {
			cursor = null;
			return null;
		}
		int at = cycling(nowMs) ? indexOf(queue, cursor) : -1;
		int pick = at + 1;
		if (!cycling(nowMs) && recentAsk != null) {
			int asked = indexOf(queue, recentAsk);
			pick = asked >= 0 ? asked : 0;
		}
		ThreadRow row = queue.get(pick % queue.size());
		cursor = row.key();
		cursorAt = nowMs;
		lastOpened = row.key();
		return row;
	}

	/** Remembers a thread the player had open (any way of opening it). */
	public void noteOpened(String key) {
		lastOpened = key;
	}

	/** The thread the "back" key returns to, or null when none was opened yet. */
	public @Nullable String lastOpened() {
		return lastOpened;
	}

	private static int indexOf(List<ThreadRow> queue, @Nullable String key) {
		for (int i = 0; i < queue.size(); i++) {
			if (queue.get(i).key().equals(key)) {
				return i;
			}
		}
		return -1;
	}
}
