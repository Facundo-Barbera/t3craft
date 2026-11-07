package dev.t3craft.client.chat;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.Pairing;
import java.net.http.HttpClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Entry points to the read-only thread chat. Stateless (no static fields), so it is safe across
 * hot swaps: each screen owns its feed and closes it when removed.
 *
 * <ul>
 *   <li>{@link #open(String, String)}: from any UI or command, by environment id + thread id.</li>
 *   <li>{@link #openFor(NpcInfo)}: from the NPC right-click (direct-mode NPC ids are
 *   {@code env:threadId}).</li>
 *   <li>{@link #create(ChatSource, String, String, String)}: build the screen without showing it.</li>
 * </ul>
 * Call on the client thread. From a client command use {@code T3CraftClient.openNextTick(create(...))}
 * because the chat screen closes right after the command runs.
 */
public final class ChatScreens {
	private ChatScreens() {
	}

	/** Opens the chat of {@code threadId} in environment {@code envId} now. */
	public static void open(String envId, String threadId) {
		Minecraft.getInstance().gui.setScreen(create(defaultSource(), envId, threadId, threadId));
	}

	/** Opens the chat for an NPC; title from the NPC until the snapshot brings the real one. */
	public static void openFor(NpcInfo npc) {
		Minecraft.getInstance().gui.setScreen(createFor(npc));
	}

	public static Screen createFor(NpcInfo npc) {
		String id = npc.id();
		int colon = id.indexOf(':');
		if (colon <= 0 || colon == id.length() - 1) {
			return new ChatScreen(npc.title(), npc.envLabel(), "Chat history is only available in direct mode (/t3craft pair).");
		}
		return create(defaultSource(), id.substring(0, colon), id.substring(colon + 1), npc.title());
	}

	public static Screen create(ChatSource source, String envId, String threadId, String title) {
		ChatSource.Endpoint endpoint;
		try {
			endpoint = source.endpoint(envId);
		} catch (ChatSource.Unavailable e) {
			return new ChatScreen(title, envId, e.getMessage());
		}
		// The feed calls back on its own thread; the screen polls the transcript every frame, so the
		// callback only needs to be cheap. Nothing to hop to the client thread for.
		ThreadFeed feed = new ThreadFeed(endpoint, threadId, source.http(), () -> {
		});
		return new ChatScreen(feed, title);
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
