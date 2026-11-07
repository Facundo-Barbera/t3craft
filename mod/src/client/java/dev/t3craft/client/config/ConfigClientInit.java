package dev.t3craft.client.config;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import dev.t3craft.T3Craft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Second client entrypoint (declared in {@code fabric.mod.json}) so the config feature needs no
 * edits to {@code T3CraftClient}: loads {@code config/t3craft.json}, registers the key mappings and
 * adds {@code /t3craft config} (merged into main's {@code /t3craft} command tree by Brigadier).
 */
public final class ConfigClientInit implements ClientModInitializer {
	private @Nullable Screen pending;

	@Override
	public void onInitializeClient() {
		ConfigManager.reset(); // hot-swap: never keep an instance/listeners from a previous class version
		T3CraftSettings s = ConfigManager.get().settings();
		T3Craft.LOGGER.info("[config] loaded {}", s); // toString redacts bridgeKey

		T3CraftKeys keys = new T3CraftKeys();
		keys.register();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pending != null) {
				mc.gui.setScreen(pending);
				pending = null;
			}
			keys.tick(mc);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ConfigManager.reset());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft")
				.then(literal("config")
						.executes(c -> {
							// The chat screen closes right after a command runs: open on the next tick.
							pending = new SettingsScreen(null, EnvironmentActions.DEFAULT);
							return 1;
						})
						.then(literal("environments").executes(c -> {
							pending = new EnvironmentsScreen(null, EnvironmentActions.DEFAULT);
							return 1;
						}))
						.then(literal("reload").executes(c -> {
							ConfigManager.get().load();
							EnvironmentActions.DEFAULT.reload();
							c.getSource().sendFeedback(dev.t3craft.client.T3CraftCommands.prefix().append(
									net.minecraft.network.chat.Component.literal("reloaded " + ConfigManager.get().file().getFileName()
											+ (ConfigManager.get().warnings().isEmpty() ? "" : " with " + ConfigManager.get().warnings().size() + " warning(s), see log"))));
							return 1;
						})))));
	}

	/** For other features that want to open the settings (e.g. a button in their own screen). */
	public static Screen settingsScreen(@Nullable Screen parent) {
		return new SettingsScreen(parent, EnvironmentActions.DEFAULT);
	}
}
