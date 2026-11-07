package dev.t3craft.t3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Multiplexes per-thread streams ({@code orchestration.subscribeThread}) and one-shot read RPCs
 * ({@code getTurnItem}, {@code getThreadProjection}, …) over the single socket of a
 * {@link T3Connection}. Pure logic (no I/O, unit-tested): the connection gives it a {@link Sender}
 * and feeds it the frames it reads.
 *
 * <ul>
 *   <li><b>Refcount</b>: every {@link #subscribe} of the same thread shares one server
 *   subscription. The first subscriber sends the request; the last {@link Subscription#close()}
 *   sends an Interrupt and forgets the thread.</li>
 *   <li><b>Late joiners</b>: the items received since the subscription started (bounded snapshot +
 *   events) are kept and replayed to a new subscriber, so it sees the same state as the others.
 *   Past {@link #REPLAY_CAP} items the buffer is dropped and a late joiner forces a fresh
 *   subscription for everyone ({@link Listener#onSubscribed()} then a new snapshot).</li>
 *   <li><b>Reconnect</b>: {@link #onOpen} (re)subscribes every live thread with a new request id;
 *   {@link #onClosed} fails pending one-shot calls and tells subscribers the stream paused.</li>
 * </ul>
 * Thread-safe: subscribe/close come from the client thread, frames from the connection thread.
 * Listener callbacks run on the connection thread, outside the registry lock.
 */
public final class StreamRegistry {
	/** Items kept per thread for late joiners before falling back to a resubscribe. */
	public static final int REPLAY_CAP = 4000;

	/** Sends one text frame on the current socket; false when there is none. */
	@FunctionalInterface
	public interface Sender {
		boolean send(String frame);
	}

	/** Receives one thread's stream. Called on the connection thread. */
	public interface Listener {
		/** A (re)subscription started: drop what you have, a fresh snapshot follows. */
		void onSubscribed();

		/** Stream items in order ({@code snapshot}, {@code synchronized}, {@code event}…). */
		void onItems(List<JsonObject> items);

		/**
		 * The stream stopped: {@code failure} null = the socket went away (it resumes on reconnect),
		 * else the server ended it with that short, secret-free reason (e.g. thread not found).
		 */
		void onEnded(@Nullable String failure);
	}

	/** Handle of one subscriber; {@link #close()} is idempotent. */
	public interface Subscription extends AutoCloseable {
		@Override
		void close();
	}

	private Sender sender = f -> false;
	private boolean open;
	private long nextId = 100; // "1" is the shell subscription
	private final Map<String, Entry> threads = new LinkedHashMap<>();
	private final Map<String, Entry> byRequest = new LinkedHashMap<>();
	private final Map<String, Call> calls = new LinkedHashMap<>();

	private static final class Entry {
		final String threadId;
		final List<Listener> listeners = new ArrayList<>();
		final List<JsonObject> replay = new ArrayList<>();
		boolean replayOverflow;
		@Nullable String requestId;
		@Nullable String endedWith;

		Entry(String threadId) {
			this.threadId = threadId;
		}
	}

	private record Call(CompletableFuture<List<JsonElement>> result, List<JsonElement> values) {
	}

	/**
	 * @param subscribeFrame builds the subscribe request for {@code (requestId, threadId)}; the
	 *                       connection passes {@link RpcFrames#subscribeThread}
	 */
	public StreamRegistry(SubscribeFrame subscribeFrame) {
		this.frames = subscribeFrame;
	}

	/** Builds a subscribe request frame. */
	@FunctionalInterface
	public interface SubscribeFrame {
		String build(String requestId, String threadId);
	}

	private final SubscribeFrame frames;

	// ---- subscriptions --------------------------------------------------------------------------

	/** Number of distinct threads with at least one subscriber. */
	public synchronized int activeThreads() {
		return threads.size();
	}

	/** Subscribers of one thread (0 when none). */
	public synchronized int refCount(String threadId) {
		Entry e = threads.get(threadId);
		return e == null ? 0 : e.listeners.size();
	}

	public Subscription subscribe(String threadId, Listener l) {
		boolean resubscribed = false;
		String toSend = null;
		synchronized (this) {
			Entry e = threads.get(threadId);
			if (e == null) {
				e = new Entry(threadId);
				threads.put(threadId, e);
				e.listeners.add(l);
				safe(l::onSubscribed);
				if (open) {
					toSend = start(e);
				}
			} else {
				e.listeners.add(l);
				if (e.replayOverflow && open) {
					// too much history to replay: everybody starts over from a fresh snapshot
					toSend = restart(e);
					resubscribed = true;
				} else {
					// Under the lock, so the replay reaches this listener before any chunk that
					// arrives meanwhile (chunks append to the replay under the same lock first).
					safe(l::onSubscribed);
					List<JsonObject> r = List.copyOf(e.replay);
					if (!r.isEmpty()) {
						safe(() -> l.onItems(r));
					}
				}
			}
		}
		if (toSend != null) {
			for (String f : drainInterrupts()) {
				sender.send(f);
			}
			if (resubscribed) {
				notifySubscribed(threadId); // reset everyone before the new snapshot can arrive
			}
			sender.send(toSend);
		}
		Listener self = l;
		return new Subscription() {
			private boolean closed;

			@Override
			public void close() {
				if (closed) {
					return;
				}
				closed = true;
				release(threadId, self);
			}
		};
	}

	private void release(String threadId, Listener l) {
		String interrupt = null;
		synchronized (this) {
			Entry e = threads.get(threadId);
			if (e == null || !e.listeners.remove(l)) {
				return;
			}
			if (e.listeners.isEmpty()) {
				threads.remove(threadId);
				if (e.requestId != null) {
					byRequest.remove(e.requestId);
					if (open && e.endedWith == null) {
						interrupt = RpcFrames.interrupt(e.requestId);
					}
				}
			}
		}
		if (interrupt != null) {
			sender.send(interrupt);
		}
	}

	/** Under the lock: new request id, clears replay; returns the frame to send. */
	private String start(Entry e) {
		String id = Long.toString(nextId++);
		e.requestId = id;
		e.endedWith = null;
		e.replay.clear();
		e.replayOverflow = false;
		byRequest.put(id, e);
		return frames.build(id, e.threadId);
	}

	/**
	 * Under the lock: queues an Interrupt for the old request (if any) and starts a new one. Returns
	 * the new subscribe frame; the interrupt goes out first ({@link #drainInterrupts}).
	 */
	private String restart(Entry e) {
		if (e.requestId != null) {
			byRequest.remove(e.requestId);
			if (e.endedWith == null) {
				pendingInterrupts.add(RpcFrames.interrupt(e.requestId));
			}
		}
		return start(e);
	}

	private final List<String> pendingInterrupts = new ArrayList<>();

	// ---- one-shot calls -------------------------------------------------------------------------

	/** Builds a request frame for a request id this registry allocated. */
	@FunctionalInterface
	public interface RequestFrame {
		String build(String requestId);
	}

	/**
	 * Sends a one-shot read request ({@code tag} + payload) and completes with every value it
	 * returns (chunks, then the exit value if any). Fails when the socket is down or goes away, or
	 * when the server answers with a failure. Cancelling the future sends an Interrupt.
	 */
	public CompletableFuture<List<JsonElement>> call(String tag, JsonObject payload) {
		return call(id -> RpcFrames.request(id, tag, payload));
	}

	/** Same, with the caller's frame builder (e.g. {@link RpcFrames#searchThreads}); one id space for everything. */
	public CompletableFuture<List<JsonElement>> call(RequestFrame frameFor) {
		CompletableFuture<List<JsonElement>> f = new CompletableFuture<>();
		String frame;
		synchronized (this) {
			if (!open) {
				f.completeExceptionally(new IllegalStateException("not connected"));
				return f;
			}
			String id = Long.toString(nextId++);
			calls.put(id, new Call(f, new ArrayList<>()));
			frame = frameFor.build(id);
		}
		f.whenComplete((v, e) -> {
			if (f.isCancelled()) {
				abandon(f);
			}
		});
		if (!sender.send(frame)) {
			failCall(f, "not connected");
		}
		return f;
	}

	/**
	 * Gives up on a pending call (timeout, cancel): forgets it and interrupts it on the server.
	 * Returns false when it was no longer pending (already answered or abandoned). A late answer is
	 * acked and dropped.
	 */
	public boolean abandon(CompletableFuture<?> f) {
		String interrupt = null;
		synchronized (this) {
			String id = null;
			for (Map.Entry<String, Call> e : calls.entrySet()) {
				if (e.getValue().result() == f) {
					id = e.getKey();
					break;
				}
			}
			if (id == null) {
				return false;
			}
			calls.remove(id);
			if (open) {
				interrupt = RpcFrames.interrupt(id);
			}
		}
		if (interrupt != null) {
			sender.send(interrupt);
		}
		return true;
	}

	private void failCall(CompletableFuture<List<JsonElement>> f, String why) {
		synchronized (this) {
			calls.values().removeIf(c -> c.result() == f);
		}
		f.completeExceptionally(new IllegalStateException(why));
	}

	public synchronized int pendingCalls() {
		return calls.size();
	}

	// ---- socket lifecycle -----------------------------------------------------------------------

	/** The socket is up: (re)subscribe every live thread. */
	public void onOpen(Sender s) {
		List<String> frames = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		synchronized (this) {
			sender = s;
			open = true;
			byRequest.clear();
			for (Entry e : threads.values()) {
				frames.add(start(e));
				ids.add(e.threadId);
			}
		}
		ids.forEach(this::notifySubscribed); // reset before the new snapshots can arrive
		frames.forEach(s::send);
	}

	/** The socket went away: pending calls fail, subscribers pause until {@link #onOpen}. */
	public void onClosed() {
		List<Call> failed;
		List<Listener> paused = new ArrayList<>();
		synchronized (this) {
			open = false;
			sender = f -> false;
			failed = new ArrayList<>(calls.values());
			calls.clear();
			byRequest.clear();
			for (Entry e : threads.values()) {
				e.requestId = null;
				paused.addAll(e.listeners);
			}
		}
		failed.forEach(c -> c.result().completeExceptionally(new IllegalStateException("connection lost")));
		paused.forEach(l -> safe(() -> l.onEnded(null)));
	}

	// ---- frames ---------------------------------------------------------------------------------

	/**
	 * A Chunk for a request this registry owns: routes it and returns true (the caller acks it).
	 * False for request ids it does not know (the shell's, or stale ones after a close).
	 */
	public boolean onChunk(String requestId, List<JsonObject> values) {
		List<Listener> to;
		synchronized (this) {
			Call c = calls.get(requestId);
			if (c != null) {
				c.values().addAll(values);
				return true;
			}
			Entry e = byRequest.get(requestId);
			if (e == null) {
				return isStaleThreadRequest(requestId);
			}
			if (!e.replayOverflow) {
				if (e.replay.size() + values.size() > REPLAY_CAP) {
					e.replay.clear();
					e.replayOverflow = true;
				} else {
					e.replay.addAll(values);
				}
			}
			to = List.copyOf(e.listeners);
		}
		List<JsonObject> v = List.copyOf(values);
		to.forEach(l -> safe(() -> l.onItems(v)));
		return true;
	}

	/** Ids above the shell's are ours even after their entry is gone: still ack them. */
	private static boolean isStaleThreadRequest(String requestId) {
		try {
			return Long.parseLong(requestId) >= 100;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** An Exit for one of our requests (true) or not (false). {@code value}: success value, if any. */
	public boolean onExit(String requestId, @Nullable String failure, @Nullable JsonElement value) {
		List<Listener> to = null;
		Call c;
		synchronized (this) {
			c = calls.remove(requestId);
			if (c == null) {
				Entry e = byRequest.get(requestId);
				if (e == null) {
					return isStaleThreadRequest(requestId);
				}
				e.endedWith = failure == null ? "ended" : failure;
				byRequest.remove(requestId);
				to = List.copyOf(e.listeners);
			}
		}
		if (c != null) {
			if (failure != null) {
				c.result().completeExceptionally(new IllegalStateException(failure));
			} else {
				if (value != null) {
					c.values().add(value);
				}
				c.result().complete(List.copyOf(c.values()));
			}
			return true;
		}
		String why = failure == null ? "thread subscription ended" : failure;
		to.forEach(l -> safe(() -> l.onEnded(why)));
		return true;
	}

	/** Interrupts queued by a forced resubscribe; the connection sends them. */
	public synchronized List<String> drainInterrupts() {
		List<String> out = List.copyOf(pendingInterrupts);
		pendingInterrupts.clear();
		return out;
	}

	private void notifySubscribed(String threadId) {
		List<Listener> to;
		synchronized (this) {
			Entry e = threads.get(threadId);
			to = e == null ? List.of() : List.copyOf(e.listeners);
		}
		to.forEach(l -> safe(l::onSubscribed));
	}

	private static void safe(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException ignored) {
			// one broken listener must not starve the others or kill the connection thread
		}
	}
}
