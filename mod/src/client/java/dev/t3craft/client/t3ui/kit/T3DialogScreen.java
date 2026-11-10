package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.UiScale;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * #42: drawn at the T3 screens' own scale ({@link UiScale}), like {@link T3PanelScreen}.
 */
public abstract class T3DialogScreen extends Screen {
	protected final @Nullable Screen parent;
	private T3Dialog dialog = new T3Dialog("", 200);
	private ChatTheme theme = ChatTheme.warmStudio();
	/** #42: the T3 scale this screen was laid out with. */
	private UiScale.Frame uiFrame = UiScale.Frame.NATIVE;
	private int ticks;

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
		uiFrame = UiScale.frame(minecraft);
		uiFrame.applyTo(this, minecraft); // #42: width / height in logical units from here on
		theme = ChatTheme.warmStudio();
		dialog = buildDialog();
		dialog.setScreen(this);
		dialog.layout(font, theme, width, height, this::addRenderableWidget);
		for (var child : children()) {
			if (child instanceof AbstractWidget box && box.isFocused()) {
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
		} else if (++ticks % 20 == 0 && minecraft != null && !UiScale.frame(minecraft).equals(uiFrame)) {
			rebuildWidgets(); // #42: layout.uiScale changed
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		uiFrame.render(g, () -> {
			g.fill(0, 0, width, height, theme.backdrop());
			g.fillGradient(0, 0, width, height, theme.backdropBottom() & 0x00FFFFFF, theme.backdropBottom());
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float a) {
		int mouseX = uiFrame.toLogical(guiMouseX);
		int mouseY = uiFrame.toLogical(guiMouseY);
		uiFrame.render(g, () -> {
			dialog.render(g, font, theme, mouseX, mouseY);
			super.extractRenderState(g, mouseX, mouseY, a); // the fields' boxes, over their frames
			dialog.renderOverlays(g, font, theme, mouseX, mouseY, new Rect(0, 0, width, height));
			List<String> tip = dialog.overlayOpen() ? null : dialog.tooltip(mouseX, mouseY);
			if (tip != null) {
				T3Draw.tooltip(g, font, tip, mouseX, mouseY);
			}
			uiFrame.flushDeferred(g, mouseX, mouseY, a); // #42: tooltips at the T3 scale
		});
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent guiEvent, boolean doubleClick) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		if (dialog.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double guiX, double guiY, double scrollX, double scrollY) {
		double x = uiFrame.toLogical(guiX);
		double y = uiFrame.toLogical(guiY);
		return dialog.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent guiEvent, double dx, double dy) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		return dialog.mouseDragged(event.x(), event.y(), event.button())
				|| super.mouseDragged(event, uiFrame.toLogical(dx), uiFrame.toLogical(dy));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent guiEvent) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		return dialog.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double guiX, double guiY) {
		super.mouseMoved(uiFrame.toLogical(guiX), uiFrame.toLogical(guiY));
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// an open select takes every key (Esc closes just the list); Esc otherwise reaches Screen -> onClose()
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
