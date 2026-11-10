package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A single-line text field: the kit's {@link ChatDraw#field} box with a borderless vanilla
 * {@link EditBox} inside (the same recipe as the search field of {@code T3Screen}). Text input is the one
 * thing the kit does not reimplement.
 *
 * <p>Lifecycle, like {@code SearchBox}: the host calls {@link #layout} from its {@code init()} (every
 * rebuild), adds the returned box as a widget, and draws {@link #render} before the widgets so the box
 * lies over its frame. The text survives rebuilds (the new box copies the old one). Keep one
 * {@code T3Field} per input for the life of the screen.
 */
public final class T3Field extends T3Control {
	private final Font font;
	private final Component name;
	private int maxLength = 256;
	private @Nullable String hint;
	private String value = "";
	private @Nullable EditBox box;
	private @Nullable Consumer<String> onChange;
	private @Nullable Runnable onSubmit;

	/** @param name accessibility name of the box */
	public T3Field(Font font, String name) {
		this.font = font;
		this.name = Component.literal(name);
	}

	public T3Field maxLength(int n) {
		this.maxLength = n;
		return this;
	}

	public T3Field hint(String hint) {
		this.hint = hint;
		return this;
	}

	public T3Field onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	/** Runs on Enter while the box has focus. */
	public T3Field onSubmit(Runnable onSubmit) {
		this.onSubmit = onSubmit;
		return this;
	}

	public String value() {
		return value;
	}

	public void setValue(String v) {
		this.value = v;
		if (box != null) {
			box.setValue(v);
		}
	}

	public boolean focused() {
		return box != null && box.isFocused();
	}

	/** Gives the box the keyboard. */
	public void focus(Screen host) {
		if (box != null) {
			host.setFocused(box);
			box.setFocused(true);
		}
	}

	public void blur(Screen host) {
		if (box != null && box.isFocused()) {
			box.setFocused(false);
			if (host.getFocused() == box) {
				host.setFocused(null);
			}
		}
	}

	/**
	 * (Re)creates the box inside {@code r}, keeping the text, and returns it for the host to add as a
	 * widget. {@code shown} false hides it (a field scrolled out of its panel).
	 */
	public EditBox layout(ChatTheme t, Rect r, boolean shown) {
		setBounds(r);
		setVisible(shown);
		boolean wasFocused = box != null && box.isFocused();
		EditBox b = new EditBox(font, r.x() + 4, r.y() + (r.h() - 8) / 2, Math.max(8, r.w() - 8), 10, box, name);
		b.setMaxLength(maxLength);
		b.setBordered(false);
		b.setTextColor(t.text());
		b.setValue(value);
		if (hint != null) {
			b.setHint(Component.literal(hint).withColor(t.ghost()));
		}
		b.setResponder(v -> {
			value = v;
			if (onChange != null) {
				onChange.accept(v);
			}
		});
		b.visible = shown;
		b.setFocused(wasFocused && shown);
		box = b;
		return b;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		ChatDraw.field(g, t, bounds(), focused());
	}

	// ---- hosts that are not a Screen (a popover over the composer) ------------------------------------------
	// The box was never added as a widget, so the host draws it and feeds it the keys and characters itself.

	/** Draws the box (text, caret, hint) over its frame. */
	public void renderBox(GuiGraphicsExtractor g, int mx, int my) {
		if (box != null && visible()) {
			box.extractRenderState(g, mx, my, 0f);
		}
	}

	/** Gives or takes the keyboard from the box without a host screen. */
	public void setBoxFocused(boolean focused) {
		if (box != null && box.isFocused() != focused) {
			box.setFocused(focused);
		}
	}

	/** An editing key (arrows, backspace, copy and paste...) while the box has focus; true when it used it. */
	public boolean boxKeyPressed(KeyEvent e) {
		return box != null && box.isFocused() && box.keyPressed(e);
	}

	/** A typed character while the box has focus; true when it took it. */
	public boolean boxCharTyped(int codepoint) {
		return box != null && box.isFocused() && box.charTyped(new CharacterEvent(codepoint));
	}

	/** Enter while focused runs {@link #onSubmit}; true when it did. */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (onSubmit != null && focused() && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) {
			onSubmit.run();
			return true;
		}
		return false;
	}
}
