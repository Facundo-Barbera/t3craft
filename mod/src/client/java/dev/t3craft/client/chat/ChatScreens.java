package dev.t3craft.client.chat;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.T3CraftSettings;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.Pairing;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.Visibility;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Entry points to the read-only thread browser, and the default data sources for embedding it.
 * Stateless (no static fields), so it is safe across hot swaps: each screen owns its browser and
 * closes it when removed.
 *
 * <ul>
 *   <li>{@link #open(String, String)}: from any UI or command, by environment id + thread id (the
 *   portable terminal discovers this signature by reflection: keep it).</li>
 *   <li>{@link #openFor(NpcInfo)}: from the NPC right-click (NPC ids are {@code env:threadId}).</li>
 *   <li>{@link #openBrowser()}: the list with the most urgent thread open.</li>
 *   <li>{@link #browser(ChatTheme)}: a {@link ThreadBrowser} wired to the defaults, to embed.</li>
 * </ul>
 * Call on the client thread. From a client command use {@code T3CraftClient.openNextTick(create(...))}
 * because the chat screen closes right after the command runs.
 */
public final class ChatScreens {
	private ChatScreens() {
	}

	/** Opens the browser on {@code threadId} of environment {@code envId}. */
	public static void open(String envId, String threadId) {
		Minecraft.getInstance().gui.setScreen(create(envId + ":" + threadId));
	}

	/** Opens the browser on an NPC's thread. */
	public static void openFor(NpcInfo npc) {
		Minecraft.getInstance().gui.setScreen(createFor(npc));
	}

	/** Opens the browser with the most urgent thread selected. */
	public static void openBrowser() {
		Minecraft.getInstance().gui.setScreen(create(null));
	}

	public static Screen createFor(NpcInfo npc) {
		return create(npc.id());
	}

	/** The browser screen, preselecting {@code key} ({@code env:threadId}) when not null. */
	public static Screen create(@Nullable String key) {
		return ThreadBrowserScreen.of(browser(ChatTheme.warmStudio()), key);
	}

	/** Kept for callers of the first feat/chat API: a browser on one thread from a custom source. */
	public static Screen create(ChatSource source, String envId, String threadId, String title) {
		ThreadBrowser b = new ThreadBrowser(defaultDirectory(), source, ChatTheme.warmStudio(), configFilterStore());
		return ThreadBrowserScreen.of(b, envId + ":" + threadId);
	}

	/** A browser wired to the default directory, source and config-backed filter. */
	public static ThreadBrowser browser(ChatTheme theme) {
		return new ThreadBrowser(defaultDirectory(), defaultSource(), theme, configFilterStore());
	}

	// ---- data sources -------------------------------------------------------------------------

	/**
	 * Rows of every environment. Direct mode: the visible threads of the controller (same filter as
	 * the NPCs) with their real last-activity time and pending-request flag. Bridge mode: the NPC
	 * model (no timestamps). Environment chips: the known environments with the label and colour
	 * configured in {@code t3craft.json} when set.
	 */
	public static ThreadDirectory defaultDirectory() {
		return new ThreadDirectory() {
			@Override
			public List<ThreadRow> rows() {
				T3CraftClient client = T3CraftClient.get();
				if (client == null) {
					return List.of();
				}
				DirectController direct = client.direct();
				List<ThreadRow> out = new ArrayList<>();
				if (direct != null) {
					long now = System.currentTimeMillis();
					Map<String, ShellState.View> views = new LinkedHashMap<>();
					for (NpcView v : direct.npcs(now)) {
						ShellState.View sv = views.computeIfAbsent(v.env(), direct::view);
						ThreadShell shell = null;
						if (sv != null) {
							for (ThreadShell s : sv.threads()) {
								if (s.id().equals(v.threadId())) {
									shell = s;
									break;
								}
							}
						}
						Long at = shell == null ? null : Visibility.lastActivity(shell);
						boolean pending = shell != null && shell.pendingRuntimeRequest() != null;
						out.add(ThreadRow.fromNpc(NpcInfo.fromView(v), at == null || at <= 0 ? null : at, pending));
					}
					return out;
				}
				for (NpcInfo n : client.npcs().all()) {
					out.add(ThreadRow.fromNpc(n));
				}
				return out;
			}

			@Override
			public Map<String, ThreadList.EnvInfo> environments() {
				T3CraftClient client = T3CraftClient.get();
				Map<String, ThreadList.EnvInfo> out = new LinkedHashMap<>();
				if (client == null) {
					return out;
				}
				T3CraftSettings s = settings();
				client.environments().forEach((id, v) -> {
					T3CraftSettings.EnvPrefs p = s == null ? T3CraftSettings.EnvPrefs.DEFAULT : s.env(id);
					String label = p.labelOr(v.length > 0 ? v[0] : id);
					out.put(id, new ThreadList.EnvInfo(label, v.length > 1 ? v[1] : "", p.color()));
				});
				return out;
			}
		};
	}

	private static @Nullable T3CraftSettings settings() {
		try {
			return ConfigManager.get().settings();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Persists the environment filter in {@code t3craft.json} ({@code chat.hiddenEnvironments}). */
	public static ThreadBrowser.FilterStore configFilterStore() {
		return new ThreadBrowser.FilterStore() {
			@Override
			public Set<String> load() {
				T3CraftSettings s = settings();
				return s == null ? Set.of() : s.chat().hiddenEnvironments();
			}

			@Override
			public void save(Set<String> hidden) {
				try {
					ConfigManager.get().update(s -> s.withChat(s.chat().withHiddenEnvironments(hidden)));
				} catch (RuntimeException e) {
					dev.t3craft.T3Craft.LOGGER.warn("[chat] could not save the environment filter: {}", e.getClass().getSimpleName());
				}
			}
		};
	}

	/** Reads environments and credentials from the direct-mode controller of {@link T3CraftClient}. */
	public static ChatSource defaultSource() {
		return new ChatSource() {
			@Override
			public Endpoint endpoint(String envId) throws Unavailable {
				T3CraftClient client = T3CraftClient.get();
				DirectController direct = client == null ? null : client.direct();
				if (direct == null) {
					throw new Unavailable("Chat history needs direct mode (set \"mode\": \"direct\" and /t3craft pair).");
				}
				Environments envs = direct.environments();
				Environments.Environment env = envs.list().stream().filter(e -> e.id().equals(envId)).findFirst().orElse(null);
				if (env == null) {
					throw new Unavailable("Environment \"" + envId + "\" is not paired (/t3craft pair).");
				}
				Pairing.Session s = envs.credential(envId);
				if (s == null) {
					throw new Unavailable("No credential for " + env.label() + ": pair again (/t3craft pair).");
				}
				if (s.expiresAtMs() > 0 && s.expiresAtMs() < System.currentTimeMillis()) {
					throw new Unavailable("The session for " + env.label() + " expired: pair again (/t3craft pair).");
				}
				return new Endpoint(env.id(), env.label(), env.url(), s.accessToken());
			}

			@Override
			public @Nullable HttpClient http() {
				T3CraftClient client = T3CraftClient.get();
				DirectController direct = client == null ? null : client.direct();
				return direct == null ? null : direct.http();
			}
		};
	}
}
