package dev.t3craft.client.drop;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.drop.DropFolder;
import dev.t3craft.drop.DropScene;
import dev.t3craft.drop.Schematic;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * feat/ingame-preview-drop: look at schematics inside the T3Craft HQ world without restarting the game.
 * Drop {@code .nbt} / {@code .schem} files in {@code config/t3craft/preview-drop/}; a showroom with one
 * pedestal and sign per file stands in the HQ world, reachable by an arch ({@code /t3craft drop portal}) or
 * {@code /t3craft drop go}, and follows the folder live. See {@code docs/features/ingame-preview-drop.md}.
 *
 * <p>This class is the client half: it polls the folder once a second on a worker thread (reading and laying
 * out a big schematic never stalls the game), hands the latest scene to {@link DropShowroom} (which builds
 * it on the integrated server's thread, only for whoever is there), reports changes and errors as a toast
 * plus a chat line, and owns the {@code /t3craft drop} commands. Without the HQ world it only reads the
 * folder; no block is ever written anywhere else.
 *
 * <p>Commands: {@code drop} (status), {@code drop go}, {@code drop back}, {@code drop portal},
 * {@code drop reload}, {@code drop rebuild}, {@code drop folder}.
 */
public final class DropClient {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);
	private static final int POLL_TICKS = 20;
	private static final int HINT_DELAY_TICKS = 100;
	private static final int LIST_MAX = 12;
	private static final DropShowroom SHOWROOM = DropShowroom.get();

	private record Note(boolean error, String text) {
	}

	private record Row(String name, String size, String format, int blocks) {
	}

	private static boolean registered;
	private static volatile boolean started;
	private static @Nullable DropFolder folder;
	private static @Nullable ExecutorService worker;
	private static final AtomicBoolean SCANNING = new AtomicBoolean();
	private static final ConcurrentLinkedQueue<Note> NOTES = new ConcurrentLinkedQueue<>();
	private static volatile List<Row> listing = List.of();
	private static volatile List<String> broken = List.of();
	// worker thread only
	private static boolean ensured;
	private static boolean firstScan = true;
	// client thread only
	private static int ticks;
	private static int worldTicks;
	private static boolean hinted;
	private static int serverFailures;

	private DropClient() {
	}

	/** Client init (idempotent). Scanning starts once the client has fully started. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		folder = new DropFolder(DropFolder.defaultDir());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				SHOWROOM.onServerStarted(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[drop] could not start for this world", e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SHOWROOM.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (serverFailures >= 3) {
				return;
			}
			try {
				SHOWROOM.tick(server);
			} catch (RuntimeException e) {
				serverFailures++;
				T3Craft.LOGGER.error("[drop] showroom tick failed ({}/3)", serverFailures, e);
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> started = true);
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			ExecutorService w = worker;
			if (w != null) {
				w.shutdownNow();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("drop", DropClient::tick));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft").then(literal("drop")
				.executes(c -> status(c.getSource()))
				.then(literal("go").executes(c -> onServer(c.getSource(), (s, server, id) -> s.go(server, id))))
				.then(literal("back").executes(c -> onServer(c.getSource(), (s, server, id) -> s.back(server, id))))
				.then(literal("portal").executes(c -> onServer(c.getSource(), (s, server, id) -> s.placePortal(server, id))))
				.then(literal("rebuild").executes(c -> onServer(c.getSource(), (s, server, id) -> s.rebuild(server, id))))
				.then(literal("reload").executes(c -> reload(c.getSource())))
				.then(literal("folder").executes(c -> folderLine(c.getSource()))))));
	}

	// ------------------------------------------------------------------ the folder, on a worker

	private static void tick(Minecraft mc) {
		drainNotes(mc);
		if (!started) {
			return;
		}
		if (ticks++ % POLL_TICKS == 0) {
			poll();
		}
		hint(mc);
	}

	private static void poll() {
		DropFolder f = folder;
		if (f == null || !SCANNING.compareAndSet(false, true)) {
			return;
		}
		executor().execute(() -> {
			try {
				scan(f);
			} catch (RuntimeException | LinkageError e) {
				T3Craft.LOGGER.error("[drop] reading the drop folder failed", e);
			} finally {
				SCANNING.set(false);
			}
		});
	}

	private static synchronized ExecutorService executor() {
		ExecutorService w = worker;
		if (w == null) {
			w = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "t3craft-drop");
				t.setDaemon(true);
				return t;
			});
			worker = w;
		}
		return w;
	}

	/** Worker thread. */
	private static void scan(DropFolder f) {
		if (!ensured) {
			ensured = true;
			if (!f.ensure()) {
				T3Craft.LOGGER.warn("[drop] could not create {}", f.dir());
			}
		}
		DropFolder.Load l = f.scan();
		if (l == null) {
			return;
		}
		List<Row> rows = new ArrayList<>();
		for (Schematic s : l.schematics().values()) {
			rows.add(new Row(s.name(), s.sizeText(), s.format(), s.cells().size()));
		}
		listing = rows;
		broken = f.brokenFiles();
		boolean content = firstScan || !l.changed().isEmpty() || !l.removed().isEmpty();
		if (content) {
			SHOWROOM.offer(DropScene.build(l.schematics().values()));
		}
		for (String e : l.errors()) {
			T3Craft.LOGGER.warn("[drop] {}", e);
			NOTES.add(new Note(true, e));
		}
		if (content) {
			String what = summary(l);
			T3Craft.LOGGER.info("[drop] {} schematic(s) in {}{}", rows.size(), f.dir(), firstScan ? "" : " (" + what + ")");
			if (!firstScan) {
				NOTES.add(new Note(false, what));
			}
		}
		firstScan = false;
	}

	private static String summary(DropFolder.Load l) {
		List<String> parts = new ArrayList<>();
		if (!l.changed().isEmpty()) {
			parts.add("new/changed: " + names(l.changed()));
		}
		if (!l.removed().isEmpty()) {
			parts.add("removed: " + names(l.removed()));
		}
		return "showroom " + String.join("; ", parts) + " (" + l.schematics().size() + " in all)";
	}

	private static String names(List<String> names) {
		return names.size() <= 3 ? String.join(", ", names) : String.join(", ", names.subList(0, 3)) + " +" + (names.size() - 3);
	}

	// ------------------------------------------------------------------ messages

	private static void drainNotes(Minecraft mc) {
		Note n;
		while ((n = NOTES.poll()) != null) {
			if (mc.player == null || !SHOWROOM.active()) {
				continue; // outside the HQ world the log has it
			}
			try {
				SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, Component.literal(n.error() ? "Preview drop: error" : "Preview drop"),
						Component.literal(n.text()));
			} catch (RuntimeException e) {
				// no toasts yet (very early)
			}
			mc.player.sendSystemMessage(T3CraftCommands.prefix()
					.append(Component.literal(n.text()).withStyle(n.error() ? ChatFormatting.RED : ChatFormatting.GRAY)));
		}
	}

	/** Once per visit to the HQ world: where the folder is and how to get to the showroom. */
	private static void hint(Minecraft mc) {
		if (mc.player == null || !SHOWROOM.active()) {
			worldTicks = 0;
			hinted = false;
			return;
		}
		if (hinted || ++worldTicks < HINT_DELAY_TICKS) {
			return;
		}
		hinted = true;
		DropFolder f = folder;
		if (f == null) {
			return;
		}
		DropShowroom.Status st = SHOWROOM.status();
		MutableComponent msg = T3CraftCommands.prefix().append(Component.literal("Preview drop: " + listing.size()
				+ " schematic(s) in the drop folder; add or replace files any time. ").withStyle(ChatFormatting.GRAY));
		msg.append(button("[Showroom]", "t3craft drop go", "Go to the showroom (/t3craft drop go)")).append(" ");
		if (st.portal() == null) {
			msg.append(button("[Place portal here]", "t3craft drop portal", "Build the arch 4 blocks in front of you")).append(" ");
		}
		msg.append(Component.literal("[Open folder]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.OpenFile(f.dir().toString()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(f.dir().toString())))));
		mc.player.sendSystemMessage(msg);
		// files that were already broken when the game started were found on the title screen: say so now
		for (String e : broken) {
			mc.player.sendSystemMessage(T3CraftCommands.prefix().append(Component.literal(e).withStyle(ChatFormatting.RED)));
		}
	}

	private static MutableComponent button(String label, String command, String hover) {
		return Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	// ------------------------------------------------------------------ commands

	private interface ServerAction {
		void run(DropShowroom showroom, MinecraftServer server, UUID player);
	}

	/** Runs {@code action} on the integrated server's thread for the local player; only in the HQ world. */
	private static int onServer(FabricClientCommandSource src, ServerAction action) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null || !SHOWROOM.active()) {
			src.sendError(T3CraftCommands.prefix().append(Component.literal("the showroom lives in the T3Craft HQ world (title screen > T3Craft HQ)")));
			return 0;
		}
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			try {
				action.run(SHOWROOM, server, id);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[drop] command failed", e);
			}
		});
		return 1;
	}

	private static int reload(FabricClientCommandSource src) {
		DropFolder f = folder;
		if (f == null) {
			return 0;
		}
		src.sendFeedback(T3CraftCommands.prefix().append(Component.literal("rescanning the drop folder").withStyle(ChatFormatting.GRAY)));
		executor().execute(f::forgetAll); // the next poll (within a second) reads every file again
		return 1;
	}

	private static int folderLine(FabricClientCommandSource src) {
		DropFolder f = folder;
		if (f == null) {
			return 0;
		}
		src.sendFeedback(T3CraftCommands.prefix().append(Component.literal(f.dir().toString()).withStyle(s -> s.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.OpenFile(f.dir().toString())))));
		return 1;
	}

	private static int status(FabricClientCommandSource src) {
		DropFolder f = folder;
		if (f == null) {
			return 0;
		}
		src.sendFeedback(T3CraftCommands.prefix().append(Component.literal("Preview drop folder: ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(f.dir().toString()).withStyle(s -> s.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.OpenFile(f.dir().toString())))));
		List<Row> rows = listing;
		if (rows.isEmpty()) {
			src.sendFeedback(line("no schematics yet: drop .nbt (structure block) or .schem (Sponge / WorldEdit) files in the folder"));
		}
		for (int i = 0; i < rows.size() && i < LIST_MAX; i++) {
			Row r = rows.get(i);
			src.sendFeedback(line("  " + r.name() + "  " + r.size() + "  " + r.format() + "  " + r.blocks() + " blocks"));
		}
		if (rows.size() > LIST_MAX) {
			src.sendFeedback(line("  ... and " + (rows.size() - LIST_MAX) + " more"));
		}
		for (String e : broken) {
			src.sendFeedback(T3CraftCommands.prefix().append(Component.literal("  " + e).withStyle(ChatFormatting.RED)));
		}
		DropShowroom.Status st = SHOWROOM.status();
		if (!st.inHq()) {
			src.sendFeedback(line("open the T3Craft HQ world to see them (title screen > T3Craft HQ)"));
		} else {
			src.sendFeedback(line("showroom " + (st.built() ? "built" : "not built yet") + " at " + (st.showroom() == null ? "?" : st.showroom().toShortString())
					+ (st.queuedBatches() > 0 ? ", " + st.queuedBatches() + " batch(es) still going up" : "") + "; portal "
					+ (st.portal() == null ? "not placed (/t3craft drop portal)" : "at " + st.portal().toShortString())));
			src.sendFeedback(line("/t3craft drop go | back | portal | reload | rebuild | folder"));
		}
		return 1;
	}

	private static MutableComponent line(String text) {
		return T3CraftCommands.prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}
}
