package dev.t3craft.client.t3ui.sidebar;

import dev.t3craft.client.chat.Ago;
import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.ThreadDetails;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.t3ui.SidebarModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One thread row of the T3 sidebar as T3 Code draws it (feat/t3-sidebar-rows), in the game's font:
 *
 * <pre>
 *  [P] ★ project ..................... ◌ Working 4m     ← line 1, muted: icon, pin, project, state/age
 *  Title of the thread, truncated…               ✳ •   ← line 2: title (icons here without line 3)
 *  ⎇ feat/branch  +141 −12  #87                  ✳ •   ← line 3, only with branch/diff/PR
 * </pre>
 *
 * The state on line 1 is the spinner + "Working 4m" while it runs, "Needs input" (attention colour,
 * only for {@link Attention#NEEDS_YOU}), "Approval", "Failed", else the age. On hover the right of
 * line 1 holds the quick actions instead (the view draws them). Stateless apart from the shared
 * {@link ClipCache}.
 */
final class SidebarRow {
	static final int LINE = 9;
	static final int H2 = 2 + 2 * LINE + 3;
	static final int H3 = H2 + LINE;
	/** Room the hover quick actions take on line 1. */
	static final int QUICK_W = 2 * 11 + 2;

	private SidebarRow() {
	}

	static int height(ThreadRow r) {
		return r.details().hasLine3() ? H3 : H2;
	}

	/** Filled box with cut corners and a matching 1 px rim (reads as rounded at GUI scale). */
	static void roundBox(GuiGraphicsExtractor g, Rect r, int fill, int rim) {
		ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), fill);
		if (rim != 0 && r.w() > 4 && r.h() > 4) {
			g.fill(r.x() + 2, r.y(), r.right() - 2, r.y() + 1, rim);
			g.fill(r.x() + 2, r.bottom() - 1, r.right() - 2, r.bottom(), rim);
			g.fill(r.x(), r.y() + 2, r.x() + 1, r.bottom() - 2, rim);
			g.fill(r.right() - 1, r.y() + 2, r.right(), r.bottom() - 2, rim);
			g.fill(r.x() + 1, r.y() + 1, r.x() + 2, r.y() + 2, rim);
			g.fill(r.right() - 2, r.y() + 1, r.right() - 1, r.y() + 2, rim);
			g.fill(r.x() + 1, r.bottom() - 2, r.x() + 2, r.bottom() - 1, rim);
			g.fill(r.right() - 2, r.bottom() - 2, r.right() - 1, r.bottom() - 1, rim);
		}
	}

	/**
	 * Draws {@code r} in {@code rr}.
	 *
	 * @param multiEnv  threads of several environments are listed: the icon is the machine's
	 * @param quick     hover quick actions shown: leave the right of line 1 free
	 */
	static void draw(GuiGraphicsExtractor g, Font font, ChatTheme t, ClipCache clips, Rect rr, ThreadRow r, boolean selected, boolean hover,
			boolean multiEnv, boolean quick, long now) {
		draw(g, font, t, clips, rr, r, selected, hover, multiEnv, quick, now, SidebarModel.Nest.NONE);
	}

	/**
	 * feat/thread-lineage: {@code nest} adds the lineage bits. A delegated child shows a "delegated"
	 * mark on line 1 (an orphaned one, whose parent is not listed, shows {@code ↳ parent: <title>}
	 * there instead of the project); a parent shows the {@code ▾ N agents} counter at the right of
	 * the title. Returns the counter's rectangle (the view makes it fold the children), or
	 * {@link Rect#EMPTY} when there is none.
	 */
	static Rect draw(GuiGraphicsExtractor g, Font font, ChatTheme t, ClipCache clips, Rect rr, ThreadRow r, boolean selected, boolean hover,
			boolean multiEnv, boolean quick, long now, SidebarModel.Nest nest) {
		if (selected) {
			// T3: the selected row is a lighter rounded card
			roundBox(g, rr, t.panelHi(), ChatTheme.withAlpha(t.edge(), 200));
		} else if (hover) {
			roundBox(g, rr, t.hover(), 0);
		}
		ThreadDetails d = r.details();
		int x0 = rr.x() + 4;
		int x1 = rr.right() - 3;
		int y1 = rr.y() + 3;
		int y2 = y1 + LINE + 1;
		int y3 = y2 + LINE;
		int dim = t.muted();

		// ---- line 1: icon, pin, project ........ state / age
		EnvBadge badge = multiEnv ? EnvBadge.of(r.env(), r.envLabel()) : EnvBadge.of("project:" + SidebarModel.projectKey(r), projectLabel(r));
		ChatDraw.envIcon(g, font, badge, x0, y1 - 1, 8);
		int x = x0 + 11;
		if (r.pinned()) {
			g.text(font, "★", x, y1, t.brass(), false);
			x += clips.width(font, "★") + 3;
		}
		int right = x1;
		if (quick) {
			right -= QUICK_W;
		} else {
			right -= drawState(g, font, t, clips, r, x1, y1, Math.max(0, (x1 - x) / 2), now);
		}
		if (nest.orphan()) {
			// feat/thread-lineage: parent not listed here, say whose task it is
			String p = nest.parentTitle().isBlank() ? "↳ parent" : "↳ parent: " + nest.parentTitle();
			g.text(font, clips.clip(font, p, right - x - 3), x, y1, t.accent(), false);
		} else if (nest.delegated()) {
			String mark = DELEGATED_MARK;
			int mw = clips.width(font, mark);
			if (x + mw < right - 3) {
				g.text(font, mark, x, y1, t.accent(), false);
				x += mw + 4;
			}
			g.text(font, clips.clip(font, projectLabel(r), right - x - 3), x, y1, dim, false);
		} else {
			g.text(font, clips.clip(font, projectLabel(r), right - x - 3), x, y1, dim, false);
		}

		// ---- icons (provider, unread): on line 3 when there is one, else on line 2
		boolean line3 = d.hasLine3();
		int iconsW = icons(g, font, t, clips, d, x1, line3 ? y3 : y2);

		// ---- line 2: title (and the children counter of a parent at its right)
		int titleRight = x1 - (line3 ? 0 : iconsW);
		Rect chip = Rect.EMPTY;
		if (nest.children() > 0) {
			String label = childrenLabel(nest);
			int cw = clips.width(font, label) + 4;
			int cx = titleRight - cw;
			if (cx > x0 + 24) {
				ChatDraw.round(g, cx, y2 - 1, cw, LINE + 1, ChatTheme.withAlpha(t.edge(), 110));
				g.text(font, label, cx + 2, y2, nest.working() > 0 ? t.working() : t.muted(), false);
				chip = new Rect(cx, y2 - 1, cw, LINE + 1);
				titleRight = cx - 3;
			}
		}
		int titleColor = r.settled() && !selected ? t.muted() : t.text();
		g.text(font, clips.clip(font, r.title(), titleRight - x0), x0, y2, titleColor, t.shadow());

		// ---- line 3: worktree/branch, diff, PR
		if (line3) {
			int lx = x0;
			int limit = x1 - iconsW;
			String tail = tail(d);
			int tailW = clips.width(font, tail);
			if (d.branch() != null || d.worktree()) {
				String icon = "⎇";
				g.text(font, icon, lx, y3, d.worktree() ? t.accent() : dim, false);
				lx += clips.width(font, icon) + 2;
				String branch = d.branch() != null ? d.branch() : "worktree";
				String b = clips.clip(font, branch, limit - lx - (tailW > 0 ? tailW + 4 : 0));
				g.text(font, b, lx, y3, dim, false);
				lx += clips.width(font, b) + 4;
			}
			if (d.additions() >= 0 && lx < limit) {
				String a = "+" + d.additions();
				g.text(font, a, lx, y3, t.done(), false);
				lx += clips.width(font, a) + 2;
			}
			if (d.deletions() >= 0 && lx < limit) {
				String m = "−" + d.deletions();
				g.text(font, m, lx, y3, t.error(), false);
				lx += clips.width(font, m) + 4;
			}
			if (d.prNumber() > 0 && lx < limit) {
				String pr = "#" + d.prNumber();
				g.text(font, clips.clip(font, pr, limit - lx), lx, y3, prColor(t, d.prState()), false);
			}
		}
		return chip;
	}

	/** Line 1 mark of a nested delegated task. */
	static final String DELEGATED_MARK = "⤷ delegated";

	/** {@code ▾ 2 agents} (open) / {@code › 2 agents} (folded). */
	static String childrenLabel(SidebarModel.Nest n) {
		return (n.collapsed() ? "› " : "▾ ") + n.children() + (n.children() == 1 ? " agent" : " agents");
	}

	/**
	 * Draws the draft thread's row (feat/t3-composer): line 1 like a thread's (icon, project) with
	 * "Draft" at the right (the view puts the ✕ there on hover), line 2 "✎ New thread" (or
	 * "Starting…" while it launches).
	 */
	static void drawDraft(GuiGraphicsExtractor g, Font font, ChatTheme t, ClipCache clips, Rect rr, ThreadRow r, boolean selected, boolean hover,
			boolean multiEnv) {
		if (selected) {
			roundBox(g, rr, t.panelHi(), ChatTheme.withAlpha(t.edge(), 200));
		} else if (hover) {
			roundBox(g, rr, t.hover(), 0);
		}
		int x0 = rr.x() + 4;
		int x1 = rr.right() - 3;
		int y1 = rr.y() + 3;
		int y2 = y1 + LINE + 1;
		EnvBadge badge = multiEnv ? EnvBadge.of(r.env(), r.envLabel()) : EnvBadge.of("project:" + SidebarModel.projectKey(r), projectLabel(r));
		ChatDraw.envIcon(g, font, badge, x0, y1 - 1, 8);
		int x = x0 + 11;
		int right = x1;
		if (hover) {
			right -= 14;
		} else {
			String tag = "Draft";
			int tw = clips.width(font, tag);
			g.text(font, tag, x1 - tw, y1, t.accent(), false);
			right -= tw + 4;
		}
		g.text(font, clips.clip(font, projectLabel(r), right - x - 3), x, y1, t.muted(), false);
		String pen = "✎";
		g.text(font, pen, x0, y2, t.accent(), false);
		int tx = x0 + clips.width(font, pen) + 3;
		g.text(font, clips.clip(font, r.title(), x1 - tx), tx, y2, selected ? t.text() : t.muted(), t.shadow());
	}

	/** Diff and PR text (to keep room for it when clipping the branch). */
	private static String tail(ThreadDetails d) {
		StringBuilder sb = new StringBuilder();
		if (d.additions() >= 0) {
			sb.append('+').append(d.additions()).append("  ");
		}
		if (d.deletions() >= 0) {
			sb.append('−').append(d.deletions()).append("  ");
		}
		if (d.prNumber() > 0) {
			sb.append('#').append(d.prNumber());
		}
		return sb.toString().strip();
	}

	static int prColor(ChatTheme t, String state) {
		if (state == null) {
			return t.link();
		}
		return switch (state) {
			case "merged" -> 0xFF8A5CC7;
			case "closed" -> t.error();
			case "draft" -> t.muted();
			default -> t.done();
		};
	}

	/** Provider glyph and the unread dot at the right of a line; returns the width used. */
	private static int icons(GuiGraphicsExtractor g, Font font, ChatTheme t, ClipCache clips, ThreadDetails d, int right, int y) {
		int x = right;
		if (d.unread()) {
			x -= 4;
			ChatDraw.round(g, x, y + 2, 4, 4, t.accent());
			x -= 3;
		}
		String glyph = providerGlyph(d.provider());
		if (glyph != null) {
			int w = clips.width(font, glyph);
			x -= w;
			g.text(font, glyph, x, y, providerColor(t, d.provider()), false);
			x -= 3;
		}
		return right - x;
	}

	static String providerGlyph(String provider) {
		if (provider == null || provider.isBlank()) {
			return null;
		}
		String p = provider.strip().toLowerCase(Locale.ROOT);
		if (p.contains("claude")) {
			return "✳";
		}
		if (p.contains("codex") || p.contains("openai") || p.contains("gpt")) {
			return "◎";
		}
		if (p.contains("gemini")) {
			return "✦";
		}
		return p.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	static int providerColor(ChatTheme t, String provider) {
		String p = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
		if (p.contains("claude")) {
			return 0xFFD97757;
		}
		if (p.contains("codex") || p.contains("openai") || p.contains("gpt")) {
			return 0xFF3C74B3;
		}
		return t.muted();
	}

	/** State or age right-aligned at {@code right} on line 1; returns the width used (gap included). */
	private static int drawState(GuiGraphicsExtractor g, Font font, ChatTheme t, ClipCache clips, ThreadRow r, int right, int y, int maxW, long now) {
		Attention a = r.attention();
		if (SidebarModel.working(r) && a != Attention.NEEDS_YOU) {
			String el = WorkingStatus.elapsedShort(now, WorkingStatus.runStart(r));
			String s = el.isEmpty() ? "Working" : "Working " + el;
			int tw = clips.width(font, s);
			if (Spinner.SIZE + 3 + tw > maxW) {
				s = el; // narrow sidebar: spinner + time
				tw = clips.width(font, s);
			}
			int w = Spinner.SIZE + (tw > 0 ? 3 + tw : 0);
			int x = right - w;
			Spinner.draw(g, x, y, t.working());
			if (tw > 0) {
				g.text(font, s, x + Spinner.SIZE + 3, y, t.working(), false);
			}
			return w + 4;
		}
		String s;
		int c;
		switch (a) {
			case NEEDS_YOU -> {
				s = "Needs input";
				c = t.needsYou();
			}
			case PENDING -> {
				s = "Approval";
				c = t.pending();
			}
			case ERROR -> {
				s = "Failed";
				c = t.error();
			}
			default -> {
				s = Ago.format(now, r.activityMs());
				c = t.muted();
			}
		}
		if (s.isEmpty()) {
			return 0;
		}
		s = clips.clip(font, s, Math.max(maxW, clips.width(font, "Failed")));
		int tw = clips.width(font, s);
		int x = right - tw;
		if (a == Attention.NEEDS_YOU) {
			// a soft pulse under the words, nothing louder (T3's attention tone)
			ChatDraw.round(g, x - 2, y - 1, tw + 4, LINE + 1, ChatTheme.withAlpha(t.needsYou(), (int) (20 + 40 * ChatDraw.pulse())));
		}
		g.text(font, s, x, y, c, false);
		return tw + 4;
	}

	static String projectLabel(ThreadRow r) {
		if (r.project() != null && !r.project().isBlank()) {
			return r.project();
		}
		String repo = r.details().prRepository();
		if (repo != null) {
			return repo;
		}
		return r.envLabel() == null || r.envLabel().isBlank() ? r.env() : r.envLabel();
	}

	/** Tooltip lines of a row. */
	static List<String> tooltip(ThreadRow r, long now, boolean actions) {
		List<String> tip = new ArrayList<>();
		tip.add(r.title());
		String phrase = Ago.phrase(now, r.activityMs());
		String state = SidebarModel.working(r) ? "working " + WorkingStatus.elapsedLong(now, WorkingStatus.runStart(r)) : r.attention().word;
		tip.add((r.pinned() ? "pinned · " : "") + state + " · " + r.envLabel() + (r.project().isEmpty() ? "" : " · " + r.project())
				+ (phrase.isEmpty() ? "" : " · " + phrase));
		ThreadDetails d = r.details();
		List<String> bits = new ArrayList<>();
		if (d.branch() != null) {
			bits.add((d.worktree() ? "worktree " : "branch ") + d.branch());
		} else if (d.worktree()) {
			bits.add("worktree");
		}
		if (d.prNumber() > 0) {
			bits.add("PR " + (d.prRepository() == null ? "" : d.prRepository()) + "#" + d.prNumber() + (d.prState() == null ? "" : " " + d.prState()));
		}
		if (d.additions() >= 0 || d.deletions() >= 0) {
			bits.add("+" + Math.max(0, d.additions()) + " −" + Math.max(0, d.deletions()));
		}
		if (d.model() != null) {
			bits.add(d.model());
		}
		if (d.unread()) {
			bits.add("unread");
		}
		if (d.snoozed(now)) {
			bits.add("snoozed, " + WorkingStatus.elapsedLong(d.snoozedUntilMs(), now) + " left");
		}
		if (!bits.isEmpty()) {
			tip.add(String.join(" · ", bits));
		}
		if (r.lineage().delegated()) {
			// feat/thread-lineage
			String p = r.lineage().parentTitle();
			tip.add("Delegated task" + (p.isBlank() ? "" : " · parent: " + p));
		}
		if (r.activity() != null && !r.activity().isBlank()) {
			tip.add(r.activity());
		}
		if (actions) {
			tip.add("Right-click: thread actions");
		}
		return tip;
	}
}
