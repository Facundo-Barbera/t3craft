package dev.t3craft.client.operate;

import dev.t3craft.client.screen.SnoozeScreen;
import dev.t3craft.t3.ThreadOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A row of thread buttons (item 22): Settle, Snooze…, Pin, Unpin, Read. Self-contained so any
 * screen can host it with one call: {@code bar = ThreadOpsBar.add(this, () -> target, x, y, w, h, this::addRenderableWidget)}
 * and {@code bar.update(visible)} when the selection changes. Each button is guarded with
 * {@link OperateUi#guard} (disabled with "Requires control (operate)" when read-only).
 */
public final class ThreadOpsBar {
	private record Op(String key, String tooltip, boolean danger, Function<ThreadOps.Target, Runnable> action) {
	}

	private final Supplier<ThreadOps.@Nullable Target> target;
	private final List<Button> buttons = new ArrayList<>();
	private final List<String> tooltips = new ArrayList<>();

	private ThreadOpsBar(Supplier<ThreadOps.@Nullable Target> target) {
		this.target = target;
	}

	/** Lays the buttons out left to right in {@code w} × {@code h} and hands each to {@code add}. */
	public static ThreadOpsBar add(Screen parent, Supplier<ThreadOps.@Nullable Target> target, int x, int y, int w, int h,
			Consumer<AbstractWidget> add) {
		ThreadOpsBar bar = new ThreadOpsBar(target);
		List<Op> ops = List.of(
				new Op("t3craft.operate.thread.settle", "t3craft.operate.kind.thread_settle", true,
						t -> () -> ThreadOpsUi.run(ThreadOpsUi.ops().settle(t), parent)),
				new Op("t3craft.operate.thread.snooze", "t3craft.operate.kind.thread_snooze", false,
						t -> () -> Minecraft.getInstance().gui.setScreen(new SnoozeScreen(parent, t))),
				new Op("t3craft.operate.thread.pin", "t3craft.operate.kind.thread_pin", false,
						t -> () -> ThreadOpsUi.run(ThreadOpsUi.ops().pin(t), parent)),
				new Op("t3craft.operate.thread.unpin", "t3craft.operate.kind.thread_unpin", false,
						t -> () -> ThreadOpsUi.run(ThreadOpsUi.ops().unpin(t), parent)),
				new Op("t3craft.operate.thread.read", "t3craft.operate.thread.read.tooltip", false,
						t -> () -> ThreadOpsUi.run(ThreadOpsUi.ops().markRead(t), parent)));
		int gap = 2;
		int bw = Math.max(20, (w - gap * (ops.size() - 1)) / ops.size());
		for (int i = 0; i < ops.size(); i++) {
			Op op = ops.get(i);
			Component label = Component.translatable(op.key());
			Button b = Button.builder(op.danger() ? label.copy().withStyle(ChatFormatting.RED) : label, btn -> {
				ThreadOps.Target t = target.get();
				if (t != null) {
					op.action().apply(t).run();
				}
			}).bounds(x + i * (bw + gap), y, bw, h).build();
			bar.buttons.add(b);
			bar.tooltips.add(op.tooltip());
			add.accept(b);
		}
		bar.update(true);
		return bar;
	}

	/** Re-evaluates enabled state for the current target (call when the selection changes). */
	public void update(boolean visible) {
		ThreadOps.Target t = target.get();
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			b.visible = visible;
			b.active = t != null;
			if (t != null && OperateUi.guard(b, t.envId())) {
				b.setTooltip(Tooltip.create(Component.translatable(tooltips.get(i))));
			}
		}
	}
}
