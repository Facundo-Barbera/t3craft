package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Base of the kit's controls (docs/features/ui-kit.md): a rectangle drawn with the {@link ChatTheme}
 * tokens and {@link dev.t3craft.client.chat.ChatDraw}, with its own hit-testing. Not a vanilla widget:
 * the host (a screen, {@link T3Panel} or {@link T3Dialog}) places it with {@link #setBounds}, calls
 * {@link #render} every frame and forwards the mouse and keys through a {@link T3Form}. The one
 * exception is text input, which wraps a vanilla widget ({@link T3Field}, {@link T3TextArea}).
 *
 * <p>Keyboard focus: a control that returns true from {@link #focusable()} is a Tab stop (see
 * {@link T3Focus}). {@link #focused()} says it has the keyboard; the brass ring is drawn only when the
 * focus came from the keyboard ({@link #focusRingVisible()}), never after a click.
 */
public abstract class T3Control {
	/** Height of a one-line control (button, toggle row, select, field). */
	public static final int H = 16;

	private Rect bounds = Rect.EMPTY;
	private boolean enabled = true;
	private boolean visible = true;
	/** Scrolled out of its panel: not drawn or clicked, but still a Tab stop (Tab scrolls it into view). */
	private boolean culled;
	private boolean focused;
	private boolean ring;
	private @Nullable List<String> tooltip;

	public Rect bounds() {
		return bounds;
	}

	public void setBounds(Rect r) {
		this.bounds = r;
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean visible() {
		return visible && !culled;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/** Hidden by its owner with {@link #setVisible}; a panel scrolling it out of view does not count. */
	final boolean hidden() {
		return !visible;
	}

	/** A panel marks the controls outside its viewport. */
	final void cull(boolean culled) {
		this.culled = culled;
	}

	/** Lines shown while the pointer is over the control; none = no tooltip. */
	public void setTooltip(String... lines) {
		this.tooltip = lines.length == 0 ? null : List.of(lines);
	}

	public @Nullable List<String> tooltipAt(double mx, double my) {
		return visible() && bounds.contains(mx, my) ? tooltip : null;
	}

	/** The pointer is over an enabled control. */
	protected boolean hovered(int mx, int my) {
		return enabled && visible() && bounds.contains(mx, my);
	}

	public abstract void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my);

	/** True when the click was the control's (it is then not passed on). */
	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		return false;
	}

	/** The pointer moved with the button down after a click this control took (a scrollbar thumb). */
	public boolean mouseDragged(double mx, double my, int button) {
		return false;
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return false;
	}

	public boolean keyPressed(KeyEvent e) {
		return false;
	}

	/** A popup that lies outside {@link #bounds()} is open: the host gives it the input first. */
	public boolean overlayOpen() {
		return false;
	}

	/** Draws the popup; {@code screen} is the area it must stay inside. Called after everything else. */
	public void renderOverlay(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my, Rect screen) {
	}

	/** Closes the popup, if any. */
	public void closeOverlay() {
	}

	// ---- keyboard focus ------------------------------------------------------------------------------

	/** The control is a Tab stop. */
	public boolean focusable() {
		return false;
	}

	/** The control types text (a field): Space and Enter are characters for it, not activations. */
	public boolean takesInput() {
		return false;
	}

	/** Enter belongs to the control (a multi-line box types a new line), so a dialog's default action must not run. */
	public boolean consumesEnter() {
		return false;
	}

	public boolean focused() {
		return focused;
	}

	/** The brass ring is on: focused by the keyboard. */
	public boolean focusRingVisible() {
		return focused() && ring;
	}

	/** Reachable by Tab right now (a panel keeps scrolled-out controls reachable). */
	final boolean canFocus() {
		return focusable() && enabled && !hidden() && bounds.w() > 0 && bounds.h() > 0;
	}

	/**
	 * Takes the keyboard. {@code ring}: draw the brass ring (focus came from Tab or a key). {@code screen}
	 * is the screen showing the control, or null; whatever vanilla widget had the screen's focus loses it.
	 */
	public void focusIn(@Nullable Screen screen, boolean ring) {
		this.focused = true;
		this.ring = ring;
		if (screen != null) {
			screen.setFocused(null);
		}
	}

	public void focusOut(@Nullable Screen screen) {
		this.focused = false;
		this.ring = false;
	}

	/** A key press made the focus visible (arrows in a list): show the ring. */
	protected final void showFocusRing() {
		if (focused()) {
			ring = true;
		}
	}

	/** Draws the brass ring over the control when the keyboard focus is on it. */
	protected final void drawFocus(GuiGraphicsExtractor g, ChatTheme t) {
		if (visible() && focusRingVisible()) {
			T3Draw.focusRing(g, t, bounds);
		}
	}
}
