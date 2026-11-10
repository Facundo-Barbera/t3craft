package dev.t3craft.client.plan;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Check;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3TextArea;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One task of the plan, opened from the list ({@link PlanPanel}): its texts (editable until it is accepted), its
 * dependencies, and what its stage allows: accept a proposal, launch or link a thread, the three review marks, Accept /
 * Send back. A new task starts here too ({@code id == null}). Opening it sends nothing; the only writes to T3 are the
 * launch ({@link PlanLaunchPanel}) and the observations ({@link PlanActions#sendBack}).
 */
final class PlanTaskPanel extends T3Panel {
	private static final int H = T3Control.H;
	private static final int GAP = 4;
	private static final int AREA_H = 48;

	private final PlanStore store;
	private final PlanActions.Say parentSay;
	private @Nullable String id;
	private long seen = -1;
	private String status = "";
	private Tone statusTone = Tone.MUTED;
	private @Nullable T3Field title;
	private @Nullable T3TextArea outcome;
	private @Nullable T3Field decision;
	private @Nullable T3TextArea notes;
	private @Nullable T3TextArea checked;
	private final Set<String> deps = new LinkedHashSet<>();

	PlanTaskPanel(PlanStore store, @Nullable String id, PlanActions.Say parentSay) {
		this.store = store;
		this.id = id;
		this.parentSay = parentSay;
		PlanTask t = id == null ? null : store.get(id);
		if (t != null) {
			deps.addAll(t.dependsOn());
		}
	}

	private @Nullable PlanTask task() {
		return id == null ? null : store.get(id);
	}

	@Override
	public String title() {
		PlanTask t = task();
		return t == null ? T3Draw.tr("t3craft.plan.task.new") : PlanText.title(t);
	}

	private void say(String text, Tone tone) {
		status = text;
		statusTone = tone;
		parentSay.say(text, tone);
	}

	@Override
	public void tick() {
		if (!overlayOpen() && store.version() != seen) {
			host().relayout();
		}
	}

	// ---- lazily made boxes (they keep their text across re-arranging) -------------------------------------------

	private T3Field titleField() {
		if (title == null) {
			title = new T3Field(font(), T3Draw.tr("t3craft.plan.field.title")).maxLength(120).hint(T3Draw.tr("t3craft.plan.field.title.hint"));
			PlanTask t = task();
			if (t != null) {
				title.setValue(t.title());
			}
		}
		return title;
	}

	private T3TextArea outcomeArea() {
		if (outcome == null) {
			outcome = new T3TextArea(font(), T3Draw.tr("t3craft.plan.field.outcome")).maxLength(4_000)
					.hint(T3Draw.tr("t3craft.plan.field.outcome.hint"));
			PlanTask t = task();
			if (t != null) {
				outcome.setValue(t.outcome());
			}
		}
		return outcome;
	}

	private T3Field decisionField() {
		if (decision == null) {
			decision = new T3Field(font(), T3Draw.tr("t3craft.plan.field.decision")).maxLength(1_000)
					.hint(T3Draw.tr("t3craft.plan.field.decision.hint"));
			PlanTask t = task();
			if (t != null) {
				decision.setValue(t.decision());
			}
		}
		return decision;
	}

	private T3TextArea notesArea() {
		if (notes == null) {
			notes = new T3TextArea(font(), T3Draw.tr("t3craft.plan.field.notes")).maxLength(4_000).hint(T3Draw.tr("t3craft.plan.field.notes.hint"));
			PlanTask t = task();
			if (t != null) {
				notes.setValue(t.notes());
			}
		}
		return notes;
	}

	private T3TextArea checkedArea() {
		if (checked == null) {
			checked = new T3TextArea(font(), T3Draw.tr("t3craft.plan.mark.checked.label")).maxLength(4_000)
					.hint(T3Draw.tr("t3craft.plan.mark.checked.hint"));
			PlanTask t = task();
			if (t != null) {
				checked.setValue(t.agentChecked());
			}
		}
		return checked;
	}

	// ---- footer ----------------------------------------------------------------------------------------------------

	@Override
	protected int footerHeight() {
		return H + 18;
	}

	@Override
	protected void arrangeFooter(Rect f) {
		pinText(f.x() + 2, f.y() + 4, ChatDraw.clip(font(), status, f.w() - 4), statusTone);
		int by = f.y() + 14;
		PlanTask t = task();
		int bw = Math.min(110, (f.w() - GAP) / 2);
		if (t == null || t.stage() != PlanTask.Stage.ACCEPTED) {
			pin(new T3Button(T3Icon.CHECK, T3Draw.tr(t == null ? "t3craft.plan.create" : "t3craft.plan.save"), T3Button.Style.PRIMARY, this::save),
					f.x(), by, bw, H);
		}
		pin(new T3Button(T3Icon.ARROW_LEFT, T3Draw.tr("t3craft.plan.back"), T3Button.Style.DEFAULT, () -> host().back()),
				f.x() + bw + GAP, by, bw, H);
	}

	private void save() {
		String name = titleField().value().strip();
		if (name.isEmpty()) {
			say(T3Draw.tr("t3craft.plan.status.need_title"), Tone.WARN);
			host().relayout();
			return;
		}
		List<String> d = new ArrayList<>(deps);
		if (id == null) {
			PlanTask created = store.create(name, outcomeArea().value(), d, decisionField().value(), notesArea().value());
			id = created.id();
			say(T3Draw.tr("t3craft.plan.status.created", name), Tone.OK);
		} else if (store.edit(id, name, outcomeArea().value(), d, decisionField().value(), notesArea().value())) {
			say(T3Draw.tr("t3craft.plan.status.saved", name), Tone.OK);
		}
		host().relayout();
	}

	// ---- body -------------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		seen = store.version();
		int x = body.x();
		int w = body.w() - 10; // clear of the scrollbar
		int y = y0;
		PlanTask t = task();
		if (id != null && t == null) {
			return notice(x, y, w, T3Draw.tr("t3craft.plan.task.gone"), Tone.WARN) + GAP;
		}
		List<PlanTask> all = store.tasks();
		if (t != null) {
			y = text(x, y, w, header(t), Tone.MUTED) + 2;
			y = incoming(x, y, w, t);
		}
		if (t != null && t.stage() == PlanTask.Stage.ACCEPTED) {
			y = readOnly(x, y, w, t, all);
		} else {
			y = form(x, y, w, all);
		}
		if (t != null) {
			y = stageActions(x, y + GAP, w, t);
		}
		return y + GAP;
	}

	private static String header(PlanTask t) {
		String s = PlanText.stage(t.stage());
		if (t.origin() == PlanTask.Origin.PROPOSAL && t.source() != null) {
			s += " · " + T3Draw.tr("t3craft.plan.from", OperateClient.label(t.source().envId()), t.source().threadId());
		}
		return s + " · " + t.id();
	}

	private int incoming(int x, int y, int w, PlanTask t) {
		PlanTask.Incoming in = t.incoming();
		if (in == null) {
			return y;
		}
		y = notice(x, y, w, T3Draw.tr("t3craft.plan.incoming"), Tone.WARN);
		y = text(x + T3Draw.NOTICE_INDENT, y, w - T3Draw.NOTICE_INDENT, T3Draw.tr("t3craft.plan.incoming.title", in.title()), Tone.MUTED);
		if (!in.outcome().isBlank()) {
			for (String line : T3Draw.wrap(font(), T3Draw.tr("t3craft.plan.outcome", in.outcome()), w - T3Draw.NOTICE_INDENT, 3)) {
				y = text(x + T3Draw.NOTICE_INDENT, y, w - T3Draw.NOTICE_INDENT, line, Tone.MUTED);
			}
		}
		T3Button dismiss = new T3Button(T3Icon.X, T3Draw.tr("t3craft.plan.incoming.dismiss"), T3Button.Style.GHOST, () -> {
			store.dismissIncoming(t.id());
			host().relayout();
		});
		place(dismiss, x + T3Draw.NOTICE_INDENT, y + 2, Math.min(dismiss.preferredWidth(font()), w - T3Draw.NOTICE_INDENT), H);
		return y + H + GAP + 2;
	}

	/** The editable texts and the dependencies. */
	private int form(int x, int y, int w, List<PlanTask> all) {
		y = text(x, y, w, T3Draw.tr("t3craft.plan.field.title"), Tone.NORMAL);
		placeField(titleField(), x, y, w);
		y += H + GAP;
		y = text(x, y, w, T3Draw.tr("t3craft.plan.field.outcome"), Tone.NORMAL);
		placeTextArea(outcomeArea(), x, y, w, AREA_H);
		y += AREA_H + GAP;
		y = text(x, y, w, T3Draw.tr("t3craft.plan.field.decision"), Tone.NORMAL);
		placeField(decisionField(), x, y, w);
		y += H + GAP;
		y = text(x, y, w, T3Draw.tr("t3craft.plan.field.deps"), Tone.NORMAL);
		boolean any = false;
		for (PlanTask o : all) {
			if (o.id().equals(id)) {
				continue;
			}
			any = true;
			String label = PlanText.title(o) + "  ·  " + PlanText.stage(o.stage());
			place(new T3Check(ChatDraw.clip(font(), label, w - 20), deps.contains(o.id()), v -> {
				if (v) {
					deps.add(o.id());
				} else {
					deps.remove(o.id());
				}
			}), x, y, w, H);
			y += H;
		}
		for (String unknown : new ArrayList<>(deps)) {
			if (all.stream().noneMatch(o -> o.id().equals(unknown))) {
				any = true;
				place(new T3Check(T3Draw.tr("t3craft.plan.dep.unknown", unknown), true, v -> {
					if (!v) {
						deps.remove(unknown);
					}
				}), x, y, w, H);
				y += H;
			}
		}
		if (!any) {
			y = text(x, y, w, T3Draw.tr("t3craft.plan.field.deps.none"), Tone.MUTED);
		}
		y += GAP;
		y = text(x, y, w, T3Draw.tr("t3craft.plan.field.notes"), Tone.NORMAL);
		placeTextArea(notesArea(), x, y, w, AREA_H);
		y += AREA_H + GAP;
		return text(x, y, w, T3Draw.tr("t3craft.plan.field.save_hint"), Tone.MUTED);
	}

	/** An accepted task: its texts, no boxes. */
	private int readOnly(int x, int y, int w, PlanTask t, List<PlanTask> all) {
		if (!t.outcome().isBlank()) {
			y = text(x, y, w, T3Draw.tr("t3craft.plan.outcome", t.outcome()), Tone.NORMAL);
		}
		String d = PlanText.deps(t, all);
		if (d != null) {
			y = text(x, y, w, d, Tone.MUTED);
		}
		if (t.hasDecision()) {
			y = text(x, y, w, T3Draw.tr("t3craft.plan.decision", t.decision()), Tone.MUTED);
		}
		if (!t.notes().isBlank()) {
			y = text(x, y, w, T3Draw.tr("t3craft.plan.notes", t.notes()), Tone.MUTED);
		}
		return y;
	}

	/** What the task's stage allows, under the texts. */
	private int stageActions(int x, int y, int w, PlanTask t) {
		y = text(x, y, w, PlanText.stage(t.stage()), Tone.TITLE) + 2;
		switch (t.stage()) {
			case PROPOSED -> {
				y = notice(x, y, w, T3Draw.tr("t3craft.plan.proposal.note"), Tone.MUTED) + 2;
				y = row(x, y, w, List.of(
						new T3Button(T3Icon.CHECK, T3Draw.tr("t3craft.plan.proposal.accept"), T3Button.Style.PRIMARY,
								() -> PlanActions.acceptProposal(host(), store, t, this::say)),
						new T3Button(T3Icon.TRASH, T3Draw.tr("t3craft.plan.proposal.discard"), T3Button.Style.DANGER, () -> confirmRemove(t))));
			}
			case DRAFT -> {
				y = notice(x, y, w, T3Draw.tr("t3craft.plan.draft.note"), Tone.MUTED) + 2;
				y = row(x, y, w, List.of(
						new T3Button(T3Icon.PLAY, T3Draw.tr("t3craft.plan.launch"), T3Button.Style.PRIMARY,
								() -> host().show(new PlanLaunchPanel(store, t.id(), this::say))),
						new T3Button(T3Icon.LINK, T3Draw.tr("t3craft.plan.link"), T3Button.Style.DEFAULT,
								() -> host().show(new PlanThreadPanel(store, PlanThreadPanel.Mode.LINK, t.id(), this::say))),
						new T3Button(T3Icon.TRASH, T3Draw.tr("t3craft.plan.draft.delete"), T3Button.Style.DANGER, () -> confirmRemove(t))));
			}
			case IN_PROGRESS -> {
				y = thread(x, y, w, t);
				y = feedback(x, y, w, t);
				y = marks(x, y, w, t);
				y = row(x, y, w, List.of(
						new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.plan.open_thread"), T3Button.Style.DEFAULT,
								() -> PlanActions.openThread(host(), t.link().ref())),
						new T3Button(T3Icon.X, T3Draw.tr("t3craft.plan.unlink"), T3Button.Style.GHOST, () -> confirmUnlink(t))));
			}
			case IN_REVIEW -> {
				y = thread(x, y, w, t);
				y = feedback(x, y, w, t);
				y = marks(x, y, w, t);
				y = row(x, y, w, List.of(
						new T3Button(T3Icon.CIRCLE_CHECK, T3Draw.tr("t3craft.plan.accept"), T3Button.Style.PRIMARY,
								() -> PlanActions.acceptDelivery(host(), store, t, this::say)),
						new T3Button(T3Icon.SEND, T3Draw.tr("t3craft.plan.send_back"), T3Button.Style.DEFAULT,
								() -> PlanActions.sendBack(host(), font(), store, t, this::say)),
						new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.plan.open_thread"), T3Button.Style.DEFAULT,
								() -> PlanActions.openThread(host(), t.link().ref()))));
			}
			case ACCEPTED -> {
				if (t.link() != null) {
					y = thread(x, y, w, t);
				}
				y = text(x, y, w, T3Draw.tr("t3craft.plan.mark.implemented.yes"), Tone.OK);
				y = text(x, y, w, t.agentChecked().isBlank() ? T3Draw.tr("t3craft.plan.mark.checked.none")
						: T3Draw.tr("t3craft.plan.mark.checked", t.agentChecked()), Tone.MUTED);
				y = text(x, y, w, T3Draw.tr(t.userTested() ? "t3craft.plan.mark.tested.yes" : "t3craft.plan.mark.tested.no"), Tone.MUTED);
				y = notice(x, y + 2, w, T3Draw.tr("t3craft.plan.accepted.note"), Tone.MUTED);
				if (t.link() != null) {
					y = row(x, y + 2, w, List.of(new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.plan.open_thread"), T3Button.Style.DEFAULT,
							() -> PlanActions.openThread(host(), t.link().ref()))));
				}
			}
		}
		return y;
	}

	private int thread(int x, int y, int w, PlanTask t) {
		PlanTask.Link l = t.link();
		if (l == null) {
			return y;
		}
		ThreadShell s = PlanClient.thread(l.ref());
		y = text(x, y, w, PlanText.threadLine(l.ref(), s), Tone.NORMAL);
		return text(x, y, w, T3Draw.tr(l.launched() ? "t3craft.plan.thread.launched" : "t3craft.plan.thread.linked"), Tone.MUTED) + 2;
	}

	private int feedback(int x, int y, int w, PlanTask t) {
		if (t.feedback() == null || t.feedback().isBlank()) {
			return y;
		}
		for (String line : T3Draw.wrap(font(), T3Draw.tr("t3craft.plan.feedback.last", t.feedback()), w, 3)) {
			y = text(x, y, w, line, Tone.MUTED);
		}
		return y + 2;
	}

	/** The three marks, apart: implemented (from the thread), checked by the agent (declared text), tested by the player. */
	private int marks(int x, int y, int w, PlanTask t) {
		boolean implemented = t.stage() == PlanTask.Stage.IN_REVIEW;
		y = text(x, y, w, T3Draw.tr(implemented ? "t3craft.plan.mark.implemented.yes" : "t3craft.plan.mark.implemented.no"),
				implemented ? Tone.OK : Tone.MUTED) + 2;
		y = text(x, y, w, T3Draw.tr("t3craft.plan.mark.checked.label"), Tone.NORMAL);
		placeTextArea(checkedArea(), x, y, w, AREA_H);
		y += AREA_H + 2;
		T3Button keep = new T3Button(T3Draw.tr("t3craft.plan.mark.checked.save"), T3Button.Style.DEFAULT, () -> {
			if (store.setAgentChecked(t.id(), checkedArea().value())) {
				say(T3Draw.tr("t3craft.plan.status.checked_saved"), Tone.OK);
			}
			host().relayout();
		});
		place(keep, x, y, Math.min(keep.preferredWidth(font()), w), H);
		y += H + GAP;
		place(new T3Check(T3Draw.tr("t3craft.plan.mark.tested.label"), t.userTested(), v -> {
			store.setUserTested(t.id(), v);
			host().relayout();
		}), x, y, w, H);
		return y + H + GAP;
	}

	private int row(int x, int y, int w, List<T3Button> buttons) {
		int bx = x;
		for (T3Button b : buttons) {
			int bw = Math.min(b.preferredWidth(font()), x + w - bx);
			if (bw <= 0) {
				break;
			}
			place(b, bx, y, bw, H);
			bx += bw + GAP;
		}
		return y + H + GAP;
	}

	private void confirmRemove(PlanTask t) {
		host().modal(T3Dialog.confirm(T3Draw.tr("t3craft.plan.remove.title"), List.of(PlanText.title(t)), T3Draw.tr("t3craft.plan.remove.note"),
				T3Draw.tr("t3craft.plan.remove.yes"), true, () -> {
					host().modal(null);
					if (store.remove(t.id())) {
						parentSay.say(T3Draw.tr("t3craft.plan.status.removed", PlanText.title(t)), Tone.MUTED);
						host().back();
					}
				}, () -> host().modal(null)));
	}

	private void confirmUnlink(PlanTask t) {
		host().modal(T3Dialog.confirm(T3Draw.tr("t3craft.plan.unlink.title"), List.of(PlanText.title(t)), T3Draw.tr("t3craft.plan.unlink.note"),
				T3Draw.tr("t3craft.plan.unlink.yes"), false, () -> {
					host().modal(null);
					if (store.unlink(t.id())) {
						say(T3Draw.tr("t3craft.plan.status.unlinked", PlanText.title(t)), Tone.MUTED);
					}
					host().relayout();
				}, () -> host().modal(null)));
	}
}
