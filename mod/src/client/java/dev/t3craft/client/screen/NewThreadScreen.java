package dev.t3craft.client.screen;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.search.SearchClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadLaunch;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * "New thread" (item 23, feat/op-launch): pick an environment (only those with control/operate),
 * a project (read-only from that environment's shell) and a model (optional: the ones the shell
 * exposes, else the project's default), write a multi-line prompt, review a summary and launch.
 * Not destructive, so no confirmation screen, but "Launch" only appears on the summary page. The
 * write goes through {@link LaunchClient#launch} → {@code OperateUi.run}; the new NPC appears
 * through the normal shell flow.
 *
 * <p>Entry points: {@link #open(String, String)} (blocks such as a lectern/board call this with a
 * project preselected), the portable terminal's "New thread…" button. A kit dialog
 * ({@link T3DialogScreen}, docs/features/ui-kit.md): three {@link T3Select}s and the prompt, which is
 * still the vanilla {@link MultiLineEditBox} (the kit has no multi-line field yet; the composer of
 * {@code T3Screen} is the real way to write a prompt since feat/t3-composer).
 */
public final class NewThreadScreen extends T3DialogScreen {
	private static final int W = 380;

	private @Nullable String env;
	private @Nullable String projectId;
	/** Null = project default. */
	private ThreadLaunch.@Nullable ModelChoice model;
	private String prompt = "";
	private boolean review;
	private boolean busy;
	private @Nullable OperateResult result;
	private @Nullable String resultEnv;
	private String message = "";
	private Tone messageTone = Tone.MUTED;

	private @Nullable MultiLineEditBox promptBox;

	public NewThreadScreen(@Nullable Screen parent, @Nullable String env, @Nullable String projectId) {
		super(Component.translatable("t3craft.operate.launch.title"), parent);
		this.env = env;
		this.projectId = projectId;
	}

	/**
	 * "New thread" with {@code env} and {@code projectId} preselected (either may be null).
	 * Hook for blocks (feat/blocks-core): {@code NewThreadScreen.open(env, projectId)}. Client thread.
	 * feat/t3-composer: as in T3 Code there is no dialog: the T3 screen opens on a draft thread of
	 * that project (composer with model, project and workspace chips); the first message launches it.
	 */
	public static void open(@Nullable String env, @Nullable String projectId) {
		Minecraft.getInstance().gui.setScreen(dev.t3craft.client.chat.ChatScreens.create(null).withDraft(env, projectId));
	}

	// ---- choices --------------------------------------------------------------------------------

	private List<String> envs() {
		return LaunchClient.operableEnvs();
	}

	private List<ThreadLaunch.ProjectChoice> projects() {
		return env == null ? List.of() : LaunchClient.projects(env);
	}

	private List<ThreadLaunch.ModelChoice> models() {
		return env == null || projectId == null ? List.of() : ThreadLaunch.models(LaunchClient.view(env), projectId);
	}

	/** Keeps the selection valid (env with operate, known project, exposed model). */
	private void normalize() {
		List<String> es = envs();
		if (env == null || !es.contains(env)) {
			env = es.isEmpty() ? null : es.getFirst();
		}
		List<ThreadLaunch.ProjectChoice> ps = projects();
		if (projectId == null || ps.stream().noneMatch(p -> p.id().equals(projectId))) {
			projectId = ps.isEmpty() ? null : ps.getFirst().id();
		}
		if (model != null && !models().contains(model)) {
			model = null;
		}
	}

	private ThreadLaunch.@Nullable ProjectChoice project() {
		return projects().stream().filter(p -> p.id().equals(projectId)).findFirst().orElse(null);
	}

	/** The text of the "project default" model choice, with the model it resolves to. */
	private String defaultModelText() {
		ThreadLaunch.ModelChoice d = env == null || projectId == null ? null : ThreadLaunch.resolveModel(LaunchClient.view(env), projectId, null);
		return d == null ? T3Draw.tr("t3craft.operate.launch.model.none") : T3Draw.tr("t3craft.operate.launch.model.default", d.label());
	}

	private String modelText() {
		return model != null ? model.label() : defaultModelText();
	}

	/** "Environment:" from the "Environment: %s" key, so the label stays translatable. */
	private static String labelOf(String key) {
		return T3Draw.template(key)[0].strip();
	}

	private String envText() {
		return env == null ? "—" : OperateClient.label(env);
	}

	// ---- dialog ---------------------------------------------------------------------------------

	@Override
	protected void init() {
		if (promptBox != null) {
			prompt = promptBox.getValue(); // typed before a rebuild
		}
		promptBox = null;
		super.init();
		if (promptBox != null) {
			setInitialFocus(promptBox);
		}
	}

	@Override
	protected T3Dialog buildDialog() {
		normalize();
		T3Dialog d = new T3Dialog(title.getString(), W);
		String cancel = CommonComponents.GUI_CANCEL.getString();
		if (!review) {
			T3Select<String> envSelect = new T3Select<>(envs(), OperateClient::label, env, v -> {
				env = v;
				projectId = null;
				model = null;
				rebuildWidgets();
			});
			envSelect.setEnabled(envs().size() > 1);
			List<ThreadLaunch.ProjectChoice> ps = projects();
			T3Select<ThreadLaunch.ProjectChoice> projectSelect = new T3Select<>(ps, ThreadLaunch.ProjectChoice::label, project(), v -> {
				projectId = v.id();
				model = null;
				rebuildWidgets();
			});
			projectSelect.setEnabled(ps.size() > 1);
			List<ThreadLaunch.ModelChoice> choices = new ArrayList<>();
			choices.add(null); // "project default"
			choices.addAll(models());
			T3Select<ThreadLaunch.ModelChoice> modelSelect = new T3Select<>(choices, m -> m == null ? defaultModelText() : m.label(), model, v -> model = v);
			modelSelect.setEnabled(!models().isEmpty());
			T3Button reviewButton = new T3Button(T3Draw.tr("t3craft.operate.launch.review"), T3Button.Style.PRIMARY, this::toReview);
			reviewButton.setEnabled(env != null && projectId != null && !busy);
			if (env != null) {
				OperateUi.guard(reviewButton, env);
			}
			int boxH = Math.max(40, Math.min(110, height - 230));
			d.labelled(labelOf("t3craft.operate.launch.env"), envSelect)
					.labelled(labelOf("t3craft.operate.launch.project"), projectSelect)
					.labelled(labelOf("t3craft.operate.launch.model"), modelSelect)
					.text(T3Draw.tr("t3craft.operate.launch.prompt"), Tone.MUTED)
					.widget(boxH, r -> {
						MultiLineEditBox box = MultiLineEditBox.builder().setX(r.x()).setY(r.y())
								.setPlaceholder(Component.translatable("t3craft.operate.launch.prompt.hint").withStyle(ChatFormatting.DARK_GRAY))
								.build(font, r.w(), r.h(), Component.translatable("t3craft.operate.launch.prompt"));
						box.setCharacterLimit(ThreadLaunch.MAX_PROMPT);
						box.setValue(prompt);
						box.setValueListener(v -> prompt = v);
						promptBox = box;
						return box;
					})
					.buttons(reviewButton, new T3Button(cancel, this::onClose));
		} else {
			T3Button launch = new T3Button(T3Draw.tr("t3craft.operate.launch.button"), T3Button.Style.PRIMARY, this::launch);
			launch.setEnabled(!busy && result == null);
			if (env != null) {
				OperateUi.guard(launch, env);
			}
			T3Button back = new T3Button(T3Draw.tr("t3craft.operate.launch.back"), () -> {
				review = false;
				result = null;
				message = "";
				rebuildWidgets();
			});
			back.setEnabled(!busy);
			ThreadLaunch.ProjectChoice p = project();
			d.text(T3Draw.tr("t3craft.operate.launch.summary"), Tone.WARN)
					.kvKey("t3craft.operate.launch.env", envText())
					.kvKey("t3craft.operate.launch.project", p == null ? "—" : p.label())
					.kvKey("t3craft.operate.launch.model", modelText())
					.kvKey("t3craft.operate.launch.summary.title", ThreadLaunch.title(prompt))
					.kvKey("t3craft.operate.launch.summary.prompt", ThreadLaunch.promptSummary(prompt))
					.text(preview(), Tone.CODE)
					.text(T3Draw.tr("t3craft.operate.launch.summary.mode"), Tone.MUTED)
					.buttons(launch, back);
			String newThread = result == null ? null : ThreadLaunch.threadId(result);
			if (newThread != null && resultEnv != null) {
				String openEnv = resultEnv;
				d.control(new T3Button(T3Draw.tr("t3craft.operate.launch.open"), () -> {
					T3CraftClient c = T3CraftClient.get();
					if (c != null) {
						SearchClient.open(c, openEnv, newThread);
					}
				}));
			}
		}
		return d.text(this::statusText, this::statusTone, 3).onCancel(this::onClose);
	}

	/** The first characters of the prompt, as far as a summary page can show. */
	private String preview() {
		String p = prompt.strip();
		return p.length() <= 360 ? p : p.substring(0, 360) + "…";
	}

	/** Why "Review" is disabled, until a message replaces it. */
	private String statusText() {
		if (!message.isEmpty()) {
			return message;
		}
		if (review) {
			return "";
		}
		return env == null ? T3Draw.tr("t3craft.operate.launch.no_envs") : projectId == null ? T3Draw.tr("t3craft.operate.launch.no_projects") : "";
	}

	private Tone statusTone() {
		return message.isEmpty() ? Tone.MUTED : messageTone;
	}

	private void toReview() {
		String err = ThreadLaunch.validatePrompt(prompt);
		if (err != null) {
			message = err;
			messageTone = Tone.ERROR;
			return;
		}
		message = "";
		result = null;
		review = true;
		rebuildWidgets();
	}

	private void launch() {
		if (busy || env == null || projectId == null) {
			return;
		}
		busy = true;
		String launchEnv = env;
		message = T3Draw.tr("t3craft.operate.launch.sending");
		messageTone = Tone.WARN;
		rebuildWidgets();
		LaunchClient.launch(new ThreadLaunch.Request(launchEnv, projectId, prompt, model), OperateGate.Origin.UI, this, r -> {
			busy = false;
			result = r;
			resultEnv = launchEnv;
			message = r.ok() ? "Launched." : r.outcome().wire + ": " + r.message();
			messageTone = r.ok() ? Tone.OK : Tone.ERROR;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.sendSystemMessage(LaunchClient.resultLine(launchEnv, r)); // clickable "[open chat]" link
			}
			if (r.ok()) {
				prompt = "";
			}
			rebuildWidgets();
		});
	}
}
