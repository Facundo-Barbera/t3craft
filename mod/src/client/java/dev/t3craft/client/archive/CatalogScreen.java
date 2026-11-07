package dev.t3craft.client.archive;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The archive catalog (the lectern at the foot of the basement stairs, or {@code /t3craft archive}):
 * every closed thread, newest close first, one page at a time, including the ones that do not fit
 * in the basement slots. "Older" reads the next page of older settled threads from T3; a click on a
 * row opens the thread read-only ({@link ArchiveOpener}). Read-only: nothing here writes to T3.
 */
public final class CatalogScreen extends Screen {
	private static final int BG = 0xF0101418;
	private static final int BORDER = 0xFF3A4A5A;
	private static final int ROW_H = 12;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int DIM = 0xFF888888;
	private static final int HOVER = 0x40FFFFFF;

	private record Hit(int x0, int y0, int x1, int y1, ArchiveEntry entry) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private final ArchiveStore store;
	private final ArchiveOpener opener;
	private final java.util.function.Predicate<String> slotted;
	private final List<Hit> hits = new ArrayList<>();
	private int page;
	private int perPage = 10;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private Button prev;
	private Button next;
	private Button older;
	private Button refresh;

	/** {@code slotted}: whether an entry key currently has a basement slot (marked in the list). */
	public CatalogScreen(ArchiveStore store, ArchiveOpener opener, java.util.function.Predicate<String> slotted) {
		super(Component.translatable("t3craft.archive.catalog.title"));
		this.store = store;
		this.opener = opener;
		this.slotted = slotted;
	}

	@Override
	protected void init() {
		x0 = Math.max(8, width / 2 - 210);
		x1 = Math.min(width - 8, width / 2 + 210);
		y0 = 16;
		y1 = height - 40;
		perPage = Math.max(1, (y1 - y0 - 48) / ROW_H);
		int bw = 76;
		int bx = width / 2 - (bw * 5 + 16) / 2;
		int by = height - 30;
		prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> page--).bounds(bx, by, bw, 20).build());
		next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page++).bounds(bx + (bw + 4), by, bw, 20).build());
		older = addRenderableWidget(Button.builder(Component.translatable("t3craft.archive.catalog.older"), b -> {
			store.loadMore(System.currentTimeMillis());
		}).bounds(bx + 2 * (bw + 4), by, bw, 20).build());
		refresh = addRenderableWidget(Button.builder(Component.translatable("t3craft.archive.catalog.refresh"),
				b -> store.refreshNow(System.currentTimeMillis())).bounds(bx + 3 * (bw + 4), by, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(bx + 4 * (bw + 4), by, bw, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int pages = store.pageCount(perPage);
		page = Math.max(0, Math.min(page, pages - 1));
		prev.active = page > 0;
		next.active = page < pages - 1;
		older.active = store.fullReads() && store.anyMore() && !store.loading();
		refresh.active = store.fullReads() && !store.loading();

		hits.clear();
		g.fill(0, 0, width, height, 0x88000000);
		g.fill(x0, y0, x1, y1, BG);
		g.outline(x0, y0, x1 - x0, y1 - y0, BORDER);
		List<ArchiveEntry> all = store.entries();
		g.text(font, title.getString() + " · " + all.size(), x0 + 8, y0 + 6, 0xFFFFD27F);
		String pageLabel = (page + 1) + "/" + pages;
		g.text(font, pageLabel, x1 - 8 - font.width(pageLabel), y0 + 6, DIM);
		g.text(font, font.plainSubstrByWidth(statusLine(), x1 - x0 - 16), x0 + 8, y0 + 18, DIM);
		g.fill(x0 + 4, y0 + 30, x1 - 4, y0 + 31, BORDER);

		int w = x1 - x0 - 16;
		int dateW = font.width("▣ 0000-00-00 ");
		int envW = Math.min(90, w / 5);
		int projW = Math.min(110, w / 4);
		int titleW = w - dateW - envW - projW - 12;
		int y = y0 + 36;
		List<ArchiveEntry> rows = store.page(page, perPage);
		if (rows.isEmpty()) {
			g.text(font, Component.translatable(store.loading() ? "t3craft.archive.catalog.loading" : "t3craft.archive.catalog.empty").getString(),
					x0 + 8, y, DIM);
		}
		ZoneId zone = ZoneId.systemDefault();
		for (ArchiveEntry e : rows) {
			if (mouseX >= x0 + 4 && mouseX < x1 - 4 && mouseY >= y - 2 && mouseY < y + ROW_H - 2) {
				g.fill(x0 + 4, y - 2, x1 - 4, y + ROW_H - 2, HOVER);
			}
			int x = x0 + 8;
			String mark = slotted.test(e.key()) ? "■ " : "  ";
			g.text(font, font.plainSubstrByWidth(mark + e.title(), titleW), x, y, TEXT);
			x += titleW + 4;
			g.text(font, font.plainSubstrByWidth(e.envLabel(), envW), x, y, 0xFF000000 | ArchiveLabel.envColor(e.env()));
			x += envW + 4;
			g.text(font, e.kind().glyph + " " + ArchiveLabel.date(e.closedAtMs(), zone), x, y, DIM);
			x += dateW + 4;
			g.text(font, font.plainSubstrByWidth(e.projectLabel(), projW), x, y, 0xFFBBBBBB);
			hits.add(new Hit(x0 + 4, y - 2, x1 - 4, y + ROW_H - 2, e));
			y += ROW_H;
		}
		super.extractRenderState(g, mouseX, mouseY, a);
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
			if (h.contains(e.x(), e.y())) {
				opener.open(h.entry());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (dy != 0) {
			page += dy > 0 ? -1 : 1;
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
