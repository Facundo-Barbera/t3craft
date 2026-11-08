package dev.t3craft.client.schedule;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.debug.DevReload;
import dev.t3craft.t3.DirectController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Scheduled-tasks board (inventory item 15): a <b>read-only</b> view of every environment's
 * {@code scheduledTasks.subscribe}, shown on the HQ board as a client-only text display and listed in
 * chat with {@code /t3craft schedule}. Client entrypoint (own entry in {@code fabric.mod.json}).
 * There is deliberately no "run now" or enable/disable: those are writes (item 26, not approved).
 *
 * <p>Pieces: {@link ScheduleSource} (where the stream comes from; never its own connection or
 * request path), {@link ScheduleBook} (latest lists), {@link ScheduleEntry}
 * (order), {@link Countdown}, {@link SchedulePager} (rotation), {@link ScheduleText} (board and chat
 * text), {@link ScheduleRenderer} (display entity, ids from −7,000,000).
 *
 * <p>Integration points: {@link #setSource} (the stream adapter, default {@link ConnectionScheduleSource}),
 * {@link #setPlacementSource} (HQ board anchor; default
 * {@link LayoutBoardPlacement}), {@link #setBoardHook} (future "task board" block of the blocks
 * module), {@link #entries()} (other UIs).
 */
public final class ScheduleClient implements ClientModInitializer {
	private static final String MODULE = "schedule";
	/** Rows (tasks) per board page: two lines each, plus the header. */
	public static final int PAGE_SIZE = 6;
	/** Each page stays this long before the board rotates to the next one. */
	public static final long ROTATE_MS = 8_000;
	/** Re-render the board this often (ticks); countdowns have one-second resolution. */
	private static final int REFRESH_TICKS = 10;

	private static @Nullable ScheduleClient instance;

	private final ScheduleBook book = new ScheduleBook();
	private final ScheduleRenderer renderer = new ScheduleRenderer();
	private final LayoutBoardPlacement provisional = new LayoutBoardPlacement(ScheduleClient::registryLayout);
	private @Nullable SchedulePlacementSource placementOverride;
	private @Nullable ScheduleBoardHook hook;
	private static final ScheduleSource DEFAULT_SOURCE = new ConnectionScheduleSource();
	private ScheduleSource source = DEFAULT_SOURCE;
	private ScheduleSource.Handle handle = ScheduleSource.Handle.NONE;
	private @Nullable DirectController subscribed;
	private boolean resubscribe;
	private @Nullable ClientLevel level;
	private List<ScheduleEntry> entries = List.of();
	private int pageCount = 1;
	private long rotationStartMs;
	private int ticks;

	/** The running instance (null before init / after the client stops). */
	public static @Nullable ScheduleClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			unsubscribe();
			renderer.clear(mc.level);
			if (instance == this) {
				instance = null;
			}
		});
		DevReload.get().register("t3craft.schedule", reason -> {
			renderer.clear(Minecraft.getInstance().level);
			ticks = 0;
		});
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(literal("t3craft")
				.then(literal("schedule").executes(this::list))));
	}

	// ---- integration points -------------------------------------------------------------------

	/** Plug the {@code scheduledTasks.subscribe} adapter in (null = {@link ConnectionScheduleSource}). Client thread. */
	public void setSource(@Nullable ScheduleSource source) {
		this.source = source == null ? DEFAULT_SOURCE : source;
		resubscribe = true;
	}

	/** Plug the HQ's board in (null = provisional {@link LayoutBoardPlacement}). */
	public void setPlacementSource(@Nullable SchedulePlacementSource source) {
		this.placementOverride = source;
	}

	/** Let a custom block draw the board (null = display entities only). See {@link ScheduleBoardHook}. */
	public void setBoardHook(@Nullable ScheduleBoardHook hook) {
		this.hook = hook;
	}

	/** Every known task in board order, as of the last refresh (client thread). */
	public List<ScheduleEntry> entries() {
		return entries;
	}

	// ---- tick ---------------------------------------------------------------------------------

	private void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// Old entity died with the old level.
			renderer.clear(null);
			level = lvl;
		}
		syncSubscription();
		if (ticks++ % REFRESH_TICKS != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		entries = subscribed == null ? List.of() : book.entries(ScheduleClient::envLabel, this::projectTitle);
		if (lvl == null || mc.player == null || subscribed == null || !dev.t3craft.client.hq.HqPresence.allows(mc)) {
			// fix/hq-gate-npcs: the board stands only in the HQ's own world and dimension
			renderer.clear(lvl);
			return;
		}
		int count = SchedulePager.pageCount(entries.size(), PAGE_SIZE);
		if (count != pageCount) {
			// Back to the soonest runs whenever the number of pages changes.
			pageCount = count;
			rotationStartMs = now;
		}
		SchedulePager.Page<ScheduleEntry> page = SchedulePager.rotating(entries, PAGE_SIZE, now - rotationStartMs, ROTATE_MS);
		ScheduleText.Board board = ScheduleText.board(page, entries.size(), now, unavailableByLabel(), !book.environments().isEmpty());
		ScheduleBoardHook h = hook;
		if (h != null) {
			try {
				if (h.show(board, entries, now)) {
					renderer.clear(lvl);
					return;
				}
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("T3Craft: schedule board hook failed", e);
			}
		}
		SchedulePlacementSource src = placementOverride != null ? placementOverride : provisional;
		SchedulePlacement where = src.placement();
		if (where == null) {
			renderer.clear(lvl);
			return;
		}
		renderer.show(lvl, where, board);
	}

	/** Follows the direct controller (it is recreated on reload); bridge mode has no schedule. */
	private void syncSubscription() {
		T3CraftClient client = T3CraftClient.get();
		DirectController d = client == null ? null : client.direct();
		if (d == subscribed && !resubscribe && (ticks % REFRESH_TICKS != 0 || !handle.stale())) {
			return;
		}
		resubscribe = false;
		unsubscribe();
		if (d != null) {
			try {
				handle = source.open(d, book);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.warn("T3Craft: could not open the scheduled tasks stream", e);
				handle = ScheduleSource.Handle.NONE;
			}
		}
		subscribed = d;
	}

	private void unsubscribe() {
		ScheduleSource.Handle h = handle;
		handle = ScheduleSource.Handle.NONE;
		try {
			h.close();
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("T3Craft: could not close the scheduled tasks stream", e);
		}
		subscribed = null;
		book.clear();
	}

	private Map<String, String> unavailableByLabel() {
		Map<String, String> out = new LinkedHashMap<>();
		book.unavailable().forEach((env, why) -> {
			String label = envLabel(env);
			out.put(label == null ? env : label, why);
		});
		return out;
	}

	private static @Nullable String envLabel(String envId) {
		T3CraftClient client = T3CraftClient.get();
		String[] e = client == null ? null : client.environments().get(envId);
		return e == null || e.length == 0 ? null : e[0];
	}

	private @Nullable String projectTitle(String envId, String projectId) {
		DirectController d = subscribed;
		if (d == null) {
			return null;
		}
		var view = d.shellViews().get(envId);
		return view == null ? null : view.projectTitle(projectId);
	}

	private static dev.t3craft.client.npc.behavior.@Nullable AnchorSource registryLayout() {
		T3CraftClient client = T3CraftClient.get();
		return client == null ? null : client.npcs().fallbackLayout();
	}

	// ---- chat ---------------------------------------------------------------------------------

	private int list(CommandContext<FabricClientCommandSource> c) {
		FabricClientCommandSource src = c.getSource();
		if (subscribed == null) {
			src.sendFeedback(prefix().append(Component.translatable("t3craft.schedule.direct_only")));
			return 0;
		}
		long now = System.currentTimeMillis();
		List<ScheduleEntry> all = book.entries(ScheduleClient::envLabel, this::projectTitle);
		Map<String, String> unavailable = unavailableByLabel();
		if (all.isEmpty()) {
			String key = !book.environments().isEmpty() ? "t3craft.schedule.none"
					: unavailable.isEmpty() ? "t3craft.schedule.waiting" : "t3craft.schedule.unavailable";
			src.sendFeedback(prefix().append(Component.translatable(key)));
		} else {
			src.sendFeedback(prefix().append(Component.translatable("t3craft.schedule.header", all.size())));
			for (ScheduleEntry e : all) {
				ScheduleText.Line l = ScheduleText.chat(e, now);
				src.sendFeedback(Component.literal("  " + l.text()).withColor(l.color()));
			}
		}
		unavailable.forEach((env, why) -> src.sendFeedback(Component.literal("  " + env + ": " + why).withStyle(ChatFormatting.GRAY)));
		return Math.max(1, all.size());
	}

	private static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
