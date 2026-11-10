package dev.t3craft.client.plan;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.NewThreadForm;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3TextArea;
import dev.t3craft.plan.PlanStore;
import dev.t3craft.plan.PlanTask;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.ThreadLaunch;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Launches a draft as a new thread: environment, project, workspace and the first message (built from the task, editable
 * here). The very {@code orchestration.launchThread} of the T3 screen's draft launcher ({@link NewThreadForm#build}, the
 * project's default model), through {@link OperateUi#run} / {@code OperateGate}, after a yes in a dialog. Once T3 took it,
 * the task is linked to the new thread and becomes "in progress".
 */
final class PlanLaunchPanel extends T3Panel {
	private static final int H = T3Control.H;
	private static final int GAP = 4;
	private static final int PROMPT_H = 110;

	private final PlanStore store;
	private final String taskId;
	private final PlanActions.Say parentSay;
	private @Nullable String envId;
	private @Nullable String projectId;
	private NewThreadForm.Workspace workspace = NewThreadForm.Workspace.LOCAL;
	private @Nullable T3Field baseRef;
	private @Nullable T3TextArea prompt;
	private boolean busy;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	PlanLaunchPanel(PlanStore store, String taskId, PlanActions.Say parentSay) {
		this.store = store;
		this.taskId = taskId;
		this.parentSay = parentSay;
		List<String> operable = LaunchClient.operableEnvs();
		this.envId = operable.isEmpty() ? null : operable.getFirst();
	}

	@Override
	public String title() {
		return T3Draw.tr("t3craft.plan.launch.title");
	}

	private void say(String text, Tone tone) {
		status = text;
		statusTone = tone;
	}

	private T3TextArea promptArea() {
		if (prompt == null) {
			prompt = new T3TextArea(font(), T3Draw.tr("t3craft.plan.launch.prompt")).maxLength(ThreadLaunch.MAX_PROMPT);
			PlanTask t = store.get(taskId);
			if (t != null) {
				prompt.setValue(PlanText.prompt(t, store.tasks()));
			}
		}
		return prompt;
	}

	private T3Field baseRefField() {
		if (baseRef == null) {
			baseRef = new T3Field(font(), T3Draw.tr("t3craft.plan.launch.base")).maxLength(200).hint("origin/main");
		}
		return baseRef;
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
		int bw = Math.min(120, (f.w() - GAP) / 2);
		T3Button go = pin(new T3Button(T3Icon.PLAY, T3Draw.tr("t3craft.plan.launch.go"), T3Button.Style.PRIMARY, this::confirmLaunch),
				f.x(), by, bw, H);
		go.setEnabled(!busy && envId != null && projectId != null);
		if (envId != null) {
			OperateUi.guard(go, envId);
		}
		pin(new T3Button(T3Icon.ARROW_LEFT, T3Draw.tr("t3craft.plan.back"), T3Button.Style.DEFAULT, () -> host().back()),
				f.x() + bw + GAP, by, bw, H);
	}

	// ---- body ---------------------------------------------------------------------------------------------------------

	@Override
	protected int arrange(Rect body, int y0) {
		int x = body.x();
		int w = body.w() - 10;
		int y = y0;
		PlanTask t = store.get(taskId);
		if (t == null || t.stage() != PlanTask.Stage.DRAFT) {
			return notice(x, y, w, T3Draw.tr("t3craft.plan.launch.not_draft"), Tone.WARN) + GAP;
		}
		y = text(x, y, w, PlanText.title(t), Tone.TITLE) + 2;
		y = notice(x, y, w, T3Draw.tr("t3craft.plan.launch.note"), Tone.MUTED) + GAP;

		List<String> envs = LaunchClient.environments().stream().map(Environments.Environment::id).toList();
		if (envs.isEmpty()) {
			return notice(x, y, w, T3Draw.tr("t3craft.plan.no_envs"), Tone.WARN) + GAP;
		}
		if (envId == null || !envs.contains(envId)) {
			envId = envs.getFirst();
		}
		y = text(x, y, w, T3Draw.tr("t3craft.plan.env"), Tone.NORMAL);
		place(new T3Select<>(envs, PlanLaunchPanel::envLabel, envId, v -> {
			envId = v;
			projectId = null;
			host().relayout();
		}), x, y, w, H);
		y += H + 2;
		if (!OperateUi.canOperate(envId)) {
			y = notice(x, y, w, T3Draw.tr(OperateUi.REQUIRES_KEY), Tone.WARN);
			String env = envId;
			T3Button enable = new T3Button(T3Draw.tr("t3craft.plan.enable_control"), T3Button.Style.DEFAULT,
					() -> host().openScreen(OperateUi.enableControlScreen(host().screen(), env)));
			place(enable, x, y + 2, Math.min(enable.preferredWidth(font()), w), H);
			y += H + 4;
		}
		y += GAP;

		List<ThreadLaunch.ProjectChoice> projects = LaunchClient.projects(envId);
		y = text(x, y, w, T3Draw.tr("t3craft.plan.project"), Tone.NORMAL);
		if (projects.isEmpty()) {
			projectId = null;
			y = notice(x, y, w, T3Draw.tr("t3craft.plan.no_projects"), Tone.WARN) + GAP;
		} else {
			ThreadLaunch.ProjectChoice current = projects.stream().filter(p -> p.id().equals(projectId)).findFirst().orElse(projects.getFirst());
			projectId = current.id();
			place(new T3Select<>(projects, ThreadLaunch.ProjectChoice::label, current, v -> {
				projectId = v.id();
				host().relayout();
			}), x, y, w, H);
			y += H + GAP;
			if (ActionsClient.projectDefault(envId, projectId) == null) {
				y = notice(x, y, w, T3Draw.tr("t3craft.plan.no_model"), Tone.WARN) + GAP;
			}
		}

		y = text(x, y, w, T3Draw.tr("t3craft.plan.workspace"), Tone.NORMAL);
		place(new T3Select<>(List.of(NewThreadForm.Workspace.values()), ws -> ws.label, workspace, v -> {
			workspace = v;
			host().relayout();
		}), x, y, w, H);
		y += H + GAP;
		if (workspace == NewThreadForm.Workspace.WORKTREE) {
			y = text(x, y, w, T3Draw.tr("t3craft.plan.launch.base"), Tone.NORMAL);
			placeField(baseRefField(), x, y, w);
			y += H + GAP;
		}

		y = text(x, y, w, T3Draw.tr("t3craft.plan.launch.prompt"), Tone.NORMAL);
		placeTextArea(promptArea(), x, y, w, PROMPT_H);
		return y + PROMPT_H + GAP;
	}

	private static String envLabel(String id) {
		return OperateClient.label(id) + (OperateUi.canOperate(id) ? "" : "  · " + T3Draw.tr("t3craft.plan.read_only"));
	}

	// ---- launch -----------------------------------------------------------------------------------------------------

	private void confirmLaunch() {
		String env = envId;
		String project = projectId;
		if (busy || env == null || project == null) {
			return;
		}
		NewThreadForm f = new NewThreadForm();
		f.envId = env;
		f.projectId = project;
		f.prompt = promptArea().value();
		f.pick = null; // the project's default model
		f.workspace = workspace;
		f.baseRef = workspace == NewThreadForm.Workspace.WORKTREE ? baseRefField().value() : "";
		ThreadLaunch.Ids ids = ThreadLaunch.Ids.random();
		NewThreadForm.Built b = f.build(OperateUi.canOperate(env), ActionsClient.projectDefault(env, project), () -> ids);
		if (b.action() == null) {
			say(b.error() == null ? "?" : b.error(), Tone.WARN);
			host().relayout();
			return;
		}
		String projectLabel = LaunchClient.projects(env).stream().filter(p -> p.id().equals(project)).map(ThreadLaunch.ProjectChoice::label)
				.findFirst().orElse(project);
		host().modal(T3Dialog.confirm(T3Draw.tr("t3craft.plan.launch.confirm"),
				List.of(T3Draw.tr("t3craft.plan.launch.confirm.where", OperateClient.label(env), projectLabel), ThreadLaunch.promptSummary(f.prompt)),
				T3Draw.tr("t3craft.plan.launch.confirm.note"), T3Draw.tr("t3craft.plan.launch.go"), false, () -> {
					host().modal(null);
					launch(env, b, ids);
				}, () -> host().modal(null)));
	}

	private void launch(String env, NewThreadForm.Built b, ThreadLaunch.Ids ids) {
		busy = true;
		say(T3Draw.tr("t3craft.plan.status.launching"), Tone.MUTED);
		host().relayout();
		OperateUi.run(b.action(), OperateGate.Origin.UI, host().screen(), r -> {
			busy = false;
			if (r.ok()) {
				String thread = ThreadLaunch.threadId(r);
				store.link(taskId, new PlanTask.ThreadRef(env, thread != null ? thread : ids.threadId()), true, null);
				PlanTask t = store.get(taskId);
				parentSay.say(T3Draw.tr("t3craft.plan.status.launched", t == null ? taskId : PlanText.title(t)), Tone.OK);
				host().back();
			} else {
				say(PlanActions.result(r), Tone.ERROR);
				host().relayout();
			}
		});
	}
}
