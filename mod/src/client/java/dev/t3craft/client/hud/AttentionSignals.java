package dev.t3craft.client.hud;

import com.mojang.blaze3d.platform.Window;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.T3CraftSettings.Attention;
import dev.t3craft.client.config.T3CraftSettings.Notifications;
import dev.t3craft.client.npc.NpcInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLVideo;

/**
 * Silent attention signals for when the game is in the background (docs/features/attention-signals.md):
 * the window title carries a summary of what is pending, the OS is asked for attention (taskbar flash /
 * dock bounce) once per thread while the window is unfocused, and the action bar can announce a notice
 * next to or instead of the toast. Every signal has its own switch in {@code notifications.attention}
 * and obeys {@code notifications.enabled}. No sound, no process, no network.
 *
 * <p>{@link Notifier#transition} calls {@link #onTransition}; {@link #register} adds the per-tick part.
 */
public final class AttentionSignals {
	/** {@code SDL_FLASH_UNTIL_FOCUSED} (SDL_FlashOperation): flash until the window gets the focus. */
	private static final int SDL_FLASH_UNTIL_FOCUSED = 2;
	/** Vanilla may rewrite the title (joining a world, ...): put ours back about once a second. */
	private static final int TITLE_REAPPLY_TICKS = 20;

	/** Threads already flashed for during this stint in the background; cleared when the window is focused. */
	private static final Set<String> flashed = ConcurrentHashMap.newKeySet();
	private static final AtomicBoolean flashRequested = new AtomicBoolean();
	private static volatile boolean flashBroken;
	private static @Nullable String appliedTitle;
	private static int titleAge;

	private AttentionSignals() {
	}

	/** Client tick: title summary, and the OS request that {@link #onTransition} queued (SDL wants the main thread). */
	public static void register(T3CraftClient client) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc, client));
	}

	/**
	 * Called for every notice {@link Notifier} raises. Shows the action bar if configured and queues the OS
	 * attention request. Returns true when the action bar replaces the toast, so the caller skips the toast.
	 *
	 * @param sound attention | done | error ({@link Notifier}'s names)
	 */
	public static boolean onTransition(Minecraft mc, @Nullable String npcId, String title, String text, int color, String sound) {
		Notifications n = ConfigManager.get().settings().notifications();
		if (!n.enabled()) {
			return false;
		}
		Attention a = n.attention();
		boolean bar = false;
		if (a.barShown() && mc.player != null) {
			// one slot: a new notice replaces the previous one instead of stacking
			mc.player.sendOverlayMessage(Component.literal(title).withColor(color)
					.append(Component.literal(" — " + text).withStyle(ChatFormatting.GRAY)));
			bar = true;
		}
		// Only what needs the player (a question, an error), never "done"; once per thread per background stint.
		if (a.taskbarFlash() && ("attention".equals(sound) || "error".equals(sound)) && !mc.getWindow().isFocused()
				&& flashed.add(npcId != null ? npcId : title)) {
			flashRequested.set(true);
		}
		return bar && a.barReplacesToast();
	}

	private static void tick(Minecraft mc, T3CraftClient client) {
		Window window = mc.getWindow();
		boolean focused = window.isFocused();
		if (focused && !flashed.isEmpty()) {
			flashed.clear(); // the player is back: the next background stint may flash again
		}
		if (flashRequested.getAndSet(false) && !focused) {
			flash(window);
		}
		Notifications n = ConfigManager.get().settings().notifications();
		String want = null;
		if (n.enabled() && n.attention().windowTitle()) {
			List<String> states = new ArrayList<>();
			for (NpcInfo npc : client.npcs().all()) {
				states.add(npc.state());
			}
			want = summary(StatusBarModel.Counts.of(states));
		}
		if (want == null) {
			if (appliedTitle != null) {
				appliedTitle = null;
				mc.updateTitle(); // back to the normal title
			}
			return;
		}
		if (!want.equals(appliedTitle) || ++titleAge >= TITLE_REAPPLY_TICKS) {
			appliedTitle = want;
			titleAge = 0;
			window.setTitle(want);
		}
	}

	/** "T3Craft · 2 need you · 1 error", or null when nothing is pending. */
	static @Nullable String summary(StatusBarModel.Counts c) {
		if (c.needsYou() == 0 && c.error() == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder("T3Craft");
		if (c.needsYou() > 0) {
			sb.append(" · ").append(c.needsYou()).append(c.needsYou() == 1 ? " needs you" : " need you");
		}
		if (c.error() > 0) {
			sb.append(" · ").append(c.error()).append(c.error() == 1 ? " error" : " errors");
		}
		return sb.toString();
	}

	/** Taskbar flash (Windows) / dock bounce (macOS); on some Linux window managers it does nothing, which is fine. */
	private static void flash(Window window) {
		if (flashBroken) {
			return;
		}
		try {
			SDLVideo.SDL_FlashWindow(window.handle(), SDL_FLASH_UNTIL_FOCUSED);
		} catch (RuntimeException | LinkageError e) {
			flashBroken = true; // no such native call here: stop trying
			T3Craft.LOGGER.debug("OS attention request unavailable: {}", e.toString());
		}
	}
}
