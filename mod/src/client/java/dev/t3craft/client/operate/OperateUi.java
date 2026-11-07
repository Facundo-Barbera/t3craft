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

	public static boolean canOperate(String envId) {
		return gate().canOperate(envId);
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
