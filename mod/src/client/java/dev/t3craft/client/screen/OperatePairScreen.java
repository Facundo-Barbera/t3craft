package dev.t3craft.client.screen;

import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.Pairing;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * "Enable control" for one environment paired read-only (upgrade): the risk warning, the exact host
 * command (always with explicit {@code --scope orchestration:read --scope orchestration:operate})
 * and a field for the one-time link or code. The operate session is stored apart from the
 * read-only one, which is kept; nothing is shown or logged. New pairings already get control by
 * default ({@link PairScreen}). A kit dialog ({@link T3DialogScreen}, docs/features/ui-kit.md).
 */
public final class OperatePairScreen extends T3DialogScreen {
	private final Environments.Environment env;
	private T3Field link;
	private T3Button enableButton;
	private String message = "";
	private Tone messageTone = Tone.MUTED;
	private boolean busy;

	public OperatePairScreen(@Nullable Screen parent, Environments.Environment env) {
		super(Component.translatable("t3craft.operate.enable.title", env.label()), parent);
		this.env = env;
	}

	public static String hostCommand(Environments.Environment env) {
		return Pairing.hostCommand(Pairing.OPERATE_SCOPES, "T3Craft operate", env.url());
	}

	@Override
	protected T3Dialog buildDialog() {
		if (link == null) {
			link = new T3Field(font, "Pairing link or code").maxLength(4096).hint("https://host/pair#token=…  or the code");
		}
		enableButton = new T3Button(T3Draw.tr("t3craft.operate.enable.button"), T3Button.Style.DANGER, this::submit);
		enableButton.setEnabled(!busy);
		return new T3Dialog(title.getString(), 420)
				.text(T3Draw.tr("t3craft.operate.enable.warning", env.label()), Tone.ERROR)
				.gap(2)
				.text(T3Draw.tr("t3craft.operate.enable.host"), Tone.MUTED)
				.text(hostCommand(env), Tone.CODE)
				.gap(2)
				.field("Pairing link or code", link)
				.buttons(enableButton, new T3Button(CommonComponents.GUI_CANCEL.getString(), this::onClose))
				.text(() -> message, () -> messageTone, 3) // pairing errors are often wider than the window
				.initialFocus(link)
				.defaultAction(this::submit);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		enableButton.setEnabled(false);
		message = "Pairing…";
		messageTone = Tone.WARN;
		OperateClient.get().enable(env.id(), link.value(), OperateGate.Origin.UI, r -> {
			busy = false;
			enableButton.setEnabled(true);
			message = r.message();
			messageTone = r.ok() ? Tone.OK : Tone.ERROR;
			if (r.ok()) {
				link.setValue(""); // spent one-time credential
			}
		});
	}
}
