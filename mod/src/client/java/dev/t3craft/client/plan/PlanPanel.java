package dev.t3craft.client.plan;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Tabs;
import dev.t3craft.plan.PlanAttention;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.ArrayList;
import java.util.List;

/**
 * The project plan as a page of the UI kit (docs/features/project-plan.md): three views over the one
 * {@link PlanStore}, and on top the next thing that needs the player ({@link PlanAttention}).
 * <ul>
 *   <li><b>Desk</b>: proposals not accepted yet, then drafts (outcome, dependencies, pending decision);</li>
 *   <li><b>In progress</b>: tasks linked to a thread, with the thread's real state and "Open thread";</li>
 *   <li><b>Review</b>: deliveries with their three separate marks, Accept / Send back; accepted tasks under them.</li>
 * </ul>
 * The list shows outcome, dependencies and decision; the rest is on the task page ({@link PlanTaskPanel}).
 *
 * <p>Opening the page or a task sends nothing. While the page is shown it re-reads the store when its
 * {@link PlanStore#version} moves and, every second, the shell rows the client already holds (no subscription, no
 * request): a linked thread that finished its turn moves its task to review.
 *
 * <p>Ready for the T3 screen's right panel: {@link #create()} is the factory for {@code RightPanels}.
 */
public final class PlanPanel extends T3Panel {
	/** The three views, in tab order. */
	public enum View {
		DESK, ACTIVE, REVIEW
	}

	private static final int H = T3Control.H;
	private static final int GAP = 4;
	private static final int LIVE_EVERY_TICKS = 20;

	private final PlanStore store;
	private View view;
	private long seen = -1;
	private String live = "";
	private int ticks;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public PlanPanel(PlanStore store, View view) {
		this.store = store;
		this.view = view;
	}

	/** A fresh page on the Desk over the client's store (the right panel's factory). */
	public static PlanPanel create() {
		return new PlanPanel(PlanClient.store(), View.DESK);
	}

	@Override
	public String title() {
		return T3Draw.tr("t3craft.plan.title");
	}

	private void say(String text, Tone tone) {
		status = text;
		statusTone = tone;
	}

	@Override
	public void tick() {
		if (++ticks % LIVE_EVERY_TICKS == 0) {
			PlanClient.syncReview();
		}
		if (overlayOpen()) {
			return;
		}
		if (store.version() != seen || ticks % LIVE_EVERY_TICKS == 0 && !live.equals(live(store.tasks()))) {
			host().relayout();
		}
	}

	/** The live states of the linked threads, to notice a change without re-arranging every tick. */
	private static String live(List<PlanTask> tasks) {
		StringBuilder b = new StringBuilder();
		for (PlanTask t : tasks) {
			if (t.link() != null) {
				ThreadShell s = PlanClient.thread(t.link().ref());
				b.append(t.id()).append('=').append(s == null ? "?" : ThreadState.of(s).wire).append(';');
			}
		}
		return b.toString();
	}

	// ---- header: tabs and the next thing that needs the player -------------------------------------------

	@Override
	protected int headerHeight() {
		return 2 * H + 8;
	}

	@Override
	protected void arrangeHeader(Rect h) {
		List<PlanTask> all = store.tasks();
		List<String> labels = List.of(
				T3Draw.tr("t3craft.plan.view.desk", count(all, View.DESK)),
				T3Draw.tr("t3craft.plan.view.active", count(all, View.ACTIVE)),
				T3Draw.tr("t3craft.plan.view.review", count(all, View.REVIEW)));
		pin(new T3Tabs(font(), labels, view.ordinal(), i -> {
			view = View.values()[i];
			host().relayout();
		}), h.x(), h.y(), h.w(), H + 2);
		int y = h.y() + H + 6;
		PlanAttention.Next next = PlanAttention.next(all, PlanClient::thread);
		if (next == null) {
			pinText(h.x() + 2, y + 4, ChatDraw.clip(font(), T3Draw.tr(all.isEmpty() ? "t3craft.plan.next.empty" : "t3craft.plan.next.none"),
					h.w() - 4), Tone.MUTED);
			return;
		}
		String text = T3Draw.tr("t3craft.plan.next." + next.reason().wire, PlanText.title(next.task()));
		pin(new T3Button(T3Icon.BELL, ChatDraw.clip(font(), text, h.w() - 40), T3Button.Style.GHOST, () -> openTask(next.task())),
				h.x(), y, h.w(), H);
	}

	private static int count(List<PlanTask> all, View v) {
		int n = 0;
		for (PlanTask t : all) {
			if (viewOf(t) == v && t.stage() != PlanTask.Stage.ACCEPTED) {
				n++;
			}
		}
		return n;
	}

	private static View viewOf(PlanTask t) {
		return switch (t.stage()) {
			case PROPOSED, DRAFT -> View.DESK;
			case IN_PROGRESS -> View.ACTIVE;
			case IN_REVIEW, ACCEPTED -> View.REVIEW;
		};
	}

	// ---- footer ----------------------------------------------------------------------------------------------

	@Override
	protected int footerHeight() {
		return H + 18;
	}

	@Override
	protected void arrangeFooter(Rect f) {
		pinText(f.x() + 2, f.y() + 4, ChatDraw.clip(font(), status, f.w() - 4), statusTone);
		int by = f.y() + 14;
		T3Button add = new T3Button(T3Icon.PLUS, T3Draw.tr("t3craft.plan.new"), T3Button.Style.PRIMARY,
				() -> host().show(new PlanTaskPanel(store, null, this::say)));
		T3Button imp = new T3Button(T3Icon.DOWNLOAD, T3Draw.tr("t3craft.plan.import"), T3Button.Style.DEFAULT,
				() -> host().show(new PlanThreadPanel(store, PlanThreadPanel.Mode.IMPORT, null, this::say)));
		int w1 = Math.min(add.preferredWidth(font()), (f.w() - GAP) / 2);
		int w2 = Math.min(imp.preferredWidth(font()), f.w() - GAP - w1);
		pin(add, f.x(), by, w1, H);
		pin(imp, f.x() + w1 + GAP, by, w2, H);
	}

	// ---- body ------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		if (seen < 0) {
			PlanClient.syncReview(); // the first look: tasks whose thread finished while the page was closed
		}
		List<PlanTask> all = store.tasks();
		seen = store.version();
		live = live(all);
		int x = body.x();
		int w = body.w() - 10; // clear of the scrollbar
		int y = y0;
		String loadProblem = store.loadProblem();
		if (loadProblem != null) {
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.load_problem", loadProblem), Tone.ERROR) + GAP;
		}
		String saveProblem = store.saveProblem();
		if (saveProblem != null) {
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.save_problem", saveProblem), Tone.ERROR) + GAP;
		}
		if (all.isEmpty()) {
			return notice(x, y, w, T3Draw.tr("t3craft.plan.empty"), Tone.MUTED) + GAP;
		}
		switch (view) {
			case DESK -> {
				y = section(x, y, w, all, PlanTask.Stage.PROPOSED, "t3craft.plan.section.proposed");
				y = section(x, y, w, all, PlanTask.Stage.DRAFT, "t3craft.plan.section.draft");
				if (count(all, View.DESK) == 0) {
					y = text(x, y, w, T3Draw.tr("t3craft.plan.none.desk"), Tone.MUTED);
				}
			}
			case ACTIVE -> {
				y = section(x, y, w, all, PlanTask.Stage.IN_PROGRESS, null);
				if (count(all, View.ACTIVE) == 0) {
					y = text(x, y, w, T3Draw.tr("t3craft.plan.none.active"), Tone.MUTED);
				}
			}
			case REVIEW -> {
				y = section(x, y, w, all, PlanTask.Stage.IN_REVIEW, null);
				if (count(all, View.REVIEW) == 0) {
					y = text(x, y, w, T3Draw.tr("t3craft.plan.none.review"), Tone.MUTED);
				}
				y = accepted(x, y + GAP, w, all);
			}
		}
		return y + GAP;
	}

	/** Every task of {@code stage}, under a title when {@code titleKey} is not null and there is any. */
	private int section(int x, int y, int w, List<PlanTask> all, PlanTask.Stage stage, String titleKey) {
		List<PlanTask> list = new ArrayList<>();
		for (PlanTask t : all) {
			if (t.stage() == stage) {
				list.add(t);
			}
		}
		if (list.isEmpty()) {
			return y;
		}
		if (titleKey != null) {
			y = text(x, y, w, T3Draw.tr(titleKey, list.size()), Tone.MUTED) + 2;
		}
		for (PlanTask t : list) {
			y = card(x, y, w, t, all);
		}
		return y;
	}

	/** Accepted tasks: one line each, under the deliveries. */
	private int accepted(int x, int y, int w, List<PlanTask> all) {
		List<PlanTask> list = all.stream().filter(t -> t.stage() == PlanTask.Stage.ACCEPTED).toList();
		if (list.isEmpty()) {
			return y;
		}
		y = text(x, y, w, T3Draw.tr("t3craft.plan.section.accepted", list.size()), Tone.MUTED) + 2;
		for (PlanTask t : list) {
			int bw = 50;
			place(new T3Button(T3Draw.tr("t3craft.plan.open"), T3Button.Style.GHOST, () -> openTask(t)), x + w - bw, y, bw, H);
			text(x, y + 4, w - bw - GAP, ChatDraw.clip(font(), "✔ " + PlanText.title(t), w - bw - GAP), Tone.OK);
			y += H + 2;
		}
		return y;
	}

	/** One task: title, outcome, dependencies, decision, and what its stage needs; then its buttons. */
	private int card(int x, int y, int w, PlanTask t, List<PlanTask> all) {
		String tag = t.stage() == PlanTask.Stage.PROPOSED ? T3Draw.tr("t3craft.plan.tag.proposed") + " " : "";
		y = lines(x, y, w, tag + PlanText.title(t), Tone.TITLE, 2);
		if (!t.outcome().isBlank()) {
			y = lines(x, y, w, T3Draw.tr("t3craft.plan.outcome", t.outcome()), Tone.NORMAL, 2);
		}
		String deps = PlanText.deps(t, all);
		if (deps != null) {
			y = lines(x, y, w, deps, Tone.MUTED, 2);
		}
		if (t.hasDecision()) {
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.decision", oneLine(t.decision())), Tone.WARN);
		}
		if (t.incoming() != null) {
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.incoming.short"), Tone.WARN);
		}
		PlanTask.Link link = t.link();
		ThreadShell shell = link == null ? null : PlanClient.thread(link.ref());
		if (link != null) {
			y = lines(x, y, w, PlanText.threadLine(link.ref(), shell), Tone.MUTED, 1);
		}
		if (t.stage() == PlanTask.Stage.IN_REVIEW) {
			y = lines(x, y, w, T3Draw.tr("t3craft.plan.mark.implemented.yes"), Tone.OK, 1);
			y = lines(x, y, w, t.agentChecked().isBlank() ? T3Draw.tr("t3craft.plan.mark.checked.none")
					: T3Draw.tr("t3craft.plan.mark.checked", oneLine(t.agentChecked())), t.agentChecked().isBlank() ? Tone.MUTED : Tone.NORMAL, 1);
			y = lines(x, y, w, T3Draw.tr(t.userTested() ? "t3craft.plan.mark.tested.yes" : "t3craft.plan.mark.tested.no"),
					t.userTested() ? Tone.OK : Tone.MUTED, 1);
		}
		y += 2;
		List<T3Button> buttons = new ArrayList<>();
		buttons.add(new T3Button(T3Icon.FILE_TEXT, T3Draw.tr("t3craft.plan.open"), T3Button.Style.DEFAULT, () -> openTask(t)));
		switch (t.stage()) {
			case PROPOSED -> buttons.add(new T3Button(T3Icon.CHECK, T3Draw.tr("t3craft.plan.proposal.accept"), T3Button.Style.PRIMARY,
					() -> PlanActions.acceptProposal(host(), store, t, this::say)));
			case DRAFT -> {
				buttons.add(new T3Button(T3Icon.PLAY, T3Draw.tr("t3craft.plan.launch"), T3Button.Style.DEFAULT,
						() -> host().show(new PlanLaunchPanel(store, t.id(), this::say))));
				buttons.add(new T3Button(T3Icon.LINK, T3Draw.tr("t3craft.plan.link"), T3Button.Style.DEFAULT,
						() -> host().show(new PlanThreadPanel(store, PlanThreadPanel.Mode.LINK, t.id(), this::say))));
			}
			case IN_PROGRESS -> buttons.add(openThreadButton(link));
			case IN_REVIEW -> {
				buttons.add(openThreadButton(link));
				buttons.add(new T3Button(T3Icon.CIRCLE_CHECK, T3Draw.tr("t3craft.plan.accept"), T3Button.Style.PRIMARY,
						() -> PlanActions.acceptDelivery(host(), store, t, this::say)));
				buttons.add(new T3Button(T3Icon.SEND, T3Draw.tr("t3craft.plan.send_back"), T3Button.Style.DEFAULT,
						() -> PlanActions.sendBack(host(), font(), store, t, this::say)));
			}
			case ACCEPTED -> {
			}
		}
		int bx = x;
		for (T3Button b : buttons) {
			int bw = Math.min(b.preferredWidth(font()), x + w - bx);
			if (bw <= 0) {
				break;
			}
			place(b, bx, y, bw, H);
			bx += bw + GAP;
		}
		y += H + 4;
		return y + 6; // a little air between cards
	}

	private T3Button openThreadButton(PlanTask.Link link) {
		T3Button b = new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.plan.open_thread"), T3Button.Style.DEFAULT,
				() -> PlanActions.openThread(host(), link.ref()));
		b.setEnabled(link != null);
		return b;
	}

	private void openTask(PlanTask t) {
		host().show(new PlanTaskPanel(store, t.id(), this::say));
	}

	/** Wrapped text cut to {@code max} lines; returns the y under it. */
	private int lines(int x, int y, int w, String s, Tone tone, int max) {
		for (String line : T3Draw.wrap(font(), s, w, max)) {
			y = text(x, y, w, line, tone);
		}
		return y;
	}

	private static String oneLine(String s) {
		return s.strip().replaceAll("\\s+", " ");
	}
}
