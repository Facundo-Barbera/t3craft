package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Rect;

/**
 * Where everything of the unified T3 screen sits, in scaled GUI pixels (so it follows the window size
 * and the GUI scale). Pure Java, tested.
 *
 * <p>Structure of T3 Code's main layout ({@code apps/web/src/components/AppSidebarLayout.tsx} +
 * {@code ChatView.tsx}): sidebar on the left, the thread view on the right with its header
 * ({@code chat/ChatHeader.tsx}) on top, the conversation in the middle and the composer
 * ({@code chat/ChatComposer.tsx}) at the bottom. The frame is centred, at most {@value #MAX_W}×{@value #MAX_H}.
 * Below {@value #COMPACT_W}×{@value #COMPACT_H} the screen is <b>compact</b>: one panel at a time (sidebar
 * or thread), like T3 Code's mobile sidebar sheet; {@code showSidebar} picks which. In the wide layout
 * {@code showSidebar = false} hides the sidebar (T3's {@code mod+b} "sidebar.toggle").
 *
 * @param frame     the whole screen area used
 * @param sidebar   sidebar panel (EMPTY in compact mode when the thread is shown)
 * @param main      thread panel (EMPTY in compact mode when the sidebar is shown)
 * @param compact   one panel at a time
 */
public record T3UiLayout(Rect frame, Rect sidebar, Rect main, boolean compact) {
	public static final int MAX_W = 900;
	public static final int MAX_H = 520;
	public static final int MARGIN = 6;
	public static final int GAP = 4;
	public static final int COMPACT_W = 330;
	public static final int COMPACT_H = 170;
	public static final int SIDEBAR_MIN = 120;
	public static final int SIDEBAR_MAX = 210;

	// sidebar rows
	public static final int PAD = 4;
	public static final int BRAND_H = 12;
	public static final int FIELD_H = 14;
	/** Footer of discreet icons (settings, environments, archive), as T3 Code's sidebar bottom row. */
	public static final int FOOTER_H = 12;
	public static final int ROW_H = 12;
	// thread header
	public static final int TITLE_H = 14;
	/** No action row any more (feat/t3-ui-clean): kept for callers, 0. */
	public static final int ACTIONS_H = 0;
	/**
	 * feat/t3-composer: max width of the transcript / composer column in the thread panel (T3 Code
	 * centres both in one {@code max-w-3xl} column).
	 */
	public static final int COLUMN_MAX = 460;
	/** Icon buttons at the end of the search row (project filter, new thread). */
	public static final int ICON_W = 14;

	public static T3UiLayout compute(int guiW, int guiH, boolean showSidebar) {
		int sw = Math.max(0, guiW);
		int sh = Math.max(0, guiH);
		int m = Math.max(0, Math.min(MARGIN, Math.min(sw, sh) / 8));
		int w = Math.max(0, Math.min(MAX_W, sw - 2 * m));
		int h = Math.max(0, Math.min(MAX_H, sh - 2 * m));
		Rect frame = new Rect((sw - w) / 2, (sh - h) / 2, w, h);
		boolean compact = w < COMPACT_W || h < COMPACT_H;
		if (compact) {
			return showSidebar ? new T3UiLayout(frame, frame, Rect.EMPTY, true) : new T3UiLayout(frame, Rect.EMPTY, frame, true);
		}
		if (!showSidebar) {
			return new T3UiLayout(frame, Rect.EMPTY, frame, false);
		}
		Rect[] split = frame.splitX(sidebarWidth(w), GAP);
		return new T3UiLayout(frame, split[0], split[1], false);
	}

	/**
	 * Which panel a compact screen starts on: the thread when it was opened on one (a block click,
	 * an NPC, a search hit), the sidebar otherwise (the T3 key).
	 */
	public static boolean startsOnSidebar(@org.jspecify.annotations.Nullable String key) {
		return key == null;
	}

	/** Sidebar width for a frame {@code w} wide: 27 %, between {@value #SIDEBAR_MIN} and {@value #SIDEBAR_MAX}. */
	public static int sidebarWidth(int w) {
		return Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, w * 27 / 100));
	}

	/**
	 * Inside the sidebar, top to bottom (T3's {@code SidebarChrome}: brand + menu; the search row with
	 * the project filter and "new thread" icons at its end, as T3 Code's search row with its filter
	 * and compose icons; the thread list; the footer with archive and settings).
	 * feat/t3-composer: {@code project} and {@code newThread} are those two icons, no rows of their own.
	 */
	public record SidebarRects(Rect brand, Rect search, Rect project, Rect newThread, Rect list, Rect footer) {
	}

	public static SidebarRects sidebar(Rect s) {
		if (s.w() == 0 || s.h() == 0) {
			return new SidebarRects(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);
		}
		int x = s.x() + PAD;
		int w = s.w() - 2 * PAD;
		int y = s.y() + PAD;
		Rect brand = new Rect(x, y, w, BRAND_H);
		y += BRAND_H + 2;
		int icons = 2 * (ICON_W + 2);
		Rect search = new Rect(x, y, Math.max(20, w - icons), FIELD_H);
		Rect project = new Rect(x + w - 2 * ICON_W - 2, y, ICON_W, FIELD_H);
		Rect newThread = new Rect(x + w - ICON_W, y, ICON_W, FIELD_H);
		y += FIELD_H + 4;
		int footerY = s.bottom() - PAD - FOOTER_H;
		Rect list = new Rect(x, y, w, Math.max(0, footerY - 2 - y));
		Rect footer = new Rect(x, footerY, w, FOOTER_H);
		return new SidebarRects(brand, search, project, newThread, list, footer);
	}

	/**
	 * Inside the thread panel: the header (one row: breadcrumb and state, like T3 Code's
	 * {@code ChatHeader}; the action row is gone, {@code actions} is {@link Rect#EMPTY}), the
	 * conversation and, when the composer is a separate component, its rectangle at the bottom
	 * ({@code composerH} 0 = the conversation draws its own command bar).
	 */
	public record MainRects(Rect title, Rect actions, Rect conversation, Rect composer) {
	}

	public static MainRects main(Rect m, int composerH) {
		if (m.w() == 0 || m.h() == 0) {
			return new MainRects(Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY);
		}
		int x = m.x() + PAD;
		int w = m.w() - 2 * PAD;
		int y = m.y() + PAD;
		Rect title = new Rect(x, y, w, TITLE_H);
		y += TITLE_H + 3;
		Rect actions = Rect.EMPTY;
		int ch = Math.max(0, Math.min(composerH, m.bottom() - y - 40));
		Rect composer = ch == 0 ? Rect.EMPTY : new Rect(m.x(), m.bottom() - ch, m.w(), ch);
		Rect conversation = new Rect(m.x(), y, m.w(), Math.max(0, m.bottom() - y - (ch == 0 ? 0 : ch + GAP)));
		return new MainRects(title, actions, conversation, composer);
	}

	/**
	 * Row hit-test of the sidebar list with scroll: index into the drawn items of the row under
	 * ({@code mx}, {@code my}), or -1. {@code first} is the first item drawn, {@code count} the items.
	 */
	public static int rowAt(double mx, double my, Rect list, int first, int count) {
		if (!list.contains(mx, my)) {
			return -1;
		}
		int i = first + (int) Math.floor((my - list.y()) / ROW_H);
		int last = Math.min(count, first + capacity(list)) - 1;
		return i >= first && i <= last ? i : -1;
	}

	/** Whole rows that fit in the list. */
	public static int capacity(Rect list) {
		return Math.max(1, list.h() / ROW_H);
	}

	/** Scroll (first item) clamped to what exists. */
	public static int clampFirst(int first, int count, Rect list) {
		return Math.max(0, Math.min(first, Math.max(0, count - capacity(list))));
	}

	/** Scroll that keeps item {@code index} visible (unchanged when it already is). */
	public static int reveal(int first, int index, int count, Rect list) {
		if (index < 0) {
			return clampFirst(first, count, list);
		}
		int cap = capacity(list);
		int f = first;
		if (index < f) {
			f = index;
		} else if (index >= f + cap) {
			f = index - cap + 1;
		}
		return clampFirst(f, count, list);
	}
}
