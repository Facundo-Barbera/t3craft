package dev.t3craft.client.search;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.portable.PortableLinks;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ThreadSearch;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Client wiring of thread search: {@code /t3craft search} ({@link SearchCommand}) and the search
 * function the terminal's {@link SearchBox} uses. One call from {@code T3CraftClient}.
 *
 * <p>Read-only: direct mode asks T3's {@code orchestration.searchThreads} over the existing
 * connections ({@link DirectController#search}); bridge mode filters the listed threads by title
 * ({@link LocalSearch}).
 */
public final class SearchClient {
	private static volatile @Nullable Runnable pendingOpen;

	private SearchClient() {
	}

	public static void register(T3CraftClient client) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> SearchCommand.build(dispatcher, client));
		// Opening is deferred one tick: a chat click runs the command while the chat screen is
		// still closing, which would close the screen we open.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Runnable r = pendingOpen;
			if (r != null) {
				pendingOpen = null;
				r.run();
			}
		});
	}

	/** Search every connected environment (or the listed threads in bridge mode). */
	public static CompletableFuture<ThreadSearch.Outcome> search(T3CraftClient client, String query, int limit) {
		DirectController direct = client.direct();
		if (direct != null) {
			return direct.search(query, limit, ThreadSearch.DEFAULT_TIMEOUT_MS);
		}
		if (client.link() != null) {
			return CompletableFuture.completedFuture(LocalSearch.filter(client.npcs().all(), query, limit));
		}
		return CompletableFuture.completedFuture(new ThreadSearch.Outcome(query, java.util.List.of(), 0, 0, java.util.List.of()));
	}

	/** True when results come from the local title filter (bridge mode), not from T3. */
	public static boolean localOnly(T3CraftClient client) {
		return client.direct() == null && client.link() != null;
	}

	/**
	 * Opens a thread with the chat/history screen that exists (feat/chat's {@code ChatScreens} when
	 * merged, else the placeholder through {@link PortableLinks}), next tick.
	 */
	public static void open(T3CraftClient client, String env, String threadId) {
		pendingOpen = () -> {
			boolean listed = client.npcs().info(env.isEmpty() ? threadId : env + ":" + threadId) != null;
			if (!PortableLinks.hasChat() && !listed) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.gui.hud.getChat().addClientSystemMessage(T3CraftCommands.prefix().append(Component.literal(
							"That thread is not listed in the world right now (settled or filtered out), so there is no chat screen for it yet.")
							.withStyle(ChatFormatting.GRAY)));
				}
				return;
			}
			PortableLinks.openChat(env, threadId);
		};
	}
}
