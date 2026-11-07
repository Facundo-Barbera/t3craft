package dev.t3craft.client.chat;

import com.google.gson.JsonObject;
import dev.t3craft.t3.StreamRegistry;
import dev.t3craft.t3.T3Connection;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Where the chat reads an environment from. The default ({@link ChatScreens#defaultSource()})
 * hands out the environment's single {@link T3Connection} (direct mode): the chat never opens a
 * socket of its own; thread streams are shared, refcounted subscriptions on that connection.
 * Tests and other hosts can pass their own.
 */
public interface ChatSource {
	/** Thrown with a player-facing reason (not paired, bridge mode, expired...). */
	final class Unavailable extends Exception {
		public Unavailable(String message) {
			super(message);
		}
	}

	/** Live read access to one environment. */
	interface Env {
		String id();

		String label();

		/** Connection status of the environment (CONNECTING, OK, OFFLINE…) and its secret-free detail. */
		T3Connection.Status status();

		String statusDetail();

		StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener);

		CompletableFuture<JsonObject> threadHistory(String threadId, String cursor);

		CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId);
	}

	Env env(String envId) throws Unavailable;

	/** Adapter over the environment's connection. */
	static Env of(T3Connection c) {
		return new Env() {
			@Override
			public String id() {
				return c.environment().id();
			}

			@Override
			public String label() {
				return c.environment().label();
			}

			@Override
			public T3Connection.Status status() {
				return c.status();
			}

			@Override
			public String statusDetail() {
				return c.detail();
			}

			@Override
			public StreamRegistry.Subscription subscribeThread(String threadId, StreamRegistry.Listener listener) {
				return c.subscribeThread(threadId, listener);
			}

			@Override
			public CompletableFuture<JsonObject> threadHistory(String threadId, String cursor) {
				return c.threadHistory(threadId, cursor);
			}

			@Override
			public CompletableFuture<@Nullable JsonObject> getTurnItem(String threadId, String itemId) {
				return c.getTurnItem(threadId, itemId);
			}
		};
	}
}
