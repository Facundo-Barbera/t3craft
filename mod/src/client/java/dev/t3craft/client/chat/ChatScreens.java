package dev.t3craft.client.chat;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.EnvironmentActions;
import dev.t3craft.client.config.EnvironmentsScreen;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.screen.OperatePairScreen;
import dev.t3craft.client.config.T3CraftSettings;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.client.t3ui.T3Slots;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.T3Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;

/**
 * Entry points to the unified T3 screen ({@link T3Screen}), and the default data sources for
 * embedding the thread browser widget.
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

	/**
	 * Opens the browser on a closed thread, read-only (the archive): no actions are wired, so the
	 * reply bar only says "read-only" (no send, no "Enable control") even with operate. The labels
	 * fill the header, since an archived thread is not in the live list.
	 */
	public static void openReadOnly(String envId, String threadId, String envLabel, String title) {
		Minecraft.getInstance().gui.setScreen(createReadOnly(envId, threadId, envLabel, title));
	}

	public static Screen createReadOnly(String envId, String threadId, String envLabel, String title) {
		return new T3Screen(defaultDirectory(), defaultSource(), null, T3Slots.DEFAULT, envId + ":" + threadId).withFallbackLabels(envLabel, title);
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

	/**
	 * The unified T3 screen (feat/t3-ui-unified: sidebar + thread, T3 Code's layout), preselecting
	 * {@code key} ({@code env:threadId}) when not null. Every entry point ends here: the T3 key, the
	 * terminal item, NPCs, desks, monitors, the thread wall and search hits.
	 */
	public static T3Screen create(@Nullable String key) {
		return new T3Screen(defaultDirectory(), defaultSource(), screenActions(), T3Slots.DEFAULT, key);
	}

	/** Kept for callers of the first feat/chat API: the screen on one thread from a custom source. */
	public static Screen create(ChatSource source, String envId, String threadId, String title) {
		return new T3Screen(defaultDirectory(), source, screenActions(), T3Slots.DEFAULT, envId + ":" + threadId).withFallbackLabels("", title);
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
				if (direct != null) {
					// memoized per shell change, with the disk-cached rows until the shells arrive
					return ThreadListCache.get().rows(direct);
				}
				List<ThreadRow> out = new ArrayList<>();
				for (NpcInfo n : client.npcs().all()) {
					out.add(ThreadRow.fromNpc(n));
				}
				return out;
			}

			@Override
			public boolean cached() {
				T3CraftClient client = T3CraftClient.get();
				return client != null && client.direct() != null && ThreadListCache.get().cached();
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

	/**
	 * The environments of the direct-mode controller of {@link T3CraftClient}: each one is its single
	 * {@code T3Connection} (thread streams are shared subscriptions on it).
	 */
	public static ChatSource defaultSource() {
		return envId -> {
			T3CraftClient client = T3CraftClient.get();
			DirectController direct = client == null ? null : client.direct();
			T3Connection c = direct == null ? null : direct.connection(envId);
			return switch (ChatAccess.history(direct != null, c != null)) {
				case DIRECT -> ChatSource.of(c, () -> {
					// still the live connection? (a reload or a pairing replaces them all)
					T3CraftClient now = T3CraftClient.get();
					DirectController d = now == null ? null : now.direct();
					return d != null && d.connection(envId) == c;
				});
				case PAIR_FOR_DIRECT -> throw new ChatSource.Unavailable(I18n.get("t3craft.chat.history.bridge"),
						ChatSource.Unavailable.Action.PAIR);
				case NOT_PAIRED -> throw new ChatSource.Unavailable(I18n.get("t3craft.chat.history.not_paired", envId),
						ChatSource.Unavailable.Action.PAIR);
			};
		};
	}

	// ---- actions (buttons in the conversation) ------------------------------------------------

	/**
	 * Whether {@code envId} can operate (write). Small wrapper so the chat has one place to ask: today
	 * {@link OperateUi#canOperate} (the operate token is present and valid); when the default pairing
	 * becomes read+operate (fix/pair-default-operate) the same query answers for both. Never throws.
	 */
	public static boolean canOperate(String envId) {
		try {
			return OperateUi.canOperate(envId);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * The conversation's buttons for a browser hosted by a screen: each opens its screen with the
	 * current one as the parent, so Done/Esc comes back to the same thread.
	 */
	public static ConversationPanel.Actions screenActions() {
		return new ConversationPanel.Actions() {
			@Override
			public boolean canOperate(String envId) {
				return ChatScreens.canOperate(envId);
			}

			@Override
			public void enableControl(String envId) {
				Minecraft mc = Minecraft.getInstance();
				Screen back = mc.gui.screen();
				var env = OperateClient.environment(envId);
				mc.gui.setScreen(env.isPresent() ? new OperatePairScreen(back, env.get()) : new EnvironmentsScreen(back, EnvironmentActions.DEFAULT));
			}

			@Override
			public void pair() {
				Minecraft mc = Minecraft.getInstance();
				mc.gui.setScreen(new EnvironmentsScreen(mc.gui.screen(), EnvironmentActions.DEFAULT));
			}
		};
	}
}
