package dev.t3craft.client.screen;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.t3.Pairing;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Pair this Minecraft instance with a T3 environment. By default read + control (operate) in a
 * single pairing ({@link Pairing#DEFAULT_SCOPES}); the "Access" switch (or
 * {@code /t3craft pair --read-only}) asks for {@code orchestration:read} only. Paste the pairing
 * link ({@code https://host/pair#token=…}) or the bare code plus the host URL. The host command
 * shown always carries explicit {@code --scope}s ({@link Pairing#pairingHostCommand}). The session
 * is stored in {@code config/t3craft/credentials/}; nothing is shown or logged.
 *
 * <p>A kit dialog ({@link T3DialogScreen}, docs/features/ui-kit.md): three {@link T3Field}s, the access
 * {@link T3Toggle}, Pair / Cancel, and a status line that follows the pairing.
 */
public final class PairScreen extends T3DialogScreen {
	/** {@code /t3craft pair --read-only}. */
	public static final String READ_ONLY_FLAG = "--read-only";

	private boolean readOnly;
	private T3Field label;
	private T3Field link;
	private T3Field host;
	private T3Button pairButton;
	private String message = "";
	private Tone messageTone = Tone.MUTED;
	private boolean busy;

	public PairScreen(@Nullable Screen parent) {
		this(parent, false);
	}

	public PairScreen(@Nullable Screen parent, boolean readOnly) {
		super(Component.translatable("t3craft.pair.title"), parent);
		this.readOnly = readOnly;
	}

	@Override
	protected T3Dialog buildDialog() {
		if (label == null) { // the fields live as long as the screen: a rebuild keeps what was typed
			label = new T3Field(font, "Environment name").maxLength(32).hint("Name shown on the villagers, e.g. my-vps");
			link = new T3Field(font, "Pairing link or code").maxLength(4096).hint("https://host/pair#token=…  or the code");
			host = new T3Field(font, "T3 URL (only with a bare code)").maxLength(512)
					.hint("Only for a bare code: https://host.ts.net or http://host:3773");
		}
		T3Toggle access = new T3Toggle(T3Draw.tr(readOnly ? "t3craft.pair.mode.read" : "t3craft.pair.mode.control"), !readOnly, control -> {
			readOnly = !control;
			rebuildWidgets(); // the host command and the button follow the access
		});
		access.setTooltip(T3Draw.tr("t3craft.pair.mode.tooltip"));
		access.setEnabled(!busy);
		pairButton = new T3Button(T3Draw.tr(readOnly ? "t3craft.pair.button.read" : "t3craft.pair.button"), T3Button.Style.PRIMARY, this::submit);
		pairButton.setEnabled(!busy);
		T3Dialog d = new T3Dialog(title.getString(), 400)
				.text(T3Draw.tr("t3craft.pair.host"), Tone.MUTED)
				.text(Pairing.pairingHostCommand(readOnly, null), Tone.CODE)
				.gap(2)
				.field("Environment name", label)
				.field("Pairing link or code", link)
				.field("T3 URL (bare code only)", host)
				.control(access)
				.buttons(pairButton, new T3Button(CommonComponents.GUI_CANCEL.getString(), this::onClose));
		if (!readOnly) {
			d.text(T3Draw.tr("t3craft.pair.warning.control"), Tone.WARN);
		}
		return d.text(() -> message, () -> messageTone, 3)
				.initialFocus(link)
				.defaultAction(this::submit);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		pairButton.setEnabled(false);
		message = "Pairing…";
		messageTone = Tone.WARN;
		T3CraftClient.get().pair(label.value(), link.value(), host.value(), readOnly, r -> {
			busy = false;
			pairButton.setEnabled(true);
			message = r.message();
			messageTone = r.ok() ? Tone.OK : Tone.ERROR;
			if (r.ok()) {
				link.setValue(""); // the one-time credential is spent; don't keep it on screen
			}
		});
	}
}
