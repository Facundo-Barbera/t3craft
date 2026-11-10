package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.UiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A {@link T3Panel} as a screen of its own: a centred paper frame over the kit's backdrop. It is how the
 * pages open when there is no {@code T3Screen} behind them (Mod Menu "Configure", the J key,
 * {@code /t3craft config}, the gallery). Esc or the header button goes back through the page stack and then
 * to {@code parent}.
 *
 * <p>#42: drawn at the T3 screens' own scale ({@link UiScale}): sizes here and in the pages are logical units; the
 * mouse events are converted before the host sees them.
 */
public class T3PanelScreen extends Screen implements T3PanelHost.Owner {
	private static final int MAX_W = 560;
	private static final int MAX_H = 400;

	private final @Nullable Screen parent;
	private final T3PanelHost host = new T3PanelHost(this);
	private ChatTheme theme = ChatTheme.warmStudio();
	/** #42: the T3 scale this screen was laid out with. */
	private UiScale.Frame uiFrame = UiScale.Frame.NATIVE;
	private int ticks;

	public T3PanelScreen(@Nullable Screen parent, T3Panel root) {
		super(Component.literal(root.title()));
		this.parent = parent;
		host.open(root);
	}

	/** Widest the frame gets (GUI pixels); a page that needs columns asks for more. */
	protected int maxWidth() {
		return MAX_W;
	}

	/** Tallest the frame gets. */
	protected int maxHeight() {
		return MAX_H;
	}

	@Override
	protected void init() {
		uiFrame = UiScale.frame(minecraft);
		uiFrame.applyTo(this, minecraft); // #42: width / height in logical units from here on
		theme = ChatTheme.warmStudio();
		int w = Math.min(maxWidth(), width - 12);
		int h = Math.min(maxHeight(), height - 12);
		host.layout(new Rect((width - w) / 2, (height - h) / 2, w, h), font, theme, this::addRenderableWidget);
		for (var child : children()) {
			if (child instanceof AbstractWidget box && box.isFocused()) {
				setFocused(box); // rebuildWidgets() dropped the screen's focus; the new box kept its own flag
			}
		}
	}

	@Override
	public void relayout() {
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	@Override
	public Screen screen() {
		return this;
	}

	@Override
	public void left() {
		onClose();
	}

	@Override
	public void tick() {
		if (ChatTheme.warmStudio() != theme) {
			relayout(); // F3+T reloaded gui/ui-style.json
		} else if (++ticks % 20 == 0 && !UiScale.frame(minecraft).equals(uiFrame)) {
			relayout(); // #42: layout.uiScale changed (config reload or the settings row)
		}
		host.tick();
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
			Rect all = new Rect(0, 0, width, height);
			host.render(g, font, theme, mouseX, mouseY, all);
			super.extractRenderState(g, mouseX, mouseY, a); // the text boxes, over their frames
			host.renderTop(g, font, theme, mouseX, mouseY, all);
			uiFrame.flushDeferred(g, mouseX, mouseY, a); // #42: tooltips at the T3 scale
		});
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent guiEvent, boolean doubleClick) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		if (host.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return !host.modalOpen() && super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double guiX, double guiY, double scrollX, double scrollY) {
		double x = uiFrame.toLogical(guiX);
		double y = uiFrame.toLogical(guiY);
		return host.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent guiEvent, double dx, double dy) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		return host.mouseDragged(event.x(), event.y(), event.button())
				|| super.mouseDragged(event, uiFrame.toLogical(dx), uiFrame.toLogical(dy));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent guiEvent) {
		MouseButtonEvent event = uiFrame.toLogical(guiEvent);
		return host.mouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double guiX, double guiY) {
		super.mouseMoved(uiFrame.toLogical(guiX), uiFrame.toLogical(guiY));
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (host.keyPressed(e)) {
			return true;
		}
		// Esc is the host's (it steps back through the pages); a modal's boxes are the host's too; anything else goes to the focused text box
		return !e.isEscape() && !host.modalOpen() && super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return host.modalOpen() ? host.charTyped(event) : super.charTyped(event);
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
