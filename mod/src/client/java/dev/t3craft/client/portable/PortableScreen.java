package dev.t3craft.client.portable;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.search.SearchBox;
import dev.t3craft.client.search.SearchClient;
import dev.t3craft.client.search.SearchModel;
import dev.t3craft.t3.ThreadSearch;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The portable T3 terminal: environments + threads (state, project, needs-attention) and recent
 * notifications, anywhere, without any block or base. Read-only: the only actions are navigating,
 * handing a thread to the chat/history screen (feat/chat, via {@link PortableLinks}) and opening the
 * environment screen (feat/config, idem). Nothing here sends anything to T3.
 *
 * <p>Keys: ↑/↓ select, ←/→ environment, Enter open, Tab notifications, Esc/Backspace back, the
 * terminal key closes, Ctrl+F search. Search is a separate component ({@link SearchBox} +
 * {@link SearchModel}): this screen only places it and forwards input, so it moves easily.
 */
public final class PortableScreen extends Screen {
	private static final int REFRESH_TICKS = 10;
	private static final int ROW_H = 12;
	private static final int BG = 0xF0101418;
	private static final int PANEL = 0xFF1A2027;
	private static final int BORDER = 0xFF3A6B73;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int TEXT = 0xFFE6E6E6;
	private static final int DIM = 0xFF8A949E;
	private static final int SELECTED = 0xFF27414A;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private final T3CraftClient client;
	private final NotificationLog log = NotificationLog.INSTANCE;
	private final PortableNav nav = new PortableNav();
	private final List<Hit> hits = new ArrayList<>();
	/** Survives re-init (resize); the {@link SearchBox} view is rebuilt in {@link #init()}. */
	private final SearchModel searchModel;
	private @Nullable SearchBox search;
	private int ticks;
	private int listFirst;
	private int notifFirst;
	private @Nullable String flash;

	private Button threadsTab;
	private Button notificationsTab;
	private Button openButton;
	private Button envButton;

	// layout, recomputed in init()
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int envColW;

	/** A clickable row drawn last frame. */
	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public PortableScreen(T3CraftClient client) {
		super(Component.literal("T3 Terminal"));
		this.client = client;
		this.searchModel = new SearchModel(q -> SearchClient.search(client, q, ThreadSearch.DEFAULT_LIMIT), SearchModel.DEFAULT_DEBOUNCE_MS);
		refresh();
	}

	private void refresh() {
		nav.update(PortableAdapter.snapshot(client));
		nav.setNotificationCount(log.size());
	}

	@Override
	protected void init() {
		int w = Math.min(440, width - 16);
		int h = Math.min(270, height - 16);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		x1 = x0 + w;
		y1 = y0 + h;
		envColW = Math.min(130, w / 3);

		int tabW = 90;
		threadsTab = addRenderableWidget(Button.builder(Component.literal("Threads"), b -> {
			nav.showThreads();
			updateButtons();
		}).bounds(x1 - 2 * tabW - 10, y0 + 4, tabW, 16).build());
		notificationsTab = addRenderableWidget(Button.builder(Component.literal("Notifications"), b -> showNotifications())
				.bounds(x1 - tabW - 6, y0 + 4, tabW, 16).build());
		openButton = addRenderableWidget(Button.builder(Component.literal("Open chat"), b -> openSelected())
				.bounds(x1 - 96, y1 - 22, 90, 16).build());
		envButton = addRenderableWidget(Button.builder(Component.literal("Environments…"), b -> PortableLinks.openEnvironments(this))
				.bounds(x0 + 6, y1 - 22, envColW - 6, 16).build());
		search = new SearchBox(font, searchModel, this::openSearchHit);
		addRenderableWidget(search.layout(x0 + envColW + 6, y0 + 32, x1 - 6 - (x0 + envColW + 6), 14));
		updateButtons();
	}

	private void updateButtons() {
		if (threadsTab == null) {
			return;
		}
		boolean threads = nav.page() == PortableNav.Page.THREADS;
		threadsTab.active = !threads;
		notificationsTab.active = threads;
		notificationsTab.setMessage(Component.literal(log.unread() > 0 ? "Notifications (" + log.unread() + ")" : "Notifications"));
		PortableThread sel = nav.selectedThread();
		openButton.visible = threads;
		if (search != null) {
			search.setVisible(threads);
		}
		if (threads && search != null && search.active()) {
			openButton.active = search.selectedHit() != null;
			envButton.visible = threads;
			return;
		}
		openButton.active = sel != null && sel.threadId() != null && !sel.env().isEmpty()
				&& nav.state().connection() == PortableState.Connection.CONNECTED;
		envButton.visible = threads;
	}

	private void showNotifications() {
		nav.showNotifications(log.size());
		log.markAllRead();
		updateButtons();
	}

	private void openSearchHit(String env, String threadId) {
		if (!PortableLinks.hasChat() && client.npcs().info(env.isEmpty() ? threadId : env + ":" + threadId) == null) {
			flash = "That thread is not listed in the world right now, so there is no chat screen for it yet.";
			return;
		}
		PortableLinks.openChat(env, threadId);
	}

	private void openSelected() {
		if (search != null && search.active()) {
			search.openSelected();
			return;
		}
		PortableThread t = nav.selectedThread();
		if (t == null || t.threadId() == null || t.env().isEmpty() || nav.state().connection() != PortableState.Connection.CONNECTED) {
			return;
		}
		PortableLinks.openChat(t.env(), t.threadId());
	}

	@Override
	public void tick() {
		if (search != null) {
			search.tick();
		}
		if (++ticks % REFRESH_TICKS == 0) {
			refresh();
			if (nav.page() == PortableNav.Page.NOTIFICATIONS) {
				log.markAllRead();
			}
			updateButtons();
		}
	}

	// ---- input --------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent e) {
		flash = null;
		if (nav.page() == PortableNav.Page.THREADS && search != null && search.keyPressed(this, e)) {
			updateButtons();
			return true;
		}
		int k = e.key();
		if (k == InputConstants.KEY_ESCAPE || k == InputConstants.KEY_BACKSPACE) {
			if (nav.back()) {
				updateButtons();
				return true;
			}
			onClose();
			return true;
		}
		if (PortableClient.key() != null && PortableClient.key().matches(e)) {
			onClose();
			return true;
		}
		if (k == InputConstants.KEY_UP) {
			nav.move(-1);
		} else if (k == InputConstants.KEY_DOWN) {
			nav.move(1);
		} else if (k == InputConstants.KEY_PAGEUP) {
			nav.move(-8);
		} else if (k == InputConstants.KEY_PAGEDOWN) {
			nav.move(8);
		} else if (k == InputConstants.KEY_LEFT && nav.page() == PortableNav.Page.THREADS) {
			nav.cycleEnv(-1);
		} else if (k == InputConstants.KEY_RIGHT && nav.page() == PortableNav.Page.THREADS) {
			nav.cycleEnv(1);
		} else if (k == InputConstants.KEY_TAB) {
			if (nav.page() == PortableNav.Page.THREADS) {
				showNotifications();
			} else {
				nav.showThreads();
			}
		} else if (k == InputConstants.KEY_RETURN) {
			activate();
		} else {
			return super.keyPressed(e);
		}
		updateButtons();
		return true;
	}

	private void activate() {
		if (nav.page() == PortableNav.Page.THREADS) {
			openSelected();
			return;
		}
		List<NotificationLog.Entry> entries = log.entries();
		if (entries.isEmpty()) {
			return;
		}
		NotificationLog.Entry e = entries.get(Math.min(nav.notificationSelected(), entries.size() - 1));
		flash = nav.focusThread(e.npcId()) ? null : "That thread is not listed any more (filtered out or archived).";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) {
			return true;
		}
		if (search != null) {
			search.blur(this); // a click outside the field gives the screen's keys back
		}
		if (nav.page() == PortableNav.Page.THREADS && search != null && search.mouseClicked(e.x(), e.y(), doubleClick)) {
			updateButtons();
			return true;
		}
		for (Hit h : List.copyOf(hits)) {
			if (h.contains(e.x(), e.y())) {
				h.action().run();
				if (doubleClick) {
					activate();
				}
				updateButtons();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (dy != 0 && nav.page() == PortableNav.Page.THREADS && search != null && search.active()) {
			search.move(dy > 0 ? -1 : 1);
			updateButtons();
			return true;
		}
		if (dy != 0) {
			nav.move(dy > 0 ? -1 : 1);
			updateButtons();
			return true;
		}
		return false;
	}

	// ---- drawing ------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		g.fill(0, 0, width, height, 0x88000000);
		g.fill(x0, y0, x1, y1, BG);
		g.outline(x0, y0, x1 - x0, y1 - y0, BORDER);

		PortableState s = nav.state();
		g.text(font, "T3 TERMINAL", x0 + 8, y0 + 6, ACCENT);
		if (flash != null) {
			g.text(font, font.plainSubstrByWidth(flash, x1 - x0 - 210), x0 + 8, y0 + 16, 0xFFFFAA00);
		} else {
			g.text(font, font.plainSubstrByWidth("read-only · " + s.sourceLabel(), x1 - x0 - 210), x0 + 8, y0 + 16, DIM);
		}
		g.fill(x0 + 4, y0 + 26, x1 - 4, y0 + 27, BORDER);

		if (nav.page() == PortableNav.Page.THREADS) {
			drawThreadsPage(g, s);
		} else {
			drawNotificationsPage(g);
		}
		String help = nav.page() == PortableNav.Page.THREADS
				? (search != null && search.active()
						? "↑↓ select · Enter open · Esc clear search"
						: "↑↓ select · ←→ environment · Enter open · Ctrl+F search · Tab notifications · Esc close")
				: "↑↓ select · Enter go to thread · Tab threads · Esc back";
		if (y1 + 4 + font.lineHeight <= height) {
			g.text(font, font.plainSubstrByWidth(help, x1 - x0 - 16), x0 + 8, y1 + 4, DIM);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void drawThreadsPage(GuiGraphicsExtractor g, PortableState s) {
		int top = y0 + 32;
		int bottom = y1 - 26;
		// left: environment filter
		int ex = x0 + 6;
		g.fill(ex, top, x0 + envColW, bottom, PANEL);
		int y = top + 3;
		List<@Nullable String> choices = nav.envChoices();
		for (String env : choices) {
			if (y + ROW_H > bottom) {
				break;
			}
			boolean sel = env == null ? nav.envFilter() == null : env.equals(nav.envFilter());
			if (sel) {
				g.fill(ex, y - 1, x0 + envColW, y + ROW_H - 1, SELECTED);
			}
			String label;
			int dot;
			int count;
			int attention;
			if (env == null) {
				label = "All";
				dot = s.connection() == PortableState.Connection.CONNECTED ? 0xFF55FF55 : 0xFFFF5555;
				count = s.threads().size();
				attention = s.needsAttention();
			} else {
				PortableEnv pe = s.environments().stream().filter(e -> e.id().equals(env)).findFirst().orElse(null);
				label = pe == null ? env : pe.label();
				dot = pe == null ? DIM : statusColor(pe.status());
				count = (int) s.threads().stream().filter(t -> env.equals(t.env())).count();
				attention = (int) s.threads().stream().filter(t -> env.equals(t.env()) && t.needsAttention()).count();
			}
			g.fill(ex + 3, y + 2, ex + 7, y + 6, dot);
			String tail = attention > 0 ? " " + count + " !" + attention : " " + count;
			int tailW = font.width(tail);
			g.text(font, font.plainSubstrByWidth(label, envColW - 18 - tailW), ex + 10, y, TEXT);
			g.text(font, tail, x0 + envColW - 3 - tailW, y, attention > 0 ? 0xFFFFAA00 : DIM);
			String target = env;
			hits.add(new Hit(ex, y - 1, x0 + envColW, y + ROW_H - 1, () -> nav.setEnv(target)));
			y += ROW_H;
		}
		// selected environment's status detail (offline reason etc.) under the list
		int envTop = top;
		if (nav.envFilter() != null) {
			s.environments().stream().filter(e -> e.id().equals(nav.envFilter())).findFirst().ifPresent(pe -> {
				int yy = Math.max(envTop + 3 + choices.size() * ROW_H + 4, envTop + 40);
				wrap(g, pe.status() + (pe.detail().isEmpty() ? "" : ": " + pe.detail()), ex + 3, yy, envColW - 10, bottom - yy, statusColor(pe.status()));
			});
		}

		// right: search field (widget, placed in init) then threads or search results
		int lx = x0 + envColW + 6;
		int rx = x1 - 6;
		top += 18;
		int summaryH = 46;
		int listBottom = bottom - summaryH;
		g.fill(lx, top, rx, bottom, PANEL);
		if (search != null && search.active()) {
			search.render(g, lx, top, rx, bottom);
			return;
		}

		String empty = emptyMessage(s);
		List<PortableThread> rows = nav.visible();
		if (empty != null) {
			wrap(g, empty, lx + 8, top + 10, rx - lx - 16, listBottom - top - 10, TEXT);
		} else {
			int capacity = Math.max(1, (listBottom - top - 4) / ROW_H);
			if (nav.selected() < listFirst) {
				listFirst = nav.selected();
			} else if (nav.selected() >= listFirst + capacity) {
				listFirst = nav.selected() - capacity + 1;
			}
			listFirst = Math.max(0, Math.min(listFirst, Math.max(0, rows.size() - capacity)));
			y = top + 3;
			for (int i = listFirst; i < rows.size() && i < listFirst + capacity; i++) {
				PortableThread t = rows.get(i);
				if (i == nav.selected()) {
					g.fill(lx, y - 1, rx, y + ROW_H - 1, SELECTED);
				}
				int color = 0xFF000000 | t.color();
				g.text(font, glyph(t.state()), lx + 4, y, color);
				String right = t.envLabel() + (t.project().isEmpty() ? "" : " · " + t.project());
				int rightW = Math.min(font.width(right), (rx - lx) / 2);
				String rightClip = font.plainSubstrByWidth(right, rightW);
				g.text(font, font.plainSubstrByWidth(t.title(), rx - lx - 26 - rightW), lx + 16, y, t.settled() ? DIM : TEXT);
				g.text(font, rightClip, rx - 4 - font.width(rightClip), y, DIM);
				int index = i;
				hits.add(new Hit(lx, y - 1, rx, y + ROW_H - 1, () -> nav.select(index)));
				y += ROW_H;
			}
			if (rows.size() > capacity) {
				g.text(font, (listFirst + 1) + "–" + Math.min(rows.size(), listFirst + capacity) + " / " + rows.size(), rx - 60, listBottom - 10, DIM);
			}
		}

		// summary of the selected thread (data the mod already has; history lives in the chat screen)
		g.fill(lx, listBottom, rx, listBottom + 1, BORDER);
		PortableThread sel = nav.selectedThread();
		if (sel != null && empty == null) {
			int sy = listBottom + 4;
			g.text(font, Component.literal(stateWord(sel.state())).withColor(sel.color()), lx + 4, sy, TEXT);
			String where = sel.envLabel() + (sel.project().isEmpty() ? "" : " · " + sel.project()) + (sel.settled() ? " · settled" : "");
			g.text(font, font.plainSubstrByWidth(where, rx - lx - 70), lx + 70, sy, DIM);
			String activity = sel.activity() == null ? "No recent activity text." : sel.activity();
			wrap(g, activity, lx + 4, sy + 12, rx - lx - 104, 22, TEXT);
		}
	}

	private @Nullable String emptyMessage(PortableState s) {
		return switch (s.connection()) {
			case NOT_CONFIGURED -> client.link() != null
					? "The bridge is not configured."
					: "No T3 environment paired yet. Use \"Environments…\" or /t3craft pair to pair one (read-only).";
			case DISCONNECTED -> {
				String why = s.environments().isEmpty() ? "" : " " + String.join("; ", s.environments().stream()
						.map(e -> e.label() + ": " + e.status()).toList()) + ".";
				yield "Not connected to T3." + why + " Reconnecting automatically; see /t3craft status."
						+ (s.threads().isEmpty() ? "" : " Showing nothing rather than stale data.");
			}
			case CONNECTED -> nav.visible().isEmpty()
					? (nav.envFilter() == null
							? "Connected. No threads to show (only active, waiting or recent threads are listed)."
							: "No threads to show in this environment.")
					: null;
		};
	}

	private void drawNotificationsPage(GuiGraphicsExtractor g) {
		int top = y0 + 32;
		int bottom = y1 - 8;
		int lx = x0 + 6;
		int rx = x1 - 6;
		g.fill(lx, top, rx, bottom, PANEL);
		List<NotificationLog.Entry> entries = log.entries();
		if (entries.isEmpty()) {
			wrap(g, "No notifications yet this session. A notice appears here when a thread needs you, fails or finishes "
					+ "(the initial state is not announced).", lx + 8, top + 10, rx - lx - 16, bottom - top - 10, TEXT);
			return;
		}
		int rowH = ROW_H * 2;
		int capacity = Math.max(1, (bottom - top - 4) / rowH);
		int sel = nav.notificationSelected();
		if (sel < notifFirst) {
			notifFirst = sel;
		} else if (sel >= notifFirst + capacity) {
			notifFirst = sel - capacity + 1;
		}
		notifFirst = Math.max(0, Math.min(notifFirst, Math.max(0, entries.size() - capacity)));
		int y = top + 3;
		for (int i = notifFirst; i < entries.size() && i < notifFirst + capacity; i++) {
			NotificationLog.Entry e = entries.get(i);
			if (i == sel) {
				g.fill(lx, y - 1, rx, y + rowH - 1, SELECTED);
			}
			String time = CLOCK.format(Instant.ofEpochMilli(e.atMs()));
			g.text(font, time, lx + 4, y, DIM);
			g.text(font, font.plainSubstrByWidth(e.title(), rx - lx - 44), lx + 36, y, 0xFF000000 | e.color());
			g.text(font, font.plainSubstrByWidth(e.text(), rx - lx - 44), lx + 36, y + ROW_H - 2, DIM);
			int index = i;
			hits.add(new Hit(lx, y - 1, rx, y + rowH - 1, () -> nav.selectNotification(index)));
			y += rowH;
		}
	}

	private void wrap(GuiGraphicsExtractor g, String text, int x, int y, int w, int maxH, int color) {
		int lines = Math.max(1, maxH / font.lineHeight);
		List<FormattedCharSequence> split = font.split(Component.literal(text), Math.max(20, w));
		for (int i = 0; i < split.size() && i < lines; i++) {
			g.text(font, split.get(i), x, y + i * font.lineHeight, color);
		}
	}

	static String glyph(String state) {
		return switch (state) {
			case "needs_attention" -> "!";
			case "error" -> "✖";
			case "working" -> "⚙";
			case "done" -> "✔";
			default -> "·";
		};
	}

	static String stateWord(String state) {
		return switch (state) {
			case "needs_attention" -> "Needs you";
			case "error" -> "Error";
			case "working" -> "Working";
			case "done" -> "Done";
			default -> "Idle";
		};
	}

	private static int statusColor(String status) {
		return switch (status) {
			case "ok" -> 0xFF55FF55;
			case "connecting" -> 0xFFFFFF55;
			default -> 0xFFFF5555;
		};
	}

	@Override
	public void removed() {
		searchModel.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
