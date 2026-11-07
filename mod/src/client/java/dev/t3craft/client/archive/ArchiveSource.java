package dev.t3craft.client.archive;

import com.google.gson.JsonObject;
import dev.t3craft.t3.ArchiveWire;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Where {@link ArchiveStore} reads from: the two read-only archive RPCs of one environment. The
 * futures may complete on any thread.
 *
 * <p>This module never sends requests itself. {@link #of(Caller)} adapts the connection's shared
 * one-shot read path ({@code StreamRegistry.call(tag, payload)} from feat/chat, which completes with
 * the Exit's values) to the typed reads; payloads and decoding are in {@link ArchiveWire}. Until
 * that path is on main, {@link #UNAVAILABLE} is used and the archive shows what
 * {@code subscribeShell} already gives (settled threads), without archived or older pages.
 */
public interface ArchiveSource {
	CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env);

	CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit);

	/** False when this source can never answer (the store then stops asking). */
	default boolean available() {
		return true;
	}

	/** One read-only, one-shot RPC on environment {@code env}'s existing connection. */
	@FunctionalInterface
	interface Caller {
		CompletableFuture<List<JsonObject>> call(String env, String tag, JsonObject payload);
	}

	/** Thin adapter over the shared one-shot read path. */
	static ArchiveSource of(Caller caller) {
		return new ArchiveSource() {
			@Override
			public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
				return caller.call(env, ArchiveWire.GET_ARCHIVED_SHELL_SNAPSHOT, ArchiveWire.archivedRequest())
						.thenApply(ArchiveWire::decodeArchivedValues);
			}

			@Override
			public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
				return caller.call(env, ArchiveWire.GET_OLDER_SETTLED_THREADS, ArchiveWire.olderSettledRequest(before, limit, null))
						.thenApply(ArchiveWire::decodeOlderSettledValues);
			}
		};
	}

	/** No one-shot read path yet: only the shell's settled threads are shown. */
	ArchiveSource UNAVAILABLE = new ArchiveSource() {
		@Override
		public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException("archive reads need the shared one-shot path"));
		}

		@Override
		public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException("archive reads need the shared one-shot path"));
		}

		@Override
		public boolean available() {
			return false;
		}
	};
}
