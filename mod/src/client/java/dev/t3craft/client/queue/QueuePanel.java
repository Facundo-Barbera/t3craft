package dev.t3craft.client.queue;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3IconButton;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Queued runs per thread, every environment, in T3's delivery order, as a page of the UI kit
 * (docs/features/ui-kit.md). One {@link T3List}: a header row per thread (environment, title, state) and
 * a row per queued run with three icon buttons, move up / move down / cancel. Reading needs no operate
 * session; the buttons need control and are disabled with "Requires control (operate)" when the
 * environment is read-only. Cancel asks first with a modal over the page, then sends the confirmed
 * action; reordering is not destructive and goes out at once. Everything goes through
 * {@link OperateGate} ({@link QueueOps} builds the actions).
 *
 * <p>The data comes from a {@link Source}: {@link #live} watches the focused thread plus the working
 * threads ({@link QueueTargets#SCREEN_CAP}) while the page is shown; the gallery passes fake queues.
 */
public final class QueuePanel extends T3Panel {
	/** Where the page reads queues from and sends its writes to. */
	public interface Source {
		/** The page is shown: start watching (idempotent). */
		void open();

		/** The page is gone: drop the watches (idempotent; {@link #open} may follow). */
		void close();

		boolean readable();

		String unavailableReason();

		/** Whether any thread is watched at all (no working thread: nothing to show). */
		boolean anyWatched();

		/** The threads to show, a queue or the focused one, in order. */
		List<QueueStore.Snapshot> snapshots();

		@Nullable String title(String envId, String threadId);

		String envLabel(String envId);

		boolean canOperate(String envId);

		QueueOps ops();

		/** Sends {@code action}; {@code confirmed}: the player said yes to the modal. Completes on any thread. */
		CompletableFuture<OperateResult> submit(OperateAction action, boolean confirmed);
	}

	private static final int ROW_H = 18;
	private static final int BTN = T3IconButton.SIZE;
	private static final int GAP = 2;
	private static final int BUTTONS_W = 3 * BTN + 2 * GAP;

	/** A thread header ({@code run == null}) or one queued run. */
	private record Row(QueueStore.Snapshot thread, @Nullable QueuedRun run, int index) {
	}

	private final Source source;
	private final T3List<Row> list;
	/** The three buttons of each run row (by run id), made when the page is arranged. */
	private final Map<String, List<T3IconButton>> buttons = new HashMap<>();
	private String signature = "";
	private String status = "";
	private Tone statusTone = Tone.MUTED;
	private boolean busy;

	public QueuePanel(Source source) {
		this.source = source;
		this.list = new T3List<>(ROW_H, new T3List.Row<Row>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Row r, Rect rr, int mx, int my) {
				drawRow(g, font, t, r, rr, mx, my);
			}

			@Override
			public boolean mouseClicked(Row r, Rect rr, double mx, double my, int button) {
				for (T3IconButton b : rowButtons(r)) {
					if (b.mouseClicked(mx, my, button)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public @Nullable List<String> tooltip(Row r, Rect rr, double mx, double my) {
				for (T3IconButton b : rowButtons(r)) {
					List<String> tip = b.tooltipAt(mx, my);
					if (tip != null) {
						return tip;
					}
				}
				return null;
			}
		});
	}

	/** The real queues: {@code focus} (may be null) plus the working threads. */
	public static Source live(QueueStore.@Nullable Key focus) {
		return new LiveSource(focus);
	}

	@Override
	public String title() {
		return T3Draw.tr("t3craft.queue.title");
	}

	@Override
	protected int footerHeight() {
		return 14;
	}

	@Override
	public void closed() {
		source.close();
	}

	/** The screen went away (another screen, or closed to the game): stop watching until it is laid out again. */
	public void release() {
		source.close();
	}

	@Override
	public void tick() {
		if (!busy && !overlayOpen() && !signature.equals(signature(rows()))) {
			host().relayout();
		}
	}

	// ---- rows ---------------------------------------------------------------------------------------------

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		for (QueueStore.Snapshot s : source.snapshots()) {
			out.add(new Row(s, null, 0));
			int i = 0;
			for (QueuedRun r : s.runs()) {
				out.add(new Row(s, r, ++i));
			}
		}
		return out;
	}

	private static String signature(List<Row> rows) {
		StringBuilder b = new StringBuilder();
		for (Row r : rows) {
			b.append(r.thread().key()).append('/').append(r.thread().status()).append('/')
					.append(r.run() == null ? "-" : r.run().id() + r.run().held()).append(';');
		}
		return b.toString();
	}

	private List<T3IconButton> rowButtons(Row r) {
		return r.run() == null ? List.of() : buttons.getOrDefault(r.run().id(), List.of());
	}

	// ---- layout -------------------------------------------------------------------------------------------

	@Override
	protected void arrangeFooter(Rect footer) {
		pinText(footer.x() + 2, footer.y() + 4, T3Draw.wrap(font(), status, footer.w() - 4, 1).getFirst(), statusTone);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		if (source.readable()) {
			source.open();
		}
		List<Row> rows = rows();
		signature = signature(rows);
		buttons.clear();
		for (Row r : rows) {
			if (r.run() != null) {
				buttons.put(r.run().id(), makeButtons(r));
			}
		}
		list.setItems(rows);
		list.setEmptyText(!source.readable() ? T3Draw.tr("t3craft.queue.unavailable", source.unavailableReason())
				: T3Draw.tr(source.anyWatched() ? "t3craft.queue.none" : "t3craft.queue.none_working"));
		place(list, body.x(), y0, body.w(), body.h());
		return y0 + body.h(); // the list scrolls inside itself
	}

	private List<T3IconButton> makeButtons(Row row) {
		QueuedRun r = row.run();
		String title = source.title(r.envId(), r.threadId());
		List<QueuedRun> q = row.thread().runs();
		OperateAction up = source.ops().moveAction(q, r, QueueOps.Direction.UP, title);
		OperateAction down = source.ops().moveAction(q, r, QueueOps.Direction.DOWN, title);
		OperateAction cancel = source.ops().cancelAction(r, title);
		List<T3IconButton> out = new ArrayList<>();
		out.add(button(T3Icon.CHEVRON_UP, "t3craft.queue.up", r.envId(), up, () -> send(up, false)));
		out.add(button(T3Icon.CHEVRON_DOWN, "t3craft.queue.down", r.envId(), down, () -> send(down, false)));
		out.add(button(T3Icon.X, "t3craft.queue.cancel", r.envId(), cancel, () -> confirmCancel(r, title, cancel)));
		return out;
	}

	private T3IconButton button(T3Icon icon, String tooltipKey, String envId, @Nullable OperateAction action, Runnable press) {
		T3IconButton b = new T3IconButton(icon, press);
		b.setTooltip(T3Draw.tr(tooltipKey));
		if (!source.canOperate(envId)) {
			b.setEnabled(false);
			b.setTooltip(T3Draw.tr(OperateUi.REQUIRES_KEY));
		} else {
			b.setEnabled(action != null && !busy);
		}
		return b;
	}

	// ---- actions ------------------------------------------------------------------------------------------

	private void confirmCancel(QueuedRun r, @Nullable String title, OperateAction cancel) {
		String what = r.preview() == null ? r.id() : r.preview();
		confirm(T3Draw.tr("t3craft.operate.confirm.title", T3Draw.tr(cancel.kind().langKey())),
				List.of(T3Draw.tr("t3craft.operate.confirm.thread", title == null ? r.threadId() : title),
						T3Draw.tr("t3craft.operate.confirm.env", source.envLabel(r.envId()) + " (" + r.envId() + ")"),
						what),
				T3Draw.tr("t3craft.operate.confirm.warning"), T3Draw.tr("t3craft.operate.confirm.yes", T3Draw.tr(cancel.kind().langKey())), true,
				() -> send(cancel, true));
	}

	private void send(@Nullable OperateAction action, boolean confirmed) {
		if (action == null || busy) {
			return;
		}
		busy = true;
		say(T3Draw.tr("t3craft.queue.sending"), Tone.MUTED);
		host().relayout();
		Minecraft mc = Minecraft.getInstance();
		source.submit(action, confirmed).whenComplete((res, err) -> mc.execute(() -> {
			busy = false;
			if (res != null) {
				say(T3Draw.tr("t3craft.queue.result." + res.outcome().wire, res.message()), tone(res.outcome()));
			} else {
				say(T3Draw.tr("t3craft.queue.result.failed", err == null ? "?" : String.valueOf(err.getMessage())), Tone.ERROR);
			}
			host().relayout();
		}));
	}

	private static Tone tone(OperateResult.Outcome o) {
		return switch (o) {
			case OK -> Tone.OK;
			case CANCELLED, READ_ONLY -> Tone.MUTED;
			case FAILED -> Tone.WARN;
			default -> Tone.ERROR;
		};
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- drawing ------------------------------------------------------------------------------------------

	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, Row row, Rect rr, int mx, int my) {
		int ty = rr.y() + (rr.h() - 8) / 2;
		if (row.run() == null) {
			drawHeader(g, font, t, row.thread(), rr, ty);
			return;
		}
		QueuedRun r = row.run();
		int x = rr.x() + 14;
		int right = rr.right() - BUTTONS_W - 6;
		String idx = row.index() + ". ";
		g.text(font, idx, x, ty, t.muted(), t.shadow());
		x += font.width(idx);
		List<String> tags = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		if (r.held()) {
			tags.add(T3Draw.tr("t3craft.queue.held"));
			colours.add(t.youText());
		}
		if (r.automatic()) {
			tags.add(T3Draw.tr("t3craft.queue.automatic"));
			colours.add(t.link());
		}
		int tagsW = 0;
		for (String s : tags) {
			tagsW += font.width(s) + 6;
		}
		String text = r.preview() == null ? r.id() : r.preview();
		String shown = ChatDraw.clip(font, text, Math.max(0, right - x - tagsW));
		g.text(font, shown, x, ty, t.text(), t.shadow());
		x += font.width(shown) + 6;
		for (int i = 0; i < tags.size(); i++) {
			g.text(font, tags.get(i), x, ty, colours.get(i), t.shadow());
			x += font.width(tags.get(i)) + 6;
		}
		int bx = rr.right() - BUTTONS_W - 2;
		int by = rr.y() + (rr.h() - BTN) / 2;
		for (T3IconButton b : rowButtons(row)) {
			b.setBounds(new Rect(bx, by, BTN, BTN));
			b.render(g, font, t, mx, my);
			bx += BTN + GAP;
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, Font font, ChatTheme t, QueueStore.Snapshot s, Rect rr, int ty) {
		String env = source.envLabel(s.key().envId());
		String title = source.title(s.key().envId(), s.key().threadId());
		String state;
		int stateColour;
		switch (s.status()) {
			case OK -> {
				state = T3Draw.tr("t3craft.queue.count", s.runs().size());
				stateColour = t.youText();
			}
			case PAUSED -> {
				state = T3Draw.tr("t3craft.queue.paused");
				stateColour = t.muted();
			}
			case LOADING -> {
				state = T3Draw.tr("t3craft.queue.loading");
				stateColour = t.muted();
			}
			default -> {
				state = T3Draw.tr("t3craft.queue.unknown_short");
				stateColour = t.errorText();
			}
		}
		int x = rr.x() + 4;
		int avail = rr.w() - 8;
		int stateW = font.width(state);
		String envShown = ChatDraw.clip(font, env, Math.max(0, avail / 3));
		g.text(font, envShown, x, ty, t.accent(), t.shadow());
		x += font.width(envShown);
		g.text(font, " - ", x, ty, t.disabled(), t.shadow());
		x += font.width(" - ");
		String name = ChatDraw.clip(font, title == null ? s.key().threadId() : title, Math.max(0, rr.x() + 4 + avail - x - stateW - 8));
		g.text(font, name, x, ty, t.text(), t.shadow());
		x += font.width(name) + 8;
		g.text(font, state, x, ty, stateColour, t.shadow());
		g.fill(rr.x() + 2, rr.bottom() - 1, rr.right() - 2, rr.bottom(), t.edge());
	}

	// ---- the real queues ------------------------------------------------------------------------------------

	/** {@link QueueClient}'s store: watches the focused thread and the working ones while the page is shown. */
	private static final class LiveSource implements Source {
		private final QueueStore.@Nullable Key focus;
		private final List<QueueStore.Watch> watches = new ArrayList<>();
		private final Set<QueueStore.Key> watched = new LinkedHashSet<>();

		LiveSource(QueueStore.@Nullable Key focus) {
			this.focus = focus;
		}

		private static Map<String, ShellState.View> views() {
			T3CraftClient c = T3CraftClient.get();
			return c == null ? Map.of() : QueueClient.views(c);
		}

		@Override
		public void open() {
			if (!watches.isEmpty()) {
				return;
			}
			Set<QueueStore.Key> want = new LinkedHashSet<>();
			if (focus != null) {
				want.add(focus);
			}
			want.addAll(QueueTargets.working(views(), QueueTargets.SCREEN_CAP));
			for (QueueStore.Key k : want) {
				if (watched.add(k)) {
					watches.add(QueueClient.store().watch(k.envId(), k.threadId()));
				}
			}
		}

		@Override
		public void close() {
			watches.forEach(QueueStore.Watch::close);
			watches.clear();
			watched.clear();
		}

		@Override
		public boolean readable() {
			return QueueClient.readable();
		}

		@Override
		public String unavailableReason() {
			return QueueClient.source().unavailableReason();
		}

		@Override
		public boolean anyWatched() {
			return !watched.isEmpty();
		}

		@Override
		public List<QueueStore.Snapshot> snapshots() {
			List<QueueStore.Snapshot> out = new ArrayList<>();
			for (QueueStore.Snapshot s : QueueClient.store().all()) {
				// only threads with something to show: a queue, or the focused one (even if empty or unknown)
				if (watched.contains(s.key()) && (!s.runs().isEmpty() || s.key().equals(focus))) {
					out.add(s);
				}
			}
			return out;
		}

		@Override
		public @Nullable String title(String envId, String threadId) {
			return QueueTargets.title(views(), envId, threadId);
		}

		@Override
		public String envLabel(String envId) {
			return OperateClient.label(envId);
		}

		@Override
		public boolean canOperate(String envId) {
			return OperateUi.canOperate(envId);
		}

		@Override
		public QueueOps ops() {
			return QueueClient.ops();
		}

		@Override
		public CompletableFuture<OperateResult> submit(OperateAction action, boolean confirmed) {
			return QueueClient.ops().submit(action, OperateGate.Origin.UI, confirmed);
		}
	}
}
