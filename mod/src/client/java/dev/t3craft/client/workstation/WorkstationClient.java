package dev.t3craft.client.workstation;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.client.npc.ChipsConfig;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.debug.DevReload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Workstations (one computer + screen per thread, as in AgentCraft): the player walks up to a desk
 * and reads the thread's title, status, last action and age off the screen without opening any
 * menu; using the computer opens that thread's chat. Client entrypoint (own entry in
 * {@code fabric.mod.json}), client-only, nothing registered on the server.
 *
 * <p>Pieces: {@link WorkstationModel} + {@link WorkstationCache} (pure, tested: lines, wrap, colours,
 * invalidation), {@link WorkstationPlacementSource} (where; provisional {@link DeskPlacement}),
 * {@link WorkstationRenderer} (client-only display entities, ids from −5,000,000) and
 * {@link ThreadOpener} (what a click opens).
 *
 * <p>Integration points: {@link #setPlacementSource} (hq-design), {@link #setThreadOpener}
 * (feat/chat), {@link #archived()} (hq-design/builders: which stations show a settled thread).
 *
 * <p>Click handling never breaks vanilla: only a main-hand use on the exact computer block of a
 * visible station is taken (returning FAIL on the client, so nothing is sent to the server and the
 * block's own action does not run); sneaking passes through so blocks can still be placed against
 * it. Everything else returns PASS.
 */
public final class WorkstationClient implements ClientModInitializer {
	private static final String MODULE = "workstations";
	/** Re-read our config block when {@code t3craft.json} changed, checked this often (ticks). */
	private static final int CONFIG_POLL_TICKS = 100;

	private static @Nullable WorkstationClient instance;

	private final WorkstationModel model = new WorkstationModel();
	private final WorkstationCache cache = new WorkstationCache(model);
	private final WorkstationRenderer renderer = new WorkstationRenderer();
	private final DeskPlacement provisional = new DeskPlacement(WorkstationClient::registryOrigin);
	private final ThreadOpener defaultOpener = new ChatScreenOpener();
	private final Map<String, WorkstationThread> threads = new HashMap<>();
	private @Nullable WorkstationPlacementSource placementOverride;
	private @Nullable ThreadOpener openerOverride;
	private WorkstationConfig config = WorkstationConfig.DEFAULTS;
	private @Nullable FileTime configStamp;
	private @Nullable ClientLevel level;
	private int ticks;

	/** The running instance (null before init / after the client stops). */
	public static @Nullable WorkstationClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		reloadConfig();
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			renderer.clear(mc.level);
			cache.clear();
			if (instance == this) {
				instance = null;
			}
		});
		DevReload.get().register("t3craft.workstations", reason -> {
			renderer.clear(Minecraft.getInstance().level);
			cache.clear();
			threads.clear();
		});
		UseBlockCallback.EVENT.register((player, lvl, hand, hit) -> {
			if (!lvl.isClientSide() || !config.enabled() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			String key = renderer.stationAt(hit.getBlockPos());
			// FAIL on the client: consumed, no packet to the server, so the block's own action never runs.
			return key == null ? InteractionResult.PASS : use(player, hand, key, InteractionResult.FAIL);
		});
		UseEntityCallback.EVENT.register((player, lvl, hand, entity, hit) -> {
			if (!lvl.isClientSide()) {
				return InteractionResult.PASS;
			}
			String key = renderer.stationFor(entity);
			// SUCCESS like the NPCs' handler: consumed here, so vanilla does not go on to use the held item.
			return key == null ? InteractionResult.PASS : use(player, hand, key, InteractionResult.SUCCESS);
		});
		// Our entities are unknown to the server: never send an attack packet for them.
		AttackEntityCallback.EVENT.register((player, lvl, hand, entity, hit) ->
				lvl.isClientSide() && renderer.stationFor(entity) != null ? InteractionResult.FAIL : InteractionResult.PASS);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommands(dispatcher));
	}

	// ---- integration points -------------------------------------------------------------------

	/** Plug the HQ's desks in (null = provisional {@link DeskPlacement}). See {@link WorkstationPlacementSource}. */
	public void setPlacementSource(@Nullable WorkstationPlacementSource source) {
		this.placementOverride = source;
	}

	/** Plug the chat UI in (null = {@link ChatScreenOpener}). See {@link ThreadOpener}. */
	public void setThreadOpener(@Nullable ThreadOpener opener) {
		this.openerOverride = opener;
	}

	/** Thread keys ({@code <env>:<thread>}) whose station currently shows a settled (archived) thread. */
	public Set<String> archived() {
		return renderer.archived();
	}

	/** Where a thread's station is, if it is shown. */
	public @Nullable StationPlacement placement(String threadKey) {
		return renderer.placement(threadKey);
	}

	/** Last rendered screen of a thread (tests, debug, other UIs). */
	public @Nullable StationScreen screen(String threadKey) {
		return cache.screen(threadKey);
	}

	public boolean enabled() {
		return config.enabled();
	}

	// ---- tick ---------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// Old entities died with the old level; rebuild everything in the new one.
			renderer.clear(null);
			cache.invalidateAll();
			level = lvl;
		}
		if (++ticks % CONFIG_POLL_TICKS == 0) {
			pollConfig();
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		if (!config.enabled()) {
			if (renderer.size() > 0) {
				renderer.clear(lvl);
				cache.invalidateAll();
			}
			return;
		}
		T3CraftClient client = T3CraftClient.get();
		List<NpcInfo> npcs = client == null ? List.of() : client.npcs().all();
		List<WorkstationThread> list = new ArrayList<>(npcs.size());
		threads.clear();
		for (NpcInfo n : npcs) {
			WorkstationThread t = WorkstationThread.fromNpc(n, ChipsConfig.current().branchWorkstation());
			list.add(t);
			threads.put(t.key(), t);
		}
		WorkstationPlacementSource source = placementOverride != null ? placementOverride : provisional;
		renderer.tick(lvl, list, source, cache, System.currentTimeMillis());
	}

	private static @Nullable Pos registryOrigin() {
		T3CraftClient client = T3CraftClient.get();
		Vec3 o = client == null ? null : client.npcs().layoutOrigin();
		return o == null ? null : new Pos(o.x, o.y, o.z);
	}

	private InteractionResult use(Player player, InteractionHand hand, String key, InteractionResult consumed) {
		WorkstationThread t = threads.get(key);
		if (t == null) {
			return InteractionResult.PASS;
		}
		if (consumed == InteractionResult.FAIL) {
			// Vanilla only swings on SUCCESS; give the block click the same feedback.
			player.swing(hand, SwingAnimation.DEFAULT, false);
		}
		ThreadOpener opener = openerOverride != null ? openerOverride : defaultOpener;
		try {
			opener.open(t.envId(), t.threadId());
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("T3Craft: could not open thread {}", key, e);
		}
		return consumed;
	}

	// ---- config -------------------------------------------------------------------------------

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	}

	private void reloadConfig() {
		Path f = configFile();
		config = WorkstationConfig.load(f);
		configStamp = stamp(f);
	}

	private void pollConfig() {
		FileTime now = stamp(configFile());
		if (now != null && !now.equals(configStamp)) {
			reloadConfig();
		}
	}

	private static @Nullable FileTime stamp(Path f) {
		try {
			return Files.exists(f) ? Files.getLastModifiedTime(f) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private int setEnabled(CommandContext<FabricClientCommandSource> c, boolean on) {
		WorkstationConfig next = new WorkstationConfig(on);
		try {
			next.save(configFile());
			config = next;
			configStamp = stamp(configFile());
			// Refresh the settings' merge base so a later save from the settings screen keeps our key.
			ConfigManager.get().load();
			c.getSource().sendFeedback(prefix().append(Component.translatable(on ? "t3craft.workstation.on" : "t3craft.workstation.off")));
			return 1;
		} catch (IOException e) {
			c.getSource().sendError(prefix().append(Component.translatable("t3craft.workstation.save_failed", e.getMessage())));
			return 0;
		}
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("t3craft").then(literal("workstation")
				.executes(c -> {
					c.getSource().sendFeedback(prefix().append(Component.translatable("t3craft.workstation.status",
							Component.translatable(config.enabled() ? "options.on" : "options.off"), renderer.size(), renderer.archived().size())));
					return 1;
				})
				.then(literal("on").executes(c -> setEnabled(c, true)))
				.then(literal("off").executes(c -> setEnabled(c, false)))));
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
