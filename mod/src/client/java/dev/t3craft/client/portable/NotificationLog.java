package dev.t3craft.client.portable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Recent thread notices for the portable terminal. It does <b>not</b> decide what is notable: it
 * records exactly what the mod already announces through {@code hud.Notifier.transition} (direct
 * mode: {@code t3.Transitions}, silent seeding included; bridge mode: {@code npc.transition}), so
 * the terminal and the chat/toast/sound notices can never disagree.
 * <p>Bounded ring of {@link #CAPACITY}, newest first, unread counter until {@link #markAllRead}.
 * Fed and read on the client thread.
 */
public final class NotificationLog {
	public static final int CAPACITY = 50;

	/** Shared log fed by the Notifier hook. */
	public static final NotificationLog INSTANCE = new NotificationLog();

	/**
	 * @param npcId {@code <env>:<threadId>} when known (lets the terminal open that thread), else null
	 * @param sound attention | done | error | "" (as decided by the transition logic)
	 */
	public record Entry(@Nullable String npcId, String title, String text, int color, String sound, long atMs) {
	}

	private final Deque<Entry> entries = new ArrayDeque<>();
	private int unread;

	public void record(Entry e) {
		entries.addFirst(e);
		while (entries.size() > CAPACITY) {
			entries.removeLast();
		}
		unread = Math.min(unread + 1, CAPACITY);
	}

	public List<Entry> entries() {
		return new ArrayList<>(entries);
	}

	public int size() {
		return entries.size();
	}

	public int unread() {
		return unread;
	}

	public void markAllRead() {
		unread = 0;
	}

	public void clear() {
		entries.clear();
		unread = 0;
	}
}
