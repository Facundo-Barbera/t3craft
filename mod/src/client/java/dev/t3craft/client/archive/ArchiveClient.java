package dev.t3craft.client.archive;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.debug.DevReload;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import dev.t3craft.t3.ArchiveWire;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.T3Connection;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The HQ's basement archive, live (inventario item 13): closed threads (settled and archived) get
 * a label on their computer in a deterministic slot, and a catalog at the foot of the stairs lists
 * all of them page by page. Client entrypoint, client-only, read-only towards T3.
 *
 * <p>Pieces: {@link ArchiveStore} (cache + rare refresh over {@link ArchiveSource}),
 * {@link ArchiveSlots} (slots, same rule as {@code HqLayout}), {@link ArchiveGeometry} (positions
 * from the HQ generator), {@link ArchiveRenderer} (client display entities, ids from −6,000,000),
 * {@link CatalogScreen} and {@link ArchiveOpener}.
 *
 * <p>Integration points:
 * <ul>
 *   <li>{@link #setSite}: whoever places the HQ in the world (builders / site / demo) passes its
 *   {@link HqSite}; without a site nothing is drawn in the world, the catalog still works
 *   ({@code /t3craft archive}).</li>
 *   <li>{@link #setCaller} / {@link #setSource}: replace where the reads go. Default: each
 *   environment's {@code T3Connection} on the shared {@code StreamRegistry.call} path.</li>
 *   <li>{@link #setOpener}: what a click opens (default {@link ArchiveOpener#DEFAULT}).</li>
 *   <li>{@link #setCatalogProp}: hook for feat/blocks-core's own catalog block (default a vanilla
 *   lectern display while the cell is air). Any real block at the catalog cell opens the catalog.</li>
 *   <li>{@link #layout()}: the current slot layout (an {@code HqLayout} with only the archive), for
 *   the HQ owner to place the archived computers where the labels are.</li>
 * </ul>
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
	private final ArchiveRenderer renderer = new ArchiveRenderer();
	private @Nullable HqSite site;
	private @Nullable ArchiveOpener opener;
	private Function<Direction, BlockState> catalogProp = ArchiveClient::lectern;
	private HqLayout layout = HqLayout.EMPTY;
	private int layoutVersion = -1;
	private @Nullable HqSite viewsSite;
	private List<ArchiveRenderer.SlotView> views = List.of();
	private @Nullable ClientLevel level;
	private int ticks;

	public static @Nullable ArchiveClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			renderer.clear(mc.level);
			if (instance == this) {
				instance = null;
			}
		});
		DevReload.get().register("t3craft.archive", reason -> {
			renderer.clear(Minecraft.getInstance().level);
			layoutVersion = -1;
		});
		UseBlockCallback.EVENT.register((player, lvl, hand, hit) -> {
			if (!lvl.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			String key = renderer.at(hit.getBlockPos());
			// FAIL on the client: consumed, nothing sent, the block's own action (lectern GUI…) never runs.
			return key == null ? InteractionResult.PASS : use(player, hand, key, InteractionResult.FAIL);
		});
		UseEntityCallback.EVENT.register((player, lvl, hand, entity, hit) -> {
			if (!lvl.isClientSide()) {
				return InteractionResult.PASS;
			}
			String key = renderer.of(entity);
			return key == null ? InteractionResult.PASS : use(player, hand, key, InteractionResult.SUCCESS);
		});
		AttackEntityCallback.EVENT.register((player, lvl, hand, entity, hit) ->
				lvl.isClientSide() && renderer.of(entity) != null ? InteractionResult.FAIL : InteractionResult.PASS);
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
	}

	// ---- integration points -------------------------------------------------------------------

	/** Where the HQ stands (null = not in this world: nothing drawn). Client thread. */
	public void setSite(@Nullable HqSite site) {
		this.site = site;
	}

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

	/** Hook for feat/blocks-core: the block state shown as the catalog while its cell is air (null = vanilla lectern). */
	public void setCatalogProp(@Nullable Function<Direction, BlockState> prop) {
		this.catalogProp = prop == null ? ArchiveClient::lectern : prop;
	}

	public ArchiveStore store() {
		return store;
	}

	/** Current slot layout (archive only). */
	public HqLayout layout() {
		return layout;
	}

	public void openCatalog() {
		Set<String> slotted = new HashSet<>(ArchiveSlots.slots(layout).keySet());
		CatalogScreen screen = new CatalogScreen(store, this::open, slotted::contains);
		T3CraftClient client = T3CraftClient.get();
		if (client != null) {
			client.openNextTick(screen);
		} else {
			Minecraft.getInstance().gui.setScreen(screen);
		}
	}

	// ---- tick ---------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			renderer.clear(null);
			level = lvl;
		}
		long now = System.currentTimeMillis();
		if (++ticks % FEED_TICKS == 0) {
			feed();
		}
		store.tick(now);
		int v = store.version();
		if (v != layoutVersion) {
			layout = ArchiveSlots.assign(store.entries(), layout);
			layoutVersion = v;
			viewsSite = null;
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		HqSite s = site;
		if (s == null) {
			if (renderer.size() > 0 || viewsSite != null) {
				renderer.clear(lvl);
				viewsSite = null;
			}
			renderer.tick(lvl, List.of(), null);
			return;
		}
		if (!s.equals(viewsSite)) {
			views = slotViews(s);
			viewsSite = s;
		}
		ArchiveGeometry.WorldCatalog cat = ArchiveGeometry.catalog(s);
		int count = store.entries().size();
		renderer.tick(lvl, views, new ArchiveRenderer.CatalogView(cat.cell(), cat.facing(),
				Component.translatable("t3craft.archive.catalog.title").getString(),
				Component.translatable("t3craft.archive.catalog.count", count).getString(), catalogProp.apply(cat.facing())));
	}

	private List<ArchiveRenderer.SlotView> slotViews(HqSite s) {
		Map<String, Integer> slots = ArchiveSlots.slots(layout);
		List<ArchiveRenderer.SlotView> out = new ArrayList<>();
		ZoneId zone = ZoneId.systemDefault();
		for (ArchiveEntry e : store.entries()) {
			Integer slot = slots.get(e.key());
			if (slot == null) {
				continue;
			}
			ArchiveGeometry.WorldSlot w = ArchiveGeometry.world(s, slot);
			out.add(new ArchiveRenderer.SlotView(e.key(), w.cell(), w.face(), ArchiveLabel.lines(e, zone)));
		}
		return out;
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

	private InteractionResult use(Player player, InteractionHand hand, String key, InteractionResult consumed) {
		if (consumed == InteractionResult.FAIL) {
			player.swing(hand, SwingAnimation.DEFAULT, false);
		}
		try {
			if (ArchiveRenderer.CATALOG.equals(key)) {
				openCatalog();
			} else {
				ArchiveEntry e = store.entry(key);
				if (e == null) {
					return InteractionResult.PASS;
				}
				open(e);
			}
		} catch (RuntimeException ex) {
			T3Craft.LOGGER.warn("T3Craft: archive click failed for {}", key, ex);
		}
		return consumed;
	}

	private void open(ArchiveEntry e) {
		ArchiveOpener o = opener != null ? opener : ArchiveOpener.DEFAULT;
		o.open(e);
	}

	private static BlockState lectern(Direction facing) {
		return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing);
	}

	// ---- commands -----------------------------------------------------------------------------

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("t3craft").then(literal("archive")
				.executes(c -> {
					openCatalog();
					return 1;
				})
				.then(literal("refresh").executes(c -> {
					boolean will = store.refreshNow(System.currentTimeMillis());
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.translatable(
							!store.fullReads() ? "t3craft.archive.shell_only" : will ? "t3craft.archive.refreshing" : "t3craft.archive.recent")));
					return 1;
				}))
				.then(literal("status").executes(c -> {
					c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.translatable("t3craft.archive.status",
							store.entries().size(), layout.archive().size(), HqLayout.ARCHIVE_CAPACITY,
							Component.translatable(site == null ? "t3craft.archive.no_site" : "t3craft.archive.site"),
							Component.translatable(store.fullReads() ? "t3craft.archive.full" : "t3craft.archive.shell_only"))));
					for (ArchiveStore.EnvStatus s : store.statuses()) {
						c.getSource().sendFeedback(Component.literal("  " + s.label() + ": " + s.listed() + (s.hasMore() ? "+" : "")
								+ (s.connected() ? "" : " (offline)") + (s.error() == null ? "" : " · " + s.error())));
					}
					return 1;
				}))));
	}
}
