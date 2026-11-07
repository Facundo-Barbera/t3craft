package dev.t3craft.client.debug;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.t3craft.debug.DevReload;
import dev.t3craft.debug.ModuleGuard;
import dev.t3craft.debug.ResetReason;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Separate client entrypoint (see {@code fabric.mod.json}) that wires the hot-swap safety net
 * without touching the rest of the mod:
 * <ul>
 *   <li>Polls {@link DevReload#checkForRedefinition()} every {@value #POLL_TICKS} ticks, only in a
 *   development environment or with {@code -Dt3craft.devreload=true}
 *   ({@code -Dt3craft.devreload=false} turns it off everywhere). The poll itself is guarded.</li>
 *   <li>Watches the layout of the mod's known state holders (looked up by name, so a class renamed on
 *   another branch is just skipped) so a redefinition is noticed even before those modules subscribe
 *   their own {@link dev.t3craft.debug.Resettable}.</li>
 *   <li>Registers the client-only {@code /t3dev} command: {@code status}, {@code reset}, {@code check},
 *   {@code enable <module>}, {@code disable <module>}.</li>
 * </ul>
 */
public final class DevReloadClient implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("t3craft/devreload");
	static final int POLL_TICKS = 40;
	/** State holders whose class layout matters after a hot-swap. Detection only; reset is theirs. */
	private static final String[] WATCHED = {
			"dev.t3craft.client.T3CraftClient",
			"dev.t3craft.client.npc.NpcRegistry",
			"dev.t3craft.client.npc.NpcInfo",
			"dev.t3craft.client.npc.ThreadAvatar",
			"dev.t3craft.client.hud.PeekHud",
			"dev.t3craft.t3.DirectController",
			"dev.t3craft.t3.ShellState",
	};

	private int ticks;

	public static boolean pollingEnabled() {
		String prop = System.getProperty("t3craft.devreload");
		if (prop != null) {
			return Boolean.parseBoolean(prop);
		}
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommand(dispatcher));
		if (!pollingEnabled()) {
			return;
		}
		List<Class<?>> watched = new ArrayList<>();
		for (String name : WATCHED) {
			try {
				watched.add(Class.forName(name, false, DevReloadClient.class.getClassLoader()));
			} catch (ClassNotFoundException | LinkageError e) {
				LOG.debug("not watching {} ({})", name, e.toString());
			}
		}
		DevReload.get().register("t3craft.watch", reason -> {
		}, watched.toArray(Class<?>[]::new));
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("devreload.poll", mc -> {
			if (++ticks % POLL_TICKS == 0) {
				DevReload.get().checkForRedefinition();
			}
		}));
		LOG.info("hot-swap safety net on: watching {} class(es), polling every {} ticks", watched.size(), POLL_TICKS);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("t3dev")
				.executes(c -> status(c.getSource()))
				.then(literal("status").executes(c -> status(c.getSource())))
				.then(literal("reset").executes(c -> {
					int ok = DevReload.get().resetAll(ResetReason.MANUAL);
					c.getSource().sendFeedback(prefix().append("reset " + ok + "/" + DevReload.get().ids().size() + " state holder(s)"));
					return 1;
				}))
				.then(literal("check").executes(c -> {
					boolean changed = DevReload.get().checkForRedefinition();
					c.getSource().sendFeedback(prefix().append(changed ? "redefinition found; state reset" : "no class layout change"));
					return 1;
				}))
				.then(literal("enable").then(argument("module", StringArgumentType.word()).executes(c -> {
					String m = StringArgumentType.getString(c, "module");
					boolean known = ModuleGuard.get().enable(m);
					c.getSource().sendFeedback(prefix().append(known ? "enabled " + m : "unknown module " + m));
					return known ? 1 : 0;
				})))
				.then(literal("disable").then(argument("module", StringArgumentType.word()).executes(c -> {
					String m = StringArgumentType.getString(c, "module");
					ModuleGuard.get().disable(m);
					c.getSource().sendFeedback(prefix().append("disabled " + m));
					return 1;
				}))));
	}

	private static int status(FabricClientCommandSource src) {
		DevReload r = DevReload.get();
		src.sendFeedback(prefix().append("polling " + (pollingEnabled() ? "on" : "off")
				+ ", " + r.ids().size() + " state holder(s), " + r.resets() + " reset(s), "
				+ r.redefinitions() + " redefinition(s)"));
		for (ModuleGuard.Status s : ModuleGuard.get().status()) {
			src.sendFeedback(Component.literal("  " + s.module() + ": ")
					.append(Component.literal(s.enabled() ? "ok" : "DISABLED").withStyle(s.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED))
					.append(Component.literal(s.totalFailures() == 0 ? "" : " (" + s.totalFailures() + " failure(s), last: " + s.lastError() + ")")
							.withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[t3dev] ").withStyle(ChatFormatting.AQUA);
	}
}
