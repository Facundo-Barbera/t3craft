package dev.t3craft.client.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Texts drawn around an NPC. Everything comes resolved in {@link NpcInfo} (colour, glyph, label);
 * this only lays it out.
 */
public final class Nameplate {
	private Nameplate() {
	}

	/** Vanilla name tag: the label (glyph + title) in the state colour. */
	public static Component of(NpcInfo info) {
		return Component.literal(info.label()).withColor(info.color());
	}

	/** Floating panel above the name tag: environment · project, then the current activity. */
	public static Component info(NpcInfo info) {
		MutableComponent c = Component.literal(info.sublabel()).withStyle(ChatFormatting.GRAY);
		if (info.activity() != null && !info.activity().isBlank()) {
			c.append(Component.literal("\n" + info.activity()).withStyle(ChatFormatting.ITALIC).withColor(0xD0D0D0));
		}
		return c;
	}

	/** Neighbourhood sign: project in bold, environment below. */
	public static Component sign(String groupLabel, String envLabel, int count) {
		return Component.literal(groupLabel).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)
				.append(Component.literal("\n" + envLabel + " · " + count + (count == 1 ? " thread" : " threads")).withStyle(ChatFormatting.GRAY));
	}
}
