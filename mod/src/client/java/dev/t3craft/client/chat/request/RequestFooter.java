package dev.t3craft.client.chat.request;

import dev.t3craft.client.chat.ConversationPanel;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The conversation's footer while a request may be pending (feat/t3-user-input): the composer,
 * with the {@link RequestCard} in its place while the card is open (T3 Code shows the pending
 * question where the composer's input is), or a one-line strip over it while the card is hidden.
 * With nothing pending it is exactly the composer.
 */
public final class RequestFooter implements ConversationPanel.Footer {
	private static final int GAP = 3;

	private final RequestCard card;
	private final ComposerView composer;

	public RequestFooter(RequestCard card, ComposerView composer) {
		this.card = card;
		this.composer = composer;
	}

	@Override
	public int height(int width) {
		if (card.expanded()) {
			return card.height(width);
		}
		return (card.visible() ? RequestCard.STRIP_H + GAP : 0) + composer.height(width);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, Rect r, int mouseX, int mouseY) {
		if (card.expanded()) {
			card.render(g, font, r, mouseX, mouseY);
			composer.render(g, font, Rect.EMPTY, mouseX, mouseY); // hidden: no box, the screen drops its focus
			return;
		}
		if (card.visible()) {
			card.renderStrip(g, font, new Rect(r.x(), r.y(), r.w(), RequestCard.STRIP_H), mouseX, mouseY);
			int top = RequestCard.STRIP_H + GAP;
			composer.render(g, font, new Rect(r.x(), r.y() + top, r.w(), Math.max(0, r.h() - top)), mouseX, mouseY);
			return;
		}
		composer.render(g, font, r, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (card.contains(mx, my)) {
			return card.mouseClicked(mx, my, button);
		}
		if (card.expanded()) {
			card.blurOther();
			return false;
		}
		return composer.mouseClicked(mx, my, button);
	}
}
