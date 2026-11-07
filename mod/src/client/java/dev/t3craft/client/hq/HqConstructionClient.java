package dev.t3craft.client.hq;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.construct.HqConstruction;
import dev.t3craft.hq.construct.HqSiteFrame;
import dev.t3craft.hq.construct.HqTarget;
import dev.t3craft.hq.construct.mc.HqRuntime;
import dev.t3craft.site.mc.SiteRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Client entrypoint of the HQ construction ({@code feat/hq-construction}).
 *
 * <ul>
 *   <li>Installs {@link HqRuntime} (the HQ's site request and designer, the reconciler) with the
 *   animated crew ({@link CrewBridge}) for expansions and in-place changes.</li>
 *   <li>Feeds the live roster (the NPCs shown, so subagents are already filtered out) once every
 *   environment is in sync, never a half-loaded one.</li>
 *   <li>Plugs the standing HQ into NPCs and workstations ({@link HqPresence}); the demo world gets
 *   its own HQ ({@code DemoClient} hands it to {@link HqPresence#demo}).</li>
 *   <li>{@code /t3craft hq [status|build|pause|resume]} (merged into {@code /t3craft}).</li>
 * </ul>
 */
public final class HqConstructionClient implements ClientModInitializer {
	private static final int ROSTER_TICKS = 20;
	/**
	 * Which threads give up their desk (settled by default). <b>Integration point for
	 * fix/hq-stale-stations</b>: install its single release policy here (settle, archived, or DONE and
	 * idle for more than X minutes), e.g. {@code setReleasePolicy(n -> StationRelease.released(n, now))}.
	 * A released thread is passed to the HQ as settled: its desk is removed from the diff (the site
	 * restores its cells from the snapshot) and its computer goes to the archive.
	 */
	private static volatile Predicate<NpcInfo> releasePolicy = NpcInfo::settled;

	public static void setReleasePolicy(@Nullable Predicate<NpcInfo> policy) {
		releasePolicy = policy == null ? NpcInfo::settled : policy;
	}
	private int ticks;

	@Override
	public void onInitializeClient() {
		HqRuntime.init();
		SiteRuntime.setBuilderFactory(CrewBridge.siteBuilder());
		HqRuntime.setWriter(CrewBridge.writer(HqRuntime.pacedWriter()));
		HqRuntime.setAnchorSink(b -> Minecraft.getInstance().execute(() -> HqPresence.site(
				b.plan() == null || b.site() == null ? null : new HqPresence.Standing(b.plan(), b.site(), b.modules()))));
		ServerTickEvents.END_SERVER_TICK.register(s -> CrewBridge.tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> CrewBridge.clear());
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
			HqRuntime.roster(null);
			HqPresence.clear();
		});
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(literal("t3craft").then(literal("hq")
				.executes(c -> run(c.getSource(), HqConstructionClient::status))
				.then(literal("status").executes(c -> run(c.getSource(), HqConstructionClient::status)))
				.then(literal("build").executes(c -> run(c.getSource(), hq -> {
					hq.build();
					return List.of("Building the HQ now (roster debounce skipped, blocked steps retried).");
				})))
				.then(literal("pause").executes(c -> run(c.getSource(), hq -> {
					hq.pause();
					HqRuntime.pauseWrites(true);
					return List.of("HQ construction paused. /t3craft hq resume to continue.");
				})))
				.then(literal("resume").executes(c -> run(c.getSource(), hq -> {
					hq.resume();
					HqRuntime.pauseWrites(false);
					return List.of("HQ construction resumed.");
				}))))));
	}

	private void tick(Minecraft mc) {
		if (++ticks % ROSTER_TICKS != 0 || mc.level == null) {
			return;
		}
		HqRuntime.roster(liveRoster());
	}

	/** The roster of the NPCs shown, or null while some environment is not in sync yet. */
	static @Nullable HqInput liveRoster() {
		T3CraftClient c;
		try {
			c = T3CraftClient.get();
		} catch (RuntimeException e) {
			return null;
		}
		if (c == null) {
			return null;
		}
		Map<String, String[]> envs = c.environments();
		List<NpcInfo> npcs = c.npcs().all();
		if (c.direct() != null) {
			if (envs.isEmpty()) {
				return null;
			}
			for (String[] e : envs.values()) {
				if (!"ok".equals(e[1])) {
					return null;
				}
			}
		} else if (npcs.isEmpty()) {
			return null;
		}
		Predicate<NpcInfo> released = releasePolicy;
		HqInput.Builder b = HqInput.builder();
		for (NpcInfo n : npcs) {
			String g = n.groupId();
			String env = g.indexOf(':') > 0 ? g.substring(0, g.indexOf(':')) : g;
			b.thread(g, n.groupLabel(), env, n.envLabel(), n.id(), released.test(n));
		}
		return b.build();
	}

	private static int run(FabricClientCommandSource src, Function<HqConstruction, List<String>> action) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			src.sendError(Component.literal("[T3Craft] The HQ is built in your own (singleplayer) world only."));
			return 0;
		}
		Consumer<List<String>> reply = lines -> mc.execute(() -> lines.forEach(l -> src.sendFeedback(
				Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA).append(Component.literal(l).withStyle(ChatFormatting.WHITE)))));
		server.execute(() -> reply.accept(action.apply(HqRuntime.construction(server))));
		return 1;
	}

	static List<String> status(HqConstruction hq) {
		List<String> out = new ArrayList<>();
		out.add("HQ: " + hq.phase().name().toLowerCase().replace('_', ' ') + (hq.busy() != null ? " (" + hq.busy() + ")" : "")
				+ (hq.paused() ? ", paused" : ""));
		HqLayout l = hq.applied();
		int desks = 0;
		for (HqLayout.Module m : l.modules()) {
			desks += m.desks().size();
			out.add("  wing " + m.index() + ": " + m.label() + (m.envLabel().isEmpty() ? "" : " (" + m.envLabel() + ")") + ", " + m.desks().size() + "/"
					+ HqLayout.DESKS + " desks");
		}
		out.add("  " + l.modules().size() + "/" + HqSiteFrame.MAX_MODULES + " wings, " + desks + " threads at desks, " + l.archive().size()
				+ " in the archive" + (l.overflow().isEmpty() ? "" : ", " + l.overflow().size() + " beyond the archive"));
		HqTarget t = hq.target();
		if (t != null && !t.unseated().isEmpty()) {
			out.add("  full: " + t.unseated().size() + " thread(s) wait for a desk");
		}
		if (hq.blocked() != null) {
			out.add("  blocked: " + hq.blocked());
		}
		if (hq.phase() == HqConstruction.Phase.NO_SITE) {
			out.add("  choose a place: /t3craft site pick (or here), then confirm; the HQ follows automatically.");
		}
		return out;
	}
}
