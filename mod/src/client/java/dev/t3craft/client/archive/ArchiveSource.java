package dev.t3craft.client.archive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.t3craft.t3.ArchiveWire;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.T3Connection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Where {@link ArchiveStore} reads from: the two read-only archive RPCs of one environment. The
 * futures may complete on any thread.
 *
 * <p>This module never sends requests itself. {@link #direct} is the thin adapter over each
 * environment's existing {@link T3Connection} ({@code getOlderSettledThreads} /
 * {@code getArchivedShellSnapshot}, both on the shared {@code StreamRegistry.call} path: one id
 * space, serialized sends, no socket of its own). {@link #of(Caller)} adapts any raw one-shot
 * caller (tests, other transports); {@link #UNAVAILABLE} lists only the shell's settled threads.
 */
public interface ArchiveSource {
	CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env);

	CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit);

	/** False when this source can never answer (the store then stops asking). */
	default boolean available() {
		return true;
	}

	/** Direct mode: the environment's connection from {@link DirectController#connection}. */
	static ArchiveSource direct(Supplier<@Nullable DirectController> controller) {
		return new ArchiveSource() {
			@Override
			public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
				T3Connection c = connection(env);
				return c == null ? offline(env) : c.getArchivedShellSnapshot();
			}

			@Override
			public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
				T3Connection c = connection(env);
				return c == null ? offline(env) : c.getOlderSettledThreads(before, limit);
			}

			private @Nullable T3Connection connection(String env) {
				DirectController d = controller.get();
				return d == null ? null : d.connection(env);
			}
		};
	}

	private static <T> CompletableFuture<T> offline(String env) {
		return CompletableFuture.failedFuture(new IllegalStateException("no connection for " + env));
	}

	/** One read-only, one-shot RPC on environment {@code env}: completes with the values (last = Exit value). */
	@FunctionalInterface
	interface Caller {
		CompletableFuture<List<JsonElement>> call(String env, String tag, JsonObject payload);
	}

	/** Thin adapter over a raw one-shot caller. */
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

	/** No reads beyond the shell: only its settled threads are listed. */
	ArchiveSource UNAVAILABLE = new ArchiveSource() {
		@Override
		public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException("archive reads unavailable"));
		}

		@Override
		public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException("archive reads unavailable"));
		}

		@Override
		public boolean available() {
			return false;
		}
	};
}
