package dev.t3craft.client.t3ui.actions;

import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadLaunch;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "New thread" dialog, like T3 Code's draft thread: environment, project, model / provider /
 * effort / access / plan ({@link ModelPicker} in new-thread mode, whose choice becomes the default
 * for the next new thread), workspace (Local or New worktree from a base branch, optional branch
 * name: {@code BranchToolbarEnvModeSelector} + {@code WorktreeBaseBranchPicker}) and the first
 * message. "Create" sends one {@code orchestration.launchThread} ({@link NewThreadForm}) through
 * {@code OperateGate}; on success {@link Created#onCreated} runs (default: the new thread is
 * selected in the chat, {@link ActionsClient#selectThread}). Validation is shown live under the
 * form. Without control, "Create" reads "Enable control" and opens the enable/pair screen.
 * Replaces {@code NewThreadScreen} ({@code NewThreadScreen.open} now opens this).
 */
public final class NewThreadDialog extends Screen {
	@FunctionalInterface
	public interface Created {
		void onCreated(String envId, String threadId);
	}

	private static final int W = 380;

	private final @Nullable Screen parent;
	private final Created created;
	private final NewThreadForm form = new NewThreadForm();
	private @Nullable ModelPicker picker;
	private @Nullable MultiLineEditBox promptBox;
	private @Nullable EditBox baseRefBox;
	private @Nullable EditBox branchBox;
	private boolean busy;
	private Component message = Component.empty();
	private boolean showErrors;

	public NewThreadDialog(@Nullable Screen parent, @Nullable String envId, @Nullable String projectId, Created created) {
		super(Component.translatable("t3craft.actions.new_thread.title"));
		this.parent = parent;
		this.created = created;
		form.envId = envId;
		form.projectId = projectId;
	}

	/** Opens the dialog; the created thread is selected in the chat. Client thread. */
	public static void open(@Nullable String envId, @Nullable String projectId) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new NewThreadDialog(mc.gui.screen(), envId, projectId, ActionsClient::selectThread));
	}

	public NewThreadForm form() {
		return form;
	}

	// ---- choices --------------------------------------------------------------------------------

	private List<String> envs() {
		return ActionsClient.environments();
	}

	private List<ProjectsService.Project> projects() {
		return form.envId == null ? List.of() : ActionsClient.projects().byEnv(form.envId);
	}

	private void normalize() {
		List<String> es = envs();
		if (form.envId == null || !es.contains(form.envId)) {
			// Prefer an environment with control.
			form.envId = es.stream().filter(e -> ActionsClient.gate().enabled(e)).findFirst().orElse(es.isEmpty() ? null : es.getFirst());
		}
		List<ProjectsService.Project> ps = projects();
		if (form.projectId == null || ps.stream().noneMatch(p -> p.projectId().equals(form.projectId))) {
			form.projectId = ps.isEmpty() ? null : ps.getFirst().projectId();
		}
	}

	private static <T> @Nullable T next(List<T> list, @Nullable T cur) {
		if (list.isEmpty()) {
			return null;
		}
		return list.get((list.indexOf(cur) + 1) % list.size());
	}

	private int left() {
		return width / 2 - w() / 2;
	}

	private int w() {
		return Math.min(W, width - 24);
	}

	private boolean canOperate() {
		return ActionsClient.gate().enabled(form.envId);
	}

	// ---- layout ---------------------------------------------------------------------------------

	@Override
	protected void init() {
		normalize();
		int x = left();
		int w = w();
		int half = w / 2 - 2;
		int y = 30;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.env",
				form.envId == null ? "—" : ActionsClient.envLabel(form.envId)), b -> {
					form.envId = next(envs(), form.envId);
					form.projectId = null;
					rebuildWidgets();
				}).bounds(x, y, half, 18).build()).active = envs().size() > 1 && !busy;
		ProjectsService.Project p = projects().stream().filter(q -> q.projectId().equals(form.projectId)).findFirst().orElse(null);
		addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.project", p == null ? "—" : p.label()), b -> {
			form.projectId = next(projects().stream().map(ProjectsService.Project::projectId).toList(), form.projectId);
			rebuildWidgets();
		}).bounds(x + w - half, y, half, 18).build()).active = projects().size() > 1 && !busy;
		y += 22;
		// Model picker (drawn by this screen, on top).
		if (picker == null) {
			picker = ModelPicker.forNewThread(font, form.envId, pick -> form.pick = pick);
		}
		picker.setEnv(form.envId);
		form.pick = picker.pick();
		picker.setBounds(x, y, w, 16);
		y += 22;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.workspace", form.workspace.label), b -> {
			form.workspace = form.workspace == NewThreadForm.Workspace.LOCAL ? NewThreadForm.Workspace.WORKTREE : NewThreadForm.Workspace.LOCAL;
			rebuildWidgets();
		}).bounds(x, y, half, 18).build()).active = !busy;
		if (form.workspace == NewThreadForm.Workspace.WORKTREE) {
			addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.from_origin",
					form.startFromOrigin ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
						form.startFromOrigin = !form.startFromOrigin;
						rebuildWidgets();
					}).bounds(x + w - half, y, half, 18).build());
			y += 22;
			baseRefBox = field(x, y, half, form.baseRef, "t3craft.actions.field.base_ref", v -> form.baseRef = v);
		} else {
			baseRefBox = null;
			y += 22;
		}
		branchBox = field(form.workspace == NewThreadForm.Workspace.WORKTREE ? x + w - half : x, y,
				form.workspace == NewThreadForm.Workspace.WORKTREE ? half : w, form.branch, "t3craft.actions.field.branch", v -> form.branch = v);
		y += 26;
		int boxH = Math.max(36, height - y - 58);
		MultiLineEditBox box = MultiLineEditBox.builder().setX(x).setY(y)
				.setPlaceholder(Component.translatable("t3craft.actions.field.prompt.hint").withStyle(ChatFormatting.DARK_GRAY))
				.build(font, w, boxH, Component.translatable("t3craft.actions.field.prompt"));
		box.setCharacterLimit(ThreadLaunch.MAX_PROMPT);
		box.setValue(form.prompt);
		box.setValueListener(v -> form.prompt = v);
		promptBox = addRenderableWidget(box);
		y += boxH + 6;
		Component createLabel = canOperate() ? Component.translatable("t3craft.actions.create").withStyle(ChatFormatting.GOLD)
				: Component.translatable(java.util.Objects.requireNonNullElse(ActionGate.disabledKey(ActionsClient.gate().state(form.envId)),
						"t3craft.actions.enable_control")).withStyle(ChatFormatting.YELLOW);
		addRenderableWidget(Button.builder(createLabel, b -> create()).bounds(x, y, half, 20).build()).active = !busy && form.envId != null;
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w - half, y, half, 20).build());
		setInitialFocus(box);
	}

	private EditBox field(int x, int y, int w, String value, String key, java.util.function.Consumer<String> onChange) {
		EditBox f = new EditBox(font, x, y + 9, w, 16, Component.translatable(key));
		f.setMaxLength(200);
		f.setValue(value);
		f.setHint(Component.translatable(key + ".hint").withStyle(ChatFormatting.DARK_GRAY));
		f.setResponder(onChange);
		return addRenderableWidget(f);
	}

	// ---- actions --------------------------------------------------------------------------------

	private void create() {
		if (busy) {
			return;
		}
		if (!canOperate()) {
			ActionsClient.enableControl(this, form.envId);
			return;
		}
		showErrors = true;
		ThreadLaunch.ModelChoice def = form.envId == null ? null : ActionsClient.projectDefault(form.envId, form.projectId);
		NewThreadForm.Built b = form.build(true, def, ThreadLaunch.Ids::random);
		if (b.action() == null) {
			message = Component.literal(b.error()).withStyle(ChatFormatting.RED);
			return;
		}
		busy = true;
		String env = form.envId;
		message = Component.translatable("t3craft.operate.launch.sending").withStyle(ChatFormatting.YELLOW);
		rebuildWidgets();
		OperateUi.run(b.action(), OperateGate.Origin.UI, this, r -> done(env, r));
	}

	private void done(String env, OperateResult r) {
		busy = false;
		if (!r.ok()) {
			message = Component.literal(r.outcome().wire + ": " + r.message()).withStyle(ChatFormatting.RED);
			rebuildWidgets();
			return;
		}
		String threadId = ThreadLaunch.threadId(r);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(LaunchClient.resultLine(env, r));
		}
		mc.gui.setScreen(parent);
		if (threadId != null) {
			created.onCreated(env, threadId);
		}
	}

	// ---- input / render -------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (picker != null && picker.mouseClicked(e.x(), e.y(), e.button())) {
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		return (picker != null && picker.mouseScrolled(x, y, sy)) || super.mouseScrolled(x, y, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (picker != null && picker.keyPressed(e)) {
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = left();
		int w = w();
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, 14, 0xFFFFFFFF);
		if (baseRefBox != null) {
			g.text(font, Component.translatable("t3craft.actions.field.base_ref"), baseRefBox.getX(), baseRefBox.getY() - 9, 0xFFA0A0A0);
		}
		if (branchBox != null) {
			g.text(font, Component.translatable("t3craft.actions.field.branch"), branchBox.getX(), branchBox.getY() - 9, 0xFFA0A0A0);
		}
		Component bottom = message;
		if (bottom.getString().isEmpty()) {
			List<String> errs = form.errors(canOperate(), form.envId == null ? null : ActionsClient.projectDefault(form.envId, form.projectId));
			// Before the first "Create", only the blocking context problems (not "write a prompt").
			if (!errs.isEmpty() && (showErrors || !errs.getFirst().equals(ThreadLaunch.validatePrompt(form.prompt)))) {
				bottom = Component.literal(errs.getFirst()).withStyle(showErrors ? ChatFormatting.RED : ChatFormatting.GRAY);
			}
		}
		int my = height - 26;
		for (FormattedCharSequence s : font.split(bottom, w)) {
			g.centeredText(font, s, width / 2, my, 0xFFFFFFFF);
			my += font.lineHeight;
		}
		if (picker != null) {
			picker.render(g, mouseX, mouseY);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
