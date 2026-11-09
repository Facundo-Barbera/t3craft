package dev.t3craft.client.screen;

import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.t3.OperateAction;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reusable confirmation before a destructive operate action: what (the action), on which thread
 * and in which environment. {@code onResult} gets {@code true} only from the confirm button;
 * Cancel and Escape give {@code false}. The caller sets the next screen (usually back to
 * {@code parent}). Usually opened by {@code OperateUi.run}, not directly.
 *
 * <p>A kit dialog ({@link T3DialogScreen}, docs/features/ui-kit.md). Enter means Cancel, as the vanilla
 * screen focused Cancel: a stray Enter never confirms a destructive action.
 */
public final class ConfirmActionScreen extends T3DialogScreen {
	private final OperateAction action;
	private final String envLabel;
	private final BooleanConsumer onResult;
	private boolean answered;

	public ConfirmActionScreen(@Nullable Screen parent, OperateAction action, String envLabel, BooleanConsumer onResult) {
		super(Component.translatable("t3craft.operate.confirm.title", Component.translatable(action.kind().langKey())), parent);
		this.action = action;
		this.envLabel = envLabel;
		this.onResult = onResult;
	}

	public @Nullable Screen parent() {
		return parent;
	}

	@Override
	protected T3Dialog buildDialog() {
		String kind = T3Draw.tr(action.kind().langKey());
		String thread = action.threadTitle() != null ? action.threadTitle() : action.threadId() != null ? action.threadId() : "-";
		T3Button yes = new T3Button(T3Draw.tr("t3craft.operate.confirm.yes", kind), T3Button.Style.DANGER, () -> answer(true));
		T3Button no = new T3Button(CommonComponents.GUI_CANCEL.getString(), () -> answer(false)).setEmphasised(true);
		return new T3Dialog(title.getString(), 340)
				.kvKey("t3craft.operate.confirm.action", kind)
				.kvKey("t3craft.operate.confirm.thread", thread)
				.kvKey("t3craft.operate.confirm.env", envLabel + " (" + action.envId() + ")")
				.gap(2)
				.text(T3Draw.tr("t3craft.operate.confirm.warning"), Tone.ERROR)
				.gap(2)
				.buttons(yes, no)
				.onCancel(() -> answer(false))
				.defaultAction(() -> answer(false));
	}

	private void answer(boolean yes) {
		if (answered) {
			return;
		}
		answered = true;
		onResult.accept(yes);
	}

	@Override
	public void onClose() {
		answer(false);
	}
}
