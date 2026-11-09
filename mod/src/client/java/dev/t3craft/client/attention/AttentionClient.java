package dev.t3craft.client.attention;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatScreens;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.config.T3CraftSettings.Hud;
import dev.t3craft.client.hud.Notifier;
import dev.t3craft.client.hud.PeekHud;
import dev.t3craft.client.hud.StatusBarModel;
import dev.t3craft.client.t3ui.T3Screen;
import dev.t3craft.t3.ThreadState;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Client wiring of the attention queue (docs/features/attention-queue.md): the two keys
 * ({@code T3CraftKeys}, unbound by default) call {@link #openNext} and {@link #openLast}, and an HUD
 * element draws an arrow on the screen's edge toward the NPC of the most urgent thread that is
 * standing in the world, in the thread's state colour.
 *
 * <p>Nothing here approves or denies: the keys only open the thread, on its question or approval card,
 * and the answer goes through the confirmation the card already has.
 *
 * <p>The ranked list is refreshed every {@link #REFRESH_TICKS} ticks, so the frame callback only
 * looks up positions and draws. Hidden with F1, with a screen open, and with {@code hud.attentionArrow}
 * off.
 */
public final class AttentionClient {
	private static final int REFRESH_TICKS = 10;
	private static final int ARROW_SIZE = 7;
	private static final int MARGIN = 14;
	/** Rows above the hotbar and the health bars, vanilla layout. */
	private static final int HOTBAR_RESERVE = 64;
	private static final int BAR_RESERVE = 2 * StatusBarModel.ROW_H + StatusBarModel.BADGE_GAP + 6;

	private static final AttentionQueue QUEUE = new AttentionQueue();
	private static volatile List<ThreadRow> ranked = List.of();
	private static int tick;
	private static @Nullable String lastSeen;

	private AttentionClient() {
	}

	/** After {@code StatusBar.register} so the arrow draws over the bar's row, never under it. */
	public static void register(T3CraftClient client) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> update(mc));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("t3craft", "attention_arrow"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			Hud hud = ConfigManager.get().settings().hud();
			if (!hud.attentionArrow() || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
				return;
			}
			ThreadRow target = null;
			Vec3 at = null;
			for (ThreadRow r : ranked) {
				Vec3 p = client.npcs().position(r.key());
				if (p != null) {
					target = r;
					at = p;
					break;
				}
			}
			if (target == null || at == null) {
				return;
			}
			int top = MARGIN + Math.max(PeekHud.bottom(), 0);
			int bottom = HOTBAR_RESERVE;
			if (hud.statusBar()) {
				if (hud.bottom()) {
					bottom += BAR_RESERVE;
				} else {
					top += BAR_RESERVE;
				}
			}
			Vec3 me = mc.player.position();
			AttentionIndicator.Placement pl = AttentionIndicator.place(me.x, me.z, mc.player.getYRot(), at.x, at.z,
					g.guiWidth(), g.guiHeight(), new AttentionIndicator.Insets(MARGIN + ARROW_SIZE, top + ARROW_SIZE, MARGIN + ARROW_SIZE, bottom + ARROW_SIZE));
			if (pl == null) {
				return;
			}
			int rgb = colorOf(target);
			for (int[] run : AttentionIndicator.arrowRows(pl.x() + 1, pl.y() + 1, pl.dirX(), pl.dirY(), ARROW_SIZE)) {
				g.fill(run[1], run[0], run[2], run[0] + 1, 0xA0000000);
			}
			for (int[] run : AttentionIndicator.arrowRows(pl.x(), pl.y(), pl.dirX(), pl.dirY(), ARROW_SIZE)) {
				g.fill(run[1], run[0], run[2], run[0] + 1, 0xFF000000 | rgb);
			}
			String label = mc.font.plainSubstrByWidth(target.title(), 90) + " · " + pl.meters() + "m";
			int w = mc.font.width(label);
			int lx = Math.max(2 + w / 2, Math.min(g.guiWidth() - 2 - w / 2, pl.x()));
			int ly = pl.y() < g.guiHeight() / 2 ? pl.y() + ARROW_SIZE + 3 : pl.y() - ARROW_SIZE - 12;
			g.fill(lx - w / 2 - 2, ly - 1, lx + w / 2 + 2, ly + 10, 0x80000000);
			g.centeredText(mc.font, label, lx, ly, 0xFF000000 | rgb);
		});
	}

	/** The "next thread that needs me" key: opens the next urgent thread on its question / approval card. */
	public static void openNext(Minecraft mc) {
		long now = System.currentTimeMillis();
		String ask = QUEUE.cycling(now) ? null : Notifier.takeRecentAsk(now);
		ThreadRow row = QUEUE.next(ChatScreens.defaultDirectory().rows(), ask, now);
		if (row == null) {
			overlay(mc, Component.translatable("t3craft.attention.nobody"));
			return;
		}
		mc.gui.setScreen(ChatScreens.create(row.key()));
	}

	/** The "back to the last opened thread" key. */
	public static void openLast(Minecraft mc) {
		String key = QUEUE.lastOpened();
		if (key == null) {
			overlay(mc, Component.translatable("t3craft.attention.no_last"));
			return;
		}
		mc.gui.setScreen(ChatScreens.create(key));
	}

	private static void update(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		// whatever opened it (key, NPC, desk, terminal), the thread on screen is the "last opened"
		if (mc.gui.screen() instanceof T3Screen screen) {
			String k = screen.selectedKey();
			if (k != null && !k.equals(lastSeen)) {
				lastSeen = k;
				for (ThreadRow r : ChatScreens.defaultDirectory().rows()) {
					if (r.key().equals(k)) {
						QUEUE.noteOpened(k);
						break;
					}
				}
			}
		}
		if (++tick % REFRESH_TICKS != 0 || !ConfigManager.get().settings().hud().attentionArrow()) {
			return;
		}
		ranked = AttentionQueue.rank(ChatScreens.defaultDirectory().rows());
	}

	/** The thread's state colour; a pending approval on a working thread reads as "needs you". */
	private static int colorOf(ThreadRow r) {
		return r.attention() == Attention.PENDING ? ThreadState.NEEDS_ATTENTION.color : r.state().color;
	}

	private static void overlay(Minecraft mc, Component text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(text);
		}
	}
}
