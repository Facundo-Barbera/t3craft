package dev.t3craft.client.archive;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * The archive as a page of the UI kit (docs/features/ui-kit.md): every settled and archived thread of every
 * environment, newest close first. Header: a search field and two {@link T3Select}s (environment, project;
 * "all" first). Body: one {@link T3List}, each row the environment's {@link EnvBadge}, title, project and
 * close date with an icon for the kind (settled / archived). A left click or Enter on a row opens the thread
 * read-only ({@link ArchiveOpener}). Footer: the read status of each environment, "Older" (reads the next
 * page of older settled threads from T3) and "Refresh". Read-only: nothing here writes to T3.
 *
 * <p>The search and filters live in {@link ArchiveList} (kept by {@link ArchiveClient}, so the archive comes
 * back with the same search). The list scrolls instead of paging: {@link ArchiveList#filter} is all it uses.
 */
public final class ArchivePanel extends T3Panel {
	/** What the page reads; {@link #of(ArchiveStore)} is the real store, the gallery passes fake entries. */
	public interface Data {
		List<ArchiveEntry> entries();

		/** Changes whenever {@link #entries()} or the read status do. */
		int version();

		/** One line: each environment's read state, or why nothing can be read. */
		String status();

		boolean loading();

		boolean canLoadMore();

		boolean canRefresh();

		void loadMore();

		void refresh();
	}

	private static final ArchiveList.Option ALL = new ArchiveList.Option("", "");
	private static final int ROW_H = 16;
	private static final int BADGE = 9;
	private static final int GAP = 4;

	private final Data data;
	private final ArchiveOpener opener;
	private final ArchiveList filter;
	private final T3List<ArchiveEntry> list;
	private @Nullable T3Field search;
	private String signature = "";
	/** Rows that pass the search and filters; -1 before the first layout. */
	private int total = -1;

	public ArchivePanel(Data data, ArchiveOpener opener, ArchiveList filter) {
		this.data = data;
		this.opener = opener;
		this.filter = filter;
		this.list = new T3List<>(ROW_H, new T3List.Row<ArchiveEntry>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, ArchiveEntry e, Rect r, int mx, int my) {
				drawRow(g, font, t, e, r);
			}

			@Override
			public boolean mouseClicked(ArchiveEntry e, Rect r, double mx, double my, int button) {
				if (!MouseButtons.isLeft(button)) {
					return false; // only a left click opens a thread (26.x buttons: left = 1)
				}
				opener.open(e);
				return true;
			}

			@Override
			public void activated(ArchiveEntry e) {
				opener.open(e);
			}

			@Override
			public @Nullable List<String> tooltip(ArchiveEntry e, Rect r, double mx, double my) {
				return List.of(e.title(), e.envLabel() + " - " + e.projectLabel(),
						T3Draw.tr(e.kind() == ArchiveEntry.Kind.ARCHIVED ? "t3craft.archive.screen.kind.archived" : "t3craft.archive.screen.kind.settled",
								ArchiveLabel.date(e.closedAtMs(), ZoneId.systemDefault())));
			}
		});
	}

	/** The real archive. */
	public static Data of(ArchiveStore store) {
		return new Data() {
			@Override
			public List<ArchiveEntry> entries() {
				return store.entries();
			}

			@Override
			public int version() {
				return store.version();
			}

			@Override
			public String status() {
				return statusLine(store);
			}

			@Override
			public boolean loading() {
				return store.loading();
			}

			@Override
			public boolean canLoadMore() {
				return store.fullReads() && store.anyMore() && !store.loading();
			}

			@Override
			public boolean canRefresh() {
				return store.fullReads() && !store.loading();
			}

			@Override
			public void loadMore() {
				store.loadMore(System.currentTimeMillis());
			}

			@Override
			public void refresh() {
				store.refreshNow(System.currentTimeMillis());
			}
		};
	}

	@Override
	public String title() {
		List<ArchiveEntry> all = data.entries();
		String head = T3Draw.tr("t3craft.archive.screen.title");
		return head + " - " + (total < 0 || total == all.size() ? Integer.toString(all.size()) : total + "/" + all.size());
	}

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected int footerHeight() {
		return T3Control.H + 6;
	}

	@Override
	public void tick() {
		// the store moved, or the search text changed: lay out again (never while a select's list is open)
		if (!overlayOpen() && !signature.equals(signature())) {
			host().relayout();
		}
	}

	private String signature() {
		if (search != null) {
			filter.setText(search.value());
		}
		return data.version() + "|" + filter.text() + "|" + filter.env() + "|" + filter.project() + "|" + data.loading();
	}

	// ---- layout -------------------------------------------------------------------------------------------

	@Override
	protected void arrangeHeader(Rect header) {
		List<ArchiveEntry> all = data.entries();
		int w = header.w();
		int searchW = Math.max(80, w * 2 / 5);
		int selW = Math.max(60, (w - searchW - 2 * GAP) / 2);
		if (search == null) {
			search = new T3Field(font(), T3Draw.tr("t3craft.archive.screen.search")).hint(T3Draw.tr("t3craft.archive.screen.search")).maxLength(128);
			search.setValue(filter.text());
		}
		pinField(search, header.x(), header.y(), searchW);
		int x = header.x() + searchW + GAP;
		String allLabel = T3Draw.tr("t3craft.archive.screen.all");

		List<ArchiveList.Option> envs = withAll(ArchiveList.envs(all));
		pin(new T3Select<>(envs, o -> T3Draw.tr("t3craft.archive.screen.env", o == ALL ? allLabel : o.label()), find(envs, filter.env()), o -> {
			filter.setEnv(o == ALL ? null : o.key());
			host().relayout();
		}), x, header.y(), selW, T3Control.H);
		x += selW + GAP;

		List<ArchiveList.Option> projects = withAll(ArchiveList.projects(all, filter.env()));
		pin(new T3Select<>(projects, o -> T3Draw.tr("t3craft.archive.screen.project", o == ALL ? allLabel : o.label()), find(projects, filter.project()), o -> {
			filter.setProject(o == ALL ? null : o.key());
			host().relayout();
		}), x, header.y(), header.right() - x, T3Control.H);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		List<ArchiveEntry> all = data.entries();
		if (search != null) {
			filter.setText(search.value());
		}
		List<ArchiveEntry> rows = filter.filter(all);
		total = rows.size();
		signature = signature();
		boolean changed = !Objects.equals(list.items(), rows);
		list.setItems(rows);
		if (changed && list.cursor() < 0) {
			list.scrollToTop();
		}
		String empty = data.loading() ? "t3craft.archive.catalog.loading" : all.isEmpty() ? "t3craft.archive.catalog.empty" : "t3craft.archive.screen.no_match";
		list.setEmptyText(T3Draw.tr(empty));
		place(list, body.x(), y0, body.w(), body.h());
		return y0 + body.h(); // the list scrolls inside itself
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		int y = footer.y() + 4;
		T3Button refresh = new T3Button(T3Icon.REFRESH_CW, T3Draw.tr("t3craft.archive.catalog.refresh"), data::refresh);
		T3Button older = new T3Button(T3Icon.CHEVRON_DOWN, T3Draw.tr("t3craft.archive.catalog.older"), data::loadMore);
		int rw = refresh.preferredWidth(font());
		int ow = older.preferredWidth(font());
		int x = footer.right() - rw;
		pin(older, x - GAP - ow, y, ow, T3Control.H).setEnabled(data.canLoadMore());
		pin(refresh, x, y, rw, T3Control.H).setEnabled(data.canRefresh());
		int textW = footer.w() - rw - ow - 2 * GAP - 6;
		pinText(footer.x() + 2, y + (T3Control.H - 8) / 2, ChatDraw.clip(font(), data.status(), textW), Tone.MUTED);
	}

	private static List<ArchiveList.Option> withAll(List<ArchiveList.Option> options) {
		List<ArchiveList.Option> out = new ArrayList<>(options.size() + 1);
		out.add(ALL);
		out.addAll(options);
		return out;
	}

	private static ArchiveList.Option find(List<ArchiveList.Option> options, @Nullable String key) {
		for (ArchiveList.Option o : options) {
			if (o != ALL && o.key().equals(key)) {
				return o;
			}
		}
		return ALL;
	}

	// ---- drawing ------------------------------------------------------------------------------------------

	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, ArchiveEntry e, Rect r) {
		int w = r.w() - 4;
		String date = ArchiveLabel.date(e.closedAtMs(), ZoneId.systemDefault());
		int dateW = T3Icon.SIZE + 3 + font.width("0000-00-00");
		int projW = Math.min(120, w / 4);
		int titleW = w - BADGE - 4 - projW - 6 - dateW - 6;
		int x = r.x() + 2;
		int ty = r.y() + (r.h() - 8) / 2;
		ChatDraw.envIcon(g, font, EnvBadge.of(e.env(), e.envLabel()), x, r.y() + (r.h() - BADGE) / 2, BADGE);
		x += BADGE + 4;
		g.text(font, ChatDraw.clip(font, e.title(), titleW), x, ty, t.text(), t.shadow());
		x += titleW + 6;
		g.text(font, ChatDraw.clip(font, e.projectLabel(), projW), x, ty, t.muted(), t.shadow());
		x += projW + 6;
		T3Icon kind = e.kind() == ArchiveEntry.Kind.ARCHIVED ? T3Icon.ARCHIVE : T3Icon.CIRCLE_CHECK;
		T3Icon.draw(g, t, kind, x, r.y() + (r.h() - T3Icon.SIZE) / 2, T3Icon.SIZE, t.muted());
		g.text(font, date, x + T3Icon.SIZE + 3, ty, t.muted(), t.shadow());
	}

	/** Each environment's read state, as the footer shows it. */
	static String statusLine(ArchiveStore store) {
		if (!store.fullReads()) {
			return T3Draw.tr("t3craft.archive.catalog.shell_only");
		}
		StringBuilder b = new StringBuilder();
		for (ArchiveStore.EnvStatus s : store.statuses()) {
			if (!b.isEmpty()) {
				b.append(" - ");
			}
			b.append(s.label()).append(' ').append(s.listed());
			if (s.hasMore()) {
				b.append('+');
			}
			if (!s.connected()) {
				b.append(" (offline)");
			} else if (s.loading()) {
				b.append(" ...");
			} else if (s.error() != null) {
				b.append(" (").append(s.error()).append(')');
			}
		}
		return b.isEmpty() ? T3Draw.tr("t3craft.archive.catalog.no_env") : b.toString();
	}
}
