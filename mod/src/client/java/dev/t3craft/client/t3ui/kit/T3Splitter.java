package dev.t3craft.client.t3ui.kit;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A vertical drag handle between two panels (#39): the 3-4 px gap between the main T3 screen's sidebar and
 * thread, or between the thread and the right panel. Invisible at rest (the gap shows the backdrop), a hover
 * wash with a 1 px edge rule and the resize cursor under the pointer, an accent rule while dragged.
 * Double click resets the width to its default.
 *
 * <p>The splitter knows nothing about widths: it reports the pointer's x to its {@link Listener} while
 * dragged, and the release once (the owner saves then, never per frame). The grab area is one pixel wider
 * on each side than the gap, so a 4 px handle is easy to hit at GUI scale 1.
 */
public final class T3Splitter {
	/** What the owner does with the drag. */
	public interface Listener {
		/** The pointer moved to {@code mouseX} while the handle is held: re-lay with the new width. */
		void drag(double mouseX);

		/** The handle was let go after a drag: persist the width. */
		void released();

		/** Double click: back to the default width (and persist it). */
		void reset();
	}

	/** Extra grab width on each side of the gap. */
	private static final int SLOP = 1;

	private final Listener listener;
	private Rect bounds = Rect.EMPTY;
	private boolean dragging;
	private boolean moved;

	public T3Splitter(Listener listener) {
		this.listener = listener;
	}

	/** The gap between the two panels ({@link Rect#EMPTY} = hidden: no hits, nothing drawn). */
	public void setBounds(Rect r) {
		bounds = r == null ? Rect.EMPTY : r;
	}

	public Rect bounds() {
		return bounds;
	}

	public boolean dragging() {
		return dragging;
	}

	/** Under the pointer, the slop included. */
	public boolean over(double mx, double my) {
		return bounds.w() > 0 && bounds.h() > 0 && mx >= bounds.x() - SLOP && mx < bounds.right() + SLOP && my >= bounds.y() && my < bounds.bottom();
	}

	/** {@code blocked}: a menu or modal is open, no hover feedback. */
	public void render(GuiGraphicsExtractor g, ChatTheme t, int mx, int my, boolean blocked) {
		if (bounds.w() == 0 || bounds.h() == 0) {
			return;
		}
		boolean hover = !blocked && over(mx, my);
		if (!hover && !dragging) {
			return;
		}
		int cx = bounds.x() + bounds.w() / 2;
		if (dragging) {
			g.fill(cx - 1, bounds.y(), cx + 1, bounds.bottom(), t.accent());
		} else {
			g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), t.hover());
			g.fill(cx, bounds.y(), cx + 1, bounds.bottom(), t.edge());
		}
		g.requestCursor(CursorTypes.RESIZE_EW);
	}

	/** A press: starts the drag, or (double click) resets. True when the press was on the handle. */
	public boolean mouseClicked(double mx, double my, int button, boolean doubleClick) {
		if (!MouseButtons.isLeft(button) || !over(mx, my)) {
			return false;
		}
		if (doubleClick) {
			dragging = false;
			listener.reset();
			return true;
		}
		dragging = true;
		moved = false;
		return true;
	}

	public boolean mouseDragged(double mx) {
		if (!dragging) {
			return false;
		}
		moved = true;
		listener.drag(mx);
		return true;
	}

	public boolean mouseReleased() {
		if (!dragging) {
			return false;
		}
		dragging = false;
		if (moved) {
			listener.released();
		}
		return true;
	}

	/** The owner lost the pointer (screen closed, re-laid while held): end the drag, saving what it reached. */
	public void cancel() {
		mouseReleased();
	}
}
