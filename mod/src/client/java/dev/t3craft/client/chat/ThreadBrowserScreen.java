// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// The veil follows AgentCraft's console (console/ConsoleScreen.java): non-pausing, a walnut dim over
// everything. Unlike the console, the browser is centred (BrowserLayout) so it reads at any GUI scale.
package dev.t3craft.client.chat;

import dev.t3craft.client.chat.compose.ChatComposer;
import dev.t3craft.client.chat.compose.ComposerView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Full-screen host of a {@link ThreadBrowser}: the unified thread list and the conversation, AgentCraft
 * console style. Owns the browser (and through it the live feed) and closes it in {@link #removed()}.
 * No static state.
 */
public final class ThreadBrowserScreen extends Screen {
	private final Supplier<ThreadBrowser> factory;
	private @Nullable ThreadBrowser browser;
	/** Thread to open when the browser is (re)built; updated when the screen is left. */
	private @Nullable String key;
	/**
	 * The composer (feat/chat-compose). Owned by the screen, not the browser, so drafts, pending
	 * messages and errors survive the confirmation and pairing screens (which rebuild the browser).
	 */
	private @Nullable ComposerView composer;

	/**
	 * The browser is built in {@link #init()} and closed in {@link #removed()}, so the screen can be
	 * the parent of another screen (pairing, control) and come back to the same thread without
	 * keeping a live feed open while hidden.
	 */
	public ThreadBrowserScreen(Supplier<ThreadBrowser> factory, @Nullable String key) {
		super(Component.literal("T3 threads"));
		this.factory = factory;
		this.key = key;
	}

	public @Nullable ThreadBrowser browser() {
		return browser;
	}

	/** Screen to go back to on Esc (the archive, the portable terminal); null = close to the game. */
	private @Nullable Screen parent;

	/** Sets the screen Esc returns to (fix/ui-audit: the archive and the portable used to be lost). */
	public ThreadBrowserScreen withParent(@Nullable Screen parent) {
		this.parent = parent;
		return this;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			minecraft.gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	protected void init() {
		if (composer == null) {
			composer = ChatComposer.create(this, ChatTheme.warmStudio(), () -> {
				if (browser != null) {
					browser.conversation().scrollToEnd();
				}
			});
		}
		if (browser == null) {
			browser = factory.get();
			composer.setTheme(browser.theme());
			ChatComposer.attach(browser.conversation(), composer);
			if (key != null) {
				browser.open(key);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		ThreadBrowser b = browser;
		if (b != null) {
			String k = b.conversation().key();
			if (k != null) {
				key = k;
			}
			b.close();
			browser = null;
		}
		super.removed();
	}

	@Override
	public void tick() {
		if (browser != null) {
			browser.tick();
		}
	}

	/** Centred in scaled GUI pixels ({@code width}/{@code height} of a Screen are guiWidth/guiHeight). */
	private static Rect layout(ThreadBrowser b, int width, int height) {
		ChatTheme t = b.theme();
		return BrowserLayout.compute(width, height, t.margin(), t.gap()).frame();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ChatTheme t = browser != null ? browser.theme() : ChatTheme.warmStudio();
		g.fill(0, 0, width, height, t.backdrop());
		g.fillGradient(0, 0, width, height, t.backdropBottom() & 0x00FFFFFF, t.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		ThreadBrowser b = browser;
		if (b != null) {
			b.setBounds(layout(b, width, height));
			ComposerView c = composer;
			if (c != null) {
				ChatComposer.sync(b.conversation(), c);
				c.setBounds(Rect.EMPTY); // the conversation places it; hidden (compact list) stays empty
			}
			b.render(g, font, mouseX, mouseY);
			if (c != null && c.bounds().w() == 0) {
				c.setFocused(false);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// event.button() is MouseButtons.LEFT/RIGHT (1/3 in Minecraft 26.x), passed through as is
		if (composer != null && !composer.contains(event.x(), event.y())) {
			composer.setFocused(false);
		}
		if (browser != null && browser.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return (composer != null && composer.mouseDragged(event.x(), event.y())) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return (composer != null && composer.mouseReleased()) || super.mouseReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return (composer != null && composer.charTyped(event)) || super.charTyped(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (composer != null && composer.mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return (browser != null && browser.mouseScrolled(x, y, scrollY)) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// while the composer has focus every key is its own: no browser shortcut fires
		if (composer != null && composer.keyPressed(event)) {
			return true;
		}
		if (browser != null && browser.keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}
}
