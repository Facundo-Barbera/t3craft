package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A multi-line text field: the kit's {@link ChatDraw#field} frame with a borderless vanilla
 * {@link MultiLineEditBox} inside (wrapping, a scrollbar of its own, selection, Enter types a new line).
 * Same lifecycle as {@link T3Field}: the host calls {@link #layout} from its {@code init()}, adds the
 * returned widget to the screen and draws {@link #render} before the widgets; the text survives rebuilds.
 *
 * <p>Unlike {@link T3Field} the box cannot be clipped (vanilla builds it through a factory), so inside a
 * scrolling {@link T3Panel} it is hidden while it is not wholly inside the viewport; give it room.
 */
public final class T3TextArea extends T3Control {
	private final Font font;
	private final Component name;
	private int maxLength;
	private @Nullable String hint;
	private String value = "";
	private @Nullable MultiLineEditBox box;
	private @Nullable Consumer<String> onChange;
	private boolean keyboard;

	/** @param name accessibility name of the box */
	public T3TextArea(Font font, String name) {
		this.font = font;
		this.name = Component.literal(name);
	}

	/** Longest text, in characters; 0 = no limit. */
	public T3TextArea maxLength(int n) {
		this.maxLength = Math.max(0, n);
		return this;
	}

	public T3TextArea hint(String hint) {
		this.hint = hint;
		return this;
	}

	public T3TextArea onChange(Consumer<String> onChange) {
		this.onChange = onChange;
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
	public boolean consumesEnter() {
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

	/** Gives the box the keyboard; {@code host} is the screen that owns it, or null when a dialog routes it itself. */
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

	/** (Re)creates the box inside {@code r}, keeping the text, and returns it for the host to add as a widget. */
	public AbstractWidget layout(ChatTheme t, Rect r, boolean shown) {
		setBounds(r);
		setVisible(shown);
		cull(false);
		return build(t, r, shown);
	}

	/** Like {@link #layout} for a page that scrolls: hidden (but still reachable by Tab) when not wholly in view. */
	public AbstractWidget layoutCulled(ChatTheme t, Rect r, boolean shown) {
		setBounds(r);
		setVisible(true);
		cull(!shown);
		return build(t, r, shown);
	}

	private AbstractWidget build(ChatTheme t, Rect r, boolean shown) {
		boolean wasFocused = box != null && box.isFocused();
		MultiLineEditBox.Builder bd = MultiLineEditBox.builder().setX(r.x() + 2).setY(r.y() + 2).setTextColor(t.text()).setTextShadow(t.shadow())
				.setCursorColor(t.text()).setShowBackground(false).setShowDecorations(false);
		if (hint != null) {
			bd.setPlaceholder(Component.literal(hint).withColor(t.ghost()));
		}
		MultiLineEditBox b = bd.build(font, Math.max(8, r.w() - 4), Math.max(8, r.h() - 4), name);
		if (maxLength > 0) {
			b.setCharacterLimit(maxLength);
		}
		b.setValue(value);
		b.setValueListener(v -> {
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
}
