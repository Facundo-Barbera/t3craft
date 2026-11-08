package dev.t3craft.client.npc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * fix/hq-build-parallel: <em>when</em> NPCs may appear while the HQ is being built (where they appear
 * is fix/hq-npc-spawn's business). The structure goes up first; agents show up only once their room
 * and the way to their desk stand, instead of waiting around a half-built site.
 *
 * <ul>
 *   <li>{@link Mode#OPEN}: every stationed thread is spawned (normal play).</li>
 *   <li>{@link Mode#HOLD_ALL}: the HQ is built from scratch: nobody is spawned until the build is
 *   finished (NPCs already standing are taken away; they come back at their desks).</li>
 *   <li>{@link Mode#HOLD_NEW}: the HQ is growing (a diff for a new thread): NPCs already spawned keep
 *   working, a newcomer waits off-stage until its desk is built.</li>
 * </ul>
 * Set from the client thread by the demo's build run; read by {@link NpcRegistry} on the client thread.
 */
public final class NpcGate {
	public enum Mode {
		OPEN,
		HOLD_ALL,
		HOLD_NEW
	}

	private static volatile Mode mode = Mode.OPEN;
	private static volatile int version;

	private NpcGate() {
	}

	public static Mode mode() {
		return mode;
	}

	/** Bumped on every change, so the registry reconciles once when the gate opens. */
	public static int version() {
		return version;
	}

	public static void set(Mode m) {
		if (mode != m) {
			mode = m;
			version++;
		}
	}

	public static void open() {
		set(Mode.OPEN);
	}

	/** The NPCs of {@code wanted} that may be spawned now, given who is already {@code spawned}. Pure. */
	public static Map<String, NpcInfo> filter(Mode m, Map<String, NpcInfo> wanted, Set<String> spawned) {
		return switch (m) {
			case OPEN -> wanted;
			case HOLD_ALL -> Map.of();
			case HOLD_NEW -> {
				Map<String, NpcInfo> out = new LinkedHashMap<>();
				wanted.forEach((id, n) -> {
					if (spawned.contains(id)) {
						out.put(id, n);
					}
				});
				yield out;
			}
		};
	}

	/** {@link #filter(Mode, Map, Set)} with the live mode. */
	static Map<String, NpcInfo> shown(Map<String, NpcInfo> wanted, Collection<String> spawned) {
		return filter(mode, wanted, Set.copyOf(spawned));
	}
}
