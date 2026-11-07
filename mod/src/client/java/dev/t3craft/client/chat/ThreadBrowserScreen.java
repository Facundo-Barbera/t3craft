// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Placement and veil follow AgentCraft's console (console/ConsoleScreen.java): non-pausing, an 8 px
// margin, the column anchored bottom-left so the world stays visible, a walnut dim over everything
// that deepens towards the bottom.
package dev.t3craft.client.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Full-screen host of a {@link ThreadBrowser}: the unified thread list and the conversation, AgentCraft
 * console style. Owns the browser (and through it the live feed) and closes it in {@link #removed()}.
 * No static state.
 */
public final class ThreadBrowserScreen extends Screen {
	private static final int MAX_W = 640;

	private final ThreadBrowser browser;

	public ThreadBrowserScreen(ThreadBrowser browser) {
		super(Component.literal("T3 threads"));
		this.browser = browser;
	}

	public ThreadBrowser browser() {
		return browser;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		browser.close();
		super.removed();
	}

	@Override
	public void tick() {
		browser.tick();
	}

	private Rect layout() {
		ChatTheme t = browser.theme();
		int m = t.margin();
		int w = Math.min(MAX_W, width - 2 * m);
		int h = Math.min(height - 2 * m, Math.max(180, height * 60 / 100 + 23));
		return new Rect(m, height - m - h, w, h);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ChatTheme t = browser.theme();
		g.fill(0, 0, width, height, t.backdrop());
		int h = height * 2 / 3;
		g.fillGradient(0, height - h, width, height, t.backdropBottom() & 0x00FFFFFF, t.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		browser.setBounds(layout());
		browser.render(g, font, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (browser.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return browser.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (browser.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	/** Convenience for hosts that only have a key. */
	public static ThreadBrowserScreen of(ThreadBrowser b, @Nullable String key) {
		if (key != null) {
			b.open(key);
		}
		return new ThreadBrowserScreen(b);
	}
}
