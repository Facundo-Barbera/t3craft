package dev.t3craft.client.plan;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatEntry;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.ThreadCache;
import dev.t3craft.client.chat.ThreadDiskCache;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Check;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.plan.PlanProposals;
import dev.t3craft.plan.PlanReview;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Picks a thread (environment, then one of its threads from the shell the client already has) to either
 * <ul>
 *   <li><b>link</b> a draft to it (nothing is sent: the thread keeps doing whatever it does; by default only its next
 *   finished turn counts as the delivery, or right away with "already delivered"), or</li>
 *   <li><b>import proposals</b> from it: the newest message with a {@code t3craft-plan} block among the messages the
 *   client already holds for that thread (the chat's transcript cache, and the shell's latest message). Nothing is
 *   fetched: when the block is not there, the page says to open the thread first.</li>
 * </ul>
 */
final class PlanThreadPanel extends T3Panel {
	enum Mode {
		LINK, IMPORT
	}

	private static final int H = T3Control.H;
	private static final int GAP = 4;

	private final PlanStore store;
	private final Mode mode;
	private final @Nullable String taskId;
	private final PlanActions.Say parentSay;
	private @Nullable String envId;
	private @Nullable String threadId;
	private boolean alreadyDelivered;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	PlanThreadPanel(PlanStore store, Mode mode, @Nullable String taskId, PlanActions.Say parentSay) {
		this.store = store;
		this.mode = mode;
		this.taskId = taskId;
		this.parentSay = parentSay;
	}

	@Override
	public String title() {
		return T3Draw.tr(mode == Mode.LINK ? "t3craft.plan.link.title" : "t3craft.plan.import.title");
	}

	private void say(String text, Tone tone) {
		status = text;
		statusTone = tone;
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
		int bw = Math.min(120, (f.w() - 2 * GAP) / 3);
		T3Button go = mode == Mode.LINK
				? new T3Button(T3Icon.LINK, T3Draw.tr("t3craft.plan.link.go"), T3Button.Style.PRIMARY, this::link)
				: new T3Button(T3Icon.DOWNLOAD, T3Draw.tr("t3craft.plan.import.go"), T3Button.Style.PRIMARY, this::importNow);
		go.setEnabled(envId != null && threadId != null);
		pin(go, f.x(), by, bw, H);
		T3Button open = new T3Button(T3Icon.MESSAGE_SQUARE, T3Draw.tr("t3craft.plan.open_thread"), T3Button.Style.DEFAULT, () -> {
			if (envId != null && threadId != null) {
				PlanActions.openThread(host(), new PlanTask.ThreadRef(envId, threadId));
			}
		});
		open.setEnabled(envId != null && threadId != null);
		pin(open, f.x() + bw + GAP, by, bw, H);
		pin(new T3Button(T3Icon.ARROW_LEFT, T3Draw.tr("t3craft.plan.back"), T3Button.Style.DEFAULT, () -> host().back()),
				f.x() + 2 * (bw + GAP), by, bw, H);
	}

	// ---- body ---------------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		int x = body.x();
		int w = body.w() - 10;
		int y = y0;
		if (mode == Mode.LINK) {
			PlanTask t = taskId == null ? null : store.get(taskId);
			if (t == null || t.stage() != PlanTask.Stage.DRAFT) {
				threadId = null;
				return notice(x, y, w, T3Draw.tr("t3craft.plan.link.not_draft"), Tone.WARN) + GAP;
			}
			y = text(x, y, w, PlanText.title(t), Tone.TITLE) + 2;
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.link.note"), Tone.MUTED) + GAP;
		} else {
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.import.note", PlanProposals.FENCE), Tone.MUTED) + GAP;
		}

		List<String> envs = LaunchClient.environments().stream().map(Environments.Environment::id).toList();
		if (envs.isEmpty()) {
			threadId = null;
			return notice(x, y, w, T3Draw.tr("t3craft.plan.no_envs"), Tone.WARN) + GAP;
		}
		if (envId == null || !envs.contains(envId)) {
			envId = envs.getFirst();
			threadId = null;
		}
		y = text(x, y, w, T3Draw.tr("t3craft.plan.env"), Tone.NORMAL);
		place(new T3Select<>(envs, OperateClient::label, envId, v -> {
			envId = v;
			threadId = null;
			host().relayout();
		}), x, y, w, H);
		y += H + GAP;

		List<ThreadShell> threads = threads(envId);
		y = text(x, y, w, T3Draw.tr("t3craft.plan.thread.pick"), Tone.NORMAL);
		if (threads.isEmpty()) {
			threadId = null;
			return notice(x, y, w, T3Draw.tr("t3craft.plan.no_threads"), Tone.WARN) + GAP;
		}
		ThreadShell current = threads.stream().filter(s -> s.id().equals(threadId)).findFirst().orElse(threads.getFirst());
		threadId = current.id();
		place(new T3Select<>(threads, s -> (s.title().isBlank() ? s.id() : s.title()) + "  · " + PlanText.state(s), current, v -> {
			threadId = v.id();
			host().relayout();
		}), x, y, w, H);
		y += H + GAP;
		y = text(x, y, w, PlanText.threadLine(new PlanTask.ThreadRef(envId, current.id()), current), Tone.MUTED) + GAP;
		if (mode == Mode.LINK) {
			place(new T3Check(T3Draw.tr("t3craft.plan.link.delivered"), alreadyDelivered, v -> {
				alreadyDelivered = v;
				host().relayout();
			}), x, y, w, H);
			y += H + 2;
			y = text(x, y, w, T3Draw.tr(alreadyDelivered ? "t3craft.plan.link.delivered.yes" : "t3craft.plan.link.delivered.no"), Tone.MUTED);
		}
		return y + GAP;
	}

	/** The environment's live threads (not archived or deleted), most recent activity first. */
	private static List<ThreadShell> threads(@Nullable String envId) {
		ShellState.View v = envId == null ? null : LaunchClient.view(envId);
		if (v == null) {
			return List.of();
		}
		List<ThreadShell> out = new ArrayList<>();
		for (ThreadShell t : v.threads()) {
			if (t.archivedAt() == null && t.deletedAt() == null) {
				out.add(t);
			}
		}
		out.sort(Comparator.comparingLong(PlanThreadPanel::activity).reversed().thenComparing(ThreadShell::id));
		return out;
	}

	private static long activity(ThreadShell t) {
		long best = 0;
		for (Long x : new Long[] {t.latestMessageAt(), t.latestRunCompletedAt(), t.latestRunStartedAt(), t.createdAt()}) {
			if (x != null && x > best) {
				best = x;
			}
		}
		return best;
	}

	// ---- actions ------------------------------------------------------------------------------------------------------

	private void link() {
		String env = envId;
		String thread = threadId;
		if (taskId == null || env == null || thread == null) {
			return;
		}
		PlanTask.ThreadRef ref = new PlanTask.ThreadRef(env, thread);
		Long baseline = alreadyDelivered ? null : PlanReview.baselineFor(PlanClient.thread(ref), System.currentTimeMillis());
		if (store.link(taskId, ref, false, baseline)) {
			PlanTask t = store.get(taskId);
			parentSay.say(T3Draw.tr("t3craft.plan.status.linked", t == null ? taskId : PlanText.title(t)), Tone.OK);
			PlanClient.syncReview();
			host().back();
		} else {
			say(T3Draw.tr("t3craft.plan.link.not_draft"), Tone.WARN);
			host().relayout();
		}
	}

	private void importNow() {
		String env = envId;
		String thread = threadId;
		if (env == null || thread == null) {
			return;
		}
		PlanTask.ThreadRef ref = new PlanTask.ThreadRef(env, thread);
		PlanProposals.Parsed p = PlanProposals.latest(messages(ref));
		if (!p.found()) {
			say(T3Draw.tr("t3craft.plan.import.none", PlanProposals.FENCE), Tone.WARN);
			host().relayout();
			return;
		}
		if (p.tasks().isEmpty()) {
			say(T3Draw.tr("t3craft.plan.import.invalid", p.problems().isEmpty() ? "?" : p.problems().getFirst()), Tone.WARN);
			host().relayout();
			return;
		}
		PlanStore.ImportResult r = store.importProposals(ref, p.tasks());
		String line = T3Draw.tr("t3craft.plan.import.done", r.added(), r.refreshed(), r.unchanged(), r.conflicts())
				+ (p.problems().isEmpty() ? "" : " " + T3Draw.tr("t3craft.plan.import.skipped", p.problems().size()));
		parentSay.say(line, r.conflicts() > 0 || !p.problems().isEmpty() ? Tone.WARN : Tone.OK);
		host().back();
	}

	/**
	 * The agent messages the client already holds for {@code ref}, oldest first: the chat transcript cache (threads
	 * opened lately), then the shell's latest message. Reads memory only; nothing is fetched or subscribed.
	 */
	private static List<String> messages(PlanTask.ThreadRef ref) {
		List<String> out = new ArrayList<>();
		for (ThreadCache.Entry e : ThreadCache.get().recent(ThreadCache.MAX_ENTRIES)) {
			if (!e.key().equals(ref.key())) {
				continue;
			}
			for (ChatEntry c : e.transcript().tail(ThreadDiskCache.MAX_ROWS)) {
				if (c.kind() == ChatEntry.Kind.ASSISTANT || c.kind() == ChatEntry.Kind.PLAN) {
					out.add(c.body());
				}
			}
		}
		ThreadShell s = PlanClient.thread(ref);
		if (s != null && s.latestMessageText() != null) {
			out.add(s.latestMessageText());
		}
		return out;
	}
}
