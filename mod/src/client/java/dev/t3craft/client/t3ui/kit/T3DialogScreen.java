package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A screen that is one {@link T3Dialog} over the kit's backdrop: what {@code Pair}, {@code OperatePair},
 * {@code Confirm} and {@code Snooze} are. The subclass builds the dialog in
 * {@link #buildDialog()} (called on every {@code init()}, so it can read its own state) and decides what
 * Esc does in {@link #onClose()}. Follows F3+T like {@code T3Screen}: a changed style rebuilds the dialog.
 */
public abstract class T3DialogScreen extends Screen {
	protected final @Nullable Screen parent;
	private T3Dialog dialog = new T3Dialog("", 200);
	private ChatTheme theme = ChatTheme.warmStudio();

	protected T3DialogScreen(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
	}

	protected abstract T3Dialog buildDialog();

	protected final ChatTheme theme() {
		return theme;
	}

	protected final T3Dialog dialog() {
		return dialog;
	}

	@Override
	protected void init() {
		theme = ChatTheme.warmStudio();
		dialog = buildDialog();
		dialog.layout(font, theme, width, height, this::addRenderableWidget);
		for (var child : children()) {
			if (child instanceof EditBox box && box.isFocused()) {
				setFocused(box); // rebuildWidgets() dropped the screen's focus; the new box kept its own flag
			}
		}
		if (getFocused() == null) {
			dialog.focusInitial(this);
		}
	}

	@Override
	public void tick() {
		if (ChatTheme.warmStudio() != theme && minecraft != null) {
			rebuildWidgets(); // F3+T reloaded gui/ui-style.json
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, theme.backdrop());
		g.fillGradient(0, 0, width, height, theme.backdropBottom() & 0x00FFFFFF, theme.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		dialog.render(g, font, theme, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a); // the fields' boxes, over their frames
		dialog.renderOverlays(g, font, theme, mouseX, mouseY, new Rect(0, 0, width, height));
		List<String> tip = dialog.overlayOpen() ? null : dialog.tooltip(mouseX, mouseY);
		if (tip != null) {
			T3Draw.tooltip(g, font, tip, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (dialog.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return dialog.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// an open select takes every key (Esc closes just the list); Esc otherwise reaches Screen → onClose()
		return dialog.keyPressed(e) || super.keyPressed(e);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
