package dev.t3craft.client.screen;

import dev.t3craft.t3.OperateAction;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Reusable confirmation before a destructive operate action: what (the action), on which thread
 * and in which environment. {@code onResult} gets {@code true} only from the confirm button;
 * Cancel and Escape give {@code false}. The caller sets the next screen (usually back to
 * {@code parent}). Usually opened by {@code OperateUi.run}, not directly.
 */
public final class ConfirmActionScreen extends Screen {
	private final @Nullable Screen parent;
	private final OperateAction action;
	private final String envLabel;
	private final BooleanConsumer onResult;
	private boolean answered;

	public ConfirmActionScreen(@Nullable Screen parent, OperateAction action, String envLabel, BooleanConsumer onResult) {
		super(Component.translatable("t3craft.operate.confirm.title", Component.translatable(action.kind().langKey())));
		this.parent = parent;
		this.action = action;
		this.envLabel = envLabel;
		this.onResult = onResult;
	}

	public @Nullable Screen parent() {
		return parent;
	}

	@Override
	protected void init() {
		int w = Math.min(320, width - 40);
		int y = height / 2 + 40;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.confirm.yes", Component.translatable(action.kind().langKey()))
				.withStyle(ChatFormatting.RED), b -> answer(true)).bounds(width / 2 - w / 2, y, w / 2 - 4, 20).build());
		Button cancel = addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> answer(false))
				.bounds(width / 2 + 4, y, w / 2 - 4, 20).build());
		setInitialFocus(cancel);
	}

	private void answer(boolean yes) {
		if (answered) {
			return;
		}
		answered = true;
		onResult.accept(yes);
	}

	private List<Component> lines() {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("t3craft.operate.confirm.action", Component.translatable(action.kind().langKey()).withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GRAY));
		String thread = action.threadTitle() != null ? action.threadTitle() : action.threadId() != null ? action.threadId() : "-";
		out.add(Component.translatable("t3craft.operate.confirm.thread", Component.literal(thread).withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GRAY));
		out.add(Component.translatable("t3craft.operate.confirm.env", Component.literal(envLabel + " (" + action.envId() + ")").withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GRAY));
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(320, width - 40);
		int y = height / 2 - 60;
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, y, 0xFFFFFFFF);
		y += 18;
		for (Component c : lines()) {
			g.text(font, c, width / 2 - w / 2, y, 0xFFFFFFFF);
			y += 12;
		}
		y += 6;
		for (FormattedCharSequence s : font.split(Component.translatable("t3craft.operate.confirm.warning").withStyle(ChatFormatting.RED), w)) {
			g.text(font, s, width / 2 - w / 2, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
	}

	@Override
	public void onClose() {
		answer(false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
