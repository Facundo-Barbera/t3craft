package dev.t3craft.client.hq;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.VisibilityPolicy;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Presentation;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.Visibility;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Which projects get a <b>room</b> (an HQ module with its sign and its bulb) — fix/hq-rooms-real.
 * Shared by the site HQ ({@link HqConstructionClient}) and the demo HQ ({@code DemoHq}).
 *
 * <p>A project has a room only while at least one of its threads that keeps a station (not settled
 * or released: the desk rule is fix/hq-stale-stations' {@code StationPolicy}, applied before this)
 * had real activity (message or run, {@link Visibility#lastActivity}) within the environment's
 * visibility window ({@code visibility.recentWindowHours}, per-environment override in the config
 * screen; 24 h by default). Visibility alone is not enough: a thread that "needs you" is shown
 * whatever its age, so a project idle for a month could keep a room forever. Every NPC of a project
 * without a room is dropped from the HQ roster (no desk, no archive entry), and the HQ diff removes
 * its module.
 *
 * <p>Threads whose last activity is unknown (bridge mode: the bridge already filtered by its own
 * window) count as active. Pure apart from {@link #current()}, which reads the live client.
 */
public final class HqRooms {
	private HqRooms() {
	}

	/** What the filter needs to know about the connected environments. */
	public interface Context {
		/** True when at least one environment is paired (then the demo never builds its example roster). */
		boolean paired();

		/** Last message/run of the thread behind NPC {@code npcId} (epoch ms), or {@code <= 0} when unknown. */
		long lastActivityMs(String npcId);

		/** Visibility window of environment {@code env} (ms). */
		long windowMs(String env);
	}

	/** Nothing paired, no timestamps: every project counts as active (the old behaviour). */
	public static final Context NONE = context(false, Map.of(), env -> Visibility.DEFAULT_RECENT_WINDOW_MS);

	public static Context context(boolean paired, Map<String, Long> lastActivity, java.util.function.ToLongFunction<String> windowMs) {
		Map<String, Long> last = Map.copyOf(lastActivity);
		Objects.requireNonNull(windowMs, "windowMs");
		return new Context() {
			@Override
			public boolean paired() {
				return paired;
			}

			@Override
			public long lastActivityMs(String npcId) {
				Long v = last.get(npcId);
				return v == null ? 0 : v;
			}

			@Override
			public long windowMs(String env) {
				return windowMs.applyAsLong(env);
			}
		};
	}

	private static volatile Supplier<Context> source = HqRooms::fromClient;

	/** Tests and other hosts: where {@link #current()} reads from (null = the live client). */
	public static void setSource(@Nullable Supplier<Context> s) {
		source = s == null ? HqRooms::fromClient : s;
	}

	/** The live context (never throws: {@link #NONE} when the client is not up). */
	public static Context current() {
		try {
			Context c = source.get();
			return c == null ? NONE : c;
		} catch (RuntimeException | LinkageError e) {
			return NONE;
		}
	}

	/** Environment of an NPC: the part of its {@code groupId} ({@code env:project}) before the colon. */
	public static String env(NpcInfo n) {
		String g = n.groupId();
		int colon = g.indexOf(':');
		return colon > 0 ? g.substring(0, colon) : g;
	}

	/** True when the thread had activity within its environment's window (or its activity is unknown). */
	public static boolean recent(NpcInfo n, Context c, long nowMs) {
		long last = c.lastActivityMs(n.id());
		return last <= 0 || nowMs - last <= c.windowMs(env(n));
	}

	/** Project keys ({@code groupId}) that have a room now. */
	public static Set<String> rooms(Collection<NpcInfo> npcs, Predicate<NpcInfo> released, Context c, long nowMs) {
		Set<String> out = new HashSet<>();
		for (NpcInfo n : npcs) {
			if (!released.test(n) && recent(n, c, nowMs)) {
				out.add(n.groupId());
			}
		}
		return out;
	}

	/** {@code npcs} without the threads of projects that have no room (order kept). */
	public static List<NpcInfo> filter(Collection<NpcInfo> npcs, Predicate<NpcInfo> released, Context c, long nowMs) {
		Set<String> rooms = rooms(npcs, released, c, nowMs);
		List<NpcInfo> out = new ArrayList<>(npcs.size());
		for (NpcInfo n : npcs) {
			if (rooms.contains(n.groupId())) {
				out.add(n);
			}
		}
		return out;
	}

	/** {@link #filter} with {@link NpcInfo#settled()} as the release rule. */
	public static List<NpcInfo> filter(Collection<NpcInfo> npcs, Context c, long nowMs) {
		return filter(npcs, NpcInfo::settled, c, nowMs);
	}

	// ---- live client ----------------------------------------------------------------------------

	private static Context fromClient() {
		T3CraftClient c = T3CraftClient.get();
		if (c == null) {
			return NONE;
		}
		boolean paired = !c.environments().isEmpty();
		Map<String, Long> last = new HashMap<>();
		DirectController d = c.direct();
		if (d != null) {
			for (Map.Entry<String, ShellState.View> e : d.shellViews().entrySet()) {
				for (ThreadShell t : e.getValue().threads()) {
					last.put(Presentation.npcId(e.getKey(), t.id()), Visibility.lastActivity(t));
				}
			}
		}
		return context(paired, last, HqRooms::windowFromSettings);
	}

	private static long windowFromSettings(String env) {
		try {
			return VisibilityPolicy.optionsFor(ConfigManager.get().settings(), env).recentWindowMs();
		} catch (RuntimeException e) {
			return Visibility.DEFAULT_RECENT_WINDOW_MS;
		}
	}
}
