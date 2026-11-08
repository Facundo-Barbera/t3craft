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
		// below the (wrapped) text: a long thread title or warning used to run under the buttons
		int y = DialogLayout.buttonsBelow(height, height / 2 + 40, textBottom(w));
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

	/** Top of the text block: centred-ish, but never above the screen on short windows. */
	private int textTop() {
		return Math.max(8, height / 2 - 60);
	}

	/** Where the text block ends for width {@code w} (same walk as the render, without drawing). */
	private int textBottom(int w) {
		return drawText(null, w);
	}

	/** Draws (when {@code g} is not null) the title, the wrapped lines and the warning; returns the bottom y. */
	private int drawText(@Nullable GuiGraphicsExtractor g, int w) {
		int x = width / 2 - w / 2;
		int y = textTop();
		if (g != null) {
			g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, y, 0xFFFFFFFF);
		}
		y += 18;
		for (Component c : lines()) {
			// wrapped: a long thread title or environment label ran past the dialog's edge
			for (FormattedCharSequence s : font.split(c, w)) {
				if (g != null) {
					g.text(font, s, x, y, 0xFFFFFFFF);
				}
				y += font.lineHeight + 1;
			}
			y += 2;
		}
		y += 6;
		for (FormattedCharSequence s : font.split(Component.translatable("t3craft.operate.confirm.warning").withStyle(ChatFormatting.RED), w)) {
			if (g != null) {
				g.text(font, s, x, y, 0xFFFFFFFF);
			}
			y += font.lineHeight;
		}
		return y;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		drawText(g, Math.min(320, width - 40));
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
