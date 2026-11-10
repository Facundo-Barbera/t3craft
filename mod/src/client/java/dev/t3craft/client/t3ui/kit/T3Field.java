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
 *
 * <p>Focus: the box is the truth ({@link #focused()}); Tab reaches it through {@link T3Focus}, and a field
 * focused that way gets the brass ring over its frame.
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
	/** The focus came from the keyboard: the ring is drawn. */
	private boolean keyboard;

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

	@Override
	public boolean focused() {
		return box != null && box.isFocused();
	}

	@Override
	public boolean focusable() {
		return box != null;
	}

	@Override
	public boolean takesInput() {
		return true;
	}

	@Override
	public boolean focusRingVisible() {
		return focused() && keyboard;
	}

	@Override
	public void focusIn(@Nullable Screen screen, boolean ring) {
		keyboard = ring;
		focus(screen);
	}

	@Override
	public void focusOut(@Nullable Screen screen) {
		blur(screen);
	}

	/** Gives the box the keyboard. {@code host} is the screen that owns the box, or null when the dialog draws and routes it itself. */
	public void focus(@Nullable Screen host) {
		if (box != null) {
			if (host != null) {
				host.setFocused(box);
			}
			box.setFocused(true);
		}
	}

	public void blur(@Nullable Screen host) {
		keyboard = false;
		if (box != null && box.isFocused()) {
			box.setFocused(false);
			if (host != null && host.getFocused() == box) {
				host.setFocused(null);
			}
		}
	}

	/**
	 * (Re)creates the box inside {@code r}, keeping the text, and returns it for the host to add as a
	 * widget. {@code shown} false hides it.
	 */
	public EditBox layout(ChatTheme t, Rect r, boolean shown) {
		setBounds(r);
		setVisible(shown);
		cull(false);
		return build(t, r, shown, null);
	}

	/**
	 * Like {@link #layout} for a field inside a scrolling page: the box is shown while any part of it is
	 * inside {@code clip} (the page's viewport) and is drawn and clicked only there, so it is cut at the
	 * edge instead of vanishing. A field wholly outside is hidden but stays reachable by Tab.
	 */
	public EditBox layoutClipped(ChatTheme t, Rect r, Rect clip) {
		boolean shown = r.x() < clip.right() && r.right() > clip.x() && r.y() < clip.bottom() && r.bottom() > clip.y();
		setBounds(r);
		setVisible(true);
		cull(!shown);
		return build(t, r, shown, clip);
	}

	private EditBox build(ChatTheme t, Rect r, boolean shown, @Nullable Rect clip) {
		boolean wasFocused = box != null && box.isFocused();
		ClippedEditBox b = new ClippedEditBox(font, r.x() + 4, r.y() + (r.h() - 8) / 2, Math.max(8, r.w() - 8), 10, box, name);
		b.setClip(clip);
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
		b.setFocused(wasFocused);
		box = b;
		return b;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font f, ChatTheme t, int mx, int my) {
		if (!visible()) {
			return;
		}
		ChatDraw.field(g, t, bounds(), focused());
		if (focusRingVisible()) {
			T3Draw.focusRing(g, t, bounds());
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
