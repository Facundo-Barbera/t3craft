package dev.t3craft.client.t3ui.actions;

import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * "Register project" dialog, like T3 Code's add-project flow (command palette "Add project" /
 * {@code AddProjectToMachineDialog}): environment, absolute workspace path on that machine, name
 * (defaults to the folder name) and "create the folder if missing". "Register" sends
 * {@code projects.mutate project.create} ({@link RegisterProjectForm}) through {@code OperateGate};
 * on success {@link Registered#onRegistered} runs with T3's project id and the dialog returns to
 * its parent. The new project reaches every list through the shell stream.
 *
 * <p>A kit dialog ({@link T3DialogScreen}, docs/features/ui-kit.md): an environment {@link T3Select}, two
 * {@link T3Field}s, a {@link T3Toggle} and Register / Cancel, with a status line that follows the form
 * (the folder name it will use, the first problem once Register was pressed, the answer of T3).
 */
public final class RegisterProjectDialog extends T3DialogScreen {
	@FunctionalInterface
	public interface Registered {
		void onRegistered(String envId, String projectId);
	}

	private static final int W = 380;

	private record Feedback(String text, Tone tone) {
	}

	private final Registered registered;
	private final RegisterProjectForm form = new RegisterProjectForm();
	/** The fields live as long as the screen: a rebuild keeps what was typed. */
	private @Nullable T3Field pathField;
	private @Nullable T3Field titleField;
	private boolean busy;
	private boolean showErrors;
	private String message = "";
	private Tone messageTone = Tone.MUTED;

	public RegisterProjectDialog(@Nullable Screen parent, @Nullable String envId, Registered registered) {
		super(Component.translatable("t3craft.actions.register_project.title"), parent);
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

	/** A "Label: %s" translation as a plain label, for the fields whose value is a control of their own. */
	private static String label(String langKey) {
		return T3Draw.template(langKey)[0].replaceAll("[:\\s]+$", "");
	}

	@Override
	protected T3Dialog buildDialog() {
		List<String> es = envs();
		if (form.envId == null || !es.contains(form.envId)) {
			form.envId = es.stream().filter(e -> ActionsClient.gate().enabled(e)).findFirst().orElse(es.isEmpty() ? null : es.getFirst());
		}
		if (pathField == null) {
			pathField = new T3Field(font, T3Draw.tr("t3craft.actions.field.path")).maxLength(RegisterProjectForm.MAX_PATH)
					.hint(T3Draw.tr("t3craft.actions.field.path.hint")).onChange(v -> form.workspaceRoot = v);
			pathField.setValue(form.workspaceRoot);
			titleField = new T3Field(font, T3Draw.tr("t3craft.actions.field.name")).maxLength(RegisterProjectForm.MAX_TITLE)
					.hint("Defaults to the folder name").onChange(v -> form.title = v);
			titleField.setValue(form.title);
		}

		T3Select<String> env = new T3Select<>(es, ActionsClient::envLabel, form.envId, v -> {
			form.envId = v;
			rebuildWidgets(); // the Register button follows the environment's control
		});
		env.setEnabled(es.size() > 1 && !busy);

		T3Toggle create = new T3Toggle(label("t3craft.actions.field.create_missing"), form.createIfMissing, v -> form.createIfMissing = v);
		create.setTooltip("Off by default: a typo in the path then fails", "instead of creating a folder on the host.");

		boolean can = canOperate();
		String registerLabel = can ? T3Draw.tr("t3craft.actions.register")
				: T3Draw.tr(Objects.requireNonNullElse(ActionGate.disabledKey(ActionsClient.gate().state(form.envId)), "t3craft.actions.enable_control"));
		T3Button register = new T3Button(registerLabel, can ? T3Button.Style.PRIMARY : T3Button.Style.DEFAULT, this::register);
		register.setEnabled(!busy && form.envId != null);

		return new T3Dialog(title.getString(), W)
				.labelled(label("t3craft.actions.field.env"), env)
				.field(T3Draw.tr("t3craft.actions.field.path"), pathField)
				.field(T3Draw.tr("t3craft.actions.field.name"), titleField)
				.control(create)
				.buttons(register, new T3Button(CommonComponents.GUI_CANCEL.getString(), this::onClose))
				.text(() -> feedback().text(), () -> feedback().tone(), 3)
				.initialFocus(pathField)
				.defaultAction(this::register);
	}

	/** What the status line says now: T3's answer, else the first problem (after a Register), else the name the project will get. */
	private Feedback feedback() {
		if (!message.isEmpty()) {
			return new Feedback(message, messageTone);
		}
		if (showErrors) {
			List<String> errs = form.errors(canOperate(), List.of());
			if (!errs.isEmpty()) {
				return new Feedback(errs.getFirst(), Tone.ERROR);
			}
		}
		if (!form.workspaceRoot.isBlank() && form.title.isBlank()) {
			return new Feedback("Project name: " + RegisterProjectForm.inferTitle(form.workspaceRoot), Tone.MUTED);
		}
		return new Feedback("", Tone.MUTED);
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
			message = ""; // the first problem shows live in the status line
			return;
		}
		busy = true;
		String env = form.envId;
		String localId = b.projectId();
		message = T3Draw.tr("t3craft.operate.launch.sending");
		messageTone = Tone.WARN;
		rebuildWidgets(); // Register and the environment select are off while T3 answers
		OperateUi.run(b.action(), OperateGate.Origin.UI, this, r -> done(env, localId, r));
	}

	private void done(String env, @Nullable String localId, OperateResult r) {
		busy = false;
		if (!r.ok()) {
			message = r.outcome().wire + ": " + r.message();
			messageTone = Tone.ERROR;
			rebuildWidgets();
			return;
		}
		String id = RegisterProjectForm.projectId(r);
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() == this) {
			mc.gui.setScreen(parent);
		}
		registered.onRegistered(env, id != null ? id : Objects.requireNonNullElse(localId, ""));
	}
}
