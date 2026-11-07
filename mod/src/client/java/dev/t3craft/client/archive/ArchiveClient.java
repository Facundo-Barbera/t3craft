package dev.t3craft.client.archive;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.debug.DevReload;
import dev.t3craft.client.hq.HqPresence;
import dev.t3craft.hq.Anchor;
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
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The HQ's "Archived" shelf, live (inventario item 13): closed threads (settled and archived) get
 * a label in a deterministic shelf slot inside the building, and the shelf's title (or an empty
 * slot) opens the catalog, which lists all of them page by page. Client entrypoint, client-only,
 * read-only towards T3.
 *
 * <p>Pieces: {@link ArchiveStore} (cache + rare refresh over {@link ArchiveSource}),
 * {@link ArchiveSlots} (slots, same rule as the HQ layout's archive), {@link ArchiveShelf} (slot
 * positions: the plan's shelf anchors, or {@link ArchiveShelf#FALLBACK} by the board),
 * {@link ArchiveRenderer} (client display entities, ids from −6,000,000), {@link CatalogScreen}
 * and {@link ArchiveOpener}.
 *
 * <p>Integration points:
 * <ul>
 *   <li>{@link #setSite}: override where the HQ stands. By default the HQ standing in the world
 *   ({@code HqPresence.current()}, site or demo, once its hub is built); without one nothing is
 *   drawn, the catalog still works ({@code /t3craft archive}).</li>
 *   <li>{@link #setShelf}: the plan's shelf ({@code ArchiveShelf.fromPlan(plan, AnchorNames::archiveShelfSlot)}
 *   once fix/hq-archive is on main); null = {@link ArchiveShelf#FALLBACK}.</li>
 *   <li>{@link #setCaller} / {@link #setSource}: replace where the reads go. Default: each
 *   environment's {@code T3Connection} on the shared {@code StreamRegistry.call} path.</li>
 *   <li>{@link #setOpener}: what a click opens (default {@link ArchiveOpener#DEFAULT}).</li>
 *   <li>{@link #setSlotProps}: hook for feat/blocks-core's own blocks: what an air slot shows
 *   (default a vanilla chiseled bookshelf, full when occupied).</li>
 *   <li>{@link #slots()}: the current assignment (entry key → slot), for the HQ owner.</li>
 * </ul>
 */
public final class ArchiveClient implements ClientModInitializer {
	private static final String MODULE = "archive";
	/** Feed the store this often (ticks); the store itself decides when to read (rarely). */
	private static final int FEED_TICKS = 20;
	/** The HQ plan group that holds the shelf (documented group name in feat-hq-design.md). */
	private static final String HUB_GROUP = "hub";

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
	/** What an air slot shows: {@code (occupied, facing) → state}. */
	@FunctionalInterface
	public interface SlotProps {
		BlockState prop(boolean occupied, Direction facing);
	}

	private SlotProps slotProps = ArchiveClient::bookshelf;
	private ArchiveShelf shelf = ArchiveShelf.FALLBACK;
	private Map<String, Integer> slots = Map.of();
	private int slotsVersion = -1;
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
			slotsVersion = -1;
		});
		UseBlockCallback.EVENT.register((player, lvl, hand, hit) -> {
			if (!lvl.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			String key = renderer.at(hit.getBlockPos());
			// FAIL on the client: consumed, nothing sent, the block's own action never runs.
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

	/** Where the HQ stands (null = the HQ {@link HqPresence} reports, if any). Client thread. */
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

	/** Slot positions (null = {@link ArchiveShelf#FALLBACK}). Client thread. */
	public void setShelf(@Nullable ArchiveShelf shelf) {
		this.shelf = shelf == null ? ArchiveShelf.FALLBACK : shelf;
		slotsVersion = -1;
	}

	public ArchiveShelf shelf() {
		return shelf;
	}

	/** Hook for feat/blocks-core: what an air slot shows (null = vanilla chiseled bookshelf). */
	public void setSlotProps(@Nullable SlotProps props) {
		this.slotProps = props == null ? ArchiveClient::bookshelf : props;
		viewsSite = null;
	}

	public ArchiveStore store() {
		return store;
	}

	/** Current assignment: entry key → shelf slot. */
	public Map<String, Integer> slots() {
		return slots;
	}

	public void openCatalog() {
		Set<String> slotted = new HashSet<>(slots.keySet());
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
		if (v != slotsVersion) {
			slots = Map.copyOf(ArchiveSlots.assign(store.entries(), slots, shelf.capacity()));
			slotsVersion = v;
			viewsSite = null;
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		HqSite s = currentSite();
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
		Anchor head = ArchiveShelf.header(shelf, s);
		renderer.tick(lvl, views, head == null ? null : new ArchiveRenderer.HeaderView(head,
				Component.translatable("t3craft.archive.shelf.title").getString(),
				Component.translatable("t3craft.archive.shelf.count", store.entries().size()).getString()));
	}

	/**
	 * {@link #setSite}'s site, else the HQ standing in this world ({@link HqPresence#current()}, read
	 * only) once its hub is built; null when there is none.
	 */
	private @Nullable HqSite currentSite() {
		if (site != null) {
			return site;
		}
		HqPresence.Standing st = HqPresence.current();
		if (st == null || (st.built() != null && !st.built().contains(HUB_GROUP))) {
			return null;
		}
		return st.site();
	}

	/** One view per shelf slot (occupied or not). */
	private List<ArchiveRenderer.SlotView> slotViews(HqSite s) {
		ArchiveEntry[] bySlot = new ArchiveEntry[shelf.capacity()];
		for (ArchiveEntry e : store.entries()) {
			Integer slot = slots.get(e.key());
			if (slot != null && slot < bySlot.length) {
				bySlot[slot] = e;
			}
		}
		List<ArchiveRenderer.SlotView> out = new ArrayList<>();
		ZoneId zone = ZoneId.systemDefault();
		for (int i = 0; i < bySlot.length; i++) {
			ArchiveShelf.WorldSlot w = ArchiveShelf.world(shelf, s, i);
			ArchiveEntry e = bySlot[i];
			out.add(new ArchiveRenderer.SlotView(i, e == null ? null : e.key(), w.cell(), w.face(),
					e == null ? List.of() : ArchiveLabel.lines(e, zone), slotProps.prop(e != null, w.face().facing())));
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

	private static BlockState bookshelf(boolean occupied, Direction facing) {
		BlockState st = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, facing);
		if (occupied) {
			for (var prop : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
				st = st.setValue(prop, true);
			}
		}
		return st;
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
							store.entries().size(), slots.size(), shelf.capacity(),
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
