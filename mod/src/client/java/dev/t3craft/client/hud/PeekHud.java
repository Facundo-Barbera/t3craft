package dev.t3craft.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One line at the top of the screen with the most relevant thread (needs you, else working), and a
 * banner when the connection or an environment is not ok. Hidden with the HUD (F1) and when nothing
 * to say.
 */
public final class PeekHud {
	private static volatile @Nullable String text;
	private static volatile int color = 0xAAAAAA;
	private static volatile @Nullable String banner;
	/** Bottom edge of what was drawn last frame (0 = nothing); {@link StatusBar} stacks under it. */
	private static volatile int bottom;

	private PeekHud() {
	}

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("t3craft", "peek"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			bottom = 0;
			if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
				return;
			}
			int y = 4;
			String b = banner;
			if (b != null) {
				int w = mc.font.width(b);
				g.fill(g.guiWidth() / 2 - w / 2 - 4, y - 2, g.guiWidth() / 2 + w / 2 + 4, y + 10, 0xA0400000);
				g.centeredText(mc.font, b, g.guiWidth() / 2, y, 0xFFFFC0C0);
				y += 14;
				bottom = y - 2;
			}
			String t = text;
			if (t != null) {
				String shown = mc.font.plainSubstrByWidth(t, Math.max(100, g.guiWidth() - 40));
				int w = mc.font.width(shown);
				g.fill(g.guiWidth() / 2 - w / 2 - 4, y - 2, g.guiWidth() / 2 + w / 2 + 4, y + 10, 0x80000000);
				g.centeredText(mc.font, Component.literal(shown).withColor(color), g.guiWidth() / 2, y, 0xFFFFFFFF);
				bottom = y + 10;
			}
		});
	}

	public static int bottom() {
		return bottom;
	}

	public static void setPeek(@Nullable String t, int c) {
		text = t;
		color = c;
	}

	public static void setBanner(@Nullable String b) {
		banner = b;
	}
}
