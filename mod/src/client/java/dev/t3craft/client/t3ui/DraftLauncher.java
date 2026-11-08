package dev.t3craft.client.t3ui;

import com.google.gson.JsonObject;
import dev.t3craft.client.chat.compose.ChatSender;
import dev.t3craft.client.chat.compose.ImageAttachment;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.NewThreadForm;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadLaunch;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;

/**
 * Launches a {@link ThreadDraft} with its first message: the same {@code orchestration.launchThread}
 * the old dialog sent ({@link NewThreadForm#build}: project, title from the prompt, model / modes
 * from the model chip or the project default, workspace strategy Local or New worktree), through
 * {@code OperateGate} ({@link OperateUi#run}). Images go first with
 * {@code assets.persistChatAttachments} under the new thread id and ride in
 * {@code initialMessage.attachments}, as T3's {@code startThreadTurn} does for a draft.
 */
final class DraftLauncher {
	private DraftLauncher() {
	}

	/** Called with the new thread's {@code env} and id once T3 accepted it. */
	interface Launched {
		void launched(String envId, String threadId);
	}

	static void launch(Screen screen, ThreadDraft d, String text, List<ImageAttachment> images, Launched launched, Consumer<OperateResult> done) {
		NewThreadForm f = new NewThreadForm();
		f.envId = d.envId;
		f.projectId = d.projectId;
		f.prompt = text.isBlank() && !images.isEmpty() ? "(image)" : text;
		f.pick = d.pick;
		f.workspace = d.workspace.workspace;
		f.baseRef = d.workspace.baseRef;
		f.branch = d.workspace.branch;
		f.startFromOrigin = d.workspace.startFromOrigin;
		ThreadLaunch.ModelChoice def = ActionsClient.projectDefault(d.envId, d.projectId);
		ThreadLaunch.Ids ids = ThreadLaunch.Ids.random();
		NewThreadForm.Built b = f.build(OperateUi.canOperate(d.envId), def, () -> ids);
		if (b.action() == null) {
			done.accept(OperateResult.of(OperateResult.Outcome.REJECTED, b.error()));
			return;
		}
		ChatSender sender = new ChatSender(OperateUi.gate(), (a, cb) -> OperateUi.run(a, OperateGate.Origin.UI, screen, cb));
		String env = d.envId;
		d.launching = true;
		sender.upload(env, ids.threadId(), ids.messageId(), images, (stored, failure) -> {
			if (failure != null || stored == null) {
				d.launching = false;
				done.accept(failure != null ? failure : OperateResult.of(OperateResult.Outcome.REJECTED, "Image upload failed."));
				return;
			}
			if (stored.size() > 0) {
				JsonObject msg = b.action().payload().getAsJsonObject("initialMessage");
				msg.add("attachments", stored.deepCopy());
				if (text.isBlank()) {
					msg.addProperty("text", "");
				}
			}
			sender.run(b.action(), r -> {
				d.launching = false;
				done.accept(r);
				if (r.ok()) {
					String threadId = ThreadLaunch.threadId(r);
					launched.launched(env, threadId != null ? threadId : ids.threadId());
				}
			});
		});
	}
}
