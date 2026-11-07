package dev.t3craft.client.archive;

import dev.t3craft.client.chat.ChatScreens;

/**
 * Opens a closed thread from the archive screen. Called on the client thread.
 *
 * <p>Default ({@link #DEFAULT}): the chat's thread browser on that thread, <b>read-only</b>
 * ({@link ChatScreens#openReadOnly}): its history over the environment's shared connection, and a
 * reply bar that only says "read-only" (no send, no "Enable control"), whatever the environment's
 * operate pairing. Replace it with {@code ArchiveClient.get().setOpener(...)}.
 */
@FunctionalInterface
public interface ArchiveOpener {
	void open(ArchiveEntry entry);

	/**
	 * What the archive asks the chat to open: always read-only.
	 *
	 * @param key {@code <env>:<threadId>}
	 */
	record Request(String key, String env, String threadId, String title, String envLabel, boolean readOnly) {
	}

	/** The request for {@code e}: its thread, labels for the header, read-only. */
	static Request request(ArchiveEntry e) {
		return new Request(e.key(), e.env(), e.threadId(), e.title(), e.envLabel(), true);
	}

	/** An opener that hands the {@link #request} to {@code sink}. */
	static ArchiveOpener of(java.util.function.Consumer<Request> sink) {
		return e -> sink.accept(request(e));
	}

	ArchiveOpener DEFAULT = of(r -> ChatScreens.openReadOnly(r.env(), r.threadId(), r.envLabel(), r.title()));
}
