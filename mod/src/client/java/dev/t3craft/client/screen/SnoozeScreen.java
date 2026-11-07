package dev.t3craft.client.screen;

import dev.t3craft.client.operate.ThreadOpsUi;
import dev.t3craft.t3.ThreadOps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Picks a snooze preset ({@link ThreadOps.Snooze}: 1 h, 4 h, tomorrow) for one thread, then back to {@code parent}. */
public final class SnoozeScreen extends Screen {
	private final @Nullable Screen parent;
	private final ThreadOps.Target target;

	public SnoozeScreen(@Nullable Screen parent, ThreadOps.Target target) {
		super(Component.translatable("t3craft.operate.snooze.title", target.title() != null ? target.title() : target.threadId()));
		this.parent = parent;
		this.target = target;
	}

	@Override
	protected void init() {
		int w = Math.min(200, width - 40);
		int y = height / 2 - 40;
		for (ThreadOps.Snooze d : ThreadOps.Snooze.values()) {
			addRenderableWidget(Button.builder(Component.translatable(d.langKey()), b -> {
				minecraft.gui.setScreen(parent);
				ThreadOpsUi.run(ThreadOpsUi.ops().snooze(target, d), parent);
			}).bounds(width / 2 - w / 2, y, w, 20).build());
			y += 24;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 - w / 2, y + 6, w, 20).build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, height / 2 - 62, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
