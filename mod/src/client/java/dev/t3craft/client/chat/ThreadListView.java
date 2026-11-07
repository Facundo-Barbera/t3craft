// Layout ideas adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Reusable widget: the unified thread list (every environment in one list, attention first) drawn
 * in any rectangle. Each row: state glyph in the state colour, title, and the environment badge on
 * the right (T3 Code style: the environment is just a marker per row). The header shows the
 * attention count and the optional environment filter chip (click: next environment, right-click:
 * previous). Embed it in any screen:
 *
 * <pre>{@code
 * ThreadList model = new ThreadList();
 * ThreadListView list = new ThreadListView(model);
 * // tick:   model.update(dir.rows(), dir.environments());
 * // render: list.extract(g, font, x, y, w, h, mouseX, mouseY);
 * // click:  ThreadRow r = list.mouseClicked(mx, my, button);  // selects it too
 * // wheel:  list.mouseScrolled(mx, my, dy);
 * }</pre>
 * No static state; client thread only.
 */
public final class ThreadListView {
	private static final int HEADER_H = 16;

	private final ThreadList model;
	private int first;
	private int x;
	private int y;
	private int w;
	private int h;
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable String emptyText;

	private record Hit(int x0, int y0, int x1, int y1, @Nullable ThreadRow row, boolean chip) {
		boolean contains(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}
	}

	public ThreadListView(ThreadList model) {
		this.model = model;
	}

	public ThreadList model() {
		return model;
	}

	/** Text shown instead of rows when the list is empty (e.g. "No environment paired"). */
	public void setEmptyText(@Nullable String text) {
		emptyText = text;
	}

	private int capacity() {
		return Math.max(1, (h - HEADER_H - 4) / ChatTheme.ROW_H);
	}

	/** Scrolls so the selected row is visible. */
	public void revealSelected() {
		int i = model.selectedIndex();
		if (i < 0) {
			return;
		}
		int cap = capacity();
		if (i < first) {
			first = i;
		} else if (i >= first + cap) {
			first = i - cap + 1;
		}
	}

	public void extract(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		hits.clear();
		g.fill(x, y, x + w, y + h, ChatTheme.PANEL);

		// header: "Threads !3" ... [filter chip]
		ThreadList.Env fe = model.filterEnv();
		String chip = fe == null ? "All ▾" : fe.label() + " ▾";
		int chipW = Math.min(font.width(chip) + 8 + (fe == null ? 0 : ChatDraw.badgeWidth(font, fe.badge()) + 3), w / 2);
		int chipX = x + w - chipW - 3;
		boolean chipHover = mouseX >= chipX && mouseX < chipX + chipW && mouseY >= y + 2 && mouseY < y + 14;
		g.fill(chipX, y + 2, chipX + chipW, y + 14, chipHover ? ChatTheme.SELECTED : ChatTheme.KEYCAP_BG);
		int cx = chipX + 4;
		if (fe != null) {
			cx += ChatDraw.badge(g, font, fe.badge(), cx, y + 3) + 3;
		}
		g.text(font, ChatDraw.clip(font, chip, chipX + chipW - cx - 2), cx, y + 4, ChatTheme.TEXT, false);
		hits.add(new Hit(chipX, y + 2, chipX + chipW, y + 14, null, true));

		g.text(font, "Threads", x + 5, y + 4, ChatTheme.ACCENT, false);
		int att = fe == null ? model.attention() : fe.attention();
		if (att > 0) {
			g.text(font, "!" + att, x + 9 + font.width("Threads"), y + 4, ChatTheme.ATTENTION, false);
		}
		g.fill(x + 3, y + HEADER_H - 1, x + w - 3, y + HEADER_H, ChatTheme.BORDER);

		List<ThreadRow> rows = model.visible();
		int top = y + HEADER_H + 2;
		if (rows.isEmpty()) {
			String msg = emptyText != null ? emptyText
					: fe != null ? "No threads in " + fe.label() + (fe.status().isEmpty() || "ok".equals(fe.status()) ? "." : " (" + fe.status() + ").")
					: "No threads to show.";
			int ly = top + 4;
			for (var line : font.split(net.minecraft.network.chat.Component.literal(msg), Math.max(20, w - 10))) {
				if (ly + font.lineHeight > y + h) {
					break;
				}
				g.text(font, line, x + 5, ly, ChatTheme.DIM, false);
				ly += font.lineHeight;
			}
			return;
		}
		int cap = capacity();
		first = Math.max(0, Math.min(first, Math.max(0, rows.size() - cap)));
		String sel = model.selectedKey();
		int ry = top;
		for (int i = first; i < rows.size() && i < first + cap; i++) {
			ThreadRow r = rows.get(i);
			boolean selected = r.key().equals(sel);
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry - 1 && mouseY < ry + ChatTheme.ROW_H - 1;
			if (selected) {
				g.fill(x + 1, ry - 1, x + w - 1, ry + ChatTheme.ROW_H - 1, ChatTheme.SELECTED);
				g.fill(x + 1, ry - 1, x + 3, ry + ChatTheme.ROW_H - 1, ChatTheme.ACCENT);
			} else if (hover) {
				g.fill(x + 1, ry - 1, x + w - 1, ry + ChatTheme.ROW_H - 1, ChatTheme.HOVER);
			}
			int stateColor = 0xFF000000 | r.state().color;
			g.text(font, r.state().glyph, x + 6, ry + 1, stateColor, false);
			EnvBadge b = r.badge();
			int bw = ChatDraw.badgeWidth(font, b);
			ChatDraw.badge(g, font, b, x + w - bw - 4, ry);
			int titleColor = r.settled() ? ChatTheme.DIM : r.needsAttention() ? ChatTheme.ATTENTION : ChatTheme.TEXT;
			g.text(font, ChatDraw.clip(font, r.title(), w - 22 - bw - 8), x + 16, ry + 1, titleColor, false);
			hits.add(new Hit(x, ry - 1, x + w, ry + ChatTheme.ROW_H - 1, r, false));
			ry += ChatTheme.ROW_H;
		}
		if (rows.size() > cap) {
			int track = cap * ChatTheme.ROW_H;
			int thumb = Math.max(8, track * cap / rows.size());
			int ty = top + (int) ((long) (track - thumb) * first / Math.max(1, rows.size() - cap));
			g.fill(x + w - 2, ty, x + w - 1, ty + thumb, ChatTheme.BORDER);
		}
	}

	/** Tooltip text for the row under the mouse (title, environment · project, activity), or null. */
	public @Nullable List<String> tooltip(int mouseX, int mouseY) {
		for (Hit hit : hits) {
			if (hit.row() != null && hit.contains(mouseX, mouseY)) {
				ThreadRow r = hit.row();
				List<String> out = new ArrayList<>();
				out.add(r.title());
				out.add(r.state().word + " · " + r.envLabel() + (r.project().isEmpty() ? "" : " · " + r.project()));
				if (r.activity() != null && !r.activity().isBlank()) {
					out.add(r.activity());
				}
				return out;
			}
		}
		return null;
	}

	/**
	 * Handles a click inside the list: selects and returns the row (the caller opens it), or cycles
	 * the filter chip (left: next, right: previous) and returns null.
	 */
	public @Nullable ThreadRow mouseClicked(double mx, double my, int button) {
		for (Hit hit : hits) {
			if (!hit.contains(mx, my)) {
				continue;
			}
			if (hit.chip()) {
				model.cycleFilter(button == 1 ? -1 : 1);
				first = 0;
				return null;
			}
			if (hit.row() != null && button == 0) {
				model.select(hit.row().key());
				return hit.row();
			}
		}
		return null;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!contains(mx, my)) {
			return false;
		}
		first = Math.max(0, first - (int) Math.signum(dy) * 3);
		return true;
	}
}
