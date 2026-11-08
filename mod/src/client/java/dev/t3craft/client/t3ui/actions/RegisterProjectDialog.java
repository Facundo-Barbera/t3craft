package dev.t3craft.client.t3ui.actions;

import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Register project" dialog, like T3 Code's add-project flow (command palette "Add project" /
 * {@code AddProjectToMachineDialog}): environment, absolute workspace path on that machine, name
 * (defaults to the folder name) and "create the folder if missing". "Register" sends
 * {@code projects.mutate project.create} ({@link RegisterProjectForm}) through {@code OperateGate};
 * on success {@link Registered#onRegistered} runs with T3's project id and the dialog returns to
 * its parent. The new project reaches every list through the shell stream.
 */
public final class RegisterProjectDialog extends Screen {
	@FunctionalInterface
	public interface Registered {
		void onRegistered(String envId, String projectId);
	}

	private static final int W = 360;

	private final @Nullable Screen parent;
	private final Registered registered;
	private final RegisterProjectForm form = new RegisterProjectForm();
	private @Nullable EditBox pathBox;
	private @Nullable EditBox titleBox;
	private boolean busy;
	private boolean showErrors;
	private Component message = Component.empty();

	public RegisterProjectDialog(@Nullable Screen parent, @Nullable String envId, Registered registered) {
		super(Component.translatable("t3craft.actions.register_project.title"));
		this.parent = parent;
		this.registered = registered;
		form.envId = envId;
	}

	/** Opens the dialog over the current screen. Client thread. */
	public static void open(@Nullable String envId, Registered registered) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new RegisterProjectDialog(mc.gui.screen(), envId, registered));
	}

	public RegisterProjectForm form() {
		return form;
	}

	private List<String> envs() {
		return ActionsClient.environments();
	}

	private boolean canOperate() {
		return ActionsClient.gate().enabled(form.envId);
	}

	private int left() {
		return width / 2 - w() / 2;
	}

	private int w() {
		return Math.min(W, width - 24);
	}

	@Override
	protected void init() {
		List<String> es = envs();
		if (form.envId == null || !es.contains(form.envId)) {
			form.envId = es.stream().filter(e -> ActionsClient.gate().enabled(e)).findFirst().orElse(es.isEmpty() ? null : es.getFirst());
		}
		int x = left();
		int w = w();
		int half = w / 2 - 2;
		int y = 34;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.env",
				form.envId == null ? "—" : ActionsClient.envLabel(form.envId)), b -> {
					int i = es.indexOf(form.envId);
					form.envId = es.isEmpty() ? null : es.get((i + 1) % es.size());
					rebuildWidgets();
				}).bounds(x, y, w, 20).build()).active = es.size() > 1 && !busy;
		y += 36;
		pathBox = new EditBox(font, x, y, w, 18, Component.translatable("t3craft.actions.field.path"));
		pathBox.setMaxLength(RegisterProjectForm.MAX_PATH);
		pathBox.setValue(form.workspaceRoot);
		pathBox.setHint(Component.translatable("t3craft.actions.field.path.hint").withStyle(ChatFormatting.DARK_GRAY));
		pathBox.setResponder(v -> {
			form.workspaceRoot = v;
			if (titleBox != null) {
				titleBox.setHint(Component.literal(v.isBlank() ? "" : RegisterProjectForm.inferTitle(v)).withStyle(ChatFormatting.DARK_GRAY));
			}
		});
		addRenderableWidget(pathBox);
		y += 34;
		titleBox = new EditBox(font, x, y, w, 18, Component.translatable("t3craft.actions.field.name"));
		titleBox.setMaxLength(RegisterProjectForm.MAX_TITLE);
		titleBox.setValue(form.title);
		titleBox.setHint(Component.literal(form.workspaceRoot.isBlank() ? "" : RegisterProjectForm.inferTitle(form.workspaceRoot))
				.withStyle(ChatFormatting.DARK_GRAY));
		titleBox.setResponder(v -> form.title = v);
		addRenderableWidget(titleBox);
		y += 26;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.actions.field.create_missing",
				form.createIfMissing ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF), b -> {
					form.createIfMissing = !form.createIfMissing;
					rebuildWidgets();
				}).bounds(x, y, w, 20).build());
		y += 32;
		Component label = canOperate() ? Component.translatable("t3craft.actions.register").withStyle(ChatFormatting.GOLD)
				: Component.translatable(java.util.Objects.requireNonNullElse(ActionGate.disabledKey(ActionsClient.gate().state(form.envId)),
						"t3craft.actions.enable_control")).withStyle(ChatFormatting.YELLOW);
		addRenderableWidget(Button.builder(label, b -> register()).bounds(x, y, half, 20).build()).active = !busy && form.envId != null;
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w - half, y, half, 20).build());
		setInitialFocus(pathBox);
	}

	private void register() {
		if (busy) {
			return;
		}
		if (!canOperate()) {
			ActionsClient.enableControl(this, form.envId);
			return;
		}
		showErrors = true;
		RegisterProjectForm.Built b = form.build(true, List.of(), RegisterProjectForm.Ids::random);
		if (b.action() == null) {
			message = Component.literal(b.error()).withStyle(ChatFormatting.RED);
			return;
		}
		busy = true;
		String env = form.envId;
		String localId = b.projectId();
		message = Component.translatable("t3craft.operate.launch.sending").withStyle(ChatFormatting.YELLOW);
		rebuildWidgets();
		OperateUi.run(b.action(), OperateGate.Origin.UI, this, r -> done(env, localId, r));
	}

	private void done(String env, @Nullable String localId, OperateResult r) {
		busy = false;
		if (!r.ok()) {
			message = Component.literal(r.outcome().wire + ": " + r.message()).withStyle(ChatFormatting.RED);
			rebuildWidgets();
			return;
		}
		String id = RegisterProjectForm.projectId(r);
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(parent);
		registered.onRegistered(env, id != null ? id : java.util.Objects.requireNonNullElse(localId, ""));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = w();
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, 14, 0xFFFFFFFF);
		if (pathBox != null) {
			g.text(font, Component.translatable("t3craft.actions.field.path"), pathBox.getX(), pathBox.getY() - 10, 0xFFA0A0A0);
		}
		if (titleBox != null) {
			g.text(font, Component.translatable("t3craft.actions.field.name"), titleBox.getX(), titleBox.getY() - 10, 0xFFA0A0A0);
		}
		Component bottom = message;
		if (bottom.getString().isEmpty() && showErrors) {
			List<String> errs = form.errors(canOperate(), List.of());
			if (!errs.isEmpty()) {
				bottom = Component.literal(errs.getFirst()).withStyle(ChatFormatting.RED);
			}
		}
		int my = height - 30;
		for (FormattedCharSequence s : font.split(bottom, w)) {
			g.centeredText(font, s, width / 2, my, 0xFFFFFFFF);
			my += font.lineHeight;
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
