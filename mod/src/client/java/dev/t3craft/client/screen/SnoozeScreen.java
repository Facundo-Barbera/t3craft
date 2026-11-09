package dev.t3craft.client.screen;

import dev.t3craft.client.operate.ThreadOpsUi;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.t3.ThreadOps;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Picks a snooze preset ({@link ThreadOps.Snooze}: 1 h, 4 h, tomorrow) for one thread, then back to {@code parent}. A kit dialog. */
public final class SnoozeScreen extends T3DialogScreen {
	private final ThreadOps.Target target;

	public SnoozeScreen(@Nullable Screen parent, ThreadOps.Target target) {
		super(Component.translatable("t3craft.operate.snooze.title", target.title() != null ? target.title() : target.threadId()), parent);
		this.target = target;
	}

	@Override
	protected T3Dialog buildDialog() {
		T3Dialog d = new T3Dialog(title.getString(), 260);
		for (ThreadOps.Snooze preset : ThreadOps.Snooze.values()) {
			d.control(new T3Button(T3Draw.tr(preset.langKey()), () -> {
				minecraft.gui.setScreen(parent);
				ThreadOpsUi.run(ThreadOpsUi.ops().snooze(target, preset), parent);
			}));
		}
		return d.gap(2).control(new T3Button(CommonComponents.GUI_CANCEL.getString(), this::onClose)).onCancel(this::onClose);
	}
}
