package dev.t3craft.client.portable;

import dev.t3craft.T3Craft;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Where the portable terminal hands off to screens owned by other branches. This package does not
 * render thread history nor manage environments; it links to:
 * <ul>
 *   <li><b>feat/chat</b>: {@code ChatScreens.open(String envId, String threadId)} — read-only
 *   chat/history of one thread.</li>
 *   <li><b>feat/config</b>: the environment configuration/management screen.</li>
 * </ul>
 * Integration (Taller): after merging, either do nothing for chat (a static
 * {@code ChatScreens.open(String, String)} is discovered by name, see {@link #CHAT_CANDIDATES}) or
 * wire both explicitly at client start-up:
 * <pre>{@code
 * PortableLinks.setChat(ChatScreens::open);
 * PortableLinks.setEnvironments(parent -> mc.gui.setScreen(new EnvironmentsScreen(parent)));
 * }</pre>
 * Until then the fallbacks are the screens already on main ({@code ThreadChatScreen} placeholder,
 * {@code PairScreen}), installed by {@link PortableClient}.
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

	/** Fully-qualified names tried for feat/chat's {@code ChatScreens}. */
	static final String[] CHAT_CANDIDATES = {
			"dev.t3craft.client.chat.ChatScreens",
			"dev.t3craft.client.screen.ChatScreens",
			"dev.t3craft.client.ChatScreens",
	};

	private static volatile @Nullable ChatOpener chat;
	private static volatile @Nullable ChatOpener chatFallback;
	private static volatile @Nullable EnvironmentsOpener environments;
	private static volatile @Nullable EnvironmentsOpener environmentsFallback;
	private static boolean discovered;

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

	/** True once feat/chat's opener is wired (explicitly or discovered). */
	public static boolean hasChat() {
		discover();
		return chat != null;
	}

	public static boolean hasEnvironments() {
		return environments != null;
	}

	public static void openChat(String envId, String threadId) {
		discover();
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

	/** One-time lookup of a public static {@code open(String, String)} on a known ChatScreens class. */
	private static synchronized void discover() {
		if (discovered || chat != null) {
			return;
		}
		discovered = true;
		ChatOpener found = findChat(PortableLinks.class.getClassLoader(), CHAT_CANDIDATES);
		if (found != null) {
			chat = found;
		}
	}

	static @Nullable ChatOpener findChat(ClassLoader loader, String... candidates) {
		for (String name : candidates) {
			try {
				Class<?> c = Class.forName(name, false, loader);
				Method m = c.getMethod("open", String.class, String.class);
				if (!Modifier.isStatic(m.getModifiers())) {
					continue;
				}
				T3Craft.LOGGER.info("portable terminal: chat screens linked via {}", name);
				return (env, thread) -> {
					try {
						m.invoke(null, env, thread);
					} catch (ReflectiveOperationException e) {
						T3Craft.LOGGER.warn("portable terminal: {}.open failed", name, e);
					}
				};
			} catch (ClassNotFoundException | NoSuchMethodException ignored) {
				// not merged yet / different name: keep looking, then fall back
			}
		}
		return null;
	}
}
