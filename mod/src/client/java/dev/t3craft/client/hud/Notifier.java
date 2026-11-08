package dev.t3craft.client.hud;

import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.portable.NotificationLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Player-facing notice for a thread state change: chat line + toast + sound. */
public final class Notifier {
	/** feat/t3-user-input: the thread that last asked for input ({@code env:threadId}) and when (epoch ms). */
	private static volatile @Nullable String lastAsk;
	private static volatile long lastAskAt;
	/** How long the T3 key opens the asking thread instead of the most recent one. */
	private static final long ASK_WINDOW_MS = 120_000;

	private Notifier() {
	}

	/**
	 * The thread whose needs-input notice came in the last two minutes, once: the T3 key opens it
	 * (so the toast's "Y to answer" lands on the card). Null when none.
	 */
	public static @Nullable String takeRecentAsk(long nowMs) {
		String k = lastAsk;
		if (k == null || nowMs - lastAskAt > ASK_WINDOW_MS) {
			return null;
		}
		lastAsk = null;
		return k;
	}

	/** "Y to answer" with the T3 key's current binding (feat/t3-user-input), or a chat hint without one. */
	static String answerHint() {
		var key = dev.t3craft.client.portable.PortableClient.key();
		return key == null || key.isUnbound() ? "click [Answer] in chat" : key.getTranslatedKeyMessage().getString() + " to answer";
	}

	/** {@code sound}: attention | done | error (decided by the logic, not here). */
	public static void transition(Minecraft mc, String title, String text, int color, String sound, boolean toast) {
		transition(mc, null, title, text, color, sound, toast);
	}

	/** Same, with the NPC id so the portable terminal's notification list can jump to the thread. */
	public static void transition(Minecraft mc, @Nullable String npcId, String title, String text, int color, String sound, boolean toast) {
		NotificationLog.INSTANCE.record(new NotificationLog.Entry(npcId, title, text, color, sound, System.currentTimeMillis()));
		boolean answerable = "attention".equals(sound) && npcId != null && npcId.indexOf(':') > 0;
		if (answerable) {
			lastAsk = npcId;
			lastAskAt = System.currentTimeMillis();
		}
		if (mc.player != null) {
			MutableComponent line = T3CraftCommands.prefix()
					.append(Component.literal(title).withColor(color))
					.append(Component.literal(" — " + text).withStyle(ChatFormatting.GRAY));
			if (answerable) {
				// feat/t3-user-input: one click to the thread's question / approval card
				String cmd = "t3craft answer " + npcId;
				line.append(" ").append(Component.literal("[Answer]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand(cmd))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the thread and answer in Minecraft")))));
			}
			mc.gui.hud.getChat().addClientSystemMessage(line);
		}
		if (toast) {
			Toasts.notify(mc, title, answerable ? text + " · " + answerHint() : text, color);
		}
		var instance = switch (sound) {
			case "attention" -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.2f);
			case "error" -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7f);
			case "done" -> SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.4f);
			default -> null;
		};
		if (instance != null) {
			mc.getSoundManager().play(instance);
		}
	}
}
