package dev.t3craft.client.plan;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.ModConfig;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.config.T3CraftKeys;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client wiring of the project plan (docs/features/project-plan.md): the store ({@code config/t3craft/plan.json}),
 * {@code /t3craft plan} (no arguments: everything else happens on the page), the "Open plan" key (Options › Controls ›
 * T3Craft, unbound by default) and the hooks the office blocks call. One call from {@code T3CraftClient}.
 *
 * <p>Nothing here subscribes to anything or runs on a timer: the page reads the shell rows the client already keeps
 * ({@link #thread}) while it is shown.
 */
public final class PlanClient {
	public static final String FILE = "plan.json";
	public static final String KEY_NAME = "key.t3craft.open_plan";

	private static @Nullable PlanStore store;
	private static @Nullable KeyMapping key;

	private PlanClient() {
	}

	public static void register(T3CraftClient client) {
		KeyMapping k = KeyMappingHelper.registerKeyMapping(new KeyMapping(KEY_NAME, InputConstants.UNKNOWN.getValue(), T3CraftKeys.CATEGORY));
		key = k;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (k.consumeClick()) {
				if (mc.gui.screen() == null) { // a text box on screen would type the key
					open(PlanPanel.View.DESK);
				}
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft").then(literal("plan")
				.executes(c -> {
					client.openNextTick(new PlanScreen(null, PlanPanel.View.DESK));
					return 1;
				}))));
	}

	/** The single plan store. */
	public static synchronized PlanStore store() {
		if (store == null) {
			store = new PlanStore(ModConfig.dataDir().resolve(FILE));
		}
		return store;
	}

	/** Opens the plan page on {@code view} (client thread). */
	public static void open(PlanPanel.View view) {
		Minecraft.getInstance().gui.setScreen(new PlanScreen(null, view));
	}

	/** The shell row of a linked thread, from what the client already has; null when not visible. */
	public static @Nullable ThreadShell thread(PlanTask.ThreadRef ref) {
		ShellState.View v = LaunchClient.view(ref.envId());
		if (v == null) {
			return null;
		}
		for (ThreadShell t : v.threads()) {
			if (t.id().equals(ref.threadId())) {
				return t;
			}
		}
		return null;
	}

	/** Moves finished linked tasks to review (local file only; nothing is sent). */
	public static void syncReview() {
		store().syncReview(PlanClient::thread);
	}
}
