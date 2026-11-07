package dev.t3craft.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hot-swap safety net: a registry of {@link Resettable}s plus a cheap detector for class
 * redefinition. When a watched class changes shape, every registered resettable is reset so no
 * stale object of an old class layout survives in a map (the {@code ClassCastException} in
 * {@code NpcRegistry} after a reload).
 *
 * <p>How a module subscribes (once, e.g. in its constructor or entrypoint):
 * <pre>{@code
 * DevReload.get().register("npc-registry", reason -> npcs.clear(Minecraft.getInstance()),
 *         NpcRegistry.class, ThreadAvatar.class);
 * }</pre>
 * The id is the key: registering the same id again replaces the previous entry, so re-running
 * an initializer after a reload does not pile up duplicates. Pass the classes whose layout the
 * state depends on; a lambda's own class is synthetic and is never watched.
 *
 * <p>Detection is polled by the client glue ({@code dev.t3craft.client.debug.DevReloadClient}) every
 * couple of seconds in development only. Everything here is plain Java so it is unit-tested
 * without Minecraft. Thread-safe; resets run on the calling thread.
 */
public final class DevReload {
	private static final Logger LOG = LoggerFactory.getLogger("t3craft/devreload");
	private static final DevReload INSTANCE = new DevReload();

	private static final class Watched {
		final Class<?> type;
		long fingerprint;

		Watched(Class<?> type) {
			this.type = type;
			this.fingerprint = ClassFingerprint.of(type);
		}
	}

	private record Entry(String id, Resettable target, List<Watched> watched) {
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private boolean resetting;
	private long resets;
	private long redefinitions;

	/** Fresh instance, for tests. Production code uses {@link #get()}. */
	public DevReload() {
	}

	public static DevReload get() {
		return INSTANCE;
	}

	/** Registers (or replaces) {@code id}. {@code watch} are the classes whose layout the state depends on. */
	public synchronized void register(String id, Resettable target, Class<?>... watch) {
		List<Watched> w = new ArrayList<>();
		for (Class<?> c : watch) {
			if (c != null && !c.isSynthetic() && !c.isHidden()) {
				w.add(new Watched(c));
			}
		}
		Class<?> own = target.getClass();
		if (watch.length == 0 && !own.isSynthetic() && !own.isHidden() && !own.isAnonymousClass()) {
			w.add(new Watched(own));
		}
		entries.put(id, new Entry(id, target, List.copyOf(w)));
	}

	public synchronized void unregister(String id) {
		entries.remove(id);
	}

	public synchronized List<String> ids() {
		return List.copyOf(entries.keySet());
	}

	/** Total {@link #resetAll} runs so far. */
	public synchronized long resets() {
		return resets;
	}

	/** Times a watched class was seen to change shape. */
	public synchronized long redefinitions() {
		return redefinitions;
	}

	/**
	 * Recomputes the fingerprint of every watched class; if any changed, records the new ones and
	 * resets everything with {@link ResetReason#REDEFINITION}. Returns whether it did.
	 */
	public boolean checkForRedefinition() {
		List<String> changed = new ArrayList<>();
		synchronized (this) {
			for (Entry e : entries.values()) {
				for (Watched w : e.watched()) {
					long now = ClassFingerprint.of(w.type);
					if (now != w.fingerprint) {
						w.fingerprint = now;
						changed.add(w.type.getName());
					}
				}
			}
			if (changed.isEmpty()) {
				return false;
			}
			redefinitions++;
		}
		LOG.warn("class redefinition detected ({}); resetting in-memory state", String.join(", ", changed));
		resetAll(ResetReason.REDEFINITION);
		return true;
	}

	/**
	 * Resets every registered target, isolating failures. Re-entrant calls (a reset that triggers
	 * another reset) are ignored. Returns how many targets reset cleanly.
	 */
	public int resetAll(ResetReason reason) {
		List<Entry> snapshot;
		synchronized (this) {
			if (resetting) {
				return 0;
			}
			resetting = true;
			resets++;
			snapshot = List.copyOf(entries.values());
		}
		int ok = 0;
		try {
			for (Entry e : snapshot) {
				try {
					e.target().reset(reason);
					ok++;
				} catch (OutOfMemoryError oom) {
					throw oom;
				} catch (Throwable t) {
					LOG.error("reset of '{}' failed ({}); continuing with the rest", e.id(), reason, t);
				}
			}
		} finally {
			synchronized (this) {
				resetting = false;
			}
		}
		LOG.info("reset {}/{} registered state holder(s) ({})", ok, snapshot.size(), reason);
		return ok;
	}
}
