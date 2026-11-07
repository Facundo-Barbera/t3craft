package dev.t3craft.client.workstation;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.screen.ThreadChatScreen;
import net.minecraft.client.Minecraft;

/**
 * Default {@link ThreadOpener}: the same screen right-clicking the NPC opens on main today
 * ({@link ThreadChatScreen}). Looks the thread up in the registry by its NPC id
 * ({@code <env>:<thread>}, or the bare thread id in bridge mode).
 */
public final class ChatScreenOpener implements ThreadOpener {
	@Override
	public void open(String envId, String threadId) {
		T3CraftClient client = T3CraftClient.get();
		if (client == null) {
			return;
		}
		NpcInfo info = client.npcs().info(envId.isEmpty() ? threadId : envId + ":" + threadId);
		if (info == null) {
			info = client.npcs().info(threadId);
		}
		if (info != null) {
			Minecraft.getInstance().gui.setScreen(new ThreadChatScreen(info));
		}
	}
}
