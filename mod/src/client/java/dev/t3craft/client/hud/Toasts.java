package dev.t3craft.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Vanilla system toasts for bridge {@code notify} messages and link changes. */
public final class Toasts {
	/** One id per kind so a new toast of the same kind replaces the old one instead of stacking. */
	private static final SystemToast.SystemToastId NOTIFY = new SystemToast.SystemToastId(6000L);
	private static final SystemToast.SystemToastId LINK = new SystemToast.SystemToastId(4000L);

	private Toasts() {
	}

	/** Wording and colour come from the bridge ({@code notify.title/text/color}). */
	public static void notify(Minecraft mc, String title, String text, int color) {
		SystemToast.addOrUpdate(mc.gui.toastManager(), NOTIFY, Component.literal(title).withColor(color), Component.literal(text));
		// TODO(phase 3): sound cue + "!" over the NPC for needs_attention.
	}

	public static void link(Minecraft mc, String text) {
		SystemToast.addOrUpdate(mc.gui.toastManager(), LINK, Component.literal("T3Craft bridge"), Component.literal(text));
	}
}
