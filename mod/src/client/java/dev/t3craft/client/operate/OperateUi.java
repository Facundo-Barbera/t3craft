package dev.t3craft.client.operate;

import dev.t3craft.client.screen.ConfirmActionScreen;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * What action threads call on the client. <i>Client thread only.</i>
 * <pre>{@code
 * OperateAction a = OperateUi.gate().operate(env).dispatch(OperateKind.THREAD_STOP, threadId, title, command);
 * OperateUi.run(a, OperateGate.Origin.UI, this, r -> status = r.message());
 * OperateUi.guard(stopButton, env); // disabled + "Requires control (operate)" when read-only
 * }</pre>
 */
public final class OperateUi {
	public static final String REQUIRES_KEY = "t3craft.operate.requires";

	private OperateUi() {
	}

	public static OperateGate gate() {
		return OperateClient.get().gate();
	}

	/**
	 * Whether this Minecraft can write to {@code envId} right now (operate session present and not
	 * expired). The query for UI text and buttons, e.g. the chat reply box: true means do not say
	 * "read-only". Cheap (reads one small file); call it when building widgets, not every frame.
	 */
	public static boolean canOperate(String envId) {
		return gate().canOperate(envId);
	}

	/** {@link OperateGate.Availability} of {@code envId}: enabled, read-only, or expired. */
	public static OperateGate.Availability availability(String envId) {
		return gate().availability(envId);
	}

	/**
	 * The screen that gives {@code envId} control: "Enable control" for a paired environment, or
	 * the pairing screen (read + operate by default) when it is not paired. For a "pair / enable
	 * control" button wherever {@link #canOperate} is false.
	 */
	public static Screen enableControlScreen(@Nullable Screen parent, @Nullable String envId) {
		return (envId == null ? java.util.Optional.<dev.t3craft.t3.Environments.Environment>empty() : OperateClient.environment(envId))
				.<Screen>map(e -> new dev.t3craft.client.screen.OperatePairScreen(parent, e))
				.orElseGet(() -> new dev.t3craft.client.screen.PairScreen(parent));
	}

	/** Disables {@code widget} with the "Requires control (operate)" tooltip when the env is read-only. */
	public static boolean guard(AbstractWidget widget, String envId) {
		boolean ok = canOperate(envId);
		if (!ok) {
			widget.active = false;
			widget.setTooltip(Tooltip.create(Component.translatable(REQUIRES_KEY)));
		}
		return ok;
	}

	/**
	 * Runs {@code action}: read-only → {@code READ_ONLY} at once; destructive → opens
	 * {@link ConfirmActionScreen} first (then returns to {@code returnTo}); otherwise sends it.
	 * {@code done} always runs once, on the client thread.
	 */
	public static void run(OperateAction action, OperateGate.Origin origin, @Nullable Screen returnTo, Consumer<OperateResult> done) {
		Minecraft mc = Minecraft.getInstance();
		OperateGate gate = gate();
		if (!gate.canOperate(action.envId()) || !action.destructive()) {
			send(mc, gate, action, origin, false, done);
			return;
		}
		mc.gui.setScreen(new ConfirmActionScreen(returnTo, action, OperateClient.label(action.envId()), yes -> {
			mc.gui.setScreen(returnTo);
			if (yes) {
				send(mc, gate, action, origin, true, done);
			} else {
				done.accept(gate.cancelled(action, origin));
			}
		}));
	}

	private static void send(Minecraft mc, OperateGate gate, OperateAction action, OperateGate.Origin origin, boolean confirmed,
			Consumer<OperateResult> done) {
		gate.submit(action, origin, confirmed).thenAccept(r -> mc.execute(() -> done.accept(r)));
	}
}
