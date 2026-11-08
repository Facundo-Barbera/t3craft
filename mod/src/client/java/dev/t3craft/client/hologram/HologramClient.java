package dev.t3craft.client.hologram;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.client.hq.HqPresence;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.debug.DevReload;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqAnchorSource;
import dev.t3craft.hq.construct.HqSiteFrame;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.T3Connection;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;

/**
 * Client entrypoint of the hub hologram (feat/hub-hologram, see {@code docs/feat-hub-hologram.md}).
 * Pure client: no block, no registry, no blocks module, so it also works in GUI-only mode.
 *
 * <p>Each client tick it collects the {@link HologramModel.Inputs} (the NPC roster of every
 * environment, "done today" from the direct-mode shell views, {@code hud.hologram}, staleness) and
 * hands them to {@link HologramState}, which rebuilds the model only when they changed; the
 * {@link HologramRenderer} pushes text only for a new model. "Done today" is recounted only when an
 * environment's shell view or the day changed.
 *
 * <p>Where: the {@code hub} anchor of the standing HQ ({@link HqPresence}, site or demo) once its
 * module stands, else the HQ's {@code board}, else above the fallback NPC grid's board, else nowhere.
 */
public final class HologramClient implements ClientModInitializer {
	private static final String MODULE = "hologram";
	private static @Nullable HologramClient instance;

	private final HologramState state = new HologramState();
	private final HologramRenderer renderer = new HologramRenderer();
	private @Nullable ClientLevel level;

	// "done today" cache: shell views by identity + day start
	private final Map<String, ShellState.View> lastViews = new HashMap<>();
	private long lastDay = Long.MIN_VALUE;
	private int doneToday;

	// HQ anchor cache
	private HqPresence.@Nullable Standing lastStanding;
	private @Nullable Pos hqAnchor;

	public static @Nullable HologramClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick(MODULE, this::tick));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			renderer.clear(mc.level);
			state.reset();
			if (instance == this) {
				instance = null;
			}
		});
		DevReload.get().register("t3craft.hologram", reason -> {
			renderer.clear(Minecraft.getInstance().level);
			state.reset();
			lastViews.clear();
			lastStanding = null;
			hqAnchor = null;
		});
	}

	/** Model currently shown (HIDDEN when off or empty). */
	public HologramModel model() {
		return state.model();
	}

	/** Number of model rebuilds since start (only on change). */
	public long builds() {
		return state.builds();
	}

	private void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			renderer.clear(null); // the old entities died with the old level
			state.reset();
			level = lvl;
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		T3CraftClient client = T3CraftClient.get();
		if (client == null) {
			return;
		}
		// fix/hq-gate-npcs: the hologram stands only in the HQ's own world and dimension
		boolean enabled = ConfigManager.get().settings().hud().hologram() && HqPresence.allows(mc);
		if (!enabled) {
			state.update(new HologramModel.Inputs(List.of(), 0, false, false));
			renderer.clear(lvl);
			return;
		}
		List<HologramModel.Entry> entries = entries(client.npcs().all());
		DirectController d = client.direct();
		boolean stale = false;
		int done;
		if (d != null) {
			done = doneToday(d.shellViews(), System.currentTimeMillis());
			stale = !d.statuses().isEmpty() && d.statuses().stream().noneMatch(s -> s.status() == T3Connection.Status.OK);
		} else {
			done = HologramModel.doneFromStates(entries);
		}
		state.update(new HologramModel.Inputs(entries, done, true, stale));
		renderer.apply(lvl, state.model(), anchor(client));
	}

	static List<HologramModel.Entry> entries(List<NpcInfo> npcs) {
		List<HologramModel.Entry> out = new ArrayList<>(npcs.size());
		for (NpcInfo n : npcs) {
			if (n.settled()) {
				continue;
			}
			out.add(new HologramModel.Entry(n.envLabel(), n.envLabel(), n.groupId(), n.groupLabel(), n.title(), n.state(), n.priority()));
		}
		return out;
	}

	private int doneToday(Map<String, ShellState.View> views, long nowMs) {
		long day = HologramModel.startOfDay(nowMs, ZoneId.systemDefault());
		boolean same = day == lastDay && views.size() == lastViews.size();
		if (same) {
			for (Map.Entry<String, ShellState.View> e : views.entrySet()) {
				if (lastViews.get(e.getKey()) != e.getValue()) {
					same = false;
					break;
				}
			}
		}
		if (same) {
			return doneToday;
		}
		int n = 0;
		for (ShellState.View v : views.values()) {
			n += HologramModel.doneToday(v.threads(), day);
		}
		lastViews.clear();
		lastViews.putAll(views);
		lastDay = day;
		doneToday = n;
		return n;
	}

	private @Nullable Pos anchor(T3CraftClient client) {
		HqPresence.Standing s = HqPresence.current();
		if (s != lastStanding) {
			lastStanding = s;
			hqAnchor = s == null ? null : hqAnchor(s);
		}
		// fix/hq-gate-npcs: no fallback over the provisional grid; without an HQ anchor, no hologram
		return hqAnchor;
	}

	/** The hub anchor once the module holding it stands, else the HQ board, else null. */
	static @Nullable Pos hqAnchor(HqPresence.Standing s) {
		HqAnchorSource hq = new HqAnchorSource(s.plan(), s.site());
		dev.t3craft.hq.Anchor rel = s.plan().anchor(AnchorNames.HUB);
		if (rel != null && standing(s, rel)) {
			Anchor hub = hq.named(AnchorNames.HUB);
			if (hub != null) {
				return hub.pos();
			}
		}
		Anchor board = hq.board();
		return board == null ? null : board.pos();
	}

	private static boolean standing(HqPresence.Standing s, dev.t3craft.hq.Anchor rel) {
		if (s.built() == null) {
			return true;
		}
		String owner = HqSiteFrame.owner(rel.block().getX(), rel.block().getZ());
		return owner != null && s.built().contains(owner);
	}
}
