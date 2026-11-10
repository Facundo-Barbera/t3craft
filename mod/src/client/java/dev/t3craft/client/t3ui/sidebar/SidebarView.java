package dev.t3craft.client.t3ui.sidebar;

import dev.t3craft.client.chat.Attention;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.t3ui.SidebarModel;
import dev.t3craft.client.t3ui.T3Slots;
import dev.t3craft.client.t3ui.kit.T3Icon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The thread list of the T3 screen's sidebar (feat/t3-sidebar-rows), T3 Code's structure in the
 * game's font and paper kit. Draws inside the list rectangle the screen gives it:
 *
 * <ul>
 *   <li><b>Top</b>, scrolling on its own: Pinned and the project folders of {@link SidebarModel#items()},
 *       with rich 2–3 line rows ({@link SidebarRow}).</li>
 *   <li><b>Dock</b>, stuck to the bottom just above the footer icons: {@link SidebarModel#dock()}
 *       (Working with its count, open; Snoozed and Settled closed, with a right chevron). An open section with
 *       many threads scrolls on its own inside a capped height; the dock never takes more than
 *       {@value #DOCK_MAX_PCT}% of the list.</li>
 * </ul>
 *
 * <p>Cheap with hundreds of threads: item offsets are summed once per model rebuild (the model hands
 * a new list only then), a binary search finds the first visible row and only visible rows are drawn
 * and hit-tested; truncated texts come from a {@link ClipCache}. Pixel scrolling, scissored.
 *
 * <p>The screen keeps the actions: a {@link Host} opens a thread, prewarms on hover, runs the hover
 * quick actions (settle, pin) and opens the right-click menu (feat/t3-ui-clean). feat/t3-composer:
 * the draft thread ({@link Host#draftKey()}) is a rich row "New thread" under its project with an x
 * on hover, and hovering a project folder shows its plus (a new draft thread there). Client thread only.
 *
 * <p>#27: every glyph is a {@link T3Icon} (chevrons of folders and dock sections, plus, check, star, x):
 * muted at rest, accent under the pointer, disabled colour when it cannot be used; the pin star is brass
 * while the thread is pinned. Hit boxes and tooltips are the same as with the old glyphs.
 */
public final class SidebarView {
	/** Folder / Pinned header row in the top list. */
	static final int HEADER_H = 12;
	/** Dock section header. */
	static final int DOCK_HEAD_H = 13;
	static final int DOCK_MAX_PCT = 55;
	/** The top list keeps at least this much when the dock is full. */
	static final int MAIN_MIN = 34;
	static final int WHEEL_PX = 24;
	static final int SCROLLBAR_W = 7;
	/** Folder and dock chevrons (#27). */
	static final int CHEVRON = 8;
	/** Hover quick-action icons (settle, pin, plus, x) inside their 11 px boxes. */
	static final int QUICK_ICON = 9;

	/** What the screen does for the sidebar. */
	public interface Host {
		/** A menu is open over the screen: no hover effects. */
		boolean menuOpen();

		/** Left click on a row: select and show it. */
		void open(ThreadRow r);

		/** The pointer rests on a row (hover prewarm). */
		void hover(ThreadRow r, boolean selected);

		/** The row's thread as an action target, or null (bridge row, read-only screen). */
		T3Slots.@Nullable Target target(ThreadRow r);

		boolean canOperate(T3Slots.Target t);

		void settle(T3Slots.Target t);

		void pin(T3Slots.Target t, boolean pin);

		/** Right click on a row: its thread actions menu. */
		void context(ThreadRow r, T3Slots.Target t);

		/** Key of the draft thread's row (feat/t3-composer), or null when there is no draft. */
		@Nullable String draftKey();

		/** The ✕ on the draft row: drop the draft. */
		void discardDraft();

		/** New threads can be started from here (not a read-only screen): the folders' "+". */
		boolean canNewThread();

		/** The "+" on a project folder: a draft thread in that project ({@code folder} = its key). */
		void newThreadIn(String folder);
	}

	private record Hit(Rect r, Runnable left, @Nullable Runnable right, @Nullable List<String> tip) {
	}

	private final SidebarModel model;
	private final Host host;
	private final ClipCache clips = new ClipCache();
	private final List<Hit> hits = new ArrayList<>();

	private int mainOffset;
	private Rect mainRect = Rect.EMPTY;
	private final Map<String, Integer> dockOffsets = new HashMap<>();
	private final Map<String, Rect> dockBodies = new HashMap<>();
	private final Set<String> envs = new HashSet<>();

	/** Item tops of the top list, summed once per model rebuild. */
	private @Nullable List<SidebarModel.Item> laidOut;
	private int[] tops = new int[1];
	private @Nullable List<ThreadRow> envsOf;

	public SidebarView(SidebarModel model, Host host) {
		this.model = model;
		this.host = host;
	}

	// ---- layout ---------------------------------------------------------------------------------

	private static int itemH(SidebarModel.Item it) {
		return it.kind() == SidebarModel.Kind.THREAD && it.row() != null ? SidebarRow.height(it.row()) : HEADER_H;
	}

	private static int[] sum(List<SidebarModel.Item> items) {
		int[] t = new int[items.size() + 1];
		for (int i = 0; i < items.size(); i++) {
			t[i + 1] = t[i] + itemH(items.get(i));
		}
		return t;
	}

	private int[] mainTops() {
		List<SidebarModel.Item> items = model.items();
		if (items != laidOut) {
			laidOut = items;
			tops = sum(items);
		}
		return tops;
	}

	/** First index whose bottom is below {@code offset}. */
	private static int firstVisible(int[] tops, int offset) {
		int lo = 0;
		int hi = tops.length - 2;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (tops[mid + 1] <= offset) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return Math.max(0, lo);
	}

	private boolean multiEnv() {
		List<ThreadRow> all = model.all();
		if (all != envsOf) {
			envsOf = all;
			envs.clear();
			all.forEach(r -> envs.add(r.env()));
		}
		return envs.size() > 1;
	}

	// ---- scrolling ------------------------------------------------------------------------------

	public void scrollToTop() {
		mainOffset = 0;
	}

	/** Scrolls whichever part lists {@code key} so its row is visible. */
	public void reveal(@Nullable String key) {
		if (key == null) {
			return;
		}
		List<SidebarModel.Item> items = model.items();
		int[] t = mainTops();
		for (int i = 0; i < items.size(); i++) {
			SidebarModel.Item it = items.get(i);
			if (it.kind() == SidebarModel.Kind.THREAD && key.equals(it.key())) {
				mainOffset = revealIn(mainOffset, t[i], t[i + 1], mainRect.h());
				return;
			}
		}
		for (SidebarModel.Dock d : model.dock()) {
			int y = 0;
			for (SidebarModel.Item it : d.rows()) {
				int h = itemH(it);
				if (key.equals(it.key())) {
					Rect body = dockBodies.get(d.key());
					int view = body == null ? 0 : body.h();
					dockOffsets.put(d.key(), revealIn(dockOffsets.getOrDefault(d.key(), 0), y, y + h, view));
					return;
				}
				y += h;
			}
		}
	}

	private static int revealIn(int offset, int top, int bottom, int view) {
		if (view <= 0) {
			return offset;
		}
		if (top < offset) {
			return top;
		}
		if (bottom > offset + view) {
			return bottom - view;
		}
		return offset;
	}

	public boolean mouseScrolled(double mx, double my, double dy) {
		if (dy == 0) {
			return false;
		}
		int step = dy > 0 ? -WHEEL_PX : WHEEL_PX;
		for (Map.Entry<String, Rect> e : dockBodies.entrySet()) {
			if (e.getValue().contains(mx, my)) {
				dockOffsets.merge(e.getKey(), step, Integer::sum);
				return true; // clamped on the next frame
			}
		}
		if (mainRect.contains(mx, my)) {
			mainOffset += step;
			return true;
		}
		return false;
	}

	// ---- input ----------------------------------------------------------------------------------

	/** Handles a click on the list (rows, headers, quick actions); false when it was elsewhere. */
	public boolean mouseClicked(double mx, double my, int button) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.r().contains(mx, my)) {
				if (MouseButtons.isRight(button)) {
					if (h.right() != null) {
						h.right().run();
					}
				} else {
					h.left().run();
				}
				return true;
			}
		}
		return false;
	}

	public @Nullable List<String> tooltip(double mx, double my) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.tip() != null && h.r().contains(mx, my)) {
				return h.tip();
			}
		}
		return null;
	}

	// ---- drawing --------------------------------------------------------------------------------

	public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect list, int mouseX, int mouseY) {
		hits.clear();
		dockBodies.clear();
		mainRect = Rect.EMPTY;
		if (list.h() <= 0 || list.w() <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		model.expireSnoozes(now);
		ChatDraw.inset(g, t, list);
		boolean hoverOk = !host.menuOpen();
		boolean multi = multiEnv();

		// ---- dock heights: headers always, open bodies share what is left of the cap
		List<SidebarModel.Dock> docks = model.dock();
		int[] bodyH = new int[docks.size()];
		int dockH = 0;
		if (!docks.isEmpty()) {
			int headers = docks.size() * DOCK_HEAD_H;
			int cap = Math.min(Math.max(headers, list.h() * DOCK_MAX_PCT / 100), Math.max(headers, list.h() - MAIN_MIN));
			int left = Math.max(0, cap - headers);
			List<Integer> open = new ArrayList<>();
			int[] content = new int[docks.size()];
			for (int i = 0; i < docks.size(); i++) {
				SidebarModel.Dock d = docks.get(i);
				if (!d.collapsed() && !d.rows().isEmpty()) {
					int c = 2;
					for (SidebarModel.Item it : d.rows()) {
						c += itemH(it);
					}
					content[i] = c;
					open.add(i);
				}
			}
			open.sort((a, b) -> Integer.compare(content[a], content[b]));
			for (int n = 0; n < open.size(); n++) {
				int i = open.get(n);
				int share = left / (open.size() - n);
				bodyH[i] = Math.min(content[i], share);
				left -= bodyH[i];
			}
			dockH = 3 + headers;
			for (int h : bodyH) {
				dockH += h;
			}
			dockH = Math.min(dockH, list.h());
		}

		Rect main = new Rect(list.x(), list.y(), list.w(), list.h() - dockH);
		mainRect = main;
		drawMain(g, font, t, main, mouseX, mouseY, hoverOk, multi, now);

		if (!docks.isEmpty()) {
			int y = main.bottom();
			g.fill(list.x() + 2, y + 1, list.right() - 2, y + 2, t.edge());
			y += 3;
			for (int i = 0; i < docks.size(); i++) {
				SidebarModel.Dock d = docks.get(i);
				if (y + DOCK_HEAD_H > list.bottom()) {
					break;
				}
				Rect head = new Rect(list.x() + 1, y, list.w() - 2, DOCK_HEAD_H);
				drawDockHeader(g, font, t, head, d, hoverOk && head.contains(mouseX, mouseY));
				String k = d.key();
				hits.add(new Hit(head, () -> model.toggleCollapsed(k), null,
						List.of(d.label() + " · " + d.count() + (d.count() == 1 ? " thread" : " threads"), "Click: open/close")));
				y += DOCK_HEAD_H;
				if (bodyH[i] > 0) {
					Rect body = new Rect(list.x(), y, list.w(), Math.min(bodyH[i], list.bottom() - y));
					dockBodies.put(k, body);
					drawDockBody(g, font, t, body, d, mouseX, mouseY, hoverOk, multi, now);
					y += body.h();
				}
			}
		}
	}

	private void drawMain(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect area, int mouseX, int mouseY, boolean hoverOk, boolean multi,
			long now) {
		if (area.h() <= 4) {
			return;
		}
		List<SidebarModel.Item> items = model.items();
		if (items.isEmpty()) {
			String msg = model.all().isEmpty() ? "No threads yet."
					: model.searching() ? "No threads match \"" + model.query() + "\"."
					: !model.dock().isEmpty() ? "Nothing else active." : "No threads in this project.";
			int ly = area.y() + 4;
			for (FormattedCharSequence line : font.split(Component.literal(msg), Math.max(20, area.w() - 8))) {
				if (ly + font.lineHeight > area.bottom()) {
					break;
				}
				g.text(font, line, area.x() + 4, ly, t.muted(), t.shadow());
				ly += font.lineHeight;
			}
			return;
		}
		int[] tp = mainTops();
		int content = tp[items.size()] + 2;
		int view = area.h() - 2;
		mainOffset = Math.max(0, Math.min(mainOffset, Math.max(0, content - view)));
		boolean bar = content > view;
		int rx0 = area.x() + 2;
		int rx1 = area.right() - (bar ? SCROLLBAR_W + 2 : 2);
		int top = area.y() + 1 - mainOffset;
		g.enableScissor(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1);
		for (int i = firstVisible(tp, mainOffset); i < items.size(); i++) {
			int y = top + tp[i];
			if (y >= area.bottom()) {
				break;
			}
			SidebarModel.Item it = items.get(i);
			Rect rr = new Rect(rx0, y, rx1 - rx0, tp[i + 1] - tp[i]);
			Rect hitR = clip(rr, area);
			boolean hover = hoverOk && hitR.contains(mouseX, mouseY);
			if (it.kind() == SidebarModel.Kind.THREAD) {
				drawThread(g, font, t, rr, hitR, it, hover, multi, mouseX, mouseY, now);
			} else {
				drawFolder(g, font, t, rr, hitR, it, hover, mouseX, mouseY);
			}
		}
		g.disableScissor();
		if (bar) {
			ChatDraw.scrollbar(g, t, area.right() - SCROLLBAR_W - 1, area.y() + 2, area.h() - 4, content, view, (double) mainOffset / Math.max(1, content - view),
					false);
		}
	}

	private void drawDockBody(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect body, SidebarModel.Dock d, int mouseX, int mouseY, boolean hoverOk,
			boolean multi, long now) {
		List<SidebarModel.Item> rows = d.rows();
		int content = 2;
		for (SidebarModel.Item it : rows) {
			content += itemH(it);
		}
		int view = body.h();
		int off = Math.max(0, Math.min(dockOffsets.getOrDefault(d.key(), 0), Math.max(0, content - view)));
		dockOffsets.put(d.key(), off);
		boolean bar = content > view;
		int rx0 = body.x() + 2;
		int rx1 = body.right() - (bar ? SCROLLBAR_W + 2 : 2);
		int y = body.y() - off;
		g.enableScissor(body.x() + 1, body.y(), body.right() - 1, body.bottom());
		for (SidebarModel.Item it : rows) {
			int h = itemH(it);
			if (y + h > body.y() && y < body.bottom()) {
				Rect rr = new Rect(rx0, y, rx1 - rx0, h);
				Rect hitR = clip(rr, body);
				boolean hover = hoverOk && hitR.contains(mouseX, mouseY);
				drawThread(g, font, t, rr, hitR, it, hover, multi, mouseX, mouseY, now);
			}
			if (y >= body.bottom()) {
				break;
			}
			y += h;
		}
		g.disableScissor();
		if (bar) {
			ChatDraw.scrollbar(g, t, body.right() - SCROLLBAR_W - 1, body.y() + 1, body.h() - 2, content, view, (double) off / Math.max(1, content - view), false);
		}
	}

	private static Rect clip(Rect r, Rect area) {
		int y0 = Math.max(r.y(), area.y());
		int y1 = Math.min(r.bottom(), area.bottom());
		return y1 <= y0 ? Rect.EMPTY : new Rect(r.x(), y0, r.w(), y1 - y0);
	}

	private void drawFolder(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect rr, Rect hitR, SidebarModel.Item it, boolean hover, int mouseX,
			int mouseY) {
		if (hover) {
			SidebarRow.roundBox(g, rr, t.hover(), 0);
		}
		int ty = rr.y() + 2;
		String count = Integer.toString(it.count());
		int right = rr.right() - 3 - clips.width(font, count);
		g.text(font, count, right, ty, t.muted(), false);
		if (it.attention() > 0) {
			ChatDraw.pulsingDot(g, t, ChatTheme.family(Attention.NEEDS_YOU), t.color(Attention.NEEDS_YOU), right - 9, rr.y() + 3);
			right -= 11;
		}
		boolean section = it.kind() == SidebarModel.Kind.SECTION;
		// T3: hovering a project shows its "new thread" button
		boolean plus = hover && !section && host.canNewThread() && !SidebarModel.NO_PROJECT.equals(it.key());
		Rect plusR = Rect.EMPTY;
		if (plus) {
			plusR = new Rect(right - 13, rr.y(), 11, rr.h());
			right = plusR.x() - 2;
		}
		int labelColor = section ? t.brass() : t.muted();
		T3Icon.draw(g, t, it.collapsed() ? T3Icon.CHEVRON_RIGHT : T3Icon.CHEVRON_DOWN, rr.x() + 2, rr.y() + (rr.h() - CHEVRON) / 2, CHEVRON, labelColor);
		int lx = rr.x() + 2 + CHEVRON + 2;
		g.text(font, clips.clip(font, it.label(), right - lx - 2), lx, ty, labelColor, false);
		String folder = it.key();
		List<String> tip = new ArrayList<>();
		tip.add(it.label() + " · " + it.count() + (it.count() == 1 ? " thread" : " threads"));
		if (!it.envs().isEmpty()) {
			tip.add(it.envs());
		}
		tip.add(section ? "Click: open/close" : "Click: open/close · Right-click: filter this project");
		hits.add(new Hit(hitR, () -> model.toggleCollapsed(folder),
				section ? null : () -> model.setProject(folder.equals(model.project()) ? null : folder), tip));
		if (plus && clip(plusR, hitR).h() > 0) {
			quickIcon(g, t, plusR, T3Icon.PLUS, true, 0, mouseX, mouseY);
			hits.add(new Hit(clip(plusR, hitR), () -> host.newThreadIn(folder), null, List.of("New thread in " + it.label())));
		}
	}

	private void drawDockHeader(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect head, SidebarModel.Dock d, boolean hover) {
		if (hover) {
			SidebarRow.roundBox(g, head, t.hover(), 0);
		}
		int ty = head.y() + (head.h() - 8) / 2;
		int right = head.right() - 3 - CHEVRON;
		T3Icon.draw(g, t, d.collapsed() ? T3Icon.CHEVRON_RIGHT : T3Icon.CHEVRON_DOWN, right, head.y() + (head.h() - CHEVRON) / 2, CHEVRON, t.muted());
		String count = Integer.toString(d.count());
		right -= 4 + clips.width(font, count);
		g.text(font, count, right, ty, t.muted(), false);
		if (d.attention() > 0) {
			ChatDraw.pulsingDot(g, t, ChatTheme.family(Attention.NEEDS_YOU), t.color(Attention.NEEDS_YOU), right - 10, head.y() + 3);
			right -= 12;
		}
		g.text(font, clips.clip(font, d.label(), right - head.x() - 6), head.x() + 3, ty, t.text(), false);
	}

	private void drawThread(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect rr, Rect hitR, SidebarModel.Item it, boolean hover, boolean multi,
			int mouseX, int mouseY, long now) {
		ThreadRow r = it.row();
		if (r == null) {
			return;
		}
		boolean selected = r.key().equals(model.selectedKey());
		if (r.key().equals(host.draftKey())) {
			drawDraft(g, font, t, rr, hitR, r, selected, hover, multi, mouseX, mouseY);
			return;
		}
		if (hover) {
			host.hover(r, selected);
		}
		T3Slots.Target target = host.target(r);
		boolean quick = hover && target != null;
		SidebarRow.draw(g, font, t, clips, rr, r, selected, hover, multi, quick, now);
		if (hitR.h() <= 0) {
			return;
		}
		hits.add(new Hit(hitR, () -> host.open(r), target == null ? null : () -> host.context(r, target), SidebarRow.tooltip(r, now, target != null)));
		if (quick) {
			// T3's hover actions where the state sits: settle, pin (feat/t3-ui-clean)
			boolean can = host.canOperate(target);
			int qy = rr.y() + 1;
			Rect pinR = new Rect(rr.right() - 14, qy, 11, SidebarRow.LINE + 2);
			Rect settleR = new Rect(pinR.x() - 11, qy, 11, SidebarRow.LINE + 2);
			boolean pinned = r.pinned();
			quickIcon(g, t, settleR, T3Icon.CHECK, can, 0, mouseX, mouseY);
			// one stroke sprite, so pinned vs not is the tint: brass pinned, muted not (accent under the pointer)
			quickIcon(g, t, pinR, T3Icon.STAR, can, pinned ? t.brass() : 0, mouseX, mouseY);
			String ro = can ? "" : " (needs control)";
			if (clip(settleR, hitR).h() > 0) {
				hits.add(new Hit(clip(settleR, hitR), () -> {
					if (can) {
						host.settle(target);
					}
				}, () -> host.context(r, target), List.of("Settle" + ro)));
				hits.add(new Hit(clip(pinR, hitR), () -> {
					if (can) {
						host.pin(target, !pinned);
					}
				}, () -> host.context(r, target), List.of((pinned ? "Unpin" : "Pin") + ro)));
			}
		}
	}

	/** The draft thread's row (feat/t3-composer): project line, pen + "New thread", an x on hover discards it. */
	private void drawDraft(GuiGraphicsExtractor g, Font font, ChatTheme t, Rect rr, Rect hitR, ThreadRow r, boolean selected, boolean hover,
			boolean multi, int mouseX, int mouseY) {
		SidebarRow.drawDraft(g, font, t, clips, rr, r, selected, hover, multi);
		if (hitR.h() <= 0) {
			return;
		}
		hits.add(new Hit(hitR, () -> host.open(r), null, List.of("New thread · " + SidebarRow.projectLabel(r), "Esc or the x discards it")));
		if (hover) {
			Rect xr = new Rect(rr.right() - 14, rr.y() + 1, 11, SidebarRow.LINE + 2);
			quickIcon(g, t, xr, T3Icon.X, true, 0, mouseX, mouseY);
			if (clip(xr, hitR).h() > 0) {
				hits.add(new Hit(clip(xr, hitR), host::discardDraft, null, List.of("Discard the draft")));
			}
		}
	}

	/**
	 * A hover quick action: the icon centred in {@code r} (which stays the hit box), muted at rest (or
	 * {@code rest} when its alpha is not 0, e.g. brass for a pinned star), accent under the pointer, disabled
	 * colour when it cannot be used.
	 */
	private static void quickIcon(GuiGraphicsExtractor g, ChatTheme t, Rect r, T3Icon icon, boolean enabled, int rest, int mouseX, int mouseY) {
		boolean hover = r.contains(mouseX, mouseY);
		if (hover && enabled) {
			ChatDraw.round(g, r.x(), r.y(), r.w(), r.h(), t.panel());
		}
		int size = Math.min(QUICK_ICON, Math.min(r.w(), r.h()));
		int tint = !enabled ? t.disabled() : hover ? t.accent() : (rest >>> 24) != 0 ? rest : t.muted();
		T3Icon.draw(g, t, icon, r.x() + (r.w() - size) / 2, r.y() + (r.h() - size) / 2, size, tint);
	}
}
