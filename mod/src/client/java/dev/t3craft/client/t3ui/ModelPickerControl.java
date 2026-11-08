package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.t3ui.actions.ModelPick;
import dev.t3craft.client.t3ui.actions.ModelPicker;
import dev.t3craft.t3.ThreadShell;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * feat/t3-actions' {@link ModelPicker} (provider · model · effort, T3's {@code ComposerModelEffort})
 * as a control of feat/chat-compose's composer bar. One picker per thread: rebuilt when the selected
 * thread changes, with the thread's current model from its shell ({@link ModelPick#fromMeta}). The
 * popup lies outside the control's rectangle, so the host routes input to it while it is open
 * ({@link T3Slots.Popup}).
 */
final class ModelPickerControl implements ComposerView.Control, T3Slots.Popup {
	static final int WIDTH = 150;

	private final Supplier<T3Slots.@Nullable Target> target;
	private @Nullable ModelPicker picker;
	private @Nullable String pickerKey;

	ModelPickerControl(Supplier<T3Slots.@Nullable Target> target) {
		this.target = target;
	}

	private @Nullable ModelPicker picker(Font font) {
		T3Slots.Target t = target.get();
		String k = t == null ? null : t.envId() + ":" + t.threadId();
		if (k == null) {
			picker = null;
			pickerKey = null;
			return null;
		}
		if (!k.equals(pickerKey) || picker == null) {
			ThreadShell shell = T3Slots.shellOf(t.envId(), t.threadId());
			picker = ModelPicker.forThread(font, t.envId(), t.threadId(), t.title(), shell == null ? null : ModelPick.fromMeta(shell.meta()), pick -> {
			});
			pickerKey = k;
		}
		return picker;
	}

	@Override
	public int width(Font font) {
		return WIDTH;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY) {
		ModelPicker p = picker(font);
		if (p != null) {
			p.setBounds(x, y, WIDTH, 12);
			p.render(g, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return picker != null && picker.mouseClicked(mx, my, button);
	}

	@Override
	public boolean isOpen() {
		return picker != null && picker.isOpen();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		return picker != null && picker.keyPressed(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dy) {
		return picker != null && picker.mouseScrolled(mx, my, dy);
	}
}
