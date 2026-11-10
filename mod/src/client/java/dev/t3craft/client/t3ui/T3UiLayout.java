package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.Rect;

/**
 * Where everything of the unified T3 screen sits, in scaled GUI pixels (so it follows the window size
 * and the GUI scale). Pure Java.
 *
 * <p>Structure of T3 Code's main layout ({@code apps/web/src/components/AppSidebarLayout.tsx} +
 * {@code ChatView.tsx}): sidebar on the left, the thread view in the middle with its header
 * ({@code chat/ChatHeader.tsx}) on top, the conversation in the middle and the composer
 * ({@code chat/ChatComposer.tsx}) at the bottom, and (#39) an optional right panel (piece browser, later
 * diff, plan, queue) after it. The frame is centred, as large as the window minus {@value #MARGIN} px on
 * each side, at most {@code maxW}×{@code maxH} (config {@code layout.maxWidth} / {@code layout.maxHeight},
 * default {@value #MAX_W}×{@value #MAX_H}).
 *
 * <p>Below {@value #COMPACT_W}×{@value #COMPACT_H} the screen is <b>compact</b>: one panel at a time (sidebar
 * or thread), like T3 Code's mobile sidebar sheet; {@code showSidebar} picks which, and the right panel is
 * hidden (the screen opens it as a full page instead). In the wide layout {@code showSidebar = false} hides
 * the sidebar (T3's {@code mod+b} "sidebar.toggle") and {@code showRight} shows the right panel.
 *
 * <p>Widths: {@code sidebarW} / {@code rightW} are the player's preferred widths (0 or less = the default:
 * 27 % of the frame for the sidebar, {@value #RIGHT_DEFAULT} px for the right panel). They are clamped to
 * {@value #SIDEBAR_MIN}..{@value #SIDEBAR_MAX} and {@value #RIGHT_MIN}..50 % of the frame, and shrunk (right
 * panel first) so the thread is never narrower than {@value #MAIN_MIN}; when even the minimums do not fit,
 * the right panel is left out. The preferences themselves are not changed: a wider window gives them back.
 * The splitters are the {@value #GAP} px gaps between the panels.
 *
 * @param frame        the whole screen area used
 * @param sidebar      sidebar panel (EMPTY when hidden, or in compact mode when the thread is shown)
 * @param main         thread panel (EMPTY in compact mode when the sidebar is shown)
 * @param right        right panel (EMPTY when closed, compact, or when it does not fit)
 * @param sidebarSplit the drag handle between sidebar and thread (EMPTY without a sidebar)
 * @param rightSplit   the drag handle between thread and right panel (EMPTY without a right panel)
 * @param compact      one panel at a time
 */
public record T3UiLayout(Rect frame, Rect sidebar, Rect main, Rect right, Rect sidebarSplit, Rect rightSplit, boolean compact) {
	/** Default cap of the frame; config {@code layout.maxWidth} changes it (was 900 before #39). */
	public static final int MAX_W = 1280;
	/** Default cap of the frame; config {@code layout.maxHeight} changes it (was 520 before #39). */
	public static final int MAX_H = 720;
	/** Smallest cap the config may set (the old fixed frame). */
	public static final int MAX_W_MIN = 900;
	public static final int MAX_H_MIN = 520;
	public static final int MARGIN = 6;
	/** Gap between panels; also the splitter's width. */
	public static final int GAP = 4;
	public static final int COMPACT_W = 330;
	public static final int COMPACT_H = 170;
	/** Narrowest sidebar (drag and default). */
	public static final int SIDEBAR_MIN = 120;
	/** Widest sidebar a drag may make. */
	public static final int SIDEBAR_MAX = 360;
	/** Widest default sidebar (27 % of the frame, capped here, as before #39). */
	public static final int SIDEBAR_DEFAULT_MAX = 210;
	public static final int RIGHT_MIN = 180;
	public static final int RIGHT_DEFAULT = 280;
	/** The thread panel never gets narrower than this while the sidebar or the right panel can give way. */
	public static final int MAIN_MIN = 220;

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

	/** No right panel, default widths (the layout before #39). */
	public static T3UiLayout compute(int guiW, int guiH, boolean showSidebar) {
		return compute(guiW, guiH, showSidebar, 0, 0, false);
	}

	/** With the default frame cap ({@value #MAX_W}×{@value #MAX_H}). */
	public static T3UiLayout compute(int guiW, int guiH, boolean showSidebar, int sidebarW, int rightW, boolean showRight) {
		return compute(guiW, guiH, showSidebar, sidebarW, rightW, showRight, MAX_W, MAX_H);
	}

	/**
	 * @param sidebarW preferred sidebar width, 0 or less = default
	 * @param rightW   preferred right panel width, 0 or less = default
	 * @param maxW     frame cap (config), never under {@value #MAX_W_MIN}
	 * @param maxH     frame cap (config), never under {@value #MAX_H_MIN}
	 */
	public static T3UiLayout compute(int guiW, int guiH, boolean showSidebar, int sidebarW, int rightW, boolean showRight, int maxW, int maxH) {
		int sw = Math.max(0, guiW);
		int sh = Math.max(0, guiH);
		int m = Math.max(0, Math.min(MARGIN, Math.min(sw, sh) / 8));
		int w = Math.max(0, Math.min(Math.max(MAX_W_MIN, maxW), sw - 2 * m));
		int h = Math.max(0, Math.min(Math.max(MAX_H_MIN, maxH), sh - 2 * m));
		Rect frame = new Rect((sw - w) / 2, (sh - h) / 2, w, h);
		boolean compact = w < COMPACT_W || h < COMPACT_H;
		if (compact) {
			return showSidebar ? new T3UiLayout(frame, frame, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, true)
					: new T3UiLayout(frame, Rect.EMPTY, frame, Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, true);
		}
		int side = showSidebar ? clampSidebar(sidebarW > 0 ? sidebarW : sidebarWidth(w)) : 0;
		int right = showRight ? clampRight(rightW > 0 ? rightW : RIGHT_DEFAULT, w) : 0;
		// the thread keeps MAIN_MIN: the right panel gives way first, then the sidebar, then the right panel goes
		int excess = MAIN_MIN - mainWidth(w, side, right);
		if (excess > 0 && right > 0) {
			int give = Math.min(excess, right - RIGHT_MIN);
			right -= Math.max(0, give);
			excess -= Math.max(0, give);
		}
		if (excess > 0 && side > 0) {
			int give = Math.min(excess, side - SIDEBAR_MIN);
			side -= Math.max(0, give);
			excess -= Math.max(0, give);
		}
		if (excess > 0 && right > 0) {
			right = 0;
		}
		int x = frame.x();
		Rect sidebar = Rect.EMPTY;
		Rect sidebarSplit = Rect.EMPTY;
		if (side > 0) {
			sidebar = new Rect(x, frame.y(), side, h);
			sidebarSplit = new Rect(sidebar.right(), frame.y(), GAP, h);
			x = sidebarSplit.right();
		}
		int mw = Math.max(0, frame.right() - x - (right > 0 ? right + GAP : 0));
		Rect main = new Rect(x, frame.y(), mw, h);
		Rect rightR = Rect.EMPTY;
		Rect rightSplit = Rect.EMPTY;
		if (right > 0) {
			rightSplit = new Rect(main.right(), frame.y(), GAP, h);
			rightR = new Rect(rightSplit.right(), frame.y(), frame.right() - rightSplit.right(), h);
		}
		return new T3UiLayout(frame, sidebar, main, rightR, sidebarSplit, rightSplit, false);
	}

	private static int mainWidth(int frameW, int side, int right) {
		return frameW - (side > 0 ? side + GAP : 0) - (right > 0 ? right + GAP : 0);
	}

	/** A sidebar width within {@value #SIDEBAR_MIN}..{@value #SIDEBAR_MAX}. */
	public static int clampSidebar(int want) {
		return Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, want));
	}

	/** A right panel width within {@value #RIGHT_MIN}..half of a frame {@code frameW} wide. */
	public static int clampRight(int want, int frameW) {
		return Math.max(RIGHT_MIN, Math.min(Math.max(RIGHT_MIN, frameW / 2), want));
	}

	/**
	 * Sidebar width while its splitter is dragged to {@code mouseX}: clamped, and never so wide that the
	 * thread gets under {@value #MAIN_MIN} next to a right panel {@code rightW} wide (0 = none shown).
	 */
	public static int dragSidebar(Rect frame, double mouseX, int rightW) {
		int want = (int) Math.round(mouseX - frame.x() - GAP / 2.0);
		int room = frame.w() - MAIN_MIN - GAP - (rightW > 0 ? rightW + GAP : 0);
		return clampSidebar(Math.min(want, Math.max(SIDEBAR_MIN, room)));
	}

	/** Right panel width while its splitter is dragged to {@code mouseX}, next to a sidebar {@code sidebarW} wide (0 = none). */
	public static int dragRight(Rect frame, double mouseX, int sidebarW) {
		int want = (int) Math.round(frame.right() - mouseX - GAP / 2.0);
		int room = frame.w() - MAIN_MIN - GAP - (sidebarW > 0 ? sidebarW + GAP : 0);
		return clampRight(Math.min(want, Math.max(RIGHT_MIN, room)), frame.w());
	}

	/**
	 * Which panel a compact screen starts on: the thread when it was opened on one (a block click,
	 * an NPC, a search hit), the sidebar otherwise (the T3 key).
	 */
	public static boolean startsOnSidebar(@org.jspecify.annotations.Nullable String key) {
		return key == null;
	}

	/** Default sidebar width for a frame {@code w} wide: 27 %, between {@value #SIDEBAR_MIN} and {@value #SIDEBAR_DEFAULT_MAX}. */
	public static int sidebarWidth(int w) {
		return Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_DEFAULT_MAX, w * 27 / 100));
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
