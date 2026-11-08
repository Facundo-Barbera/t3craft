package dev.t3craft.client.t3ui.actions;

import dev.t3craft.client.operate.ThreadControlUi;
import dev.t3craft.client.operate.ThreadOpsUi;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.ThreadControl;
import dev.t3craft.t3.ThreadOps;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Thread header for a chat panel: the {@link ThreadStatusChip} plus T3 Code's thread actions that
 * T3Craft already has in operate: Interrupt (while a run is in progress, {@code run.interrupt}),
 * Stop ({@code thread.stop}), Settle ({@code thread.settle}) and Archive ({@code thread.archive}).
 * All destructive, so each goes through the in-game confirmation; all disabled with
 * "Requires control (operate)" when read-only. Reuses {@link ThreadControlUi} and {@link ThreadOpsUi}.
 *
 * <p>Host: {@code bar = new ThreadActionsBar(font, feedback)}; {@code bar.buttons().forEach(this::addRenderableWidget)}
 * once; {@code bar.setBounds(x, y, w, h)} on layout; {@code bar.setThread(env, shell)} when the
 * selection or its shell changes; {@code bar.render(g)} to draw the status chip.
 */
public final class ThreadActionsBar {
	private final Font font;
	private final Consumer<Component> feedback;
	private final ThreadStatusChip chip;
	private final Button interrupt;
	private final Button stop;
	private final Button settle;
	private final Button archive;
	private @Nullable String envId;
	private @Nullable ThreadShell thread;
	private int x;
	private int y;
	private int w;
	private int h = 14;

	/** {@code feedback} gets the player-facing result line of each action (client thread). */
	public ThreadActionsBar(Font font, Consumer<Component> feedback) {
		this.font = font;
		this.feedback = feedback;
		this.chip = new ThreadStatusChip(font, true);
		interrupt = Button.builder(Component.translatable("t3craft.actions.interrupt"), b -> interrupt()).bounds(0, 0, 50, 14).build();
		stop = Button.builder(Component.translatable("t3craft.actions.stop").withStyle(ChatFormatting.RED), b -> stop()).bounds(0, 0, 40, 14).build();
		settle = Button.builder(Component.translatable("t3craft.operate.thread.settle"), b -> settle()).bounds(0, 0, 40, 14).build();
		archive = Button.builder(Component.translatable("t3craft.actions.archive"), b -> archive()).bounds(0, 0, 44, 14).build();
		update();
	}

	public List<Button> buttons() {
		return List.of(interrupt, stop, settle, archive);
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		layout();
	}

	public void setThread(@Nullable String envId, @Nullable ThreadShell t) {
		this.envId = envId;
		this.thread = t;
		chip.setThread(t);
		update();
	}

	public void setVisible(boolean v) {
		for (Button b : buttons()) {
			b.visible = v && thread != null;
		}
	}

	private void layout() {
		int bx = x + w;
		List<Button> bs = new ArrayList<>(buttons());
		java.util.Collections.reverse(bs);
		for (Button b : bs) {
			int bw = font.width(b.getMessage()) + 10;
			bx -= bw;
			b.setRectangle(bw, h, bx, y);
			bx -= 2;
		}
		chip.setBounds(x, y + (h - ThreadStatusChip.height(font)) / 2, Math.max(0, bx - x - 2), h);
	}

	private void update() {
		boolean has = envId != null && thread != null;
		boolean canOp = has && ActionsClient.gate().enabled(envId);
		ThreadStatus s = thread == null ? ThreadStatus.IDLE : ThreadStatus.of(thread);
		for (Button b : buttons()) {
			b.visible = has;
			b.active = canOp;
			b.setTooltip(canOp ? null : Tooltip.create(Component.translatable("t3craft.operate.requires")));
		}
		interrupt.active = canOp && s.running() && thread.activeRunId() != null;
		if (canOp && !interrupt.active) {
			interrupt.setTooltip(Tooltip.create(Component.translatable("t3craft.operate.control.no_run")));
		}
		layout();
	}

	public void render(GuiGraphicsExtractor g) {
		if (thread != null) {
			chip.render(g);
		}
	}

	private void interrupt() {
		if (envId == null || thread == null) {
			return;
		}
		ThreadControlUi.interrupt(new ThreadControl.Target(envId, thread.id()), thread.title(), OperateGate.Origin.UI,
				Minecraft.getInstance().gui.screen(), f -> feedback.accept(line(f)));
	}

	private void stop() {
		if (envId == null || thread == null) {
			return;
		}
		ThreadControlUi.stop(new ThreadControl.Target(envId, thread.id()), thread.title(), OperateGate.Origin.UI,
				Minecraft.getInstance().gui.screen(), f -> feedback.accept(line(f)));
	}

	private void settle() {
		if (envId == null || thread == null) {
			return;
		}
		ThreadOps.Target t = new ThreadOps.Target(envId, thread.id(), thread.title());
		ThreadOpsUi.run(ThreadOpsUi.ops().settle(t), OperateGate.Origin.UI, Minecraft.getInstance().gui.screen(),
				r -> feedback.accept(line(ThreadControl.feedback(r))));
	}

	private void archive() {
		if (envId == null || thread == null) {
			return;
		}
		ActionsClient.archive(envId, thread.id(), thread.title(), Minecraft.getInstance().gui.screen(),
				r -> feedback.accept(line(ThreadControl.feedback(r))));
	}

	private static Component line(ThreadControl.Feedback f) {
		return Component.literal(f.text()).withStyle(f.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
	}
}
