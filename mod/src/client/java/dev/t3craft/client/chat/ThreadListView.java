// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// Paper panel, header, pills, inset list, clay selection bar and status dots follow AgentCraft's kit
// (ui/Panels.java, hud/UiBits.java, console/ConsoleScreen.java popup selection).
package dev.t3craft.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reusable widget: the unified thread list (every environment in one list, attention first, then
 * recent activity) drawn in any rectangle with any {@link ChatTheme}. Each row: the environment
 * icon (initial on a stable colour, like T3 Code's sidebar), the AgentCraft status dot (pulsing
 * while it waits on you), the title and the age
 * on the right. Above the rows, one chip per environment is the only place environments appear:
 * click toggles it, right-click shows only that one, "All" clears the filter.
 *
 * <pre>{@code
 * ThreadList model = new ThreadList();
 * ThreadListView list = new ThreadListView(model, ChatTheme.terminal());
 * list.setOnFilterChanged(hidden -> save(hidden));        // optional persistence
 * // each tick:  model.update(dir.rows(), dir.environments());
 * // render:     list.setBounds(new Rect(x, y, w, h)); list.render(g, font, mouseX, mouseY);
 * // click:      ThreadRow r = list.mouseClicked(mx, my, button);   // selects it too
 * // wheel:      list.mouseScrolled(mx, my, dy);
 * }</pre>
 * No static state; client thread only.
 */
public final class ThreadListView {
	private static final int HEADER_H = 14;
	private static final int CHIP_H = 11;
	private static final int ICON = 10;

	private final ThreadList model;
	private ChatTheme theme;
	private Rect bounds = Rect.EMPTY;
	private int first;
	private boolean chips = true;
	private boolean focused;
	private @Nullable String emptyText;
	private @Nullable Consumer<Set<String>> onFilterChanged;
	private final List<Hit> hits = new ArrayList<>();
	private int rowsTop;
	private int rowsBottom;

	private record Hit(Rect r, @Nullable ThreadRow row, @Nullable String chipEnv, boolean all) {
	}

	public ThreadListView(ThreadList model, ChatTheme theme) {
		this.model = model;
		this.theme = theme;
	}

	public ThreadListView(ThreadList model) {
		this(model, ChatTheme.warmStudio());
	}

	public ThreadList model() {
		return model;
	}

	public ChatTheme theme() {
		return theme;
	}

	public void setTheme(ChatTheme theme) {
		this.theme = theme;
	}

	public Rect bounds() {
		return bounds;
	}

	public void setBounds(Rect r) {
		bounds = r;
	}

	/** Shows or hides the environment chips (e.g. a host with its own filter UI). */
	public void setShowChips(boolean v) {
		chips = v;
	}

	/** Draws the selection with the accent bar (keyboard focus is on the list). */
	public void setFocused(boolean v) {
		focused = v;
	}

	/** Called with the new hidden set after a chip click (persist it if you like). */
	public void setOnFilterChanged(@Nullable Consumer<Set<String>> l) {
		onFilterChanged = l;
	}

	/** Text shown instead of rows when the list is empty (e.g. "No environment paired"). */
	public void setEmptyText(@Nullable String text) {
		emptyText = text;
	}

	private int capacity() {
		return Math.max(1, (rowsBottom - rowsTop) / theme.listRow());
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

	public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		ChatTheme t = theme;
		hits.clear();
		if (bounds.w() < 40 || bounds.h() < 40) {
			return;
		}
		ChatDraw.panel(g, t, bounds);
		int pad = t.pad();
		int x = bounds.x() + pad;
		int w = bounds.w() - 2 * pad;
		int y = bounds.y() + pad;

		// header (AgentCraft console): title in ink, summary in muted, count on the right
		g.text(font, "Threads", x, y + 2, t.text(), t.shadow());
		int att = model.attention();
		String count = model.visible().size() + (model.filtered() ? "/" + model.all().size() : "");
		int cw = font.width(count);
		g.text(font, count, x + w - cw, y + 2, t.muted(), t.shadow());
		if (att > 0) {
			String sub = "· " + att + " need you";
			g.text(font, ChatDraw.clip(font, sub, w - font.width("Threads") - cw - 12), x + font.width("Threads") + 6, y + 2, t.youText(), t.shadow());
		}
		int cy = y + HEADER_H;

		// environment chips: the only place environments appear
		if (chips && !model.environments().isEmpty()) {
			cy = chipRows(g, font, x, cy, w, mouseX, mouseY) + 3;
		}
		g.fill(x, cy, x + w, cy + 1, t.edge());
		cy += 4;
		Rect list = new Rect(x, cy, w, bounds.bottom() - pad - cy);
		ChatDraw.inset(g, t, list);
		rowsTop = list.y() + 3;
		rowsBottom = list.bottom() - 3;

		List<ThreadRow> rows = model.visible();
		if (rows.isEmpty()) {
			String msg = emptyText != null ? emptyText
					: !model.all().isEmpty() ? "Every environment with threads is filtered out. Click All."
					: "No threads to show.";
			int ly = rowsTop + 3;
			for (var line : font.split(Component.literal(msg), Math.max(20, list.w() - 12))) {
				if (ly + font.lineHeight > rowsBottom) {
					break;
				}
				g.text(font, line, list.x() + 6, ly, t.muted(), t.shadow());
				ly += font.lineHeight;
			}
			return;
		}
		int rh = t.listRow();
		int cap = capacity();
		first = Math.max(0, Math.min(first, Math.max(0, rows.size() - cap)));
		String sel = model.selectedKey();
		long now = System.currentTimeMillis();
		int ry = rowsTop;
		int rx0 = list.x() + 2;
		int rx1 = list.right() - (rows.size() > cap ? 10 : 3);
		for (int i = first; i < rows.size() && i < first + cap; i++) {
			ThreadRow r = rows.get(i);
			Rect rr = new Rect(rx0, ry, rx1 - rx0, rh);
			boolean selected = r.key().equals(sel);
			if (selected) {
				// AgentCraft popup selection: clay wash + 2 px clay bar
				g.fill(rr.x(), ry, rr.right(), ry + rh, t.selected());
				g.fill(rr.x(), ry, rr.x() + 2, ry + rh, focused ? t.accent() : t.edge());
			} else if (rr.contains(mouseX, mouseY)) {
				g.fill(rr.x(), ry, rr.right(), ry + rh, t.hover());
			}
			int ty = ry + (rh - 8) / 2;
			ChatDraw.envIcon(g, font, model.badge(r), rr.x() + 4, ry + (rh - ICON) / 2, ICON);
			Attention a = r.attention();
			int dx = rr.x() + 17;
			int dy = ry + (rh - 7) / 2;
			if (a == Attention.NEEDS_YOU) {
				ChatDraw.pulsingDot(g, t, ChatTheme.family(a), t.color(a), dx, dy);
			} else {
				ChatDraw.dot(g, t, ChatTheme.family(a), t.color(a), dx, dy);
			}
			String age = Ago.format(now, r.activityMs());
			int ageW = age.isEmpty() ? 0 : font.width(age) + 6;
			if (!age.isEmpty()) {
				g.text(font, age, rr.right() - 3 - font.width(age), ty, t.muted(), t.shadow());
			}
			int titleColor = r.settled() ? t.muted() : t.text();
			int tx = rr.x() + 28;
			g.text(font, ChatDraw.clip(font, r.title(), rr.right() - 3 - ageW - tx), tx, ty, titleColor, t.shadow());
			hits.add(new Hit(rr, r, null, false));
			ry += rh;
		}
		if (rows.size() > cap) {
			double frac = (double) first / Math.max(1, rows.size() - cap);
			ChatDraw.scrollbar(g, t, list.right() - 8, list.y() + 2, list.h() - 4, rows.size(), cap, frac, false);
		}
	}

	/** Lays out and draws the chips (AgentCraft pills with a dot); returns the y below them. */
	private int chipRows(GuiGraphicsExtractor g, Font font, int x0, int y0, int maxW, int mouseX, int mouseY) {
		ChatTheme t = theme;
		int cx = x0;
		int cy = y0;
		int line = 0;
		boolean any = model.filtered();
		List<Object> items = new ArrayList<>();
		items.add("All");
		items.addAll(model.environments());
		for (Object it : items) {
			String label;
			EnvBadge badge = null;
			boolean on;
			if (it instanceof ThreadList.Env e) {
				label = e.label() + (e.attention() > 0 ? " " + e.attention() : "");
				badge = e.badge();
				on = model.isShown(e.id());
			} else {
				label = "All";
				on = !any;
			}
			int cw = Math.min(maxW, font.width(label) + 10 + (badge == null ? 0 : 11));
			if (cx + cw > x0 + maxW && cx > x0) {
				if (++line >= 2) {
					break; // no room: the rest stays reachable with right-click solo / All
				}
				cx = x0;
				cy += CHIP_H + 2;
			}
			Rect r = new Rect(cx, cy, cw, CHIP_H);
			boolean hover = r.contains(mouseX, mouseY);
			if (on) {
				ChatDraw.pillBox(g, t, cx, cy, cw, hover);
			} else {
				ChatDraw.round(g, cx, cy, cw, CHIP_H, hover ? t.hover() : 0);
				ChatDraw.outline(g, cx, cy, cw, CHIP_H, t.edge());
			}
			int tx = cx + 5;
			if (badge != null) {
				EnvBadge shown = on ? badge : new EnvBadge(badge.initial(), badge.text(), t.disabled(), t.panel());
				ChatDraw.round(g, tx - 1, cy + 2, 8, 7, shown.background());
				tx += 10;
			}
			g.text(font, ChatDraw.clip(font, label, cx + cw - tx - 4), tx, cy + 2, on ? t.keycapText() : t.disabled(), false);
			hits.add(new Hit(r, null, it instanceof ThreadList.Env e ? e.id() : null, !(it instanceof ThreadList.Env)));
			cx += cw + 3;
		}
		return cy + CHIP_H;
	}

	/** Tooltip lines for the row or chip under the mouse, or null. */
	public @Nullable List<String> tooltip(int mouseX, int mouseY) {
		for (Hit hit : hits) {
			if (!hit.r().contains(mouseX, mouseY)) {
				continue;
			}
			if (hit.row() != null) {
				ThreadRow r = hit.row();
				List<String> out = new ArrayList<>();
				out.add(r.title());
				String age = Ago.phrase(System.currentTimeMillis(), r.activityMs());
				out.add(r.attention().word + " · " + r.envLabel() + (r.project().isEmpty() ? "" : " · " + r.project())
						+ (age.isEmpty() ? "" : " · " + age));
				if (r.activity() != null && !r.activity().isBlank()) {
					out.add(r.activity());
				}
				return out;
			}
			if (hit.chipEnv() != null) {
				for (ThreadList.Env e : model.environments()) {
					if (e.id().equals(hit.chipEnv())) {
						return List.of(e.label() + " · " + e.threads() + " threads" + (e.status().isEmpty() ? "" : " · " + e.status()),
								"Click: show/hide · Right-click: only this one");
					}
				}
			}
			if (hit.all()) {
				return List.of("Show every environment");
			}
		}
		return null;
	}

	/**
	 * Handles a click: returns the clicked row (now selected; the host opens it), or handles a chip
	 * (left: toggle, right: solo, "All": show all) and returns null.
	 */
	public @Nullable ThreadRow mouseClicked(double mx, double my, int button) {
		for (Hit hit : hits) {
			if (!hit.r().contains(mx, my)) {
				continue;
			}
			if (hit.all() || hit.chipEnv() != null) {
				Set<String> before = model.hidden();
				if (hit.all()) {
					model.showAll();
				} else if (button == 1) {
					model.solo(hit.chipEnv());
				} else {
					model.toggle(hit.chipEnv());
				}
				first = 0;
				if (onFilterChanged != null && !before.equals(model.hidden())) {
					onFilterChanged.accept(model.hidden());
				}
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
		return bounds.contains(mx, my);
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (!contains(mx, my)) {
			return false;
		}
		first = Math.max(0, first - (int) Math.signum(dy) * 3);
		return true;
	}
}
