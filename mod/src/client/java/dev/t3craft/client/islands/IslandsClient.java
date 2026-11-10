package dev.t3craft.client.islands;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.client.demo.VanillaDemoWorld;
import dev.t3craft.mapgen.MapTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Map type "floating islands" (feat/algo-map-generators): its own world (save folder
 * {@value IslandsWorld#FOLDER}, a void world like the HQ's) whose islands and bridges are built at run time by
 * {@link IslandsRuntime}. The HQ world stays the default and is never touched.
 *
 * <p>Commands (merged under {@code /t3craft}): {@code islands} creates the world the first time and opens it
 * afterwards (from another world it asks for {@code islands confirm}, which saves and leaves that one),
 * {@code islands status}, {@code map list} and {@code map open floating-islands}.
 */
public final class IslandsClient implements ClientModInitializer {
	private enum Pending { NONE, OPEN, OPEN_CONFIRMED }

	private volatile Pending pending = Pending.NONE;

	@Override
	public void onInitializeClient() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!VanillaDemoWorld.isDemoServer(server, IslandsWorld.FOLDER)) {
				IslandsRuntime.stop();
				return;
			}
			try {
				IslandsRuntime.start(server, VanillaDemoWorld.worldRoot(server));
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[islands] could not start, the islands world stays empty this session", e);
				IslandsRuntime.stop();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> IslandsRuntime.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				IslandsRuntime.tick(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[islands] tick failed, turned off for this session", e);
				IslandsRuntime.stop();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("islands", this::tick));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft")
				.then(literal("islands")
						// The chat screen closes right after a command runs: act on the next tick.
						.executes(c -> queue(Pending.OPEN))
						.then(literal("confirm").executes(c -> queue(Pending.OPEN_CONFIRMED)))
						.then(literal("status").executes(c -> {
							say("islands: " + IslandsRuntime.describe());
							return 1;
						})))
				.then(literal("map")
						.executes(c -> {
							list();
							return 1;
						})
						.then(literal("list").executes(c -> {
							list();
							return 1;
						}))
						.then(literal("open").then(literal(MapTypes.FLOATING_ISLANDS.id()).executes(c -> queue(Pending.OPEN)))))));
	}

	private int queue(Pending p) {
		pending = p;
		return 1;
	}

	private void tick(Minecraft mc) {
		Pending p = pending;
		pending = Pending.NONE;
		if (p != Pending.NONE) {
			open(mc, p == Pending.OPEN_CONFIRMED, null);
		}
	}

	/**
	 * Creates the islands world the first time and opens it afterwards: what {@code /t3craft islands} does, also
	 * called by the HQ menu's map type selector. {@code back} is the screen to return to if vanilla cancels the
	 * load (null: the title screen).
	 */
	public static void open(Minecraft mc, boolean confirmed, @Nullable Screen back) {
		Screen origin = back != null ? back : new TitleScreen();
		boolean inWorld = mc.level != null;
		if (inWorld && mc.isLocalServer() && IslandsRuntime.running()) {
			say("you are already in " + IslandsWorld.NAME);
			return;
		}
		if (inWorld && !confirmed) {
			say("you are in another world: /t3craft islands confirm saves and leaves it, then opens " + IslandsWorld.NAME);
			return;
		}
		if (inWorld) {
			VanillaDemoWorld.leaveWorld(mc); // blocks until the integrated server has stopped
		}
		boolean exists = VanillaDemoWorld.exists(mc, IslandsWorld.FOLDER);
		T3Craft.LOGGER.info("[islands] {} '{}'", exists ? "OPEN" : "CREATE", IslandsWorld.FOLDER);
		if (exists) {
			VanillaDemoWorld.open(mc, IslandsWorld.FOLDER, origin);
		} else {
			VanillaDemoWorld.createVoidWorld(mc, IslandsWorld.FOLDER, IslandsWorld.NAME, IslandsWorld.SEED, origin);
		}
	}

	private static void list() {
		for (MapTypes t : MapTypes.all()) {
			say(t.name() + " (" + t.id() + "): " + t.summary() + " Open with " + t.openWith());
		}
	}

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(T3CraftCommands.prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		} else {
			T3Craft.LOGGER.info("[islands] {}", text);
		}
	}
}
