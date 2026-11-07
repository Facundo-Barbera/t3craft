package dev.t3craft.client.hud;

import dev.t3craft.client.T3CraftCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Player-facing notice for a thread state change: chat line + toast + sound. */
public final class Notifier {
	private Notifier() {
	}

	/** {@code sound}: attention | done | error (decided by the logic, not here). */
	public static void transition(Minecraft mc, String title, String text, int color, String sound, boolean toast) {
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
