package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
