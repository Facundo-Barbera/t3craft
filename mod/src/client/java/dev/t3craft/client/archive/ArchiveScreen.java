package dev.t3craft.client.archive;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.Rect;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The archive (the archive terminal's screen; also {@code /t3craft archive} and the portable's
 * "Archive" button): every settled and archived thread of every environment, newest close first,
 * paged, with a search box and environment / project filter chips. Each row: the environment's
 * {@link EnvBadge}, title, project and close date. A click opens the thread read-only
 * ({@link ArchiveOpener}). "Older" reads the next page of older settled threads from T3,
 * "Refresh" re-reads. Drawn with the chat's kit ({@link ChatTheme#warmStudio()}, {@link ChatDraw}).
 * Read-only: nothing here writes to T3. The list state lives in {@link ArchiveList} (tested).
 */
public final class ArchiveScreen extends Screen {
	private static final int MAX_W = 460;

	private record Hit(Rect r, @Nullable ArchiveEntry entry, int chip) {
	}

	private static final int CHIP_ENV = 1;
	private static final int CHIP_PROJECT = 2;

	private final ArchiveStore store;
	private final ArchiveOpener opener;
	private final ArchiveList list;
	/** Where Done / Esc go (the portable terminal); null = back to the game. */
	private final @Nullable Screen parent;
	/** The UI tokens ({@code gui/ui-style.json}); swapped in {@link #tick} after an F3+T. */
	private ChatTheme theme = ChatTheme.warmStudio();
	private final List<Hit> hits = new ArrayList<>();
	private Rect frame = Rect.EMPTY;
	private int perPage = 10;
	private @Nullable EditBox search;
	private @Nullable Button prev;
	private @Nullable Button next;
	private @Nullable Button older;
	private @Nullable Button refresh;

	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener) {
		this(store, opener, new ArchiveList());
	}

	/** {@code list}: the list state to show (kept by the caller to come back to the same search). */
	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener, ArchiveList list) {
		this(store, opener, list, null);
	}

	/** {@code parent}: the screen Done / Esc return to (null = close to the game). */
	public ArchiveScreen(ArchiveStore store, ArchiveOpener opener, ArchiveList list, @Nullable Screen parent) {
		super(Component.translatable("t3craft.archive.screen.title"));
		this.store = store;
		this.opener = opener;
		this.list = list;
		this.parent = parent;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void tick() {
		super.tick();
		ChatTheme current = ChatTheme.warmStudio();
		if (current != theme) { // F3+T reloaded gui/ui-style.json (#12)
			theme = current;
			rebuildWidgets();
		}
	}

	@Override
	protected void init() {
		int m = theme.margin();
		int w = Math.min(MAX_W, width - 2 * m);
		int h = height - 2 * m;
		frame = new Rect((width - w) / 2, m, w, h);
		int pad = theme.pad();
		int x0 = frame.x() + pad;
		int searchW = Math.max(80, (frame.w() - 2 * pad) / 2);
		EditBox box = new EditBox(font, x0 + 3, frame.y() + pad + 18, searchW - 6, 12, search, Component.translatable("t3craft.archive.screen.search"));
		box.setBordered(false);
		box.setTextColor(theme.text());
		box.setHint(Component.translatable("t3craft.archive.screen.search"));
		box.setValue(list.text());
		box.setResponder(list::setText);
		search = addRenderableWidget(box);
		setInitialFocus(box);

		int listTop = frame.y() + pad + 46;
		int listBottom = frame.bottom() - pad - 26;
		perPage = Math.max(1, (listBottom - listTop) / theme.listRow());

		int bw = Math.min(76, (frame.w() - 2 * pad - 16) / 5);
		int bx = frame.x() + (frame.w() - (bw * 5 + 16)) / 2;
		int by = frame.bottom() - pad - 20;
		prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> list.step(-1)).bounds(bx, by, bw, 20).build());
		next = addRenderableWidget(Button.builder(Component.literal(">"), b -> list.step(1)).bounds(bx + (bw + 4), by, bw, 20).build());
		older = addRenderableWidget(Button.builder(Component.translatable("t3craft.archive.catalog.older"),
				b -> store.loadMore(System.currentTimeMillis())).bounds(bx + 2 * (bw + 4), by, bw, 20).build());
		refresh = addRenderableWidget(Button.builder(Component.translatable("t3craft.archive.catalog.refresh"),
				b -> store.refreshNow(System.currentTimeMillis())).bounds(bx + 3 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(bx + 4 * (bw + 4), by, bw, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<ArchiveEntry> all = store.entries();
		ArchiveList.Page page = list.page(all, perPage);
		if (prev != null && next != null && older != null && refresh != null) {
			prev.active = page.index() > 0;
			next.active = page.index() < page.pages() - 1;
			older.active = store.fullReads() && store.anyMore() && !store.loading();
			refresh.active = store.fullReads() && !store.loading();
		}
		hits.clear();
		g.fillGradient(0, 0, width, height, theme.backdrop(), theme.backdropBottom());
		ChatDraw.panel(g, theme, frame);

		int pad = theme.pad();
		int x0 = frame.x() + pad;
		int x1 = frame.right() - pad;
		int y = frame.y() + pad;
		String head = title.getString() + " · " + (page.total() == all.size() ? Integer.toString(all.size()) : page.total() + "/" + all.size());
		g.text(font, head, x0, y + 2, theme.accent(), theme.shadow());
		String pageLabel = (page.index() + 1) + "/" + page.pages();
		g.text(font, pageLabel, x1 - font.width(pageLabel), y + 2, theme.muted(), theme.shadow());

		// search field + filter chips
		y += 16;
		int searchW = Math.max(80, (frame.w() - 2 * pad) / 2);
		ChatDraw.field(g, theme, new Rect(x0, y - 1, searchW, 14), search != null && search.isFocused());
		int cx = x0 + searchW + 6;
		List<ArchiveList.Option> envs = ArchiveList.envs(all);
		String envName = ArchiveList.label(envs, list.env());
		cx += chip(g, CHIP_ENV, Component.translatable("t3craft.archive.screen.env", envName == null
				? Component.translatable("t3craft.archive.screen.all").getString() : envName).getString(), cx, y + 1, mouseX, mouseY) + 4;
		List<ArchiveList.Option> projects = ArchiveList.projects(all, list.env());
		String projName = ArchiveList.label(projects, list.project());
		chip(g, CHIP_PROJECT, Component.translatable("t3craft.archive.screen.project", projName == null
				? Component.translatable("t3craft.archive.screen.all").getString() : projName).getString(), cx, y + 1, mouseX, mouseY);

		y += 16;
		g.text(font, ChatDraw.clip(font, statusLine(), x1 - x0), x0, y, theme.muted(), theme.shadow());
		y += 12;
		Rect well = new Rect(x0 - 2, y - 2, x1 - x0 + 4, perPage * theme.listRow() + 4);
		ChatDraw.inset(g, theme, well);

		int w = x1 - x0 - 4;
		int badge = 9;
		int dateW = font.width("▣ 0000-00-00");
		int projW = Math.min(120, w / 4);
		int titleW = w - badge - 4 - projW - 6 - dateW - 6;
		int row = theme.listRow();
		if (page.rows().isEmpty()) {
			String empty = store.loading() ? "t3craft.archive.catalog.loading"
					: all.isEmpty() ? "t3craft.archive.catalog.empty" : "t3craft.archive.screen.no_match";
			g.text(font, Component.translatable(empty).getString(), x0 + 2, y + 2, theme.muted(), theme.shadow());
		}
		ZoneId zone = ZoneId.systemDefault();
		for (ArchiveEntry e : page.rows()) {
			Rect r = new Rect(x0, y, x1 - x0, row);
			if (r.contains(mouseX, mouseY)) {
				g.fill(r.x(), r.y(), r.right(), r.bottom(), theme.hover());
			}
			int x = x0 + 2;
			int ty = y + (row - 8) / 2;
			ChatDraw.envIcon(g, font, EnvBadge.of(e.env(), e.envLabel()), x, y + (row - badge) / 2, badge);
			x += badge + 4;
			g.text(font, ChatDraw.clip(font, e.title(), titleW), x, ty, theme.text(), theme.shadow());
			x += titleW + 6;
			g.text(font, ChatDraw.clip(font, e.projectLabel(), projW), x, ty, theme.muted(), theme.shadow());
			x += projW + 6;
			g.text(font, e.kind().glyph + " " + ArchiveLabel.date(e.closedAtMs(), zone), x, ty, theme.muted(), theme.shadow());
			hits.add(new Hit(r, e, 0));
			y += row;
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** A filter chip (pill); returns its width and records its hit box. */
	private int chip(GuiGraphicsExtractor g, int id, String text, int x, int y, int mouseX, int mouseY) {
		int w = font.width(text) + 10;
		Rect r = new Rect(x, y, w, 11);
		ChatDraw.pillBox(g, theme, x, y, w, r.contains(mouseX, mouseY));
		g.text(font, text, x + 5, y + 2, theme.keycapText(), false);
		hits.add(new Hit(r, null, id));
		return w;
	}

	private String statusLine() {
		if (!store.fullReads()) {
			return Component.translatable("t3craft.archive.catalog.shell_only").getString();
		}
		StringBuilder b = new StringBuilder();
		for (ArchiveStore.EnvStatus s : store.statuses()) {
			if (!b.isEmpty()) {
				b.append(" · ");
			}
			b.append(s.label()).append(' ').append(s.listed());
			if (s.hasMore()) {
				b.append('+');
			}
			if (!s.connected()) {
				b.append(" (offline)");
			} else if (s.loading()) {
				b.append(" …");
			} else if (s.error() != null) {
				b.append(" (").append(s.error()).append(')');
			}
		}
		return b.isEmpty() ? Component.translatable("t3craft.archive.catalog.no_env").getString() : b.toString();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) {
			return true;
		}
		for (Hit h : List.copyOf(hits)) {
			if (!h.r().contains(e.x(), e.y())) {
				continue;
			}
			if (h.entry() != null) {
				if (!dev.t3craft.client.chat.MouseButtons.isLeft(e.button())) {
					return false; // only a left click opens a thread (26.x buttons: left = 1, not GLFW's 0)
				}
				opener.open(h.entry());
			} else if (h.chip() == CHIP_ENV) {
				list.cycleEnv(store.entries());
			} else if (h.chip() == CHIP_PROJECT) {
				list.cycleProject(store.entries());
			}
			return true;
		}
		return false;
	}

	/** PgUp / PgDn turn the page (also while typing in the search field, which ignores them). */
	@Override
	public boolean keyPressed(KeyEvent e) {
		if (super.keyPressed(e)) {
			return true;
		}
		int dir = ArchiveKeys.pageStep(e.key());
		if (dir != 0) {
			list.step(dir);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (dy != 0) {
			list.step(dy > 0 ? -1 : 1);
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
