package dev.t3craft.client.screen;

import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.t3.Environments;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.Pairing;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Enable control (operate)…" for one already paired environment: the risk warning, the exact host
 * command (always with explicit {@code --scope orchestration:read --scope orchestration:operate})
 * and a field for the one-time link or code. The operate session is stored apart from the
 * read-only one; nothing is shown or logged.
 */
public final class OperatePairScreen extends Screen {
	private final @Nullable Screen parent;
	private final Environments.Environment env;
	private EditBox link;
	private Button enableButton;
	private Component message = Component.empty();
	private boolean busy;

	public OperatePairScreen(@Nullable Screen parent, Environments.Environment env) {
		super(Component.translatable("t3craft.operate.enable.title", env.label()));
		this.parent = parent;
		this.env = env;
	}

	public static String hostCommand(Environments.Environment env) {
		return Pairing.hostCommand(Pairing.OPERATE_SCOPES, "T3Craft operate", env.url());
	}

	private int fieldY() {
		return height / 2 + 4;
	}

	@Override
	protected void init() {
		int w = Math.min(380, width - 40);
		int x = width / 2 - w / 2;
		link = new EditBox(font, x, fieldY(), w, 20, link, Component.literal("Pairing link or code"));
		link.setMaxLength(4096);
		link.setHint(Component.literal("https://host/pair#token=…  or the code").withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(link);
		enableButton = addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.enable.button").withStyle(ChatFormatting.RED), b -> submit())
				.bounds(x, fieldY() + 28, w / 2 - 4, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w / 2 + 4, fieldY() + 28, w / 2 - 4, 20).build());
		enableButton.active = !busy;
		setInitialFocus(link);
	}

	private void submit() {
		if (busy) {
			return;
		}
		busy = true;
		enableButton.active = false;
		message = Component.literal("Pairing…").withStyle(ChatFormatting.YELLOW);
		OperateClient.get().enable(env.id(), link.getValue(), OperateGate.Origin.UI, r -> {
			busy = false;
			if (enableButton != null) {
				enableButton.active = true;
			}
			message = Component.literal(r.message()).withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
			if (r.ok()) {
				link.setValue(""); // spent one-time credential
			}
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(380, width - 40);
		int x = width / 2 - w / 2;
		int y = 16;
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, y, 0xFFFFFFFF);
		y += 16;
		List<FormattedCharSequence> warn = font.split(Component.translatable("t3craft.operate.enable.warning", env.label()).withStyle(ChatFormatting.RED), w);
		for (FormattedCharSequence s : warn) {
			g.text(font, s, x, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
		y += 6;
		g.text(font, Component.translatable("t3craft.operate.enable.host").withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFFFF);
		y += 11;
		for (FormattedCharSequence s : font.split(Component.literal(hostCommand(env)).withStyle(ChatFormatting.AQUA), w)) {
			g.text(font, s, x, Math.min(y, fieldY() - 22), 0xFFFFFFFF);
			y += font.lineHeight;
		}
		g.text(font, "Pairing link or code", x, fieldY() - 10, 0xFFA0A0A0);
		g.centeredText(font, message, width / 2, fieldY() + 56, 0xFFFFFFFF);
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
