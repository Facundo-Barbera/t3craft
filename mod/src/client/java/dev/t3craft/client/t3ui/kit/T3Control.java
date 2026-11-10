package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
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
 * exception is text input, which wraps a vanilla {@code EditBox} ({@link T3Field}).
 */
public abstract class T3Control {
	/** Height of a one-line control (button, toggle row, select, field). */
	public static final int H = 16;

	private Rect bounds = Rect.EMPTY;
	private boolean enabled = true;
	private boolean visible = true;
	private boolean focus;
	private @Nullable Screen screen;
	private @Nullable List<String> tooltip;

	// ---- keyboard focus ------------------------------------------------------------------------------

	/** True when Tab can land on the control (buttons, switches, selects, lists, fields). */
	public boolean focusable() {
		return false;
	}

	/** True when a click on the control gives it the keyboard (a list, a text area; a button keeps no focus). */
	public boolean takesClickFocus() {
		return false;
	}

	/** The control has the keyboard. {@link T3Field} answers for its vanilla box. */
	public boolean hasFocus() {
		return focus;
	}

	/** Moves the kit focus flag; the {@link T3Focus} of the host decides who gets it. */
	public void setFocus(boolean on) {
		if (focus != on) {
			focus = on;
			focusChanged(on);
		}
	}

	/** The control gained or lost the keyboard (a text area starts or stops the platform's text input). */
	protected void focusChanged(boolean on) {
	}

	/** The screen showing the control, given by its host at every layout; null before the first. */
	protected final @Nullable Screen screen() {
		return screen;
	}

	final void bindScreen(@Nullable Screen s) {
		this.screen = s;
	}

	/** Enter, keypad Enter or Space: what presses a focused button or switch. */
	protected static boolean isActivateKey(KeyEvent e) {
		int k = e.key();
		return k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_SPACE;
	}

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
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/** Lines shown while the pointer is over the control; none = no tooltip. */
	public void setTooltip(String... lines) {
		this.tooltip = lines.length == 0 ? null : List.of(lines);
	}

	public @Nullable List<String> tooltipAt(double mx, double my) {
		return visible && bounds.contains(mx, my) ? tooltip : null;
	}

	/** The pointer is over an enabled control. */
	protected boolean hovered(int mx, int my) {
		return enabled && visible && bounds.contains(mx, my);
	}

	public abstract void render(GuiGraphicsExtractor g, Font font, ChatTheme t, int mx, int my);

	/** True when the click was the control's (it is then not passed on). */
	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		return false;
	}

	public boolean keyPressed(KeyEvent e) {
		return false;
	}

	/** A typed character; only the control that has the keyboard gets it (see {@link T3Form#charTyped}). */
	public boolean charTyped(int codepoint) {
		return false;
	}

	/** The pointer moved with a button held (a scrollbar thumb, a text selection). */
	public boolean mouseDragged(double mx, double my, int button) {
		return false;
	}

	/** The button went up; a control that was dragging stops. */
	public boolean mouseReleased(double mx, double my, int button) {
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
}
