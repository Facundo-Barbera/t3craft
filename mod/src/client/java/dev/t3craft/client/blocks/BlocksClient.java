package dev.t3craft.client.blocks;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.T3Craft;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.BindingWriter;
import dev.t3craft.blocks.BoundBlockEntity;
import dev.t3craft.blocks.HqBindings;
import dev.t3craft.blocks.LampApplier;
import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.LiveLamps;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.T3Blocks;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.workstation.ChatScreenOpener;
import dev.t3craft.client.workstation.ThreadOpener;
import dev.t3craft.client.workstation.WorkstationClient;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.debug.DevReload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the blocks module (own {@code client} entrypoint). Does <b>nothing</b> block-related when
 * {@link T3Blocks#active()} is false: no renderer, no lamp tick, no hook (only {@link HubBeacon}'s client-only glow); the workstations keep their own
 * screens and the HQ its vanilla blocks.
 *
 * <p>When active, once per tick it reads the threads the client already has ({@code NpcRegistry},
 * which {@code ThreadShell}/{@code T3Connection} or the bridge keep up to date; no subscription of its
 * own) and:
 * <ul>
 *   <li>feeds {@link MonitorScreens} (monitors redraw only when a screen's version changes);</li>
 *   <li>computes the wanted lamp states ({@link LampSignals}) and, in singleplayer / LAN host, hands
 *       them to the integrated server ({@link LampApplier}) when they change, plus a resync every
 *       {@value #RESYNC_TICKS} ticks for lamps that were just placed or loaded.</li>
 * </ul>
 * Lamps get {@link LampRenderer} (breathing glow, the hub beacon) and motes; {@link HubBeacon} runs
 * even with the module off (client-only glow over the hub lantern).
 *
 * <p>Using a monitor opens its thread through the workstations' {@link ThreadOpener}.
 *
 * <p>Live log ({@link MonitorLiveLog}): monitors close to the player and in view hold a read-only
 * share of their thread's stream ({@link MonitorVisibility} decides, with hysteresis; one stream per
 * thread however many monitors show it). Options: {@code blocks.monitor.*} ({@link MonitorSettings}).
 *
 * <p>Integration points: {@link #setHqBindings} (HQ owner: the bindings the HQ's fixtures should have
 * for the live roster; written into the block entities, only where they change) and
 * {@link #setPositionBindings} (world cell → binding fallback for blocks without their own data).
 */
public final class BlocksClient implements ClientModInitializer {
	private static final String MODULE = "blocks";
	static final int RESYNC_TICKS = 40;
	/** How often the HQ bindings are re-read (the source caches by roster, so this is cheap). */
	static final int HQ_SYNC_TICKS = 20;
	/** Full re-send of the HQ bindings (chunks loaded since, fixtures rebuilt); the writer skips equal ones. */
	static final int HQ_RESYNC_TICKS = 200;

	private static @Nullable BlocksClient instance;

	private static final int MOTE_TTL_TICKS = 40;
	private static final float MOTE_CHANCE = 0.15f;

	/** A lamp the renderer drew recently (motes only rise from lamps in view). */
	private record Seen(Binding.Kind kind, LampStatus status, int tick) {
	}

	private final HubBeacon beacon = new HubBeacon();
	private final Map<Long, Seen> seenLamps = new HashMap<>();
	private final Random random = new Random();
	private final MonitorScreens screens = new MonitorScreens();
	private final Map<String, WorkstationThread> threads = new HashMap<>();
	private final ThreadOpener fallbackOpener = new ChatScreenOpener();
	private volatile MonitorSettings monitorSettings = MonitorSettings.DEFAULT;
	private volatile ScreenExtent.@Nullable Provider extents;
	private final MonitorLiveLog liveLog = new MonitorLiveLog(MonitorSettings.DEFAULT);
	private @Nullable MonitorRenderer renderer;
	private @Nullable PlaqueRenderer plaqueRenderer;
	private volatile @Nullable Map<Long, Binding> positional;
	private volatile @Nullable Map<String, LampStatus> lastWanted;
	private volatile @Nullable Supplier<@Nullable Map<Long, Binding>> hqBindings;
	private @Nullable Map<Long, Binding> lastHq;
	private int lastHqChanged;
	private boolean suppressorInstalled;
	private int ticks;
	private int lastChanged;

	public static @Nullable BlocksClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		// the hub beacon: the lamp's BER when the module put it in the lantern, a client-only glow otherwise
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE + ".beacon", beacon::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> beacon.clear(mc.level));
		DevReload.get().register("t3craft.blocks.beacon", reason -> beacon.clear(Minecraft.getInstance().level));
		T3Blocks.Content c = T3Blocks.content();
		if (c == null) {
			T3Craft.LOGGER.info("T3Craft blocks module off ({}): no block renderers, workstations keep their own screens",
					T3Blocks.decision().reason());
			return;
		}
		instance = this;
		loadMonitorSettings();
		BlockEntityRendererRegistry.register(c.monitorType(), ctx -> {
			MonitorRenderer r = new MonitorRenderer(this);
			renderer = r;
			return r;
		});
		BlockEntityRendererRegistry.register(c.lampType(), ctx -> new LampRenderer(this));
		BlockEntityRendererRegistry.register(c.kit().plaqueType(), ctx -> {
			PlaqueRenderer r = new PlaqueRenderer();
			plaqueRenderer = r;
			return r;
		});
		MonitorBlock.setClientUse(this::use);
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> liveLog.onLoad(be));
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> liveLog.onUnload(be));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			reset();
			liveLog.close();
		});
		DevReload.get().register("t3craft.blocks", reason -> {
			reset();
			loadMonitorSettings();
		});
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> registerCommands(d));
	}

	// ---- integration points / accessors -------------------------------------------------------

	/** World cell ({@code BlockPos.asLong}) → binding for blocks without their own data. Null = none. */
	public void setPositionBindings(@Nullable Map<Long, Binding> bindings) {
		positional = bindings == null ? null : Map.copyOf(bindings);
		lastWanted = null; // re-apply lamps with the new fallback
	}

	/**
	 * Automatic HQ binding: {@code source} gives world cell ({@code BlockPos.asLong}) → binding for every
	 * HQ fixture as the live roster wants it now (e.g. {@code HqBindings.world(plan, site)} of the plan
	 * for the current roster against the built layout; null when unknown). Every
	 * {@value #HQ_SYNC_TICKS} ticks the change since the last read ({@link HqBindings#changes}) is
	 * written into the block entities on the integrated server, so a thread that moves desk or is
	 * settled rebinds its monitor and lamp and the freed desk goes unbound (off). Null = stop.
	 */
	public void setHqBindings(@Nullable Supplier<@Nullable Map<Long, Binding>> source) {
		hqBindings = source;
		lastHq = null;
	}

	/** Block entities rewritten by the last HQ binding pass (status, tests). */
	public int lastHqChanged() {
		return lastHqChanged;
	}

	MonitorScreens screens() {
		return screens;
	}

	/**
	 * Integration point for connected (multi-block) monitors: {@code provider} tells the renderer
	 * which screen each monitor block belongs to ({@link ScreenExtent}: size in blocks and whether
	 * this block is the origin that draws it). Null = every monitor is a 1×1 screen. Client thread.
	 */
	public void setScreenExtents(ScreenExtent.@Nullable Provider provider) {
		extents = provider;
		MonitorRenderer r = renderer;
		if (r != null) {
			r.clear();
		}
	}

	ScreenExtent extentOf(dev.t3craft.blocks.MonitorBlockEntity be) {
		ScreenExtent.Provider p = extents;
		if (p == null) {
			return ScreenExtent.ONE;
		}
		try {
			ScreenExtent e = p.extent(be);
			return e == null ? ScreenExtent.ONE : e;
		} catch (RuntimeException ex) {
			return ScreenExtent.ONE;
		}
	}

	MonitorSettings monitorSettings() {
		return monitorSettings;
	}

	MonitorFeeds feeds() {
		return liveLog.feeds();
	}

	/** The thread the client knows under {@code key} (this tick), or null. */
	@Nullable WorkstationThread thread(String key) {
		return threads.get(key);
	}

	private void loadMonitorSettings() {
		MonitorSettings s = MonitorSettings.load(FabricLoader.getInstance().getConfigDir().resolve("t3craft.json"));
		monitorSettings = s;
		liveLog.settings(s);
	}

	/** Effective binding of the block at {@code pos} with block entity binding {@code own}. */
	Binding resolve(BlockPos pos, String own) {
		Map<Long, Binding> p = positional;
		return Binding.resolve(own, p == null ? null : p.get(pos.asLong()));
	}

	/** Render thread (= client thread): {@link LampRenderer} reports each lamp it draws. */
	void sawLamp(BlockPos pos, Binding.Kind kind, LampStatus status) {
		seenLamps.put(pos.asLong(), new Seen(kind, status, ticks));
	}

	/** Motes in the lamp's colour from seen lamps that ask for the player ({@link LampGlow#motes}). */
	private void motes(Minecraft mc) {
		if (seenLamps.isEmpty() || mc.level == null || mc.isPaused()) {
			return;
		}
		var it = seenLamps.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Seen s = e.getValue();
			if (ticks - s.tick() > MOTE_TTL_TICKS) {
				it.remove();
				continue;
			}
			if (!LampGlow.motes(s.kind(), s.status()) || random.nextFloat() >= MOTE_CHANCE) {
				continue;
			}
			BlockPos p = BlockPos.of(e.getKey());
			Direction d = s.kind() == Binding.Kind.BEACON ? Direction.UP : Direction.from2DDataValue(random.nextInt(4));
			if (!mc.level.getBlockState(p.relative(d)).isAir()) {
				continue;
			}
			double x = p.getX() + 0.5 + d.getStepX() * 0.62 + (d.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.7 : 0);
			double y = p.getY() + (d == Direction.UP ? 1.1 : 0.15 + random.nextDouble() * 0.7);
			double z = p.getZ() + 0.5 + d.getStepZ() * 0.62 + (d.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.7 : 0);
			mc.level.addParticle(new DustParticleOptions(s.status().color, 0.6f), x, y, z, 0, 0.015, 0);
		}
	}

	// ---- tick ---------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		ticks++;
		if (!suppressorInstalled) {
			WorkstationClient ws = WorkstationClient.get();
			if (ws != null) {
				// over a monitor the station's floating TextDisplay stays blank: one screen, not two
				ws.setScreenSuppressor(state -> state.getBlock() instanceof MonitorBlock);
				suppressorInstalled = true;
			}
		}
		if (mc.level == null) {
			if (liveLog.feeds().size() > 0) {
				liveLog.clearTracking(); // left the world: no stream outlives it
			}
			return;
		}
		T3CraftClient client = T3CraftClient.get();
		List<NpcInfo> npcs = client == null ? List.of() : client.npcs().all();
		List<WorkstationThread> list = new ArrayList<>(npcs.size());
		threads.clear();
		for (NpcInfo n : npcs) {
			WorkstationThread t = WorkstationThread.fromNpc(n);
			list.add(t);
			threads.put(t.key(), t);
		}
		screens.tick(list, System.currentTimeMillis());
		liveLog.tick(mc, this::resolve, this::extentOf);
		motes(mc);

		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			return; // remote: lamps keep their saved state (the server owns block states)
		}
		syncHq(mc, server);
		Map<String, LampStatus> wanted = LampSignals.wanted(list);
		if (wanted.equals(lastWanted) && ticks % RESYNC_TICKS != 0) {
			return;
		}
		lastWanted = wanted;
		Map<Long, Binding> pos = positional;
		server.execute(() -> lastChanged = LampApplier.apply(wanted, pos));
	}

	private void syncHq(Minecraft mc, IntegratedServer server) {
		Supplier<@Nullable Map<Long, Binding>> source = hqBindings;
		if (source == null || mc.level == null || ticks % HQ_SYNC_TICKS != 0) {
			return;
		}
		Map<Long, Binding> now;
		try {
			now = source.get();
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("T3Craft: HQ bindings failed", e);
			return;
		}
		if (now == null) {
			return;
		}
		Map<Long, Binding> send = ticks % HQ_RESYNC_TICKS == 0 ? now : HqBindings.changes(lastHq, now);
		lastHq = now;
		if (send.isEmpty()) {
			return;
		}
		var dim = mc.level.dimension();
		server.execute(() -> {
			ServerLevel level = server.getLevel(dim);
			if (level != null) {
				int n = BindingWriter.apply(level, send);
				lastHqChanged = n;
				if (n > 0) {
					lastWanted = null; // lamps follow their new bindings on the next tick
				}
			}
		});
	}

	private boolean use(BlockPos pos, String own) {
		Binding b = resolve(pos, own);
		if (b.kind() != Binding.Kind.THREAD) {
			return false;
		}
		WorkstationThread t = threads.get(b.value());
		String env;
		String thread;
		if (t != null) {
			env = t.envId();
			thread = t.threadId();
		} else {
			int colon = b.value().indexOf(':');
			env = colon > 0 ? b.value().substring(0, colon) : "";
			thread = colon > 0 ? b.value().substring(colon + 1) : b.value();
		}
		WorkstationClient ws = WorkstationClient.get();
		ThreadOpener opener = ws != null ? ws.opener() : fallbackOpener;
		try {
			opener.open(env, thread);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("T3Craft: could not open thread {}", b.value(), e);
		}
		return true;
	}

	private void reset() {
		screens.clear();
		threads.clear();
		seenLamps.clear();
		lastWanted = null;
		lastHq = null;
		liveLog.clearTracking();
		MonitorRenderer r = renderer;
		if (r != null) {
			r.clear();
		}
		PlaqueRenderer pr = plaqueRenderer;
		if (pr != null) {
			pr.clear();
		}
	}

	// ---- commands -----------------------------------------------------------------------------

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("t3craft").then(literal("blocks")
				.executes(c -> {
					c.getSource().sendFeedback(prefix().append(Component.translatable("t3craft.blocks.status",
							Component.translatable("options.on"), LiveLamps.size(), lastChanged)));
					c.getSource().sendFeedback(prefix().append(Component.translatable("t3craft.blocks.monitor_status",
							liveLog.loaded(), liveLog.feeds().size(), String.join(", ", liveLog.feeds().keys()))));
					return 1;
				})
				.then(literal("link").then(argument("thread", StringArgumentType.greedyString())
						.executes(c -> link(c, Binding.thread(StringArgumentType.getString(c, "thread").trim())))))
				.then(literal("unlink").executes(c -> link(c, Binding.NONE)))));
	}

	/** Binds the t3craft block the player looks at (singleplayer / LAN host only). */
	private int link(CommandContext<FabricClientCommandSource> c, Binding b) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null) {
			c.getSource().sendError(prefix().append(Component.translatable("t3craft.blocks.singleplayer_only")));
			return 0;
		}
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK
				|| !(mc.level.getBlockEntity(bh.getBlockPos()) instanceof BoundBlockEntity)) {
			c.getSource().sendError(prefix().append(Component.translatable("t3craft.blocks.look_at_block")));
			return 0;
		}
		BlockPos pos = bh.getBlockPos();
		var dim = mc.level.dimension();
		server.execute(() -> {
			ServerLevel level = server.getLevel(dim);
			if (level != null && level.getBlockEntity(pos) instanceof BoundBlockEntity be) {
				be.setBinding(b.wire());
			}
		});
		lastWanted = null;
		c.getSource().sendFeedback(prefix().append(Component.translatable("t3craft.blocks.linked", b.toString())));
		return 1;
	}

	private static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
