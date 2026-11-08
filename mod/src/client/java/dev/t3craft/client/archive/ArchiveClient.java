package dev.t3craft.client.archive;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.debug.DevReload;
import dev.t3craft.t3.ArchiveWire;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.T3Connection;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The archive: closed threads (settled and archived) of every environment, browsed in one place,
 * the {@link ArchiveScreen}. Client entrypoint, client-only, read-only towards T3.
 *
 * <p>The archive lives <b>only</b> in that screen: nothing is drawn in the world for each closed
 * thread (no shelf slots, labels or props). It is reached from:
 * <ul>
 *   <li>the archive terminal block ({@code t3craft:archive_terminal}, blocks module on): its screen
 *   shows {@link #display()} and a click calls {@link #openArchive()} (wired by {@code BlocksClient});</li>
 *   <li>{@value ArchiveAccess#COMMAND} (always registered, the way in for "GUI only" without the blocks
 *   module) and the portable terminal's "Archive" button.</li>
 * </ul>
 *
 * <p>Pieces: {@link ArchiveStore} (cache + rare refresh over {@link ArchiveSource}),
 * {@link ArchiveList} (search, filters, pages), {@link TerminalDisplay} (the block's screen, cached by
 * store version) and {@link ArchiveOpener} (read-only chat).
 *
 * <p>Integration points: {@link #setCaller} / {@link #setSource} (where reads go; default each
 * environment's {@code T3Connection} on the shared {@code StreamRegistry.call} path) and
 * {@link #setOpener} (what a row opens).
 */
public final class ArchiveClient implements ClientModInitializer {
	private static final String MODULE = "archive";
	/** Feed the store this often (ticks); the store itself decides when to read (rarely). */
	private static final int FEED_TICKS = 20;

	private static @Nullable ArchiveClient instance;

	private static final ArchiveSource DIRECT = ArchiveSource.direct(() -> {
		T3CraftClient c = T3CraftClient.get();
		return c == null ? null : c.direct();
	});

	private volatile ArchiveSource source = DIRECT;
	private final ArchiveStore store = new ArchiveStore(new ArchiveSource() {
		@Override
		public CompletableFuture<ArchiveWire.ArchivedSnapshot> archived(String env) {
			return source.archived(env);
		}

		@Override
		public CompletableFuture<ArchiveWire.OlderSettledPage> older(String env, ArchiveWire.Cursor before, int limit) {
			return source.older(env, before, limit);
		}

		@Override
		public boolean available() {
			return source.available();
		}
	});
	private final TerminalDisplay display = new TerminalDisplay();
	/** Kept across openings: the screen comes back with the same search, filters and page. */
	private final ArchiveList list = new ArchiveList();
	private @Nullable ArchiveOpener opener;
	private int ticks;

	public static @Nullable ArchiveClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (instance == this) {
				instance = null;
			}
		});
		DevReload.get().register("t3craft.archive", reason -> display.invalidate());
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
	}

	// ---- integration points -------------------------------------------------------------------

	/** A raw one-shot caller instead of the direct connections (null = the direct connections). */
	public void setCaller(ArchiveSource.@Nullable Caller caller) {
		setSource(caller == null ? null : ArchiveSource.of(caller));
	}

	/** Another source (null = the direct connections). */
	public void setSource(@Nullable ArchiveSource source) {
		this.source = source == null ? DIRECT : source;
	}

	public void setOpener(@Nullable ArchiveOpener opener) {
		this.opener = opener;
	}

	public ArchiveStore store() {
		return store;
	}

	/** What the archive terminal's screen shows now (cached until the store's version moves). Render thread. */
	public TerminalDisplay.Screen display() {
		return display.show(store.version(), store::entries, ZoneId.systemDefault(),
				n -> I18n.get("t3craft.archive.terminal.header", n), I18n.get("t3craft.archive.terminal.empty"));
	}

	/** Opens the archive screen (next tick, so it also works from a command or a block click). */
	public void openArchive() {
		openArchive(null);
	}

	/** Like {@link #openArchive()}; Done / Esc return to {@code parent} (e.g. the portable terminal). */
	public void openArchive(net.minecraft.client.gui.screens.@Nullable Screen parent) {
		ArchiveScreen screen = new ArchiveScreen(store, this::open, list, parent);
		T3CraftClient client = T3CraftClient.get();
		if (client != null) {
			client.openNextTick(screen);
		} else {
			Minecraft.getInstance().gui.setScreen(screen);
		}
	}

	/** Opens the archive screen if the client module is up; false otherwise. */
	public static boolean openIfReady() {
		return openIfReady(null);
	}

	/** Like {@link #openIfReady()}, returning to {@code parent} on close. */
	public static boolean openIfReady(net.minecraft.client.gui.screens.@Nullable Screen parent) {
		ArchiveClient c = instance;
		if (c == null) {
			return false;
		}
		c.openArchive(parent);
		return true;
	}

	// ---- tick ---------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		if (++ticks % FEED_TICKS == 0) {
			feed();
		}
		store.tick(System.currentTimeMillis());
	}

	/** Feeds every paired environment's shell view and connection state into the store. */
	private void feed() {
		T3CraftClient client = T3CraftClient.get();
		DirectController d = client == null ? null : client.direct();
		if (d == null) {
			store.retainEnvs(Set.of());
			return;
		}
		Map<String, String> labels = d.labels();
		Map<String, ShellState.View> shells = d.shellViews();
		Set<String> connected = new HashSet<>();
		d.statuses().forEach(st -> {
			if (st.status() == T3Connection.Status.OK) {
				connected.add(st.id());
			}
		});
		labels.forEach((id, label) -> store.updateEnv(id, label, shells.get(id), connected.contains(id)));
		store.retainEnvs(labels.keySet());
	}

	private void open(ArchiveEntry e) {
		ArchiveOpener o = opener != null ? opener : ArchiveOpener.DEFAULT;
		o.open(e);
	}

	// ---- commands -----------------------------------------------------------------------------

	/**
	 * {@code t3craft archive}: opens the archive screen. Registered in every mode (with or without the
	 * blocks module), so "GUI only" reaches the archive without the terminal block.
	 */
	static <S> LiteralArgumentBuilder<S> openCommand(Runnable open) {
		return LiteralArgumentBuilder.<S>literal("t3craft").then(LiteralArgumentBuilder.<S>literal("archive").executes(c -> {
			open.run();
			return 1;
		}));
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(openCommand(this::openArchive));
		d.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("t3craft").then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("archive")
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refresh").executes(c -> {
					boolean will = store.refreshNow(System.currentTimeMillis());
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.translatable(
							!store.fullReads() ? "t3craft.archive.shell_only" : will ? "t3craft.archive.refreshing" : "t3craft.archive.recent")));
					return 1;
				}))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(c -> {
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.translatable("t3craft.archive.status",
							store.entries().size(),
							Component.translatable(store.fullReads() ? "t3craft.archive.full" : "t3craft.archive.shell_only"),
							Component.translatable(ArchiveAccess.hintKey(dev.t3craft.blocks.T3Blocks.active()), ArchiveAccess.COMMAND))));
					for (ArchiveStore.EnvStatus s : store.statuses()) {
						c.getSource().sendFeedback(Component.literal("  " + s.label() + ": " + s.listed() + (s.hasMore() ? "+" : "")
								+ (s.connected() ? "" : " (offline)") + (s.error() == null ? "" : " · " + s.error())));
					}
					return 1;
				}))));
	}
}
