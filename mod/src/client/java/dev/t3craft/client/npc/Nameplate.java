package dev.t3craft.client.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Builds the vanilla custom-name nameplate: "{glyph} {title}" in the state colour. */
public final class Nameplate {
	private static final int MAX_TITLE = 32;

	private Nameplate() {
	}

	public static Component of(NpcInfo info) {
		String title = info.title().length() > MAX_TITLE ? info.title().substring(0, MAX_TITLE - 1) + "…" : info.title();
		MutableComponent c = Component.literal(info.state().glyph + " ").withStyle(info.state().color, ChatFormatting.BOLD);
		c.append(Component.literal(title).withStyle(info.state().color));
		return c;
	}
}
