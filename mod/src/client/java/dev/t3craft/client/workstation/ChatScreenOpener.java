package dev.t3craft.client.workstation;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.chat.ChatScreens;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link ThreadOpener}: the same chat browser right-clicking the thread's NPC opens
 * ({@link ChatScreens}). Used by workstations, the blocks module's monitors and the thread wall's
 * cards. It used to open a placeholder screen ("Chat is not implemented yet", since deleted),
 * because nothing ever plugged the chat in with {@code setThreadOpener}.
 */
public final class ChatScreenOpener implements ThreadOpener {
	@Override
	public void open(String envId, String threadId) {
		T3CraftClient client = T3CraftClient.get();
		String key = key(envId, threadId, client == null ? k -> false : k -> client.npcs().info(k) != null);
		if (key != null) {
			Minecraft.getInstance().gui.setScreen(ChatScreens.create(key));
		}
	}

	/**
	 * The chat key to open: the NPC id when a listed thread matches ({@code <env>:<thread>}, or
	 * the bare thread id in bridge mode), else {@code <env>:<thread>} when the environment is
	 * known (the browser shows an unlisted thread too), else null (nothing to open).
	 */
	static @Nullable String key(String envId, String threadId, Predicate<String> listed) {
		if (threadId == null || threadId.isEmpty()) {
			return null;
		}
		String full = envId == null || envId.isEmpty() ? threadId : envId + ":" + threadId;
		if (listed.test(full)) {
			return full;
		}
		if (listed.test(threadId)) {
			return threadId;
		}
		return envId == null || envId.isEmpty() ? null : full;
	}
}
