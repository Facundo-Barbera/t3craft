package dev.t3craft.client.hq.layout;

import dev.t3craft.T3Craft;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.hq.pack.HqLayoutPacks;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * feat/hq-layout-packs: keeps the layout packs in {@code config/t3craft/hq-layouts/} live. Reads them
 * once at client init, then checks the folder's file times every second (client tick) and reloads
 * when something changed: the demo HQ then re-plans and applies the difference as a diff
 * ({@code DemoClient}), the survival HQ re-targets ({@code HqConstruction}). A file that fails to
 * read keeps the last good pack and shows a toast plus a chat line with {@code file:line: what}.
 * Logic changes (Java) still need a restart; packs never do.
 */
public final class HqLayoutPackWatcher {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);
	private static final int POLL_TICKS = 20;
	private static int ticks;
	private static boolean registered;

	private HqLayoutPackWatcher() {
	}

	/** Loads the packs and starts watching (client init; idempotent). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			HqLayoutPacks.Load l = HqLayoutPacks.reload(HqLayoutPacks.dir());
			for (String e : l.errors()) {
				T3Craft.LOGGER.warn("[hq] layout pack: {}", e);
			}
		} catch (RuntimeException e) {
			T3Craft.LOGGER.error("[hq] layout packs failed to load", e);
		}
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("hq.layoutPacks", mc -> {
			if (++ticks % POLL_TICKS != 0) {
				return;
			}
			HqLayoutPacks.Load l = HqLayoutPacks.poll(HqLayoutPacks.dir());
			if (l != null) {
				report(l, false);
			}
		}));
	}

	/** Reloads every pack now ("Reload layouts"); returns a one-line summary. */
	public static String reloadNow() {
		HqLayoutPacks.Load l = HqLayoutPacks.reload(HqLayoutPacks.dir());
		report(l, true);
		return summary(l);
	}

	/** Writes the current values of every layout to the packs folder ("Export layouts"); returns a one-line summary. */
	public static String exportNow() {
		Path dir = HqLayoutPacks.dir();
		try {
			List<Path> files = HqLayoutPacks.export(dir);
			String msg = "Exported " + files.size() + " file(s) to " + dir + " (existing packs kept; reference copies in "
					+ HqLayoutPacks.DEFAULTS + "/)";
			T3Craft.LOGGER.info("[hq] {}", msg);
			return msg;
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.error("[hq] layout pack export failed", e);
			return "Export failed: " + e.getMessage();
		}
	}

	private static String summary(HqLayoutPacks.Load l) {
		if (!l.errors().isEmpty()) {
			return l.errors().size() + " problem(s): " + l.errors().get(0);
		}
		return l.changed() ? "Layouts reloaded: " + String.join(", ", l.changedLayouts()) : "Layouts reloaded: no changes";
	}

	private static void report(HqLayoutPacks.Load l, boolean manual) {
		if (l.errors().isEmpty() && !l.changed() && !manual) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Component title = Component.literal(l.errors().isEmpty() ? "HQ layouts" : "HQ layouts: error");
		Component body = Component.literal(summary(l));
		try {
			SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, title, body);
		} catch (RuntimeException e) {
			// no toasts yet (very early): the log has it
		}
		if (mc.player != null) {
			for (String e : l.errors()) {
				mc.player.sendSystemMessage(Component.literal("[HQ layouts] " + e).withStyle(ChatFormatting.RED));
			}
			if (l.changed()) {
				mc.player.sendSystemMessage(Component.literal("[HQ layouts] reloaded " + String.join(", ", l.changedLayouts())
						+ (l.errors().isEmpty() ? "" : " (files with errors keep their last good version)")).withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
