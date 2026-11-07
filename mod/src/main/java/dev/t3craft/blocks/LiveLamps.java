package dev.t3craft.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-side lamps that are currently loaded (added when their block entity joins a server level,
 * removed when it is removed or unloaded). Weak, so a missed removal (crash, hot-swap) never leaks a
 * level. Only server-thread code touches it; synchronized anyway because it is cheap.
 */
public final class LiveLamps {
	private static final Set<LampBlockEntity> LAMPS = Collections.newSetFromMap(new WeakHashMap<>());

	private LiveLamps() {
	}

	static synchronized void add(LampBlockEntity lamp) {
		LAMPS.add(lamp);
	}

	static synchronized void remove(LampBlockEntity lamp) {
		LAMPS.remove(lamp);
	}

	/** A copy, safe to iterate while lamps change state. */
	public static synchronized List<LampBlockEntity> snapshot() {
		return new ArrayList<>(LAMPS);
	}

	public static synchronized int size() {
		return LAMPS.size();
	}

	public static synchronized void clear() {
		LAMPS.clear();
	}
}
