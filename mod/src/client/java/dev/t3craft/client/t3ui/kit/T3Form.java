package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * A set of controls with the input routing every host needs: a control with an open popup gets the mouse
 * and the keys first, otherwise the click goes to the topmost control (the last added) under it. Typed
 * characters go to the control that has the keyboard; a drag goes to whoever started one.
 */
public final class T3Form {
	private final List<T3Control> controls = new ArrayList<>();
	private @Nullable T3Focus focus;
	private Supplier<List<T3Control>> ring = this::controls;

	public <C extends T3Control> C add(C c) {
		controls.add(c);
		return c;
	}

	public void clear() {
		controls.clear();
	}

	public List<T3Control> controls() {
		return controls;
	}

	/** Lets the clicks of this form move the host's keyboard focus; {@code ring} is every control the host tabs through. */
	void useFocus(T3Focus focus, Supplier<List<T3Control>> ring) {
		this.focus = focus;
		this.ring = ring;
	}

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my) {
		for (T3Control c : controls) {
			c.render(g, font, t, mx, my);
		}
	}

	/** The open popups (selects), drawn last. */
	public void renderOverlays(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
		for (T3Control c : controls) {
			if (c.overlayOpen()) {
				c.renderOverlay(g, font, t, mx, my, screen);
			}
		}
	}

	public boolean overlayOpen() {
		for (T3Control c : controls) {
			if (c.overlayOpen()) {
				return true;
			}
		}
		return false;
	}

	public void closeOverlays() {
		controls.forEach(T3Control::closeOverlay);
	}

	public boolean mouseClicked(double mx, double my, int button) {
		for (T3Control c : controls) {
			if (c.overlayOpen()) {
				return c.mouseClicked(mx, my, button);
			}
		}
		for (int i = controls.size() - 1; i >= 0; i--) {
			T3Control c = controls.get(i);
			if (c.visible() && c.bounds().contains(mx, my)) {
				if (focus != null) {
					focus.clicked(c, ring.get()); // before the action: it may lay the page out again
				}
				if (c.mouseClicked(mx, my, button)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		for (T3Control c : controls) {
			if (c.overlayOpen()) {
				return c.mouseScrolled(mx, my, dy);
			}
		}
		for (int i = controls.size() - 1; i >= 0; i--) {
			if (controls.get(i).mouseScrolled(mx, my, dy)) {
				return true;
			}
		}
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button) {
		for (T3Control c : controls) {
			if (c.visible() && c.mouseDragged(mx, my, button)) {
				return true;
			}
		}
		return false;
	}

	/** Every control hears the release, so none is left dragging. */
	public boolean mouseReleased(double mx, double my, int button) {
		boolean any = false;
		for (T3Control c : controls) {
			any |= c.mouseReleased(mx, my, button);
		}
		return any;
	}

	public boolean keyPressed(KeyEvent e) {
		for (T3Control c : controls) {
			if (c.overlayOpen()) {
				return c.keyPressed(e);
			}
		}
		for (T3Control c : controls) {
			if (c.visible() && c.keyPressed(e)) {
				return true;
			}
		}
		return false;
	}

	/** A typed character goes to the control that has the keyboard. */
	public boolean charTyped(int codepoint) {
		for (T3Control c : controls) {
			if (c.visible() && c.hasFocus() && c.charTyped(codepoint)) {
				return true;
			}
		}
		return false;
	}

	public @Nullable List<String> tooltip(double mx, double my) {
		for (int i = controls.size() - 1; i >= 0; i--) {
			List<String> tip = controls.get(i).tooltipAt(mx, my);
			if (tip != null) {
				return tip;
			}
		}
		return null;
	}
}
