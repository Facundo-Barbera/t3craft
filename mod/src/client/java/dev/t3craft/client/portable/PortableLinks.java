package dev.t3craft.client.portable;

import dev.t3craft.client.chat.ChatScreens;
import org.jspecify.annotations.Nullable;

/**
 * Where the portable terminal hands off to screens owned by other branches. This package does not
 * render thread history nor manage environments; it links to:
 * <ul>
 *   <li><b>feat/chat</b>: {@code ChatScreens.open(String envId, String threadId)} — read-only
 *   chat/history of one thread. Linked directly (compile-time reference to {@link ChatScreens}, same
 *   source set); {@link PortableClient} replaces it with {@link #setChat}.</li>
 *   <li><b>feat/config</b>: the environment configuration/management screen.</li>
 * </ul>
 * The environments screen is wired explicitly at client start-up:
 * <pre>{@code
 * PortableLinks.setEnvironments(parent -> mc.gui.setScreen(new EnvironmentsScreen(parent)));
 * }</pre>
 * Until then the fallbacks are the screens already on main, installed by {@link PortableClient}.
 */
public final class PortableLinks {
	@FunctionalInterface
	public interface ChatOpener {
		void open(String envId, String threadId);
	}

	@FunctionalInterface
	public interface EnvironmentsOpener {
		/** {@code parent}: the portable screen, to return to on close (may be ignored). */
		void open(net.minecraft.client.gui.screens.@Nullable Screen parent);
	}

	private static volatile @Nullable ChatOpener chat = ChatScreens::open;
	private static volatile @Nullable ChatOpener chatFallback;
	private static volatile @Nullable EnvironmentsOpener environments;
	private static volatile @Nullable EnvironmentsOpener environmentsFallback;

	private PortableLinks() {
	}

	public static void setChat(@Nullable ChatOpener opener) {
		chat = opener;
	}

	public static void setEnvironments(@Nullable EnvironmentsOpener opener) {
		environments = opener;
	}

	static void setFallbacks(ChatOpener chatOpener, EnvironmentsOpener envOpener) {
		chatFallback = chatOpener;
		environmentsFallback = envOpener;
	}

	/** True while a chat opener is wired (by default {@link ChatScreens#open}). */
	public static boolean hasChat() {
		return chat != null;
	}

	public static boolean hasEnvironments() {
		return environments != null;
	}

	public static void openChat(String envId, String threadId) {
		ChatOpener o = chat != null ? chat : chatFallback;
		if (o != null) {
			o.open(envId, threadId);
		}
	}

	public static void openEnvironments(net.minecraft.client.gui.screens.@Nullable Screen parent) {
		EnvironmentsOpener o = environments != null ? environments : environmentsFallback;
		if (o != null) {
			o.open(parent);
		}
	}
}
