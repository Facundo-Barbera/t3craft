package dev.t3craft.client.t3ui.kit;

import dev.t3craft.client.chat.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * An {@link EditBox} that is drawn and clicked only inside a clip rectangle: a field in the middle of a
 * scrolling {@link T3Panel} is cut at the edge of the viewport (a scissor) instead of jumping out of view
 * as a whole. The screen still owns it as a normal widget; only the drawing and the hit-test change.
 */
final class ClippedEditBox extends EditBox {
	private @Nullable Rect clip;

	ClippedEditBox(Font font, int x, int y, int w, int h, @Nullable EditBox copyFrom, Component name) {
		super(font, x, y, w, h, copyFrom, name);
	}

	/** Null = no clip. */
	void setClip(@Nullable Rect clip) {
		this.clip = clip;
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Rect c = clip;
		if (c == null) {
			super.extractWidgetRenderState(g, mouseX, mouseY, a);
			return;
		}
		g.enableScissor(c.x(), c.y(), c.right(), c.bottom());
		super.extractWidgetRenderState(g, mouseX, mouseY, a);
		g.disableScissor();
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY) && (clip == null || clip.contains(mouseX, mouseY));
	}
}
