package dev.t3craft.client.debug;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Icon;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * {@code /t3dev ui icons} (issue #24): every {@link T3Icon} in a grid, once on each theme (Warm Studio and
 * terminal side by side). A cell shows the name, the icon at the usual size ({@link T3Icon#SIZE}, 1x) in
 * its three states (muted, accent, disabled) and big (32 px, the sprite's own resolution) in the text
 * colour. The wheel scrolls both grids; hovering a cell shows the sprite id. A sprite that is missing (the
 * build did not run {@code generateIcons}, or a constant has no SVG) shows as the missing-texture square.
 *
 * <p>Both themes are read on every frame, so F3+T (which reloads {@code gui/ui-style.json} and the sprite
 * atlas) restyles the gallery live.
 */
public final class IconGalleryScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int GAP = 6;
	private static final int PAD = 8;
	private static final int HEAD = 14;
	private static final int CELL_W = 96;
	private static final int CELL_H = 50;
	private static final int CELL_GAP = 3;
	private static final int BIG = 32;
	private static final int SCROLL_STEP = 18;
	private static final int FOOT = 12;

	private final @Nullable Screen parent;
	private int scroll;

	public IconGalleryScreen(@Nullable Screen parent) {
		super(Component.literal("Icon gallery"));
		this.parent = parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ChatTheme t = ChatTheme.warmStudio();
		g.fill(0, 0, width, height, t.backdrop());
		g.fillGradient(0, 0, width, height, t.backdropBottom() & 0x00FFFFFF, t.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int pw = (width - 2 * MARGIN - GAP) / 2;
		int ph = height - 2 * MARGIN - FOOT;
		scroll = Math.max(0, Math.min(scroll, maxScroll(pw, ph)));
		String[] names = {"Warm Studio", "Terminal"};
		ChatTheme[] themes = {ChatTheme.warmStudio(), ChatTheme.terminal()};
		List<String> tooltip = null;
		for (int i = 0; i < themes.length; i++) {
			List<String> tip = drawTheme(g, themes[i], names[i], new Rect(MARGIN + i * (pw + GAP), MARGIN, pw, ph), mouseX, mouseY);
			tooltip = tip != null ? tip : tooltip;
		}
		g.text(font, ChatDraw.clip(font, T3Icon.values().length + " icons · wheel scrolls · F3+T reloads sprites and styles · Esc closes",
				width - 2 * MARGIN), MARGIN, height - MARGIN - 8, 0xFFB8B0A4, true);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip != null) {
			T3Draw.tooltip(g, font, tooltip, mouseX, mouseY);
		}
	}

	private static int cols(Rect grid) {
		return Math.max(1, (grid.w() + CELL_GAP) / (CELL_W + CELL_GAP));
	}

	private static Rect grid(Rect panel) {
		Rect inner = panel.inset(PAD);
		return new Rect(inner.x(), inner.y() + HEAD, inner.w(), inner.h() - HEAD);
	}

	private int maxScroll(int panelW, int panelH) {
		Rect grid = grid(new Rect(0, 0, panelW, panelH));
		int rows = (T3Icon.values().length + cols(grid) - 1) / cols(grid);
		return Math.max(0, rows * (CELL_H + CELL_GAP) - CELL_GAP - grid.h());
	}

	/** One themed panel; returns the tooltip of the hovered cell, if any. */
	private @Nullable List<String> drawTheme(GuiGraphicsExtractor g, ChatTheme t, String name, Rect panel, int mx, int my) {
		ChatDraw.panel(g, t, panel);
		Rect inner = panel.inset(PAD);
		g.text(font, name, inner.x(), inner.y(), t.accent(), t.shadow());
		String legend = "12 px: muted · accent · disabled   32 px: text";
		int lw = font.width(legend);
		if (inner.w() > font.width(name) + lw + 12) {
			g.text(font, legend, inner.right() - lw, inner.y(), t.muted(), t.shadow());
		}
		Rect grid = grid(panel);
		int cols = cols(grid);
		T3Icon[] icons = T3Icon.values();
		List<String> tooltip = null;
		g.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		for (int n = 0; n < icons.length; n++) {
			int x = grid.x() + (n % cols) * (CELL_W + CELL_GAP);
			int y = grid.y() + (n / cols) * (CELL_H + CELL_GAP) - scroll;
			if (y + CELL_H < grid.y() || y > grid.bottom()) {
				continue;
			}
			boolean hover = grid.contains(mx, my) && mx >= x && mx < x + CELL_W && my >= y && my < y + CELL_H;
			drawCell(g, t, icons[n], x, y, hover);
			if (hover) {
				tooltip = List.of(icons[n].sprite().toString());
			}
		}
		g.disableScissor();
		return tooltip;
	}

	private void drawCell(GuiGraphicsExtractor g, ChatTheme t, T3Icon icon, int x, int y, boolean hover) {
		ChatDraw.round(g, x, y, CELL_W, CELL_H, hover ? t.hover() : t.inset());
		g.text(font, ChatDraw.clip(font, icon.id(), CELL_W - 8), x + 4, y + 4, t.text(), t.shadow());
		int s = T3Icon.SIZE;
		int small = y + 14 + (BIG - s) / 2;
		T3Icon.draw(g, t, icon, x + 4, small, s, false, true);
		T3Icon.draw(g, t, icon, x + 4 + (s + 4), small, s, true, true);
		T3Icon.draw(g, t, icon, x + 4 + 2 * (s + 4), small, s, false, false);
		T3Icon.draw(g, t, icon, x + CELL_W - BIG - 4, y + 14, BIG, t.text());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll - (int) Math.round(scrollY * SCROLL_STEP));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
