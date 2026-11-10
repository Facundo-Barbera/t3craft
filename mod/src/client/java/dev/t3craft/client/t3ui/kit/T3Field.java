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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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

	@Override
	public boolean focusable() {
		return true;
	}

	/** The vanilla box has the keyboard (the kit flag is not used for fields). */
	@Override
	public boolean hasFocus() {
		return focused();
	}

	/** Gives the box the keyboard. */
	public void focus(Screen host) {
		if (box != null) {
			host.setFocused(box);
			box.setFocused(true);
		}
	}

	/**
	 * A click at ({@code mx}, {@code my}) on the box: it takes the keyboard and puts its caret there. For a host
	 * that does not pass clicks on to the screen's widgets (a modal over a page); false = the click was not on it.
	 */
	public boolean clickAt(Screen host, double mx, double my, int button) {
		if (box == null || !box.visible || !box.active || !bounds().contains(mx, my)) {
			return false;
		}
		host.setFocused(box);
		box.setFocused(true);
		box.mouseClicked(new MouseButtonEvent(mx, my, new MouseButtonInfo(button, 0)), false);
		return true;
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
	 * widget. {@code shown} false hides the box (a field not wholly inside its panel's area: a box cannot
	 * be clipped); the frame and the text are then drawn by {@link #render}, which the panel does clip, so
	 * a field slides out of view instead of vanishing at the edge. The field itself stays visible; a host
	 * that wants it gone (fully scrolled out) calls {@code setVisible(false)} after this.
	 */
	public EditBox layout(ChatTheme t, Rect r, boolean shown) {
		setBounds(r);
		setVisible(true);
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
		Rect r = bounds();
		ChatDraw.field(g, t, r, focused());
		if (focused()) {
			ChatDraw.outline(g, r.x(), r.y(), r.w(), r.h(), t.brass());
		}
		if (box == null || !box.visible) {
			// the box is hidden (half out of its panel, or under a modal's veil): draw its text here, clipped by the host
			String s = value.isEmpty() && hint != null ? hint : value;
			if (!s.isEmpty()) {
				g.text(f, ChatDraw.clip(f, s, r.w() - 8), r.x() + 4, r.y() + (r.h() - 8) / 2, value.isEmpty() ? t.ghost() : t.text(), t.shadow());
			}
		}
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
