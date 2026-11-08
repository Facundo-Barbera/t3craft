package dev.t3craft.client.t3ui;

import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.operate.ThreadControlUi;
import dev.t3craft.client.operate.ThreadOpsBar;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateKind;
import dev.t3craft.t3.ThreadControl;
import dev.t3craft.t3.ThreadOps;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The thread header's action row (T3's {@code ChatHeader} actions + the composer's stop button):
 * Interrupt and Stop (op-stop, {@link ThreadControlUi}), then Settle, Snooze, Pin, Unpin, Read
 * ({@link ThreadOpsBar}, op-settle). Every button goes through the existing guarded flows
 * ({@link OperateUi#guard}: disabled with "Requires control" when the environment is read-only;
 * destructive ones ask first). Nothing here writes by itself.
 */
final class T3HeaderActions {
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);

	private final Supplier<T3Slots.@Nullable Target> target;
	private final Button interrupt;
	private final Button stop;
	private final ThreadOpsBar ops;

	private T3HeaderActions(Supplier<T3Slots.@Nullable Target> target, Button interrupt, Button stop, ThreadOpsBar ops) {
		this.target = target;
		this.interrupt = interrupt;
		this.stop = stop;
		this.ops = ops;
	}

	/** Button width for {@code n} buttons in {@code w} with 2 px gaps. */
	static int buttonWidth(int w, int n) {
		return Math.max(16, (w - 2 * (n - 1)) / n);
	}

	static T3HeaderActions add(Screen screen, Supplier<T3Slots.@Nullable Target> target, Rect r, T3Slots slots, Consumer<AbstractWidget> add) {
		int bw = buttonWidth(r.w(), 7);
		Button interrupt = Button.builder(Component.translatable("t3craft.operate.control.interrupt"), b -> {
			ThreadControl.Target t = control(target.get());
			if (t != null) {
				ThreadControlUi.interrupt(t, title(target.get()), OperateGate.Origin.UI, screen, f -> toast(OperateKind.RUN_INTERRUPT, f));
			}
		}).bounds(r.x(), r.y(), bw, r.h()).build();
		Button stop = Button.builder(Component.translatable("t3craft.operate.control.stop").copy().withStyle(ChatFormatting.RED), b -> {
			ThreadControl.Target t = control(target.get());
			if (t != null) {
				ThreadControlUi.stop(t, title(target.get()), OperateGate.Origin.UI, screen, f -> toast(OperateKind.THREAD_STOP, f));
			}
		}).bounds(r.x() + bw + 2, r.y(), bw, r.h()).build();
		add.accept(interrupt);
		add.accept(stop);
		int ox = r.x() + 2 * (bw + 2);
		ThreadOpsBar ops = ThreadOpsBar.add(screen, () -> {
			T3Slots.Target t = target.get();
			return t == null ? null : new ThreadOps.Target(t.envId(), t.threadId(), t.title());
		}, ox, r.y(), r.right() - ox, r.h(), add);
		T3HeaderActions h = new T3HeaderActions(target, interrupt, stop, ops);
		h.update(true);
		return h;
	}

	/** Re-evaluates the buttons for the selected thread. */
	void update(boolean visible) {
		T3Slots.Target t = target.get();
		ThreadControl.Target c = control(t);
		interrupt.visible = visible;
		stop.visible = visible;
		interrupt.active = c != null;
		stop.active = c != null;
		interrupt.setTooltip(null);
		stop.setTooltip(null);
		if (c != null && OperateUi.guard(interrupt, c.envId()) && ThreadControlUi.activeRunId(c) == null) {
			interrupt.active = false;
			interrupt.setTooltip(Tooltip.create(Component.translatable("t3craft.operate.control.no_run")));
		}
		if (c != null) {
			OperateUi.guard(stop, c.envId());
		}
		ops.update(visible);
	}

	private static ThreadControl.@Nullable Target control(T3Slots.@Nullable Target t) {
		return t == null ? null : new ThreadControl.Target(t.envId(), t.threadId());
	}

	private static @Nullable String title(T3Slots.@Nullable Target t) {
		return t == null ? null : t.title();
	}

	private static void toast(OperateKind kind, ThreadControl.Feedback f) {
		Minecraft mc = Minecraft.getInstance();
		SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
				Component.translatable(kind.langKey()).withStyle(f.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), Component.literal(f.text()));
	}
}
