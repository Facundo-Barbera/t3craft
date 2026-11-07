package dev.t3craft.client.hud;

import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.portable.NotificationLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Player-facing notice for a thread state change: chat line + toast + sound. */
public final class Notifier {
	private Notifier() {
	}

	/** {@code sound}: attention | done | error (decided by the logic, not here). */
	public static void transition(Minecraft mc, String title, String text, int color, String sound, boolean toast) {
		transition(mc, null, title, text, color, sound, toast);
	}

	/** Same, with the NPC id so the portable terminal's notification list can jump to the thread. */
	public static void transition(Minecraft mc, @Nullable String npcId, String title, String text, int color, String sound, boolean toast) {
		NotificationLog.INSTANCE.record(new NotificationLog.Entry(npcId, title, text, color, sound, System.currentTimeMillis()));
		if (mc.player != null) {
			mc.gui.hud.getChat().addClientSystemMessage(T3CraftCommands.prefix()
					.append(Component.literal(title).withColor(color))
					.append(Component.literal(" — " + text).withStyle(ChatFormatting.GRAY)));
		}
		if (toast) {
			Toasts.notify(mc, title, text, color);
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
