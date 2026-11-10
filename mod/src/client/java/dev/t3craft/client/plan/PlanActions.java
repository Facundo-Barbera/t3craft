package dev.t3craft.client.plan;

import dev.t3craft.client.chat.ChatScreens;
import dev.t3craft.client.chat.compose.ChatSender;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3TextArea;
import dev.t3craft.plan.PlanReview;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadShell;
import java.util.List;
import net.minecraft.client.gui.Font;

/**
 * The plan's actions shared by the list and the task page. Only {@link #sendBack} writes to T3 (one message through
 * {@link OperateUi} / {@code OperateGate}); the rest only change the local plan or open a screen.
 */
final class PlanActions {
	/** Where an action reports, for the page's status line. */
	@FunctionalInterface
	interface Say {
		void say(String text, Tone tone);
	}

	private PlanActions() {
	}

	/** Opens the linked thread's chat; Esc there comes back to the plan. Reading only: nothing is sent. */
	static void openThread(T3Panel.Host host, PlanTask.ThreadRef ref) {
		host.openScreen(ChatScreens.create(ref.key()).withParent(host.screen()));
	}

	/** A proposal enters the plan as a draft. */
	static void acceptProposal(T3Panel.Host host, PlanStore store, PlanTask t, Say say) {
		if (store.acceptProposal(t.id())) {
			say.say(T3Draw.tr("t3craft.plan.status.proposal_accepted", PlanText.title(t)), Tone.OK);
		}
		host.relayout();
	}

	/** Asks, then accepts the delivery. Nothing else is launched or authorised. */
	static void acceptDelivery(T3Panel.Host host, PlanStore store, PlanTask t, Say say) {
		List<String> lines = List.of(PlanText.title(t), T3Draw.tr("t3craft.plan.accept.marks", mark(!t.agentChecked().isBlank()),
				mark(t.userTested())));
		host.modal(T3Dialog.confirm(T3Draw.tr("t3craft.plan.accept.title"), lines, T3Draw.tr("t3craft.plan.accept.note"),
				T3Draw.tr("t3craft.plan.accept.yes"), false, () -> {
					host.modal(null);
					if (store.accept(t.id())) {
						say.say(T3Draw.tr("t3craft.plan.status.accepted", PlanText.title(t)), Tone.OK);
					}
					host.relayout();
				}, () -> host.modal(null)));
	}

	private static String mark(boolean yes) {
		return T3Draw.tr(yes ? "t3craft.plan.mark.yes" : "t3craft.plan.mark.no");
	}

	/**
	 * The "send observations back" dialog: one message to the linked thread through {@code OperateGate}; once T3 took
	 * it, the task goes back to "in progress" and only the thread's next finished turn brings it to review again.
	 */
	static void sendBack(T3Panel.Host host, Font font, PlanStore store, PlanTask t, Say say) {
		PlanTask.Link link = t.link();
		if (link == null) {
			return;
		}
		boolean can = OperateUi.canOperate(link.envId());
		T3TextArea area = new T3TextArea(font, T3Draw.tr("t3craft.plan.feedback.label")).maxLength(4_000)
				.hint(T3Draw.tr("t3craft.plan.feedback.hint"));
		String[] error = {""};
		T3Dialog d = new T3Dialog(T3Draw.tr("t3craft.plan.feedback.title"), 380);
		d.closable();
		d.text(PlanText.threadLine(link.ref(), PlanClient.thread(link.ref())), Tone.MUTED);
		if (!can) {
			d.notice(T3Draw.tr(OperateUi.REQUIRES_KEY), Tone.WARN);
		}
		d.textArea(T3Draw.tr("t3craft.plan.feedback.label"), area, 76);
		d.text(() -> error[0], () -> Tone.ERROR, 1);
		Runnable close = () -> host.modal(null);
		T3Button send = new T3Button(T3Draw.tr("t3craft.plan.feedback.send"), T3Button.Style.PRIMARY, () -> {
			String text = area.value().strip();
			if (text.isEmpty()) {
				error[0] = T3Draw.tr("t3craft.plan.feedback.empty");
				return;
			}
			close.run();
			send(host, store, t, link, text, say);
		});
		send.setEnabled(can);
		if (!can) {
			send.setTooltip(T3Draw.tr(OperateUi.REQUIRES_KEY));
		}
		d.buttons(send, new T3Button(T3Draw.tr("t3craft.plan.cancel"), close));
		d.onCancel(close);
		d.initialFocus(area);
		host.modal(d);
	}

	private static void send(T3Panel.Host host, PlanStore store, PlanTask t, PlanTask.Link link, String text, Say say) {
		ThreadShell before = PlanClient.thread(link.ref());
		long baseline = PlanReview.baselineFor(before, System.currentTimeMillis());
		String message = T3Draw.tr("t3craft.plan.feedback.header", PlanText.title(t)) + "\n\n" + text;
		say.say(T3Draw.tr("t3craft.plan.status.sending"), Tone.MUTED);
		host.relayout();
		ChatSender sender = new ChatSender(OperateUi.gate(), (a, cb) -> OperateUi.run(a, OperateGate.Origin.UI, host.screen(), cb));
		String title = before == null || before.title().isBlank() ? null : before.title();
		sender.send(link.envId(), link.threadId(), title, message, sender.newIds(), r -> {
			if (r.ok()) {
				store.returned(t.id(), text, baseline);
				say.say(T3Draw.tr("t3craft.plan.status.sent_back", PlanText.title(t)), Tone.OK);
			} else {
				say.say(result(r), r.outcome() == OperateResult.Outcome.CANCELLED ? Tone.MUTED : Tone.ERROR);
			}
			host.relayout();
		});
	}

	/** One line for a T3 answer that was not OK. */
	static String result(OperateResult r) {
		return T3Draw.tr("t3craft.plan.status.failed", r.outcome().wire, r.message());
	}
}
