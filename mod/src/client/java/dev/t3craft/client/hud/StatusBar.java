// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.hud;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.T3CraftSettings.Hud;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.portable.PortableClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * HUD bar with the working / needs you / error counts of every environment and a
 * "n waiting · KEY" badge (KEY = the portable terminal key, as bound). Counted once per client tick;
 * the {@link StatusBarModel} is rebuilt only when the counts, the key or the {@code hud} config
 * change, so the frame callback only draws. Hidden with F1 and while a screen is open.
 */
public final class StatusBar {
	private static volatile StatusBarModel model = StatusBarModel.HIDDEN;
	private static volatile Hud cfg = Hud.defaults();

	private static StatusBarModel.Counts lastCounts;
	private static String lastKey;
	private static Hud lastCfg;

	private StatusBar() {
	}

	/** After {@link PeekHud#register()} so the bar draws (and stacks) under the peek line. */
	public static void register(T3CraftClient client) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> update(mc, client));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("t3craft", "status_bar"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			StatusBarModel m = model;
			if (m.hidden() || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
				return;
			}
			int[] xy = m.place(cfg, g.guiWidth(), g.guiHeight(), PeekHud.bottom());
			int x = xy[0];
			int y = xy[1];
			g.fill(x, y, x + m.width(), y + StatusBarModel.ROW_H, 0x90000000);
			int tx = x + StatusBarModel.PAD;
			for (StatusBarModel.Segment s : m.segments()) {
				g.text(mc.font, s.text(), tx, y + 2, 0xFF000000 | s.color());
				tx += s.width() + StatusBarModel.GAP;
			}
			StatusBarModel.Segment b = m.badge();
			if (b != null) {
				int by = y + StatusBarModel.ROW_H + StatusBarModel.BADGE_GAP;
				int bw = b.width() + StatusBarModel.PAD * 2;
				int bx = switch (cfg.align()) {
					case "left" -> x;
					case "right" -> x + m.width() - bw;
					default -> x + (m.width() - bw) / 2;
				};
				g.fill(bx, by, bx + bw, by + StatusBarModel.ROW_H, 0xA0402000);
				g.text(mc.font, b.text(), bx + StatusBarModel.PAD, by + 2, 0xFF000000 | b.color());
			}
		});
	}

	private static void update(Minecraft mc, T3CraftClient client) {
		if (mc.player == null) {
			return;
		}
		Hud c = ConfigManager.get().settings().hud();
		List<String> states = new ArrayList<>();
		for (NpcInfo n : client.npcs().all()) {
			states.add(n.state());
		}
		StatusBarModel.Counts counts = StatusBarModel.Counts.of(states);
		KeyMapping k = PortableClient.key();
		String key = k == null || k.isUnbound() ? null : k.getTranslatedKeyMessage().getString();
		if (counts.equals(lastCounts) && Objects.equals(key, lastKey) && c.equals(lastCfg)) {
			return;
		}
		lastCounts = counts;
		lastKey = key;
		lastCfg = c;
		cfg = c;
		model = StatusBarModel.build(counts, c, key, mc.font::width);
	}
}
