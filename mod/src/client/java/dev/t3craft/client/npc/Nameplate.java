package dev.t3craft.client.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Vanilla custom-name nameplate: the bridge's {@code label} in its {@code color}, then the
 * environment tag ({@code envLabel}) in grey so you can see which machine the thread is on.
 * TODO(phase 2): proper two-line plate with {@code sublabel} and {@code activity} (custom renderer).
 */
public final class Nameplate {
	private Nameplate() {
	}

	public static Component of(NpcInfo info) {
		MutableComponent c = Component.literal(info.label()).withColor(info.color());
		if (!info.envLabel().isEmpty()) {
			c.append(Component.literal("  [" + info.envLabel() + "]").withStyle(ChatFormatting.GRAY));
		}
		return c;
	}
}
