package dev.t3craft.client.portable.pending;

import dev.t3craft.client.portable.PortableThread;

import org.jspecify.annotations.Nullable;

/**
 * Where the terminal's conversation panel (and the held-item screen) gets the open thread's
 * messages. One shared instance ({@link PortableClient#conversations()}), driven from the client
 * thread; implementations do their I/O off-thread and publish an immutable {@link Conversation}.
 *
 * <p>This is the seam for feat/chat: today {@link HttpConversationSource} is a minimal reader of
 * the bounded thread snapshot. When feat/chat exposes its live transcript, an adapter over
 * {@code ChatTranscript}/{@code ThreadFeed} implementing this interface replaces it with one call:
 * {@code PortableClient.setConversations(new ChatTranscriptSource())}. The panel does not change.
 * Must stay read-only.
 */
public interface ConversationSource extends AutoCloseable {
	/** Selects the thread to follow (null: none). Cheap; any fetch happens on {@link #tick}. */
	void select(@Nullable PortableThread thread);

	/**
	 * Called every client tick while someone shows the conversation (screen open or terminal held
	 * on its chat page). {@code thread} is the latest row of the selected thread (state/activity
	 * changes are the hint that new messages exist), or null if it left the list.
	 */
	void tick(@Nullable PortableThread thread, long nowMs);

	/** Latest published state; never null. */
	Conversation current();

	/** Bumped on every change of {@link #current()}, so views can cache their layout. */
	long revision();

	@Override
	void close();
}
